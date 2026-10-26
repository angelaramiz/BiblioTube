export const THUMB_HOSTS = Object.freeze([
  "ytimg.com",
  "cdninstagram.com",
  "fbcdn.net",
  "tiktokcdn.com",
  "tiktokcdn-us.com",
]);

export const THUMB_MAX_BYTES = 5 * 1024 * 1024;

export function validateThumbUrl(raw) {
  if (!raw || typeof raw !== "string" || !raw.trim()) {
    throw new Error("invalid url: required");
  }
  let u;
  try {
    u = new URL(raw.trim());
  } catch {
    throw new Error("invalid url: must be http(s)");
  }
  if (u.protocol !== "http:" && u.protocol !== "https:") {
    throw new Error("invalid url: must be http(s)");
  }
  const host = u.hostname.toLowerCase();
  const allowed = THUMB_HOSTS.some((s) => host === s || host.endsWith("." + s));
  if (!allowed) {
    throw new Error("invalid url: host no permitido");
  }
  return u;
}
