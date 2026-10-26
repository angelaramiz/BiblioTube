import dotenv from "dotenv";
import express from "express";
import path from "path";
import { fileURLToPath } from "url";
import { createClient } from "@supabase/supabase-js";
import { createApp } from "./src/createApp.js";

dotenv.config();

const url = process.env.SUPABASE_URL;
const anonKey = process.env.SUPABASE_ANON_KEY || process.env.SUPABASE_KEY;
const serviceKey = process.env.SUPABASE_KEY;

// Lecturas publicas OTA (sin login) con service key, como antes.
const publicClient = createClient(url, serviceKey);

// Boveda: un cliente por request con el JWT del usuario (anon key)
// para que rijan las politicas RLS (auth.uid()). La service key
// bypasea RLS y nunca debe usarse en rutas con auth.
const userClientFactory = (jwt) =>
  createClient(url, anonKey, {
    global: { headers: { Authorization: `Bearer ${jwt}` } },
  });

const app = createApp(userClientFactory, { publicClient });

// Salud para Render + landing de descarga en /
app.get("/api/health", (_req, res) => res.json({ success: true, data: { ok: true } }));
const __dirname = path.dirname(fileURLToPath(import.meta.url));
app.use("/", express.static(path.join(__dirname, "public")));

const port = process.env.PORT || 3000;
app.listen(port, () => console.log(`bibliotube-server en puerto ${port}`));
