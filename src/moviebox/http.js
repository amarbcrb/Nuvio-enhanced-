import { PROVIDER_NAME } from './constants.js';

export async function fetchJson(url, options) {
  const res = await fetch(url, options || {});
  if (!res.ok) {
    throw new Error(`HTTP ${res.status} for ${url}`);
  }
  return res.json();
}

// Like fetchJson but logs and returns null instead of throwing.
export async function tryFetchJson(url, options) {
  try {
    return await fetchJson(url, options);
  } catch (e) {
    console.log(`[${PROVIDER_NAME}] ${e.message}`);
    return null;
  }
}

// MovieBox wraps payloads as { data: {...} } or { data: { data: {...} } }.
export function unwrap(json) {
  if (!json || typeof json !== 'object') return {};
  const data = json.data;
  if (!data || typeof data !== 'object') return json;
  return data.data && typeof data.data === 'object' ? data.data : data;
}
