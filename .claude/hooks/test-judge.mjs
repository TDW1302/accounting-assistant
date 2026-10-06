#!/usr/bin/env node
// Test judge — mechanical half of the Stop gate (the other half is the read-only
// agent hook driven by .claude/hooks/test-judge-agent.md).
//
//   session-start  (SessionStart)  remember the commit the session started from
//   snapshot       (PostToolUse)   write the diff under review for the agent judge
//   stop           (Stop)          check the test/coverage reports, count refusals
//   baseline       (manual)        ratchet the coverage baseline up to the reports
//
// A stop is refused when a test suite touched by the session was not run after
// the last change, fails, lost tests, gained skipped tests, or lost coverage
// against .claude/test-judge/coverage-baseline.json. Refusals from either half
// are counted through `stop_hook_active`; after MAX_REFUSALS in a row a report
// is written to .claude/test-judge/reports/ and the session stops with an error.

import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';

const MAX_REFUSALS = 5;
const MAX_DIFF_CHARS = 150_000;
const PCT_TOLERANCE = 0.005; // percentages are compared rounded to 2 decimals

const projectDir = process.env.CLAUDE_PROJECT_DIR || process.cwd();
const judgeDir = path.join(projectDir, '.claude', 'test-judge');
const stateDir = path.join(judgeDir, 'state');
const reportDir = path.join(judgeDir, 'reports');
const baselineRel = '.claude/test-judge/coverage-baseline.json';
const baselinePath = path.join(projectDir, baselineRel);

const BACKEND = {
  name: 'backend',
  prefix: 'backend/',
  command: 'cd backend && ./gradlew test',
  resultsDir: 'backend/build/test-results/test',
  coverage: 'backend/build/reports/jacoco/test/jacocoTestReport.xml',
};
const FRONTEND = {
  name: 'frontend',
  prefix: 'frontend/',
  command: 'cd frontend && npx ng test --watch=false',
  results: 'frontend/test-results/junit.xml',
  coverage: 'frontend/coverage/frontend/coverage-summary.json',
};
const SCOPES = [BACKEND, FRONTEND];

// Changing these always puts the work under review, even without a test scope.
const GUARDED = ['.claude/hooks/', '.claude/settings.json', baselineRel];
const IGNORED = ['.claude/test-judge/state/', '.claude/test-judge/reports/'];
const NOT_IN_DIFF = [...IGNORED, '**/package-lock.json']; // listed as changed, too noisy to review

let currentSessionId = 'unknown';

// ---------------------------------------------------------------- helpers

function readStdin() {
  try {
    const raw = fs.readFileSync(0, 'utf8');
    return raw.trim() ? JSON.parse(raw) : {};
  } catch {
    return {};
  }
}

function tryGit(...args) {
  try {
    return execFileSync('git', args, {
      cwd: projectDir,
      encoding: 'utf8',
      maxBuffer: 64 * 1024 * 1024,
      stdio: ['ignore', 'pipe', 'pipe'],
    });
  } catch {
    return null;
  }
}

function abs(rel) {
  return path.join(projectDir, rel);
}

function rel(file) {
  return path.relative(projectDir, file).split(path.sep).join('/');
}

function mtime(relPath) {
  try {
    return fs.statSync(abs(relPath)).mtimeMs;
  } catch {
    return null;
  }
}

function readJson(file, fallback) {
  try {
    return JSON.parse(fs.readFileSync(file, 'utf8'));
  } catch {
    return fallback;
  }
}

function writeJson(file, data) {
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, JSON.stringify(data, null, 2) + '\n');
}

function pct(covered, total) {
  return total === 0 ? 100 : Math.round((covered / total) * 10000) / 100;
}

function attr(tag, name) {
  const m = tag.match(new RegExp(`\\b${name}="([^"]*)"`));
  return m ? Number(m[1]) : 0;
}

function emit(obj) {
  process.stdout.write(JSON.stringify(obj));
}

function statePath(sessionId) {
  return path.join(stateDir, `${sessionId || 'unknown'}.json`);
}

