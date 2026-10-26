# input.md — bibliotube

## Decisiones pendientes del usuario

1. Nombre de la app y `{{dir_app}}` (prop: `BiblioTube-Android/`).
2. Package ID `{{paquete}}` (prop: `com.bibliotube.app`).
3. Que hace la app (pantallas, backend si/no, Supabase si/no, OTA si/no).
4. Min/target SDK (prop: minSdk 26, target/compile 35 contra el AVD disponible).

## Plan de integracion de skills (hecho 2026-10-25)

- Copiadas a `.agents/skill/<nombre>/` sin editar + `ADAPTER.md` local cada una.
- `emulador-android`: mapeo completo (AVD/SDK conocidos), pendiente PKG/APK hasta crear la app.
- `android-kotlin-app` / `ota-android-generic`: variables TBD hasta definir backend.
- Siguiente paso: crear el proyecto Android y rellenar ADAPTERs con valores reales.

## Evaluacion arquitectura-android v0.2.1 (hecho 2026-10-26)

- Mapeo: `BiblioTube-Android/` = `:app` (~1.5k lineas, 17 `.kt`, capas por
  paquete `api/data/ui/work`) + lib `:bt-downloader`; sin Hilt/Koin/Dagger
  (0 menciones en build + fuentes); backend Express + Supabase; OTA E2E OK.
- Referencias consultadas: nowinandroid (dirección) + showcase (comparación).
- Decisión (criterio "dimensionar antes de copiar"): NO modularizar por
  feature, NO DI, NO convention plugins por ahora — 1 dev, v1 shell+OTA,
  build sano. Detalle + triggers en
  `.agents/skill/arquitectura-android/ADAPTER.md`.
- Instalada: copia estándar v0.2.1 sin editar + ADAPTER local.
