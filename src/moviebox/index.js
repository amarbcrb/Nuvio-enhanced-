import { MAX_VARIANTS, PROVIDER_NAME, SUBJECT_TYPE } from './constants.js';
import { getTmdbInfo } from './tmdb.js';
import { getGuestToken, getSources, playbackHeaders, search } from './api.js';
import { formatSize, pickVariants, qualityLabel } from './match.js';

const log = (msg) => console.log(`[${PROVIDER_NAME}] ${msg}`);

function toStreams(variant, sources) {
  const headers = playbackHeaders();
  const subtitles = sources.captions
    .filter((c) => c && c.url)
    .map((c) => ({ url: c.url, language: c.lan || c.lanName || 'Unknown', name: c.lanName || c.lan, headers }));
  const label = `${PROVIDER_NAME} [${variant.language}]`;
  const out = [];
  const seenRes = new Set();

  const push = (url, resolution, size, format) => {
    const quality = qualityLabel(resolution);
    out.push({
      name: label,
      title: `${variant.language} - ${quality}${format ? ` (${format})` : ''}`,
      url,
      quality,
      size: formatSize(size),
      language: variant.language,
      headers,
      subtitles: subtitles.length ? subtitles : undefined,
    });
  };

  for (const d of sources.downloads) {
    if (!d || !d.url || d.vipLocked) continue;
    const res = Number(d.resolution) || 0;
    seenRes.add(res);
    push(d.url, res, d.size, null);
  }
  for (const s of sources.streams) {
    if (!s || !s.url || s.vipLocked) continue;
    const res = Number(s.resolutions) || Number(s.resolution) || 0;
    if (seenRes.has(res)) continue;
    seenRes.add(res);
    push(s.url, res, s.size, s.format || null);
  }
  for (const s of sources.dash) {
    if (!s || !s.url || s.vipLocked) continue;
    push(s.url, 0, null, 'DASH');
  }
  return out;
}

async function findVariants(token, info, mediaType) {
  const subjectType = SUBJECT_TYPE[mediaType] || SUBJECT_TYPE.movie;
  for (const keyword of info.titles) {
    const items = await search(token, keyword, subjectType);
    const variants = pickVariants(items, info.titles, info.year, MAX_VARIANTS);
    if (variants.length) return variants;
  }
  return [];
}

async function getStreams(tmdbId, mediaType, season, episode) {
  try {
    const isTv = mediaType === 'tv';
    if (isTv && (season == null || episode == null)) {
      log('tv request without season/episode');
      return [];
    }
    const info = await getTmdbInfo(tmdbId, mediaType);
    log(`${mediaType} ${tmdbId}: "${info.titles[0]}" (${info.year || '?'})`);

    const token = await getGuestToken();
    const variants = await findVariants(token, info, mediaType);
    if (!variants.length) {
      log('no matching title');
      return [];
    }
    log(`variants: ${variants.map((v) => v.language).join(', ')}`);

    const results = await Promise.all(
      variants.map((v) =>
        getSources(token, v, isTv ? season : null, isTv ? episode : null)
          .then((src) => toStreams(v, src))
          .catch((e) => {
            log(`${v.subjectId}: ${e.message}`);
            return [];
          })
      )
    );
    const streams = [].concat(...results);
    log(`${streams.length} streams`);
    return streams;
  } catch (e) {
    console.error(`[${PROVIDER_NAME}] ${e.message}`);
    return [];
  }
}

module.exports = { getStreams };
