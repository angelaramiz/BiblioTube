# DEPLOY — backend + APK en Render

Render corre `server/` (API + landing + APK). La app Android apunta al
dominio de Render como `server_url`.

## 1. Preparar (una vez)

1. Backup del keystore: copia `BiblioTube-Android/debug.keystore` a un lugar
   seguro (USB/caja fuerte). **Sin este archivo, las apps ya instaladas no
   aceptan actualizaciones OTA** (cambia la firma). No va al repo.
2. `server/supabase.sql` ya aplicado en Supabase (tablas + RLS).
3. Crea repo git y súbelo a GitHub:
   ```powershell
   cd C:\Users\angel\Desktop\Proyectos\bibliotube
   git init; git add -A; git commit -m "feat: bibliotube v1 + landing + render"; git branch -M main
   git remote add origin <tu-repo>; git push -u origin main
   ```
   El APK (`server/public/bibliotube.apk`, ~46 MB) SÍ se commitea: Render lo
   sirve desde disco. Alternativa (si crece): Supabase Storage + `apkUrl`
   absoluta en la tabla.

## 2. Desplegar en Render

1. Dashboard → **New + → Blueprint** → conecta el repo (lee `render.yaml`).
2. En Environment, completa (dashboard, nunca en el repo):
   - `SUPABASE_URL` = `https://buccrasmylvkzymbhfwx.supabase.co`
   - `SUPABASE_ANON_KEY` = publishable key
   - `SUPABASE_KEY` = misma publishable (solo lecturas OTA)
3. Deploy. Verifica: `https://<tu-app>.onrender.com/api/health` → `{"success":true,...}`
   y la landing en `https://<tu-app>.onrender.com/`.

## 3. Publicar una versión (cada release)

1. Bump `versionCode`/`versionName` en `BiblioTube-Android/app/build.gradle.kts`.
2. `.\gradlew.bat assembleRelease` (en `BiblioTube-Android/`).
3. Copiar APK → `server/public/bibliotube.apk` (o `release.ps1 -AppsJson ./apps.json ...`).
4. UPDATE fila `app_version_bibliotube` en tabla `app_versions`
   (`versionCode`, `versionName`, `apkUrl: "/public/bibliotube.apk"`).
5. `git add server/public/bibliotube.apk` + commit + push.
6. Dispara el redeploy inmediato: `.\scripts\deploy-render.ps1`
   (usa el deploy hook de `.agents/render-deploy-hook.local`, gitignored —
   no esperar al auto-deploy). Verifica el estado en el dashboard de Render.
7. Verificar: `/api/app-version?app=bibliotube` responde lo nuevo y el APK
   descarga (HTTP 200).
7. La app instalada lo detecta al abrir (o Ajustes → Buscar actualizaciones).
   Para probar: instala un APK viejo encima del flujo una vez.

## 4. Apuntar la app al servidor

En la app (Ajustes o primer arranque) `server_url` debe ser
`https://<tu-app>.onrender.com`. El emulador usa `http://10.0.2.2:3000`
solo para backend local.

## Problemas comunes

| Síntoma | Causa | Fix |
|---|---|---|
| Landing dice "Versión no disponible" | `app_versions` vacía o RLS | Re-ejecuta `supabase.sql`; verifica `/api/app-version` |
| APK 404 | No commiteado o ruta distinta a `apkUrl` | Revisa `server/public/` y la fila en Supabase |
| OTA detecta pero no instala | Firma distinta / falta permiso | Mismo `debug.keystore` + `REQUEST_INSTALL_PACKAGES` (ya en Manifest) |
| Render "Deploy failed" | `SUPABASE_URL` vacía | `createClient(url)` lanza; completa env vars y redeploy |
