#!/usr/bin/env node
// Redact secrets — PreToolUse hook that replaces confidential values in a tool's
// input with REDACTED before the tool runs: passwords, secrets, API keys and
// tokens, private keys, credentials in URLs, IBANs and payment card numbers.
//
// It rewrites the input through `updatedInput`, so a secret never reaches a file,
// a shell command, a commit message or an external service. Fields that locate
// or match existing content (paths, Edit's old_string, search patterns) are left
// untouched, otherwise the tool could no longer find what it targets.
//
// Fails closed: if the hook itself breaks, the tool call is denied.
//
// Usable as a module too (`redact`, `redactInput`) — see redact-secrets.test.mjs.

import fs from 'node:fs';
import { fileURLToPath } from 'node:url';

export const MARK = 'REDACTED';

// Input fields that are never rewritten, wherever they appear in the input.
const UNTOUCHED_FIELDS = new Set([
  'file_path', 'path', 'notebook_path', 'cwd', 'old_string', 'pattern', 'glob', 'type', 'subagent_type',
]);

// Test sources are full of fake credentials; there only the unambiguous token
// formats below are redacted, not `password = "..."`-style assignments.
const TEST_PATH = /(^|[\\/])(src[\\/]test[\\/]|__tests__[\\/])|\.(spec|test)\.[cm]?[jt]sx?$/i;

// ---------------------------------------------------------------- unambiguous formats

const TOKEN_PATTERNS = [
  ['private key', /-----BEGIN [A-Z0-9 ]*PRIVATE KEY-----[\s\S]*?-----END [A-Z0-9 ]*PRIVATE KEY-----/g],
  ['Anthropic key', /\bsk-ant-[A-Za-z0-9_-]{20,}/g],
  ['OpenAI key', /\bsk-(?:proj-)?[A-Za-z0-9_-]{32,}/g],
  ['Google API key', /\bAIza[0-9A-Za-z_-]{35}\b/g],
  ['GitHub token', /\b(?:gh[pousr]_[A-Za-z0-9]{36,}|github_pat_[A-Za-z0-9_]{60,})\b/g],
  ['GitLab token', /\bglpat-[A-Za-z0-9_-]{20,}\b/g],
  ['AWS access key', /\b(?:AKIA|ASIA)[0-9A-Z]{16}\b/g],
  ['Slack token', /\bxox[abprs]-[A-Za-z0-9-]{10,}\b/g],
  ['Stripe key', /\b(?:sk|rk|pk)_(?:live|test)_[A-Za-z0-9]{16,}\b/g],
  ['JWT', /\beyJ[A-Za-z0-9_-]{8,}\.eyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\b/g],
];

// `Authorization: Bearer <token>` / `Basic <credentials>`.
const AUTH_HEADER = /\b(Bearer|Basic|Token)(\s+)([A-Za-z0-9._~+/=-]{12,})/g;

