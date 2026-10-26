# Changelog — ota-android-generic

## [Unreleased]

## [1.0.0] — 2026-10-04

- Primera versión estándar. Consolida las copias idénticas (verificado byte
  por byte) de Gestor inventory (`skills/ota-android-generic/`, 751 lín.) e
  inventorio (`.agents/skill/ota-android-generic/`).
- Los scripts dejan de ser snippets pegados en el doc: ahora son archivos
  reales en `scripts/` (`build-debug`, `build-release`, `build-and-deploy`,
  `update-version`, `full-release`, `release`) + `apps.example.json`.
- Parametrización: `release.ps1` ya no trae las 3 apps de inventorio
  hardcodeadas (`alpha/conteo/operations`, `com.inventorio.*`, `apps movil/`,
  `warehouse_settings`, `inventorio.onrender.com`); lee `apps.json` del
  proyecto (`-AppsJson`) con `VersionEndpoint` por app en vez de ramas
  if/elseif. `update-version.ps1` y `full-release.ps1` aceptan `-DbTable`.
- Relación con `skill/android-kotlin-app` (§3–§5): mismo patrón
  versionado/OTA/deploy; si esa skill evoluciona el patrón, esta plantilla
  debe re-sincronizarse (y viceversa).
