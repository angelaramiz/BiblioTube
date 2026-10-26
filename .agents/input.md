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
