import { TMDB_API_KEY, TMDB_BASE } from './constants.js';
import { fetchJson } from './http.js';

// Returns { titles: [english, original], year } for a TMDB id.
export async function getTmdbInfo(tmdbId, mediaType) {
  const kind = mediaType === 'tv' ? 'tv' : 'movie';
  const data = await fetchJson(
    `${TMDB_BASE}/${kind}/${tmdbId}?api_key=${TMDB_API_KEY}&language=en-US`
  );
  const title = kind === 'tv' ? data.name : data.title;
  const original = kind === 'tv' ? data.original_name : data.original_title;
  const date = kind === 'tv' ? data.first_air_date : data.release_date;
  const year = date ? Number(String(date).slice(0, 4)) || null : null;
  const titles = [title];
  if (original && original !== title) titles.push(original);
  return { titles, year };
}
