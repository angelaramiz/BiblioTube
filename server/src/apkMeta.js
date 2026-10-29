/**
 * Metadatos del APK publicado (tamano + SHA-256) para que el cliente OTA
 * pueda verificar la descarga antes de instalarla.
 *
 * Cachea por (mtime, size): recalcular 53 MB en cada request seria caro.
 */
import { createHash } from "node:crypto";
import { promises as fs } from "node:fs";
import path from "node:path";

/** @type {Map<string, {mtimeMs: number, size: number, sha256: string}>} */
const cache = new Map();

/**
 * Devuelve { size, sha256 } del archivo.
 * @param {string} filePath
 * @returns {Promise<{size: number, sha256: string}>}
 */
export async function apkMeta(filePath) {
  if (typeof filePath !== "string" || filePath.trim() === "") {
    throw new Error("invalid apk path");
  }
  const key = path.resolve(filePath);
  const st = await fs.stat(key);
  const hit = cache.get(key);
  if (hit && hit.mtimeMs === st.mtimeMs && hit.size === st.size) {
    return { size: hit.size, sha256: hit.sha256 };
  }
  const buf = await fs.readFile(key);
  const sha256 = createHash("sha256").update(buf).digest("hex");
  cache.set(key, { mtimeMs: st.mtimeMs, size: st.size, sha256 });
  return { size: st.size, sha256 };
}

/** Solo para tests: limpia la cache en memoria. */
export function _resetApkMetaCache() {
  cache.clear();
}

/**
 * Enriquece un VersionInfo con size/sha256 si el APK es local (/public/...).
 * Si el archivo no existe, devuelve el info tal cual (nunca rompe el OTA).
 * @template T
 * @param {T & {apkUrl: string}} info
 * @param {string} publicRoot - carpeta public/ del servidor
 * @returns {Promise<T & {apkSize?: number, apkSha256?: string}>}
 */
export async function withApkMeta(info, publicRoot) {
  if (!info || typeof info.apkUrl !== "string") return info;
  const url = info.apkUrl;
  if (!url.startsWith("/public/")) return info;
  // Solo el nombre base: nada de traversal desde la BD.
  const name = path.basename(url);
  if (name === "" || name === "." || name === "..") return info;
  try {
    const meta = await apkMeta(path.join(publicRoot, name));
    return { ...info, apkSize: meta.size, apkSha256: meta.sha256 };
  } catch {
    return info;
  }
}
