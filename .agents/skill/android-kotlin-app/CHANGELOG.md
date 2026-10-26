# Changelog — android-kotlin-app

## [Unreleased]

## [1.0.0] — 2026-10-04

- Primera versión estándar. Consolida las copias idénticas (verificado byte
  por byte) de Gestor inventory (`skills/android-kotlin-app/`) e inventorio
  (`.agent/skills/android-kotlin-app/`), ambas de origen inventorio.
- Parametrización: `{{dir_app}}`, `{{paquete}}`, `{{tabla_versiones}}`,
  `{{clave_version}}`, `{{endpoint_version}}`, `{{endpoint_catalogo}}`,
  `{{stream_realtime}}`, `{{dir_public_apk}}`, `{{deploy}}`. Los valores de
  inventorio (`apps movil/`, `com.inventorio.*`, `warehouse_settings`,
  `/api/android-version`, Render + `public/`) salieron del estándar.
- Fix genérico: el temporal del snippet Management API usa `$env:TEMP` en vez
  de una ruta de usuario concreta.
