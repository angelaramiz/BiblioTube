/**
 * Contratos del API de versiones OTA.
 * @typedef {Object} VersionInfo
 * @property {number} versionCode - entero >= 1
 * @property {string} versionName - "x.y.z"
 * @property {string} apkUrl - ruta o URL del APK
 *
 * @typedef {Object} ApiEnvelope
 * @property {boolean} success
 * @property {*} [data]
 * @property {string} [error]
 */

const APP_RE = /^[a-z0-9][a-z0-9._-]{0,63}$/;
const VERSION_NAME_RE = /^\d+\.\d+\.\d+(-[0-9A-Za-z.-]+)?$/;
const MAX_APP_LEN = 64;

/** Valor por defecto cuando Supabase no tiene fila. */
export const DEFAULT_VERSION = Object.freeze({
  versionCode: 1,
  versionName: "1.0.0",
  apkUrl: "/public/bibliotube.apk",
});

/** App por defecto. */
export const DEFAULT_APP = "bibliotube";

/**
 * Valida y normaliza el nombre de app del query param.
 * @param {unknown} app
 * @returns {string}
 */
export function sanitizeAppName(app) {
  if (app === undefined || app === null) return DEFAULT_APP;
  if (typeof app !== "string") throw new Error("invalid app name: must be string");
  const v = app.trim().toLowerCase();
  if (v === "") return DEFAULT_APP;
  if (v.length > MAX_APP_LEN) throw new Error("invalid app name: too long");
  if (v.includes("\0") || v.includes("/") || v.includes("\\") || v.includes("..")) {
    throw new Error("invalid app name: path traversal");
  }
  if (!APP_RE.test(v)) throw new Error(`invalid app name: ${JSON.stringify(app)}`);
  return v;
}

/**
 * Construye la clave de BD para una app.
 * @param {string} appName - ya sanitizado
 * @returns {string}
 */
export function buildVersionKey(appName) {
  if (typeof appName !== "string" || appName.trim() === "") {
    throw new Error("invalid app name: empty");
  }
  return `app_version_${appName}`;
}

/**
 * Valida un VersionInfo crudo (lo que venga de Supabase).
 * @param {unknown} raw
 * @returns {VersionInfo}
 * @throws {Error} si es invalido
 */
export function normalizeVersionInfo(raw) {
  if (raw === null || raw === undefined) return { ...DEFAULT_VERSION };
  if (typeof raw !== "object" || Array.isArray(raw)) {
    throw new Error("invalid version info: must be object");
  }
  const r = /** @type {Record<string, unknown>} */ (raw);
  const { versionCode, versionName, apkUrl } = r;
  if (!Number.isInteger(versionCode) || versionCode < 1 || versionCode > 1_000_000_000) {
    throw new Error(`invalid versionCode: ${JSON.stringify(versionCode)}`);
  }
  if (typeof versionName !== "string" || !VERSION_NAME_RE.test(versionName)) {
    throw new Error(`invalid versionName: ${JSON.stringify(versionName)}`);
  }
  if (typeof apkUrl !== "string" || apkUrl.trim() === "") {
    throw new Error(`invalid apkUrl: ${JSON.stringify(apkUrl)}`);
  }
  // Inmutabilidad: objeto nuevo, solo campos conocidos.
  return { versionCode, versionName: versionName.trim(), apkUrl: apkUrl.trim() };
}

/**
 * Envelope OK.
 * @param {*} data
 */
export function ok(data) {
  return { success: true, data };
}

/**
 * Envelope de error.
 * @param {string} message
 */
export function fail(message) {
  if (typeof message !== "string" || message.trim() === "") {
    throw new Error("invalid error message: must be non-empty string");
  }
  return { success: false, error: message };
}
