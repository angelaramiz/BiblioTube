/**
 * Logica pura de la Boveda BiblioTube (sin red ni BD).
 * Testeable sin mocks salvo fetchPreview, que recibe fetch inyectable.
 */

export const PLATFORMS = Object.freeze(["youtube", "instagram", "tiktok", "otra"]);

export const REPEAT_VALUES = Object.freeze(["none", "daily", "weekly"]);

export const DRIVE_STATUSES = Object.freeze(["none", "pending", "uploading", "done", "error"]);

/**
 * Detecta errores de autenticacion de Supabase/PostgREST (JWT invalido o
 * caducado) para devolver 401 en vez de 500. Nunca incluye detalle interno.
 */
export function isAuthError(error) {
  if (!error || typeof error !== "object") return false;
  const code = String(error.code ?? "");
  if (/^PGRST30\d$/.test(code)) return true;
  const msg = String(error.message ?? "");
  return /jwt|token|expired|unauthorized|not authenticated/i.test(msg);
}

const YOUTUBE_HOSTS = new Set(["youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com", "youtu.be"]);
const INSTAGRAM_HOSTS = new Set(["instagram.com", "www.instagram.com"]);
const TIKTOK_HOSTS = new Set(["tiktok.com", "www.tiktok.com", "vm.tiktok.com", "vt.tiktok.com"]);

const COLOR_RE = /^#[0-9a-fA-F]{3}([0-9a-fA-F]{3})?$/;

export const DEFAULT_CATEGORY_COLOR = "#7C3AED";

function hostOf(url) {
  try {
    return new URL(url).hostname.toLowerCase();
  } catch {
    return null;
  }
}

function isHttpUrl(url) {
  try {
    const u = new URL(url);
    return u.protocol === "http:" || u.protocol === "https:";
  } catch {
    return false;
  }
}

/**
 * Detecta la plataforma por dominio. Nunca lanza: lo desconocido es "otra".
 * @param {unknown} url
 * @returns {"youtube"|"instagram"|"tiktok"|"otra"}
 */
export function detectPlatform(url) {
  const host = typeof url === "string" ? hostOf(url) : null;
  if (!host) return "otra";
  const base = host.startsWith("www.") ? host : `www.${host}`;
  if (YOUTUBE_HOSTS.has(host) || YOUTUBE_HOSTS.has(base) || host.endsWith(".youtube.com")) return "youtube";
  if (INSTAGRAM_HOSTS.has(host) || host.endsWith(".instagram.com")) return "instagram";
  if (TIKTOK_HOSTS.has(host) || host.endsWith(".tiktok.com")) return "tiktok";
  return "otra";
}

function mustBeObject(body, what) {
  if (body === null || body === undefined || typeof body !== "object" || Array.isArray(body)) {
    throw new Error(`invalid ${what} payload: must be object`);
  }
  return body;
}

function cleanText(v, max) {
  if (typeof v !== "string") return null;
  const t = v.trim();
  if (t === "" || t.length > max) return null;
  return t;
}

/**
 * Valida input de categoria. POST: nombre obligatorio. PATCH (partial): todo opcional.
 * Devuelve objeto NUEVO solo con campos conocidos (nunca user_id).
 * @param {unknown} body
 * @param {{partial?: boolean}} [opts]
 */
export function validateCategoryInput(body, opts = {}) {
  const partial = opts?.partial === true;
  const b = mustBeObject(body, "category");
  const out = {};
  if ("nombre" in b) {
    const t = cleanText(b.nombre, 80);
    if (t === null) throw new Error("invalid nombre: 1-80 chars");
    out.nombre = t;
  } else if (!partial) {
    throw new Error("invalid nombre: required");
  }
  if ("color" in b) {
    if (typeof b.color !== "string" || !COLOR_RE.test(b.color.trim())) {
      throw new Error("invalid color: must be hex like #7C3AED");
    }
    out.color = b.color.trim();
  } else if (!partial) {
    out.color = DEFAULT_CATEGORY_COLOR;
  }
  if ("orden" in b) {
    if (!Number.isInteger(b.orden)) throw new Error("invalid orden: must be integer");
    out.orden = b.orden;
  } else if (!partial) {
    out.orden = 0;
  }
  if (partial && Object.keys(out).length === 0) {
    throw new Error("invalid category update: empty");
  }
  return out;
}

/**
 * Valida input de video. Si no trae plataforma, la auto-detecta por dominio.
 * Devuelve objeto NUEVO solo con campos conocidos (nunca user_id).
 * @param {unknown} body
 * @param {{partial?: boolean}} [opts]
 */
