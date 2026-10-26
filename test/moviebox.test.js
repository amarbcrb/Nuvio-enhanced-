// Offline tests: match helpers on sample titles, and the built provider
// against a stubbed fetch to check request flow and stream shape.
// They do not prove the live MovieBox API still answers this way.

const test = require('node:test');
const assert = require('node:assert');
const path = require('path');
const fs = require('fs');
const esbuild = require('esbuild');

function loadEsm(file) {
  const out = esbuild.buildSync({ entryPoints: [file], bundle: true, write: false, format: 'cjs', platform: 'node' });
  const mod = { exports: {} };
  new Function('module', 'exports', 'require', out.outputFiles[0].text)(mod, mod.exports, require);
  return mod.exports;
}

const match = loadEsm(path.join(__dirname, '../src/moviebox/match.js'));

test('splitSubjectTitle strips season range and language tag', () => {
  assert.deepStrictEqual(match.splitSubjectTitle('Breaking Bad S1-S5 [Hindi]'), { base: 'Breaking Bad', language: 'Hindi' });
  assert.deepStrictEqual(match.splitSubjectTitle('Inception'), { base: 'Inception', language: 'Original' });
});

test('normalizeTitle ignores punctuation, case and accents', () => {
  assert.strictEqual(match.normalizeTitle('Spider-Man: No Way Home'), match.normalizeTitle('spider man no way home'));
  assert.strictEqual(match.normalizeTitle('Amélie'), 'amelie');
});

test('pickVariants keeps exact titles, dedupes, filters by year, original first', () => {
  const items = [
    { subjectId: '1', title: 'Dune [Hindi]', releaseDate: '2021-10-22' },
    { subjectId: '2', title: 'Dune', releaseDate: '2021-10-22' },
    { subjectId: '2', title: 'Dune', releaseDate: '2021-10-22' },
    { subjectId: '3', title: 'Dune', releaseDate: '1984-12-14' },
    { subjectId: '4', title: 'Dune: Part Two', releaseDate: '2024-03-01' },
  ];
  const v = match.pickVariants(items, ['Dune'], 2021, 6);
  assert.deepStrictEqual(v.map((x) => [x.subjectId, x.language]), [['2', 'Original'], ['1', 'Hindi']]);
});

test('qualityLabel and formatSize', () => {
  assert.strictEqual(match.qualityLabel(1080), '1080p');
  assert.strictEqual(match.qualityLabel(2160), '4K');
  assert.strictEqual(match.qualityLabel(0), 'Auto');
  assert.strictEqual(match.formatSize(1610612736), '1.50 GB');
  assert.strictEqual(match.formatSize('524288000'), '500 MB');
  assert.strictEqual(match.formatSize(null), undefined);
});

function response(body, headers) {
  return {
    ok: true,
    status: 200,
    headers: { get: (n) => (headers || {})[n.toLowerCase()] || null },
    json: () => Promise.resolve(body),
  };
}

test('built provider: tv flow builds requests and streams', async () => {
  const built = path.join(__dirname, '../providers/moviebox.js');
  assert.ok(fs.existsSync(built), 'run `npm run build` first');
  const calls = [];
  global.fetch = async (url, opts) => {
    calls.push({ url, opts: opts || {} });
    if (url.includes('api.themoviedb.org')) {
      return response({ name: 'Breaking Bad', original_name: 'Breaking Bad', first_air_date: '2008-01-20' });
    }
    if (url.includes('get-latest-app-pkgs')) return response({}, { 'x-user': '{"token":"tok"}' });
    if (url.includes('/subject/search')) {
      return response({ data: { items: [
        { subjectId: '10', title: 'Breaking Bad S1-S5', detailPath: 'breaking-bad-abc', releaseDate: '2008-01-20' },
        { subjectId: '11', title: 'Breaking Bad S1-S5 [Hindi]', releaseDate: '2008-01-20' },
      ] } });
    }
    if (url.includes('/post/list/subject')) {
      return response({ data: { items: [{ subject: { detailPath: 'breaking-bad-hindi' } }] } });
    }
    if (url.includes('/subject/download')) {
      return response({ data: {
        downloads: [{ url: 'https://cdn/x720.mp4', resolution: 720, size: '524288000' }, { url: 'https://cdn/vip.mp4', resolution: 1080, vipLocked: true }],
        captions: [{ lan: 'fr', lanName: 'French', url: 'https://cdn/fr.srt' }],
      } });
    }
    if (url.includes('/subject/play')) {
      return response({ data: { streams: [{ url: 'https://cdn/x720.m3u8', resolutions: '720' }, { url: 'https://cdn/x480.m3u8', resolutions: '480', format: 'HLS' }] } });
    }
    throw new Error('unexpected ' + url);
  };

  delete require.cache[built];
  const { getStreams } = require(built);
  const streams = await getStreams('1396', 'tv', 2, 3);

  const search = calls.find((c) => c.url.includes('/subject/search'));
  assert.deepStrictEqual(JSON.parse(search.opts.body), { keyword: 'Breaking Bad', page: 1, perPage: 24, subjectType: 2 });
  assert.strictEqual(search.opts.headers.Authorization, 'Bearer tok');

  const dl = calls.filter((c) => c.url.includes('/subject/download')).map((c) => c.url).sort();
  assert.ok(dl[0].includes('subjectId=10&se=2&ep=3&detailPath=breaking-bad-abc'));
  assert.ok(dl[1].includes('subjectId=11&se=2&ep=3&detailPath=breaking-bad-hindi'));

  const orig = streams.filter((s) => s.language === 'Original');
  assert.deepStrictEqual(orig.map((s) => [s.url, s.quality]), [['https://cdn/x720.mp4', '720p'], ['https://cdn/x480.m3u8', '480p']]);
  assert.strictEqual(orig[0].size, '500 MB');
  assert.strictEqual(orig[0].headers.Referer, 'https://fmoviesunblocked.net/');
  assert.deepStrictEqual(orig[0].subtitles[0].language, 'fr');
  assert.strictEqual(streams.length, 4);
  assert.ok(!streams.some((s) => s.url.includes('vip')));
});

test('built provider: returns [] on network failure', async () => {
  global.fetch = async () => { throw new Error('offline'); };
  const built = path.join(__dirname, '../providers/moviebox.js');
  delete require.cache[built];
  const { getStreams } = require(built);
  assert.deepStrictEqual(await getStreams('550', 'movie'), []);
});
