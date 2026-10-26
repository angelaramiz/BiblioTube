# Changelog — arquitectura-android

## [Unreleased]

## [0.2.1] — 2026-10-26

- Revisión externa del agente de BiblioTube (aprobada con 2 ajustes):
  jerga `PendingTx/PendingOp` del CHANGELOG → redacción genérica (fuga de
  proyecto-origen corregida en la entrada v0.2.0); nueva regla "Dimensionar
  antes de copiar" (capas por paquete bastan en apps chicas; modularizar/DI
  solo cuando duela); fecha de re-chequeo `2027-04` en Referencias.
- Verificado en disco: BiblioTube-Android es single-app (`:app` + lib
  `:bt-downloader`), sin Hilt/Koin/Dagger (0 menciones en build + 17 `.kt`),
  capas por paquete — la premisa de la revisión es correcta.

## [0.2.0] — 2026-10-26

- Recorte a 2 referencias primarias (decisión del dueño): nowinandroid
  (dirección oficial) + android-showcase (estructura copiable).
- Descartadas a propósito, no re-agregar sin razón nueva:
  `architecture-samples` (enseña lo mismo que la guía oficial, en juguete),
  `MarvelHeroes` (MVVM+Koin datado, solo ejemplo mínimo de repositorio),
  `clean-architecture-components-boilerplate` (capas vigentes, libs 2017–2018),
  `OfflineSampleApp` (offline puntual; el patrón offline-queue ya está cubierto
  cuando el proyecto tiene cola propia — re-agregar solo si no la hay).

## [0.1.0] — 2026-10-26

- Alta como índice de referencias externas (no vendoreadas): 6 repos
  verificados (nowinandroid, architecture-samples, MarvelHeroes,
  android-showcase, clean-architecture-components-boilerplate,
  OfflineSampleApp) con enfoque, licencia y cuándo consultarlos.
- Correcciones incluidas: 2 URLs del mensaje origen daban 404
  (`bufferapp/clean-architecture-components` → `...-boilerplate`;
  `jshvartsman/offlinefirst` → `jshvarts/OfflineSampleApp`) y MarvelHeroes
  es MVVM+Koin, no MVI+Hilt.
- Estado: en evaluación, pendiente de primer uso real en un proyecto.
