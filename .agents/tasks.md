# tasks.md — bibliotube

- [x] (2026-10-25) Instalar skills: android-kotlin-app, emulador-android, ota-android-generic v1.0.0 + ADAPTER.md
- [x] (2026-10-25) Setup entorno: scripts/setup-env.ps1 (ANDROID_HOME, JAVA_HOME=JBR17)
- [ ] Definir app: nombre, paquete, alcance (esperando usuario)
- [x] (2026-10-25) Crear proyecto Android (BiblioTube-Android/) + backend (server/) + Supabase SQL
- [x] (2026-10-26) TDD backend: 30/30 tests, cobertura 95% lineas. API con envelope {success,data}. ApiClient.kt compatible dual.
- [x] (2026-10-26) Boveda v1 app: Auth GoTrue REST, Room + sync, carpetas/videos CRUD, preview oEmbed, recordatorios con notificacion, prioridad. Compila + login verificado en emulador.
- [ ] Usuario: ejecutar server/supabase.sql en dashboard (tablas profiles/categories/videos + RLS)
- [ ] E2E: backend local + registro + crear carpeta/video + sync + recordatorio
- [x] (2026-10-26) Render-ready: landing en / + /api/health + render.yaml + .gitignore + DEPLOY.md. 79/79 tests, health y landing verificados local.
- [x] (2026-10-26) Evaluar arquitectura-android v0.2.1: decisión NO modularizar/DI por ahora (criterio dimensionar) + ADAPTER.md. Primera instalación real de la skill.
- [x] (2026-10-26) Repo git conectado a github.com/angelaramiz/BiblioTube (main, commit a6daa54). Secretos excluidos.
- [x] (2026-10-26) APK v1.0.0 publicado: server/public/bibliotube.apk (46MB) + push d96107f. Render redespliega solo.
- [x] (2026-10-26) OTA real E2E: v3 detecto 1.0.3 en Render → descargo a updates/ → instalador (origenes + Play Protect) → v4 corriendo, sesion intacta.
- [x] (2026-10-25) Supabase: URL + publishable key en server/.env, npm install OK (80 pkgs). Tabla pendiente de crear.
- [x] (2026-10-25) Probar en emulador: install + launch OK, captura verifica UI (header, placeholder, BottomBar visible)
- [x] (2026-10-26) Fase 0 arquitectura: SessionManager, DownloaderProvider singleton, Room exportSchema + 1.json.
- [x] (2026-10-26) v1.0.5 publicada: identidad visible + copiar logs. Pendiente: logs del telefono + foto capa oscura.
