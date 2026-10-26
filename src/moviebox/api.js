import { MB_BASE, MB_HOST, MB_WEB_BASE, PLAYER_ORIGIN, USER_AGENT } from './constants.js';
import { fetchJson, tryFetchJson, unwrap } from './http.js';

// The H5 API hands out a guest token in the `x-user` response header.
export async function getGuestToken() {
  const res = await fetch(`${MB_BASE}/app/get-latest-app-pkgs?app_name=moviebox`, {
    headers: { 'User-Agent': USER_AGENT, Accept: 'application/json' },
  });
  const xUser = res.headers.get('x-user');
  if (!xUser) throw new Error('no x-user header in token response');
  const token = JSON.parse(xUser).token;
  if (!token) throw new Error('empty guest token');
  return token;
}

export function apiHeaders(token) {
  return {
    'User-Agent': USER_AGENT,
    Accept: 'application/json',
    'Accept-Language': 'en-US,en;q=0.5',
    'X-Client-Info': '{"timezone":"Africa/Nairobi"}',
    Referer: `https://${MB_HOST}/`,
    Authorization: `Bearer ${token}`,
  };
}

export function playbackHeaders() {
  return { 'User-Agent': USER_AGENT, Referer: `${PLAYER_ORIGIN}/`, Origin: PLAYER_ORIGIN };
}

export async function search(token, keyword, subjectType) {
  const json = await fetchJson(`${MB_BASE}/subject/search`, {
    method: 'POST',
    headers: Object.assign({ 'Content-Type': 'application/json' }, apiHeaders(token)),
    body: JSON.stringify({ keyword, page: 1, perPage: 24, subjectType }),
  });
  return unwrap(json).items || [];
}

async function resolveDetailPath(subjectId) {
  const json = await tryFetchJson(`${MB_WEB_BASE}/post/list/subject?id=${encodeURIComponent(subjectId)}`, {
    headers: { 'User-Agent': USER_AGENT, Accept: 'application/json' },
  });
  const items = (json && json.data && json.data.items) || [];
  return (items[0] && items[0].subject && items[0].subject.detailPath) || '';
}

// Returns { downloads, streams, dash, captions } for one subject (and episode for TV).
export async function getSources(token, variant, season, episode) {
  const detailPath = variant.detailPath || (await resolveDetailPath(variant.subjectId));
  let qs = `subjectId=${encodeURIComponent(variant.subjectId)}`;
  if (season != null) qs += `&se=${season}&ep=${episode}`;
  if (detailPath) qs += `&detailPath=${encodeURIComponent(detailPath)}`;

  const headers = Object.assign(apiHeaders(token), {
    Referer: `${PLAYER_ORIGIN}/spa/videoPlayPage/movies/${detailPath}?id=${variant.subjectId}&type=/movie/detail`,
    Origin: PLAYER_ORIGIN,
  });

  const [dl, play] = await Promise.all([
    tryFetchJson(`${MB_BASE}/subject/download?${qs}`, { headers }),
    tryFetchJson(`${MB_BASE}/subject/play?${qs}`, { headers }),
  ]);
  const d = unwrap(dl);
  const p = unwrap(play);
  return {
    downloads: d.downloads || [],
    captions: d.captions || [],
    streams: p.streams || [],
    dash: p.dash || [],
  };
}
