# Changelog — emulador-android

## [Unreleased]

## [1.0.0] — 2026-10-04

- Primera versión estándar. Consolida las copias de CalendarFinace
  (`.agents/skill/emulador-android/`, 63 lín.) y Gestor inventory
  (`skills/emulador-android/`, 48 lín., `SKILL.md` idéntico).
- Parametrización: `EMU_AVD` / `EMU_PKG` / `EMU_APK` / `EMU_RES` vía entorno;
  los valores concretos salieron del estándar al `ADAPTER.md` de cada proyecto.
- Fix genérico traído del diff entre copias: el temporal en el equipo pasa de
  nombre por proyecto (`fintrack_preview.png`, `gestor_preview.png`) a
  `emu_preview.png`.
- Lo específico de FinTrack (credenciales demo, tabs, Tile, gestos) queda fuera
  del estándar: pertenece al futuro `ADAPTER.md` de CalendarFinace.
