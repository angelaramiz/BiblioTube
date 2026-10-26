# Contrato Bóveda BiblioTube v1 (2026-10-26)

Fuente de verdad hibrida: Room local + Supabase sync via backend. Auth: Supabase Auth en el cliente; el backend recibe `Authorization: Bearer <JWT>` y crea cliente Supabase con ese token (RLS por `auth.uid()`).

## Entidades

Categoria: `{id, nombre, color, orden}`
Video: `{id, category_id, titulo, descripcion, plataforma, url, thumbnail_url, fecha_guardado, prioridad, recordatorio_at, recordatorio_repeat}`
- `plataforma`: `youtube | instagram | tiktok | otra` (deteccion por dominio en backend)
- `recordatorio_repeat`: `none | daily | weekly`
- Listado videos: `prioridad DESC, fecha_guardado DESC`

## Endpoints (envelope `{success, data}` / `{success:false, error}` como `src/version.js`)

- `GET /api/categories` → `[{...}]` orden `orden ASC`
- `POST /api/categories` `{nombre, color?}` → creada (201)
- `PATCH /api/categories/:id` `{nombre?, color?, orden?}` → actualizada
- `DELETE /api/categories/:id` → `{deleted:true}` (videos quedan con `category_id` NULL — "Sin categoria")
- `GET /api/videos?category_id=` → lista ordenada (sin param: todas)
- `POST /api/videos` `{category_id?, titulo, descripcion?, plataforma?, url, thumbnail_url?, prioridad?, recordatorio_at?, recordatorio_repeat?}` → creado (201). Si no trae `plataforma`, el backend la detecta por dominio. Validar URL http(s).
- `PATCH /api/videos/:id` → actualizado
- `DELETE /api/videos/:id` → `{deleted:true}`
- `GET /api/preview?url=` → `{titulo, descripcion, thumbnail_url, plataforma}` via oEmbed (YouTube `noembed.com`? no — usar oEmbed oficial: youtube `https://www.youtube.com/oembed`, instagram/tiktok sin oEmbed publico estable → fallback: titulo=dominio, thumbnail vacia). Sin auth (para el pegado rapido pre-login tambien sirve).

Errores: 400 validacion (`fail`), 401 sin JWT, 404 inexistente, todo con mensaje real en `error`.

## Archivos

- `server/src/vault.js` — logica pura (deteccion plataforma, validacion, orden) testeable sin red
- `server/src/createApp.js` — nuevas rutas (inyectar supabase como ya hace)
- `server/tests/vault.test.js` + `server/tests/vault-api.test.js`
- `server/supabase.sql` — tablas `profiles/categories/videos` + RLS por `auth.uid()`
