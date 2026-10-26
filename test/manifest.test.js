// Mirrors the checks in Nuvio's PluginManifestParser / PluginManifestScraper.
const test = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const path = require('path');

test('manifest.json matches the Nuvio manifest shape', () => {
  const root = path.join(__dirname, '..');
  const m = JSON.parse(fs.readFileSync(path.join(root, 'manifest.json'), 'utf8'));
  assert.ok(m && !Array.isArray(m) && typeof m === 'object', 'manifest must be an object, not an array');
  assert.ok(typeof m.name === 'string' && m.name.trim(), 'name required');
  assert.ok(typeof m.version === 'string' && m.version.trim(), 'version must be a non-empty string');
  assert.ok(Array.isArray(m.scrapers) && m.scrapers.length > 0, 'scrapers must be a non-empty array');
  for (const s of m.scrapers) {
    for (const k of ['id', 'name', 'version', 'filename']) {
      assert.ok(typeof s[k] === 'string' && s[k], `scraper.${k} must be a string`);
    }
    assert.ok(fs.existsSync(path.join(root, s.filename)), `${s.filename} missing`);
  }
});
