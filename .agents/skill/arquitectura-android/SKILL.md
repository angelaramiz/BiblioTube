---
name: arquitectura-android
version: 0.2.1
description: Repositorios de referencia para arquitectura Android (Clean Architecture, modularización, calidad). Usar al estructurar una app Kotlin o decidir patrones de capas.
---

# SKILL: arquitectura-android

## Resumen

Dos repositorios externos de referencia para estructura y arquitectura de apps
Android en Kotlin, curados como primarios. Son solo lectura: aquí vive el
procedimiento de consulta, nunca su contenido.

## Referencias primarias (verificadas oct-2026)

| Repo | Qué tomar de él | Licencia |
|------|-----------------|----------|
| https://github.com/android/nowinandroid | El "qué hacer": guía oficial de arquitectura + modularización, Hilt, dobles de prueba sin mocks, Roborazzi, sabores demo/prod, baseline profiles. Única referencia viva mantenida por Google | Apache-2.0 |
| https://github.com/igorwojda/android-showcase | El "cómo organizarlo": template con módulos por feature (presentación/dominio/datos), MVVM+MVI, Koin, convention plugins, catálogo de versiones, Konsist/Ktlint/Detekt + CI | MIT |

## Uso

1. Definir `{{decision}}` en una línea (qué capa o patrón está en juego).
2. nowinandroid primero (dirección oficial) → showcase después (estructura
   copiable y convenciones de calidad).
3. Registrar la decisión y el porqué en el `ADAPTER.md` o decisiones del proyecto.

## Reglas

- Enlazar, nunca copiar: respetar licencias (Apache-2.0/MIT exigen atribución).
- Si el proyecto ya usa un DI (Koin/Hilt) o async (Coroutines/Flow), la
  referencia no lo cambia: solo informa la estructura.
- Dimensionar antes de copiar: 1–2 devs / pocas pantallas → capas por paquete
  en un solo módulo bastan. Modularizar por feature, DI y convention plugins
  solo cuando duela el build o haya equipos paralelos. Registrar el porqué en
  el ADAPTER (origen: revisión BiblioTube, que es single-app sin DI y funciona).
- Referencias descartadas a propósito (ver CHANGELOG v0.2.0): no re-agregarlas
  sin una razón nueva.

## Variables del proyecto

| Variable | Descripción | Ejemplo |
|----------|-------------|---------|
| `{{decision}}` | Decisión de arquitectura a tomar | `cómo separar repo local/remoto en la capa de datos` |
| `{{patron}}` | Patrón en evaluación | `MVVM`, `MVI`, `Clean por feature` |

## Adaptadores conocidos

- _(ninguno aún — en evaluación, pendiente de primer uso real)_

## Referencias

| Recurso | Ruta |
|---------|------|
| Changelog | `CHANGELOG.md` (junto a este archivo) |
| Skills hermanas | `skill/movil/android-kotlin-app`, `skill/movil/emulador-android`, `skill/movil/ota-android-generic` |
| Re-verificar | `2027-04` (los dos upstreams se mueven rápido) |