function loadState(sessionId) {
  const state = readJson(statePath(sessionId), {});
  if (!state.baseRef || tryGit('cat-file', '-e', `${state.baseRef}^{commit}`) === null) {
    state.baseRef = tryGit('rev-parse', 'HEAD')?.trim();
  }
  return state;
}

function changedFiles(baseRef) {
  const tracked = (tryGit('diff', '--name-only', baseRef) ?? '').split('\n').filter(Boolean);
  const untracked = (tryGit('ls-files', '--others', '--exclude-standard') ?? '').split('\n').filter(Boolean);
  const changed = [...new Set([...tracked, ...untracked])].filter((f) => !IGNORED.some((p) => f.startsWith(p)));
  return { changed, untracked };
}

function isGuarded(f) {
  return GUARDED.some((g) => f === g || f.startsWith(g));
}

// ---------------------------------------------------------------- measurements

function measureBackend() {
  const dir = abs(BACKEND.resultsDir);
  let files = [];
  try {
    files = fs.readdirSync(dir).filter((f) => /^TEST-.*\.xml$/.test(f));
  } catch {
    // no results yet
  }
  if (files.length === 0) return { error: `no JUnit results in ${BACKEND.resultsDir}` };

  const m = { tests: 0, skipped: 0, failures: 0, errors: 0, resultsAt: Infinity };
  for (const f of files) {
    const xml = fs.readFileSync(path.join(dir, f), 'utf8');
    const tag = xml.match(/<testsuite\b[^>]*>/)?.[0] ?? '';
    m.tests += attr(tag, 'tests');
    m.skipped += attr(tag, 'skipped');
    m.failures += attr(tag, 'failures');
    m.errors += attr(tag, 'errors');
    m.resultsAt = Math.min(m.resultsAt, fs.statSync(path.join(dir, f)).mtimeMs);
  }

  const covAt = mtime(BACKEND.coverage);
  if (covAt === null) return { ...m, error: `no JaCoCo report at ${BACKEND.coverage}` };
  const xml = fs.readFileSync(abs(BACKEND.coverage), 'utf8');
  // Report-level counters are the last ones in the file.
  const last = (type) => [...xml.matchAll(new RegExp(`<counter type="${type}"[^>]*/>`, 'g'))].pop()?.[0] ?? '';
  const line = last('LINE');
  const branch = last('BRANCH');
  m.linePct = pct(attr(line, 'covered'), attr(line, 'covered') + attr(line, 'missed'));
  m.branchPct = pct(attr(branch, 'covered'), attr(branch, 'covered') + attr(branch, 'missed'));
  m.coverageAt = covAt;
  return m;
}

function measureFrontend() {
  const resultsAt = mtime(FRONTEND.results);
  if (resultsAt === null) return { error: `no JUnit results at ${FRONTEND.results}` };
  const xml = fs.readFileSync(abs(FRONTEND.results), 'utf8');
  const root = xml.match(/<testsuites\b[^>]*>/)?.[0] ?? '';
  const m = {
    tests: attr(root, 'tests'),
    failures: attr(root, 'failures'),
    errors: attr(root, 'errors'),
    skipped: [...xml.matchAll(/<testsuite\b[^>]*>/g)].reduce((s, t) => s + attr(t[0], 'skipped'), 0),
    resultsAt,
  };

  const covAt = mtime(FRONTEND.coverage);
  if (covAt === null) return { ...m, error: `no coverage summary at ${FRONTEND.coverage}` };
  const total = readJson(abs(FRONTEND.coverage), {}).total ?? {};
  m.linePct = pct(total.lines?.covered ?? 0, total.lines?.total ?? 0);
  m.branchPct = pct(total.branches?.covered ?? 0, total.branches?.total ?? 0);
  m.coverageAt = covAt;
  return m;
}

const MEASURE = { backend: measureBackend, frontend: measureFrontend };

// ---------------------------------------------------------------- baseline

function baselineAt(ref) {
  const raw = tryGit('show', `${ref}:${baselineRel}`);
  try {
    return raw === null ? null : JSON.parse(raw);
  } catch {
    return null;
  }
}

