# DESIGN.md — BiblioTube (contrato de marca)

Fuente de verdad visual. Ningún render la contradice (skill `open-design`).

## Marca

Bóveda nocturna: la app se usa para guardar y revisar videos, de noche, en el
móvil. Fondo oscuro por defecto, acento morado propio, texto siempre legible.

## Tokens (mapeados a Material3 en `ui/theme/`)

| Token | Light | Dark (default) |
|---|---|---|
| `primary` (marca) | `#6750A4` | `#D0BCFF` |
| `onPrimary` | `#FFFFFF` | `#381E72` |
| `background` | `#FFFBFE` | `#141218` |
| `surface` | `#FFFBFE` | `#1D1B20` |

Tipografía: Material3 por defecto (`Type.kt` existe para futuros pesos de marca).
Sin emojis como iconos. Contraste texto ≥ 4.5:1 (pares M3 verificados).
`dynamicColor = false`: la marca manda sobre el wallpaper (Android 12+).

## Reglas de pantalla

- Header: título + versión/sesión (`bodySmall`, `onBackground` 70%).
- BottomBar siempre visible (patrón `Box(weight(1f))`, skill `android-kotlin-app`).
- Estados vacíos con acción (`Sin videos… + botón +`).
- Errores: mensaje real del servidor, nunca genérico.
- Notificaciones: canal `bibliotube_reminders`; pedir permiso al primer uso.
