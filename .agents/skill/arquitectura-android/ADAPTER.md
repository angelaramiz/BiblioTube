# ADAPTER — arquitectura-android @ bibliotube

Instalado 2026-10-26. Skill estándar v0.2.1 (copia sin editar). Primera
instalación real de la skill (la lleva a v1.0.0 en BiblioSkills).

## Mapeo (verificado en disco)

| Variable | Valor en BiblioTube |
|----------|---------------------|
| `{{decision}}` | Estructura de capas para v1 (shell + OTA + bóveda) |
| `{{patron}}` | Capas por paquete en un solo módulo (sin MVVM/MVI formal) |
| Módulos | `:app` + lib `:bt-downloader` (acople yt-dlp, `com.bibliotube.downloader`) |
| DI | Ninguno (0 menciones Hilt/Koin/Dagger en build + 17 `.kt`) |
| Capas | `api/` (ApiClient OTA+bóveda, SseClient) · `data/` (VaultRepository, local Room, remote AuthClient GoTrue REST) · `ui/` (Auth + bóveda) · `work/` (Sync + recordatorios) |
| Package | `com.bibliotube.app` |

## Decisión: no DI, no módulos por feature, no convention plugins

Aplicando la regla "dimensionar antes de copiar": 1 dev, v1 = shell + OTA +
bóveda (~1.5k líneas), build sano, OTA real E2E verificado. Copiar la
estructura multi-módulo de nowinandroid/showcase sería sobre-arquitectura.

Revisar cuando: el build duela, haya equipos paralelos, o el dominio crezca
más allá de la bóveda. Entonces re-evaluar contra la skill (showcase como
plantilla de modularización).

## Referencias consultadas

- nowinandroid: confirma capas repository + Room como verdad local + sync
  (igual que `VaultRepository`), sin necesidad de sus módulos.
- showcase: confirma que capas por paquete son el paso previo legítimo a
  módulos por feature.