// `scheme://user:password@host` — only the password goes.
const URL_CREDENTIALS = /\b([a-z][a-z0-9+.-]*:\/\/[^\s:@/'"]+:)([^\s@/'"]+)(@)/gi;

// ---------------------------------------------------------------- banking

const IBAN = /\b[A-Z]{2}\d{2}(?:[ ]?[A-Z0-9]){11,30}\b/g;
// Card networks only (Visa, Mastercard, Amex, Discover): keeps timestamps and ids out.
const CARD = /\b(?:4\d{3}|5[1-5]\d{2}|2[2-7]\d{2}|6(?:011|5\d{2})|3[47]\d{2})(?:[ -]?\d){11,15}\b/g;

function isIban(candidate) {
  const s = candidate.replace(/ /g, '');
  if (s.length < 15 || s.length > 34) return false;
  const digits = (s.slice(4) + s.slice(0, 4)).replace(/[A-Z]/g, (c) => String(c.charCodeAt(0) - 55));
  let rest = 0;
  for (const d of digits) rest = (rest * 10 + Number(d)) % 97;
  return rest === 1;
}

function isLuhn(candidate) {
  const digits = candidate.replace(/\D/g, '');
  if (digits.length < 13 || digits.length > 19) return false;
  let sum = 0;
  for (let i = 0; i < digits.length; i++) {
    let d = Number(digits[digits.length - 1 - i]);
    if (i % 2 === 1) d = d * 2 > 9 ? d * 2 - 9 : d * 2;
    sum += d;
  }
  return sum % 10 === 0;
}

// ---------------------------------------------------------------- assignments

const KEYWORD = String.raw`(?:password|passwd|passphrase|secret|api[-_.]?key|apikey|access[-_.]?key|private[-_.]?key|client[-_.]?secret|token(?![a-z])|credentials?|cvv|cvc)`;
const KEY_NAME = String.raw`[A-Za-z0-9_.-]*${KEYWORD}[A-Za-z0-9_.-]*`;
// Not inside `${...}`: `${DB_PASSWORD:-default}` is a reference, not an assignment.
const KEY = String.raw`(?<![\w.$\{-])${KEY_NAME}`;

// key = "value" / "key": "value" / key: 'value' — on the same line only
const QUOTED_ASSIGNMENT = new RegExp(String.raw`(["']?${KEY}["']?[ \t]*[:=][ \t]*)(["'\`])([^"'\`\r\n]+)\2`, 'gi');
// KEY=value / key: value / X-Api-Key: value (env, properties, yaml, headers, shell)
const BARE_ASSIGNMENT = new RegExp(String.raw`(${KEY}[ \t]*[:=][ \t]*)([^\s'"\`,;(){}\[\]<>=]+)`, 'gi');
// --password value / --api-key=value
const CLI_FLAG = new RegExp(String.raw`(--${KEY_NAME}(?:=|\s+))(["']?)([^\s'"]+)\2`, 'gi');

// Values that are references or code, not secrets.
function isPlaceholder(value) {
  const v = value.trim();
  return (
    v.length < 4 ||
    v.includes(MARK) ||
    /^(\$|%|\{\{|<.*>$|\*+$|x+$|\.\.\.)/i.test(v) ||
    /^(changeme|example|dummy|placeholder|null|none|nil|true|false|undefined|string|number|boolean|required|optional)$/i.test(v) ||
    /^\d+[lLfFdD]?$/.test(v) || // counts, sizes, lengths
    /^[\^~]?\d+(\.[\w-]+)+$/.test(v) || // versions
    /\s\S+\s/.test(v) || // prose, not a credential
    /^(process\.env|System\.getenv|os\.environ|env\.)/.test(v) ||
    // identifiers and member paths (`newPassword`, `this.password`, `PASSWORD_HINT`, `password!`)
    /^[A-Za-z_$][\w$]*(\.[A-Za-z_$][\w$]*)*[!?]?$/.test(v) && !/\d/.test(v)
  );
}

// ---------------------------------------------------------------- engine

export function redact(text, { testFile = false } = {}) {
  if (typeof text !== 'string' || text.length === 0) return { text, found: [] };
  const found = [];
  const hit = (kind) => found.push(kind);
  let out = text;

  for (const [kind, re] of TOKEN_PATTERNS) {
    out = out.replace(re, (m) => (m.includes(MARK) ? m : (hit(kind), MARK)));
  }
  out = out.replace(AUTH_HEADER, (m, scheme, sp, token) =>
    isPlaceholder(token) ? m : (hit('authorization header'), `${scheme}${sp}${MARK}`));
  out = out.replace(URL_CREDENTIALS, (m, head, pass, at) =>
    isPlaceholder(pass) ? m : (hit('URL credentials'), `${head}${MARK}${at}`));
  out = out.replace(IBAN, (m) => (isIban(m) ? (hit('IBAN'), MARK) : m));
  out = out.replace(CARD, (m) => (isLuhn(m) ? (hit('card number'), MARK) : m));

  if (!testFile) {
    out = out.replace(QUOTED_ASSIGNMENT, (m, head, q, value) =>
      isPlaceholder(value) ? m : (hit('assigned secret'), `${head}${q}${MARK}${q}`));
    out = out.replace(BARE_ASSIGNMENT, (m, head, value) =>
      isPlaceholder(value) ? m : (hit('assigned secret'), `${head}${MARK}`));
    out = out.replace(CLI_FLAG, (m, head, q, value) =>
      isPlaceholder(value) ? m : (hit('command-line secret'), `${head}${q}${MARK}${q}`));
  }
  return { text: out, found };
}

// Walks the tool input and redacts every string, except the untouched fields.
export function redactInput(toolInput) {
  const filePath = String(toolInput?.file_path ?? toolInput?.notebook_path ?? '');
  const testFile = TEST_PATH.test(filePath);
  const found = [];

  const walk = (value, key) => {
    if (UNTOUCHED_FIELDS.has(key)) return value;
    if (typeof value === 'string') {
      const r = redact(value, { testFile });
      found.push(...r.found);
      return r.text;
    }
    if (Array.isArray(value)) return value.map((v) => walk(v, key));
    if (value && typeof value === 'object') {
      return Object.fromEntries(Object.entries(value).map(([k, v]) => [k, walk(v, k)]));
    }
    return value;
  };

  return { input: walk(toolInput, ''), found };
}

// ---------------------------------------------------------------- hook entry point

function main() {
  let payload;
  try {
    payload = JSON.parse(fs.readFileSync(0, 'utf8') || '{}');
    const { input, found } = redactInput(payload.tool_input ?? {});
    if (found.length === 0) return;
    const kinds = [...new Set(found)].join(', ');
    process.stdout.write(JSON.stringify({
      systemMessage: `Redact secrets: ${found.length} value(s) replaced by ${MARK} in the ${payload.tool_name} input (${kinds}).`,
      hookSpecificOutput: {
        hookEventName: 'PreToolUse',
        updatedInput: input,
        additionalContext: `Confidential values (${kinds}) were replaced by ${MARK} in this ${payload.tool_name} call before it ran. Never write real secrets: reference them through environment variables or placeholders.`,
      },
    }));
  } catch (e) {
    // Fail closed: if the redaction cannot be trusted, the call does not run.
    process.stdout.write(JSON.stringify({
      hookSpecificOutput: {
        hookEventName: 'PreToolUse',
        permissionDecision: 'deny',
        permissionDecisionReason: `Redact secrets hook failed, the call was blocked: ${e.message}`,
      },
    }));
  }
}

if (process.argv[1] && fileURLToPath(import.meta.url) === fs.realpathSync(process.argv[1])) main();
