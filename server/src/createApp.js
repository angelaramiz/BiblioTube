import express from "express";
import path from "path";
import { fileURLToPath } from "url";
import { sanitizeAppName, buildVersionKey, normalizeVersionInfo, ok, fail, DEFAULT_VERSION } from "./version.js";
import { validateCategoryInput, validateVideoInput, sortVideos, fetchPreview } from "./vault.js";
import { validateThumbUrl, THUMB_MAX_BYTES } from "./thumb.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

/**
 * Fabrica de app Express testeable (Supabase inyectable).
 * @param {*} supabaseOrFactory - cliente supabase fijo (compat) o factoria (jwt) => cliente con RLS
 * @param {{ publicClient?: *, fetchFn?: Function }} [options]
 */
export function createApp(supabaseOrFactory, options = {}) {
  const app = express();
  app.use(express.json({ limit: "16kb" }));

  const isFactory = typeof supabaseOrFactory === "function";
  const toScoped = isFactory ? supabaseOrFactory : () => supabaseOrFactory;
  const publicDb = isFactory ? (options.publicClient ?? null) : supabaseOrFactory;
  const fetchFn = options.fetchFn ?? ((...args) => globalThis.fetch(...args));

  const TABLE = process.env.VERSION_TABLE || "app_versions";
  const KEY_COL = process.env.VERSION_KEY_COLUMN || "clave";
  const VAL_COL = process.env.VERSION_VALUE_COLUMN || "valor";

  app.get("/api/app-version", async (req, res) => {
    let appName;
    try {
      appName = sanitizeAppName(req.query.app);
    } catch {
      return res.status(400).json(fail("invalid app name"));
    }
    try {
      const key = buildVersionKey(appName);
      const { data, error } = await publicDb.from(TABLE).select(VAL_COL).eq(KEY_COL, key).single();
      if (error || !data) return res.json(ok({ ...DEFAULT_VERSION }));
      try {
        return res.json(ok(normalizeVersionInfo(data[VAL_COL])));
      } catch {
        return res.json(ok({ ...DEFAULT_VERSION }));
      }
    } catch (err) {
      console.error("app-version error:", err?.message ?? err);
      return res.status(500).json(fail("internal error"));
    }
  });

  app.get("/api/app-catalog", async (req, res) => {
    try {
      const { data, error } = await publicDb.from(TABLE).select(`${KEY_COL},${VAL_COL}`);
      if (error) return res.status(500).json(fail("internal error"));
      return res.json(ok({ apps: Array.isArray(data) ? data : [] }));
    } catch (err) {
      console.error("app-catalog error:", err?.message ?? err);
      return res.status(500).json(fail("internal error"));
    }
  });

  // ================= Boveda BiblioTube v1 =================
  // Auth: el cliente pide Supabase Auth y el backend recibe el JWT.
  // La factoria crea un cliente scoped por request para que rija RLS.
  function getBearer(req) {
    const h = req.headers?.authorization;
    if (typeof h !== "string") return null;
    const m = h.match(/^Bearer\s+(.+?)\s*$/);
    return m ? m[1] : null;
  }

  function vaultAuth(req, res, next) {
    const token = getBearer(req);
    if (!token) return res.status(401).json(fail("missing auth token"));
    try {
      req.vaultDb = toScoped(token);
    } catch (err) {
      console.error("vault auth error:", err?.message ?? err);
      return res.status(500).json(fail("internal error"));
    }
    return next();
  }

  function cleanId(raw) {
    return typeof raw === "string" ? raw.trim() : "";
  }

  app.get("/api/categories", vaultAuth, async (req, res) => {
    try {
      const { data, error } = await req.vaultDb.from("categories").select("*").order("orden", { ascending: true });
      if (error) return res.status(500).json(fail("internal error"));
      return res.json(ok(Array.isArray(data) ? data : []));
    } catch (err) {
      console.error("categories list error:", err?.message ?? err);
      return res.status(500).json(fail("internal error"));
    }
  });

  app.post("/api/categories", vaultAuth, async (req, res) => {
    let row;
    try {
      row = validateCategoryInput(req.body);
    } catch (err) {
      return res.status(400).json(fail(err?.message ?? "invalid category"));
    }
    try {
      const { data, error } = await req.vaultDb.from("categories").insert(row).select().single();
      if (error || !data) return res.status(500).json(fail("internal error"));
      return res.status(201).json(ok(data));
    } catch (err) {
      console.error("categories create error:", err?.message ?? err);
      return res.status(500).json(fail("internal error"));
    }
  });

  app.patch("/api/categories/:id", vaultAuth, async (req, res) => {
    const id = cleanId(req.params.id);
    if (!id) return res.status(400).json(fail("invalid id"));
    let patch;
    try {
      patch = validateCategoryInput(req.body, { partial: true });
    } catch (err) {
      return res.status(400).json(fail(err?.message ?? "invalid category"));
    }
    try {
      const { data, error } = await req.vaultDb.from("categories").update(patch).eq("id", id).select().single();
      if (!error && data) return res.json(ok(data));
      if (error && error.code !== "PGRST116") {
        console.error("categories update error:", error?.message ?? error);
        return res.status(500).json(fail("internal error"));
      }
      return res.status(404).json(fail("category not found"));
    } catch (err) {
      console.error("categories update error:", err?.message ?? err);
      return res.status(500).json(fail("internal error"));
    }
  });

  app.delete("/api/categories/:id", vaultAuth, async (req, res) => {
    const id = cleanId(req.params.id);
    if (!id) return res.status(400).json(fail("invalid id"));
    try {
      const { data, error } = await req.vaultDb.from("categories").delete().eq("id", id).select();
      if (error) return res.status(500).json(fail("internal error"));
      if (!Array.isArray(data) || data.length === 0) {
        return res.status(404).json(fail("category not found"));
      }
      return res.json(ok({ deleted: true }));
    } catch (err) {
      console.error("categories delete error:", err?.message ?? err);
      return res.status(500).json(fail("internal error"));
    }
  });

  app.get("/api/videos", vaultAuth, async (req, res) => {
    try {
      const rawCat = typeof req.query.category_id === "string" ? req.query.category_id.trim() : "";
      let q = req.vaultDb.from("videos").select("*");
      if (rawCat) q = q.eq("category_id", rawCat);
      q = q.order("prioridad", { ascending: false });
      q = q.order("fecha_guardado", { ascending: false });
      const { data, error } = await q;
      if (error) return res.status(500).json(fail("internal error"));
      return res.json(ok(sortVideos(Array.isArray(data) ? data : [])));
    } catch (err) {
      console.error("videos list error:", err?.message ?? err);
      return res.status(500).json(fail("internal error"));
    }
  });

  app.post("/api/videos", vaultAuth, async (req, res) => {
    let row;
    try {
      row = validateVideoInput(req.body);
    } catch (err) {
      return res.status(400).json(fail(err?.message ?? "invalid video"));
    }
    try {
      const { data, error } = await req.vaultDb.from("videos").insert(row).select().single();
      if (error || !data) return res.status(500).json(fail("internal error"));
      return res.status(201).json(ok(data));
    } catch (err) {
      console.error("videos create error:", err?.message ?? err);
      return res.status(500).json(fail("internal error"));
    }
  });

  app.patch("/api/videos/:id", vaultAuth, async (req, res) => {
    const id = cleanId(req.params.id);
    if (!id) return res.status(400).json(fail("invalid id"));
    let patch;
    try {
      patch = validateVideoInput(req.body, { partial: true });
    } catch (err) {
      return res.status(400).json(fail(err?.message ?? "invalid video"));
    }
    try {
      const { data, error } = await req.vaultDb.from("videos").update(patch).eq("id", id).select().single();
      if (!error && data) return res.json(ok(data));
      if (error && error.code !== "PGRST116") {
        console.error("videos update error:", error?.message ?? error);
        return res.status(500).json(fail("internal error"));
      }
      return res.status(404).json(fail("video not found"));
    } catch (err) {
      console.error("videos update error:", err?.message ?? err);
      return res.status(500).json(fail("internal error"));
    }
  });

  app.delete("/api/videos/:id", vaultAuth, async (req, res) => {
    const id = cleanId(req.params.id);
    if (!id) return res.status(400).json(fail("invalid id"));
    try {
      const { data, error } = await req.vaultDb.from("videos").delete().eq("id", id).select();
      if (error) return res.status(500).json(fail("internal error"));
      if (!Array.isArray(data) || data.length === 0) {
        return res.status(404).json(fail("video not found"));
      }
      return res.json(ok({ deleted: true }));
    } catch (err) {
      console.error("videos delete error:", err?.message ?? err);
      return res.status(500).json(fail("internal error"));
    }
  });

  // Sin auth: sirve tambien para el pegado rapido pre-login.
  app.get("/api/preview", async (req, res) => {
    const url = typeof req.query.url === "string" ? req.query.url.trim() : "";
    if (!url) return res.status(400).json(fail("invalid url: required"));
    try {
      const preview = await fetchPreview(url, fetchFn);
      return res.json(ok(preview));
    } catch (err) {
      return res.status(400).json(fail(err?.message ?? "invalid url"));
    }
  });

  // Proxy de miniaturas (evita hotlink directo + redes que bloquean CDNs).
  // Allowlist estricta: nunca open-proxy. Sin auth (las thumbs son publicas).
  app.get("/api/thumb", async (req, res) => {
    const url = typeof req.query.url === "string" ? req.query.url.trim() : "";
    if (!url) return res.status(400).json(fail("invalid url: required"));
    let target;
    try {
      target = validateThumbUrl(url);
    } catch (err) {
      return res.status(403).json(fail(err?.message ?? "invalid url"));
    }
    try {
      const upstream = await fetchFn(target.toString());
      if (!upstream.ok) return res.status(502).json(fail(`thumb upstream: HTTP ${upstream.status}`));
      const buf = Buffer.from(await upstream.arrayBuffer());
      if (buf.length === 0 || buf.length > THUMB_MAX_BYTES) {
        return res.status(502).json(fail("thumb upstream: empty or too large"));
      }
      const ct = upstream.headers?.get?.("content-type") || "image/jpeg";
      res.set("Content-Type", ct);
      res.set("Cache-Control", "public, max-age=86400");
      return res.send(buf);
    } catch (err) {
      return res.status(502).json(fail("thumb upstream: unreachable"));
    }
  });

  const clients = new Set();
  app.get("/api/events/stream", (req, res) => {
    res.writeHead(200, {
      "Content-Type": "text/event-stream",
      "Cache-Control": "no-cache",
      Connection: "keep-alive",
    });
    res.write('data: {"type":"hello"}\n\n');
    clients.add(res);
    req.on("close", () => clients.delete(res));
  });

  app.post("/api/events/emit", (req, res) => {
    const body = req.body;
    if (body === null || typeof body !== "object" || Array.isArray(body)) {
      return res.status(400).json(fail("invalid event payload"));
    }
    let payload;
    try {
      payload = JSON.stringify(body);
    } catch {
      return res.status(400).json(fail("invalid event payload"));
    }
    if (payload.length > 8192) return res.status(400).json(fail("event too large"));
    for (const c of clients) c.write(`data: ${payload}\n\n`);
    res.json(ok({ delivered: clients.size }));
  });

  app.use("/public", express.static(path.join(__dirname, "..", "public")));

  // Handler de JSON malformado -> envelope 400 (no HTML de body-parser)
  // eslint-disable-next-line no-unused-vars
  app.use((err, _req, res, _next) => {
    if (err?.type === "entity.parse.failed" || err instanceof SyntaxError) {
      return res.status(400).json(fail("invalid JSON"));
    }
    console.error("unhandled error:", err?.message ?? err);
    return res.status(500).json(fail("internal error"));
  });

  return app;
}