// The bar is the stricter of the baseline committed at session start and the
// working copy: raising it in-session is fine, relaxing it is a refusal.
function effectiveBaseline(baseRef, problems) {
  const atBase = baselineAt(baseRef) ?? {};
  const current = readJson(baselinePath, {});
  const result = {};
  for (const { name } of SCOPES) {
    const b = atBase[name] ?? {};
    const c = current[name] ?? {};
    for (const key of ['tests', 'linePct', 'branchPct', 'skipped']) {
      if (b[key] === undefined) continue;
      const relaxed = c[key] === undefined || (key === 'skipped' ? c[key] > b[key] : c[key] < b[key]);
      if (relaxed) {
        problems.push(`${baselineRel}: ${name}.${key} was changed from ${b[key]} to ${c[key]} — the baseline must never be relaxed.`);
      }
    }
    result[name] = {
      tests: Math.max(b.tests ?? 0, c.tests ?? 0),
      skipped: Math.min(b.skipped ?? Infinity, c.skipped ?? Infinity),
      linePct: Math.max(b.linePct ?? 0, c.linePct ?? 0),
      branchPct: Math.max(b.branchPct ?? 0, c.branchPct ?? 0),
    };
    if (result[name].skipped === Infinity) result[name].skipped = 0;
  }
  return result;
}

// ---------------------------------------------------------------- mechanical checks

function checkScope(scope, files, baseline, problems) {
  const m = MEASURE[scope.name]();
  const run = `\`${scope.command}\``;
  if (m.error) {
    problems.push(`${scope.name}: ${m.error}. Run ${run}.`);
    return m;
  }

  const lastChange = Math.max(0, ...files.map(mtime).filter((t) => t !== null));
  if (m.resultsAt < lastChange || m.coverageAt < lastChange) {
    problems.push(`${scope.name}: the test/coverage reports are older than the last change under ${scope.prefix} — the tests were not run after the work was done. Run ${run}.`);
  }
  if (m.failures + m.errors > 0) {
    problems.push(`${scope.name}: ${m.failures} failing and ${m.errors} erroring test(s). Fix the code (not the tests) and run ${run}.`);
  }

  const b = baseline[scope.name];
  if (m.tests < b.tests) {
    problems.push(`${scope.name}: ${m.tests} tests ran, the baseline is ${b.tests}. Tests were removed or filtered out — run the full suite with ${run}.`);
  }
  if (m.skipped > b.skipped) {
    problems.push(`${scope.name}: ${m.skipped} skipped test(s), the baseline allows ${b.skipped}. Re-enable the skipped tests.`);
  }
  if (m.linePct + PCT_TOLERANCE < b.linePct) {
    problems.push(`${scope.name}: line coverage ${m.linePct}% is below the baseline ${b.linePct}%. Add tests for the new/changed code.`);
  }
  if (m.branchPct + PCT_TOLERANCE < b.branchPct) {
    problems.push(`${scope.name}: branch coverage ${m.branchPct}% is below the baseline ${b.branchPct}%. Add tests for the new/changed code.`);
  }
  return m;
}

// ---------------------------------------------------------------- report

// The agent judge cannot write anything, so its refusals are recovered from the
// transcript: every hook feedback entry since the last real user prompt.
function hookFeedbackFromTranscript(transcriptPath) {
  let lines;
  try {
    lines = fs.readFileSync(transcriptPath, 'utf8').split('\n').filter(Boolean);
  } catch {
    return [];
  }
  const entries = [];
  for (const line of lines) {
    let e;
    try {
      e = JSON.parse(line);
    } catch {
      continue;
    }
    const content = e.message?.content;
    const text = typeof content === 'string' ? content
      : Array.isArray(content) ? content.filter((c) => c.type === 'text').map((c) => c.text).join('\n') : '';
    if (e.type === 'user' && !e.isMeta && text && !/hook/i.test(text) && !e.toolUseResult) {
      entries.length = 0; // a new user prompt starts a new series of refusals
    } else if (e.type !== 'assistant' && /stop hook|hook feedback|TEST JUDGE/i.test(text) || (e.subtype === 'stop_hook_summary' && e.hookErrors?.length)) {
      entries.push(text || JSON.stringify(e.hookErrors));
    }
  }
  return entries;
}