export function validateVideoInput(body, opts = {}) {
  const partial = opts?.partial === true;
  const b = mustBeObject(body, "video");
  const out = {};

  if ("titulo" in b) {
    const t = cleanText(b.titulo, 200);
    if (t === null) throw new Error("invalid titulo: 1-200 chars");
    out.titulo = t;
  } else if (!partial) {
    throw new Error("invalid titulo: required");
  }

  if ("url" in b) {
    if (typeof b.url !== "string" || !isHttpUrl(b.url.trim())) {
      throw new Error("invalid url: must be http(s)");
    }
    out.url = b.url.trim();
  } else if (!partial) {
    throw new Error("invalid url: required");
  }

  if ("descripcion" in b) {
    if (typeof b.descripcion !== "string") throw new Error("invalid descripcion: must be string");
    out.descripcion = b.descripcion;
  } else if (!partial) {
    out.descripcion = "";
  }

  if ("plataforma" in b) {
    if (!PLATFORMS.includes(b.plataforma)) {
      throw new Error(`invalid plataforma: one of ${PLATFORMS.join("|")}`);
    }
    out.plataforma = b.plataforma;
  } else if (!partial) {
    out.plataforma = detectPlatform(out.url);
  }

  if ("thumbnail_url" in b) {
    if (typeof b.thumbnail_url !== "string") throw new Error("invalid thumbnail_url: must be string");
    out.thumbnail_url = b.thumbnail_url.trim();
  } else if (!partial) {
    out.thumbnail_url = "";
  }

  if ("category_id" in b) {
    if (b.category_id !== null && (typeof b.category_id !== "string" || b.category_id.trim() === "")) {
      throw new Error("invalid category_id: must be string or null");
    }
    out.category_id = b.category_id === null ? null : b.category_id.trim();
  } else if (!partial) {
    out.category_id = null;
  }

  if ("prioridad" in b) {
    if (!Number.isInteger(b.prioridad)) throw new Error("invalid prioridad: must be integer");
    out.prioridad = b.prioridad;
  } else if (!partial) {
    out.prioridad = 0;
  }

  if ("recordatorio_at" in b) {
    if (b.recordatorio_at !== null) {
      if (typeof b.recordatorio_at !== "string" || Number.isNaN(Date.parse(b.recordatorio_at))) {
        throw new Error("invalid recordatorio_at: must be ISO date or null");
      }
      out.recordatorio_at = b.recordatorio_at;
    } else {
      out.recordatorio_at = null;
    }
  } else if (!partial) {
    out.recordatorio_at = null;
  }

  if ("recordatorio_repeat" in b) {
    if (!REPEAT_VALUES.includes(b.recordatorio_repeat)) {
      throw new Error(`invalid recordatorio_repeat: one of ${REPEAT_VALUES.join("|")}`);
    }
    out.recordatorio_repeat = b.recordatorio_repeat;
  } else if (!partial) {
    out.recordatorio_repeat = "none";
  }

  if ("drive_file_id" in b) {
    if (typeof b.drive_file_id !== "string") throw new Error("invalid drive_file_id: must be string");
    out.drive_file_id = b.drive_file_id.trim();
  } else if (!partial) {
    out.drive_file_id = "";
  }

  if ("drive_status" in b) {
    if (!DRIVE_STATUSES.includes(b.drive_status)) {
      throw new Error(`invalid drive_status: one of ${DRIVE_STATUSES.join("|")}`);
    }
    out.drive_status = b.drive_status;
  } else if (!partial) {
    out.drive_status = "none";
  }

  if ("local_path" in b) {
    if (typeof b.local_path !== "string") throw new Error("invalid local_path: must be string");
    out.local_path = b.local_path;
  } else if (!partial) {
    out.local_path = "";
  }

  if (partial && Object.keys(out).length === 0) {
    throw new Error("invalid video update: empty");
  }
  return out;
}

function timeOf(row) {
  const t = Date.parse(row?.fecha_guardado);
  return Number.isNaN(t) ? 0 : t;
}

/**
 * Ordena videos por prioridad DESC, fecha_guardado DESC. No muta el input.
 * @param {unknown} rows
 * @returns {Array}
 */
export function sortVideos(rows) {
  if (!Array.isArray(rows)) throw new Error("invalid videos list: must be array");
  return [...rows].sort((a, b) => {
    const pb = (b?.prioridad ?? 0) - (a?.prioridad ?? 0);
    if (pb !== 0) return pb;
    return timeOf(b) - timeOf(a);
  });
}

const YOUTUBE_OEMBED = "https://www.youtube.com/oembed";

function fallbackPreview(url) {
  return {
    titulo: hostOf(url) ?? String(url),
    descripcion: "",
    thumbnail_url: "",
    plataforma: detectPlatform(url),
  };
}

/**
 * Preview de un video: YouTube via oEmbed oficial, resto fallback por dominio.
 * Nunca lanza por fallos de red: usa fallback. Solo rechaza URL invalida.
 * @param {unknown} url
 * @param {(input: string) => Promise<{ok: boolean, json: () => Promise<unknown>}>} [fetchImpl]
 */
export async function fetchPreview(url, fetchImpl = globalThis.fetch) {
  if (typeof url !== "string" || !isHttpUrl(url.trim())) {
    throw new Error("invalid url: must be http(s)");
  }
  const clean = url.trim();
  if (detectPlatform(clean) !== "youtube") return fallbackPreview(clean);
  try {
    const endpoint = `${YOUTUBE_OEMBED}?url=${encodeURIComponent(clean)}&format=json`;
    const res = await fetchImpl(endpoint);
    if (!res || res.ok !== true) return fallbackPreview(clean);
    const data = await res.json();
    if (!data || typeof data !== "object" || typeof data.title !== "string" || data.title.trim() === "") {
      return fallbackPreview(clean);
    }
    return {
      titulo: data.title.trim(),
      descripcion: typeof data.author_name === "string" ? data.author_name : "",
      thumbnail_url: typeof data.thumbnail_url === "string" ? data.thumbnail_url : "",
      plataforma: "youtube",
    };
  } catch {
    return fallbackPreview(clean);
  }
}
