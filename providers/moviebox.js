/**
 * moviebox - built from src/moviebox/
 */
var __async = (__this, __arguments, generator) => {
  return new Promise((resolve, reject) => {
    var fulfilled = (value) => {
      try {
        step(generator.next(value));
      } catch (e) {
        reject(e);
      }
    };
    var rejected = (value) => {
      try {
        step(generator.throw(value));
      } catch (e) {
        reject(e);
      }
    };
    var step = (x) => x.done ? resolve(x.value) : Promise.resolve(x.value).then(fulfilled, rejected);
    step((generator = generator.apply(__this, __arguments)).next());
  });
};

// src/moviebox/constants.js
var PROVIDER_NAME = "MovieBox";
var TMDB_API_KEY = "439c478a771f35c05022f9feabcca01c";
var TMDB_BASE = "https://api.themoviedb.org/3";
var MB_HOST = "h5-api.aoneroom.com";
var MB_BASE = `https://${MB_HOST}/wefeed-h5api-bff`;
var MB_WEB_BASE = "https://h5.aoneroom.com/wefeed-h5-bff/web";
var PLAYER_ORIGIN = "https://fmoviesunblocked.net";
var USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36";
var SUBJECT_TYPE = { movie: 1, tv: 2 };
var MAX_VARIANTS = 6;

// src/moviebox/http.js
function fetchJson(url, options) {
  return __async(this, null, function* () {
    const res = yield fetch(url, options || {});
    if (!res.ok) {
      throw new Error(`HTTP ${res.status} for ${url}`);
    }
    return res.json();
  });
}
function tryFetchJson(url, options) {
  return __async(this, null, function* () {
    try {
      return yield fetchJson(url, options);
    } catch (e) {
      console.log(`[${PROVIDER_NAME}] ${e.message}`);
      return null;
    }
  });
}
function unwrap(json) {
  if (!json || typeof json !== "object") return {};
  const data = json.data;
  if (!data || typeof data !== "object") return json;
  return data.data && typeof data.data === "object" ? data.data : data;
}

// src/moviebox/tmdb.js
function getTmdbInfo(tmdbId, mediaType) {
  return __async(this, null, function* () {
    const kind = mediaType === "tv" ? "tv" : "movie";
    const data = yield fetchJson(
      `${TMDB_BASE}/${kind}/${tmdbId}?api_key=${TMDB_API_KEY}&language=en-US`
    );
    const title = kind === "tv" ? data.name : data.title;
    const original = kind === "tv" ? data.original_name : data.original_title;
    const date = kind === "tv" ? data.first_air_date : data.release_date;
    const year = date ? Number(String(date).slice(0, 4)) || null : null;
    const titles = [title];
    if (original && original !== title) titles.push(original);
    return { titles, year };
  });
}

// src/moviebox/api.js
function getGuestToken() {
  return __async(this, null, function* () {
    const res = yield fetch(`${MB_BASE}/app/get-latest-app-pkgs?app_name=moviebox`, {
      headers: { "User-Agent": USER_AGENT, Accept: "application/json" }
    });
    const xUser = res.headers.get("x-user");
    if (!xUser) throw new Error("no x-user header in token response");
    const token = JSON.parse(xUser).token;
    if (!token) throw new Error("empty guest token");
    return token;
  });
}
function apiHeaders(token) {
  return {
    "User-Agent": USER_AGENT,
    Accept: "application/json",
    "Accept-Language": "en-US,en;q=0.5",
    "X-Client-Info": '{"timezone":"Africa/Nairobi"}',
    Referer: `https://${MB_HOST}/`,
    Authorization: `Bearer ${token}`
  };
}
function playbackHeaders() {
  return { "User-Agent": USER_AGENT, Referer: `${PLAYER_ORIGIN}/`, Origin: PLAYER_ORIGIN };
}
function search(token, keyword, subjectType) {
  return __async(this, null, function* () {
    const json = yield fetchJson(`${MB_BASE}/subject/search`, {
      method: "POST",
      headers: Object.assign({ "Content-Type": "application/json" }, apiHeaders(token)),
      body: JSON.stringify({ keyword, page: 1, perPage: 24, subjectType })
    });
    return unwrap(json).items || [];
  });
}
function resolveDetailPath(subjectId) {
  return __async(this, null, function* () {
    const json = yield tryFetchJson(`${MB_WEB_BASE}/post/list/subject?id=${encodeURIComponent(subjectId)}`, {
      headers: { "User-Agent": USER_AGENT, Accept: "application/json" }
    });
    const items = json && json.data && json.data.items || [];
    return items[0] && items[0].subject && items[0].subject.detailPath || "";
  });
}
function getSources(token, variant, season, episode) {
  return __async(this, null, function* () {
    const detailPath = variant.detailPath || (yield resolveDetailPath(variant.subjectId));
    let qs = `subjectId=${encodeURIComponent(variant.subjectId)}`;
    if (season != null) qs += `&se=${season}&ep=${episode}`;
    if (detailPath) qs += `&detailPath=${encodeURIComponent(detailPath)}`;
    const headers = Object.assign(apiHeaders(token), {
      Referer: `${PLAYER_ORIGIN}/spa/videoPlayPage/movies/${detailPath}?id=${variant.subjectId}&type=/movie/detail`,
      Origin: PLAYER_ORIGIN
    });
    const [dl, play] = yield Promise.all([
      tryFetchJson(`${MB_BASE}/subject/download?${qs}`, { headers }),
      tryFetchJson(`${MB_BASE}/subject/play?${qs}`, { headers })
    ]);
    const d = unwrap(dl);
    const p = unwrap(play);
    return {
      downloads: d.downloads || [],
      captions: d.captions || [],
      streams: p.streams || [],
      dash: p.dash || []
    };
  });
}