function writeReport(input, state, title, extra) {
  fs.mkdirSync(reportDir, { recursive: true });
  const stamp = new Date().toISOString().replace(/[:.]/g, '-');
  const file = path.join(reportDir, `${stamp}-${String(input.session_id || 'unknown').slice(0, 8)}.md`);
  const lines = [
    `# Test judge report — ${title}`,
    ``,
    `- Date: ${new Date().toISOString()}`,
    `- Session: ${input.session_id}`,
    `- Transcript: ${input.transcript_path ?? 'unknown'}`,
    `- Base commit: ${state.baseRef}`,
    `- HEAD: ${tryGit('rev-parse', 'HEAD')?.trim()}`,
    ``,
  ];
  if (extra) lines.push(extra, ``);
  if (state.history?.length) {
    lines.push(`## Mechanical refusals`, ``);
    for (const h of state.history) lines.push(`### ${h.at}`, ``, ...h.reasons.map((r) => `- ${r}`), ``);
  }
  const feedback = hookFeedbackFromTranscript(input.transcript_path);
  if (feedback.length) {
    lines.push(`## Stop-hook feedback found in the transcript (includes the agent judge)`, ``);
    for (const f of feedback) lines.push('```', f.trim(), '```', ``);
  }
  if (state.lastMeasurements) {
    lines.push(`## Last measurements`, '```json', JSON.stringify(state.lastMeasurements, null, 2), '```', ``);
  }
  const { changed } = changedFiles(state.baseRef);
  lines.push(`## Files changed since the base commit`, ``, ...changed.map((f) => `- ${f}`), ``);
  fs.writeFileSync(file, lines.join('\n'));
  return rel(file);
}

function haltWithReport(input, state, title, extra) {
  const report = writeReport(input, state, title, extra);
  writeJson(statePath(input.session_id), { ...state, refusals: 0, history: [] });
  const msg = `Test judge: ${title}. The session is stopped — see ${report}.`;
  process.stderr.write(msg + '\n');
  emit({ continue: false, stopReason: msg, systemMessage: msg });
}

// ---------------------------------------------------------------- commands

function sessionStart() {
  const input = readStdin();
  const file = statePath(input.session_id);
  if (fs.existsSync(file)) return; // resume/compact: keep the original base
  const head = tryGit('rev-parse', 'HEAD')?.trim();
  if (head) writeJson(file, { baseRef: head, refusals: 0, history: [] });
}

// Writes what the agent judge reviews; PostToolUse runs before Stop, so the
// snapshot taken after the last tool call is the final state of the work.
function snapshot() {
  const input = readStdin();
  const state = loadState(input.session_id);
  if (!state.baseRef) return;
  const { changed, untracked } = changedFiles(state.baseRef);
  const relevant = changed.filter((f) => isGuarded(f) || SCOPES.some((s) => f.startsWith(s.prefix)));

  let diff = tryGit('diff', state.baseRef, '--', '.', ...NOT_IN_DIFF.map((p) => `:(exclude)${p}`)) ?? '';
  if (diff.length > MAX_DIFF_CHARS) {
    diff = diff.slice(0, MAX_DIFF_CHARS) + '\n\n[diff truncated — Read the changed files directly]\n';
  }
  fs.mkdirSync(stateDir, { recursive: true });
  fs.writeFileSync(
    path.join(stateDir, `${input.session_id || 'unknown'}-context.md`),
    [
      `# Work under review`,
      ``,
      `Base commit (session start): ${state.baseRef}`,
      `Code or test-guard files changed: ${relevant.length ? 'YES' : 'NO'}`,
      ``,
      `## Changed files`,
      ...changed.map((f) => `- ${f}${untracked.includes(f) ? ' (new, untracked — not in the diff, Read it)' : ''}`),
      ``,
      `## Diff against the base commit (tracked files)`,
      '```diff',
      diff,
      '```',
      ``,
    ].join('\n'),
  );
}

