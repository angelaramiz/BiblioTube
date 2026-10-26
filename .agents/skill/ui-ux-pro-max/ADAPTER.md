# ADAPTER — ui-ux-pro-max @ bibliotube

Instalado 2026-10-26. Skill estandar v1.0.0 (copia sin editar). Primer uso real.

| Variable | Valor en BiblioTube |
|----------|---------------------|
| `{{asistente}}` | `opencode` |
| `{{stack}}` | `Kotlin + Jetpack Compose + Material3` (NO el default upstream HTML+Tailwind: tokens mapeados a mano en `ui/theme/`) |
| `{{estilo}}` | Boveda nocturna + baseline Material3 (ver `DESIGN.md`) |
| `{{slug_proyecto}}` | `bibliotube` (MASTER equivalente: `DESIGN.md` en raiz, no `design-system/` del CLI) |

Metodo aplicado: design system primero (`DESIGN.md` + `ui/theme/Color.kt|Type.kt|Theme.kt`),
checklist manual (contraste 4.5:1 con pares M3, foco M3, sin animaciones custom,
dark/light por `isSystemInDarkTheme()` + `windowBackground` dia/noche).
CLI `uipro` no instalado: stack nativo, mapeo manual.
