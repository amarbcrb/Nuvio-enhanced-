export const PROVIDER_NAME = 'MovieBox';

export const TMDB_API_KEY = '439c478a771f35c05022f9feabcca01c';
export const TMDB_BASE = 'https://api.themoviedb.org/3';

export const MB_HOST = 'h5-api.aoneroom.com';
export const MB_BASE = `https://${MB_HOST}/wefeed-h5api-bff`;
export const MB_WEB_BASE = 'https://h5.aoneroom.com/wefeed-h5-bff/web';
// The stream CDN checks Referer/Origin against the web player domain.
export const PLAYER_ORIGIN = 'https://fmoviesunblocked.net';

export const USER_AGENT =
  'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36';

export const SUBJECT_TYPE = { movie: 1, tv: 2 };

// Each language dub is a separate subject; cap how many we resolve per title.
export const MAX_VARIANTS = 6;