function stop() {
  const input = readStdin();
  currentSessionId = input.session_id || 'unknown';
  const state = loadState(currentSessionId);
  if (!state.baseRef) return; // not a git checkout: nothing to judge

  // stop_hook_active: this stop follows a refused one (by this hook or the agent judge).
  state.refusals = input.stop_hook_active ? (state.refusals ?? 0) + 1 : 0;
  if (!input.stop_hook_active) state.history = [];
  if (state.refusals >= MAX_REFUSALS) {
    haltWithReport(input, state, `${MAX_REFUSALS} consecutive refusals`);
    return;
  }

  const { changed } = changedFiles(state.baseRef);
  const scopes = SCOPES.filter((s) => changed.some((f) => f.startsWith(s.prefix)));
  const problems = [];
  const baseline = effectiveBaseline(state.baseRef, problems);
  const measurements = {};
  for (const scope of scopes) {
    measurements[scope.name] = checkScope(scope, changed.filter((f) => f.startsWith(scope.prefix)), baseline, problems);
  }
  if (scopes.length) state.lastMeasurements = { measurements, baseline };

  if (problems.length === 0) {
    writeJson(statePath(currentSessionId), state);
    const raised = scopes.filter((s) => {
      const m = measurements[s.name];
      const b = baseline[s.name];
      return m.tests > b.tests || m.linePct > b.linePct + PCT_TOLERANCE || m.branchPct > b.branchPct + PCT_TOLERANCE;
    });
    if (raised.length) {
      emit({ systemMessage: `Test judge: tests/coverage rose for ${raised.map((s) => s.name).join(', ')} — \`node .claude/hooks/test-judge.mjs baseline\` ratchets the baseline.` });
    }
    return;
  }

  state.history = [...(state.history ?? []), { at: new Date().toISOString(), reasons: problems }];
  writeJson(statePath(currentSessionId), state);
  emit({
    decision: 'block',
    reason:
      `TEST JUDGE — refusal ${state.refusals + 1}/${MAX_REFUSALS}. You may not finish yet:\n` +
      problems.map((r) => `- ${r}`).join('\n') +
      `\nFix the cause (never by weakening tests, the test configuration or the baseline), re-run the tests, then finish again.`,
  });
}

// Ratchets the baseline up to the current reports; never lowers it unless --allow-decrease.
function baseline() {
  const allowDecrease = process.argv.includes('--allow-decrease');
  const current = readJson(baselinePath, {});
  for (const [name, measure] of Object.entries(MEASURE)) {
    const m = measure();
    if (m.error || m.failures + m.errors > 0) {
      console.error(`${name}: ${m.error ?? 'failing tests'} — kept as is`);
      continue;
    }
    const next = { tests: m.tests, skipped: m.skipped, linePct: m.linePct, branchPct: m.branchPct };
    const prev = current[name];
    if (prev && !allowDecrease) {
      next.tests = Math.max(next.tests, prev.tests);
      next.skipped = Math.min(next.skipped, prev.skipped);
      next.linePct = Math.max(next.linePct, prev.linePct);
      next.branchPct = Math.max(next.branchPct, prev.branchPct);
    }
    current[name] = next;
  }
  writeJson(baselinePath, current);
  console.log(JSON.stringify(current, null, 2));
}

const commands = { 'session-start': sessionStart, snapshot, stop, baseline };
const command = commands[process.argv[2]];
if (!command) {
  console.error(`usage: test-judge.mjs ${Object.keys(commands).join('|')}`);
  process.exit(1);
}
try {
  command();
} catch (e) {
  // Fail closed: a broken judge must not silently wave the work through.
  if (process.argv[2] !== 'stop') throw e;
  const input = { session_id: currentSessionId };
  haltWithReport(input, readJson(statePath(currentSessionId), {}), 'the hook crashed', `**Error:**\n\n\`\`\`\n${e.stack || e}\n\`\`\``);
}
