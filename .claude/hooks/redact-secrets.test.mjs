// Tests for redact-secrets.mjs — run with `node --test .claude/hooks/*.test.mjs`.
// Fake secrets are assembled at runtime so that this file never contains one
// literally (the hook would otherwise redact its own fixtures).

import assert from 'node:assert/strict';
import { test } from 'node:test';
import { MARK, redact, redactInput } from './redact-secrets.mjs';

const j = (...parts) => parts.join('');
const r = (text, opts) => redact(text, opts).text;

test('unambiguous token formats are redacted everywhere', () => {
  const tokens = [
    j('sk-', 'ant-api03-', 'a'.repeat(40)),
    j('gh', 'p_', 'A1b2'.repeat(9)),
    j('AK', 'IA', 'ABCDEFGHIJ123456'),
    j('AI', 'za', 'B'.repeat(35)),
    j('xo', 'xb-', '1234567890-abcdef'),
    j('sk', '_live_', 'c'.repeat(24)),
    j('ey', 'J', 'h'.repeat(12), '.ey', 'J', 'p'.repeat(12), '.', 's'.repeat(12)),
  ];
  for (const t of tokens) {
    assert.equal(r(`use ${t} here`), `use ${MARK} here`, t.slice(0, 6));
    assert.equal(r(`use ${t} here`, { testFile: true }), `use ${MARK} here`);
  }
});

test('private key blocks are redacted whole', () => {
  const key = j('-----BEGIN ', 'RSA PRIVATE KEY-----\nMIIabc\ndef\n-----END ', 'RSA PRIVATE KEY-----');
  assert.equal(r(`a\n${key}\nb`), `a\n${MARK}\nb`);
});

test('assignments of secrets are redacted, the key is kept', () => {
  const pw = j('S3cr', 'et!x9');
  assert.equal(r(j('DB_PASS', 'WORD=', pw)), j('DB_PASS', 'WORD=', MARK));
  assert.equal(r(j('export FALCO_API_', 'KEY=', pw, ' && run')), j('export FALCO_API_', 'KEY=', MARK, ' && run'));
  assert.equal(r(j('app.falco.app-sec', 'ret=', pw)), j('app.falco.app-sec', 'ret=', MARK));
  assert.equal(r(j('{"api', 'Key": "', pw, '"}')), j('{"api', 'Key": "', MARK, '"}'));
  assert.equal(r(j("const pass", "word = '", pw, "';")), j("const pass", "word = '", MARK, "';"));
  assert.equal(r(j('curl -H "X-Falco-Api-', 'Key: ', pw, '" url')), j('curl -H "X-Falco-Api-', 'Key: ', MARK, '" url'));
  assert.equal(r(j('login --pass', 'word ', pw)), j('login --pass', 'word ', MARK));
  assert.equal(r(j('Authorization: Bear', 'er ', 'abcDEF123456789xyz')), j('Authorization: Bear', 'er ', MARK));
  assert.equal(r(j('postgres://app:', pw, '@db:5432/x')), j('postgres://app:', MARK, '@db:5432/x'));
});

test('references, code and numbers are left alone', () => {
  const untouched = [
    j('spring.datasource.pass', 'word=${DB_PASSWORD:changeme}'),
    j('app.anthropic.api-', 'key=${ANTHROPIC_API_KEY:}'),
    j('pass', 'word: ${{ secrets.GITHUB_TOKEN }}'),
    j('ADMIN_PASS', 'WORD='),
    j("pass", "word = '';"),
    j('this.authService.login({ username: this.username, pass', 'word: this.password })'),
    j('pass', 'wordChangedAt: string;'),
    j('readonly pass', 'wordHint = PASSWORD_HINT;'),
    j('export const PASS', 'WORD_MIN_LENGTH = 8;'),
    j('private static final long CLAUDE_MAX_TOK', 'ENS = 4096L;'),
    j('const token = process.env.API_', 'TOKEN;'),
    j('CsrfTo', 'ken csrfToken = (CsrfToken) request.getAttribute(x);'),
    j('<input type="pass', 'word" formControlName="password">'),
    j('pass', 'word=', MARK),
    // false positives once found in this repository
    j('- POSTGRES_PASS', 'WORD=${DB_PASSWORD:-changeme}'),
    j('"@csstools/css-tok', 'enizer": "^4.0.0",'),
    j('"@inquirer/pass', 'word": "^4.0.23",'),
    j("export const PASS", "WORD_HINT =\n  'Min. 8 caractères, avec minuscule, majuscule, chiffre';"),
    j('pass', 'word: password!,'),
  ];
  for (const text of untouched) assert.equal(r(text), text);
});

test('IBANs and card numbers are redacted only when their checksum is valid', () => {
  const iban = j('BE68 5390 ', '0754 7034');
  const card = j('4111 1111 ', '1111 1111');
  assert.equal(r(`IBAN ${iban}.`), `IBAN ${MARK}.`);
  assert.equal(r(`card ${card}`), `card ${MARK}`);
  assert.equal(r(j('BE68 5390 ', '0754 7035')), j('BE68 5390 ', '0754 7035'));
  assert.equal(r(j('4111 1111 ', '1111 1112')), j('4111 1111 ', '1111 1112'));
  assert.equal(r('created at 1759780000123'), 'created at 1759780000123'); // a timestamp
  assert.equal(r('0403.258.197'), '0403.258.197'); // a Belgian enterprise number
});

test('test sources keep their fake credentials but not real token formats', () => {
  const line = j('String pass', 'word = "Admin123!x";');
  assert.equal(r(line, { testFile: true }), line);
  const { input } = redactInput({ file_path: 'backend/src/test/java/FooTest.java', content: line });
  assert.equal(input.content, line);
  const { input: prod } = redactInput({ file_path: 'backend/src/main/java/Foo.java', content: line });
  assert.equal(prod.content, j('String pass', 'word = "', MARK, '";'));
});

test('locating fields are never rewritten', () => {
  const pw = j('S3cr', 'et!x9');
  const old = j('DB_PASS', 'WORD=', pw);
  const { input, found } = redactInput({
    file_path: '.env',
    old_string: old,
    new_string: `${old}\nOTHER=1`,
    edits: [{ old_string: old, new_string: old }],
  });
  assert.equal(input.old_string, old);
  assert.equal(input.edits[0].old_string, old);
  assert.equal(input.new_string, j('DB_PASS', 'WORD=', MARK, '\nOTHER=1'));
  assert.equal(input.edits[0].new_string, j('DB_PASS', 'WORD=', MARK));
  assert.equal(found.length, 2);
});