// src/moviebox/match.js
var SEASON_SUFFIX = /\s+S\d+(?:\s*-\s*S?\d+)*$/i;
var LANG_SUFFIX = /\s*\[([^\]]+)\]\s*$/;
function normalizeTitle(s) {
  let t = String(s || "").toLowerCase();
  if (typeof t.normalize === "function") t = t.normalize("NFD").replace(/[̀-ͯ]/g, "");
  return t.replace(/&/g, " and ").replace(/[^a-z0-9]+/g, " ").trim();
}
function splitSubjectTitle(raw) {
  let title = String(raw || "").trim();
  let language = "Original";
  const lang = title.match(LANG_SUFFIX);
  if (lang) {
    language = lang[1].trim();
    title = title.slice(0, lang.index).trim();
  }
  title = title.replace(SEASON_SUFFIX, "").trim();
  return { base: title, language };
}
function yearOf(item) {
  const d = item.releaseDate || item.releaseTime || "";
  const m = String(d).match(/^(\d{4})/);
  return m ? Number(m[1]) : null;
}
function pickVariants(items, titles, year, max) {
  const wanted = new Set(titles.filter(Boolean).map(normalizeTitle));
  const seen = /* @__PURE__ */ new Set();
  const out = [];
  for (const item of items || []) {
    if (!item || !item.subjectId || seen.has(item.subjectId)) continue;
    const { base, language } = splitSubjectTitle(item.title);
    if (!wanted.has(normalizeTitle(base))) continue;
    seen.add(item.subjectId);
    out.push({
      subjectId: String(item.subjectId),
      detailPath: item.detailPath || "",
      language,
      year: yearOf(item)
    });
  }
  let result = out;
  if (year) {
    const dated = out.filter((v) => v.year !== null && Math.abs(v.year - year) <= 1);
    if (dated.length > 0) result = dated.concat(out.filter((v) => v.year === null));
  }
  result.sort((a, b) => (a.language === "Original" ? 0 : 1) - (b.language === "Original" ? 0 : 1));
  return result.slice(0, max);
}
function formatSize(bytes) {
  const n = Number(bytes);
  if (!n || !isFinite(n)) return void 0;
  if (n >= 1024 ** 3) return `${(n / 1024 ** 3).toFixed(2)} GB`;
  return `${Math.round(n / 1024 ** 2)} MB`;
}
function qualityLabel(resolution) {
  const r = Number(resolution);
  if (!r) return "Auto";
  if (r >= 2160) return "4K";
  return `${r}p`;
}

// src/moviebox/index.js
var log = (msg) => console.log(`[${PROVIDER_NAME}] ${msg}`);
function toStreams(variant, sources) {
  const headers = playbackHeaders();
  const subtitles = sources.captions.filter((c) => c && c.url).map((c) => ({ url: c.url, language: c.lan || c.lanName || "Unknown", name: c.lanName || c.lan, headers }));
  const label = `${PROVIDER_NAME} [${variant.language}]`;
  const out = [];
  const seenRes = /* @__PURE__ */ new Set();
  const push = (url, resolution, size, format) => {
    const quality = qualityLabel(resolution);
    out.push({
      name: label,
      title: `${variant.language} - ${quality}${format ? ` (${format})` : ""}`,
      url,
      quality,
      size: formatSize(size),
      language: variant.language,
      headers,
      subtitles: subtitles.length ? subtitles : void 0
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
    push(s.url, 0, null, "DASH");
  }
  return out;
}
function findVariants(token, info, mediaType) {
  return __async(this, null, function* () {
    const subjectType = SUBJECT_TYPE[mediaType] || SUBJECT_TYPE.movie;
    for (const keyword of info.titles) {
      const items = yield search(token, keyword, subjectType);
      const variants = pickVariants(items, info.titles, info.year, MAX_VARIANTS);
      if (variants.length) return variants;
    }
    return [];
  });
}
function getStreams(tmdbId, mediaType, season, episode) {
  return __async(this, null, function* () {
    try {
      const isTv = mediaType === "tv";
      if (isTv && (season == null || episode == null)) {
        log("tv request without season/episode");
        return [];
      }
      const info = yield getTmdbInfo(tmdbId, mediaType);
      log(`${mediaType} ${tmdbId}: "${info.titles[0]}" (${info.year || "?"})`);
      const token = yield getGuestToken();
      const variants = yield findVariants(token, info, mediaType);
      if (!variants.length) {
        log("no matching title");
        return [];
      }
      log(`variants: ${variants.map((v) => v.language).join(", ")}`);
      const results = yield Promise.all(
        variants.map(
          (v) => getSources(token, v, isTv ? season : null, isTv ? episode : null).then((src) => toStreams(v, src)).catch((e) => {
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
  });
}
module.exports = { getStreams };
