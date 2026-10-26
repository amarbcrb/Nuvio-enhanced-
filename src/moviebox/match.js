// Pure helpers: title matching and stream shaping. No network here.

const SEASON_SUFFIX = /\s+S\d+(?:\s*-\s*S?\d+)*$/i;
const LANG_SUFFIX = /\s*\[([^\]]+)\]\s*$/;

export function normalizeTitle(s) {
  let t = String(s || '').toLowerCase();
  // Strip accents where the runtime supports normalize().
  if (typeof t.normalize === 'function') t = t.normalize('NFD').replace(/[̀-ͯ]/g, '');
  return t
    .replace(/&/g, ' and ')
    .replace(/[^a-z0-9]+/g, ' ')
    .trim();
}

// "Breaking Bad S1-S5 [Hindi]" -> { base: "Breaking Bad", language: "Hindi" }
export function splitSubjectTitle(raw) {
  let title = String(raw || '').trim();
  let language = 'Original';
  const lang = title.match(LANG_SUFFIX);
  if (lang) {
    language = lang[1].trim();
    title = title.slice(0, lang.index).trim();
  }
  title = title.replace(SEASON_SUFFIX, '').trim();
  return { base: title, language };
}

function yearOf(item) {
  const d = item.releaseDate || item.releaseTime || '';
  const m = String(d).match(/^(\d{4})/);
  return m ? Number(m[1]) : null;
}

// Keeps search items whose title equals one of `titles`, one entry per subjectId.
// When release years are available, drops items more than a year off.
export function pickVariants(items, titles, year, max) {
  const wanted = new Set(titles.filter(Boolean).map(normalizeTitle));
  const seen = new Set();
  const out = [];
  for (const item of items || []) {
    if (!item || !item.subjectId || seen.has(item.subjectId)) continue;
    const { base, language } = splitSubjectTitle(item.title);
    if (!wanted.has(normalizeTitle(base))) continue;
    seen.add(item.subjectId);
    out.push({
      subjectId: String(item.subjectId),
      detailPath: item.detailPath || '',
      language,
      year: yearOf(item),
    });
  }
  let result = out;
  if (year) {
    const dated = out.filter((v) => v.year !== null && Math.abs(v.year - year) <= 1);
    if (dated.length > 0) result = dated.concat(out.filter((v) => v.year === null));
  }
  // Original audio first, then dubs.
  result.sort((a, b) => (a.language === 'Original' ? 0 : 1) - (b.language === 'Original' ? 0 : 1));
  return result.slice(0, max);
}

export function formatSize(bytes) {
  const n = Number(bytes);
  if (!n || !isFinite(n)) return undefined;
  if (n >= 1024 ** 3) return `${(n / 1024 ** 3).toFixed(2)} GB`;
  return `${Math.round(n / 1024 ** 2)} MB`;
}

export function qualityLabel(resolution) {
  const r = Number(resolution);
  if (!r) return 'Auto';
  if (r >= 2160) return '4K';
  return `${r}p`;
}
