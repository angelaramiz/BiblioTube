---
name: open-design
version: 0.1.0
description: Workspace colaborativo de diseño con agentes IA (prototipos, decks, imágenes, video). Usar al estructurar prototipos, layouts y sistemas de diseño antes de escribir código.
---

# SKILL: open-design

## Resumen

Referencia al proyecto externo `open-design` (Apache-2.0): workspace local-first
donde un coding agent genera prototipos (web/móvil/desktop), decks, imágenes y
video a partir de un brief + `DESIGN.md` como contrato de marca. Incluye 100+
skills funcionales (`skills/`, convención `SKILL.md`) y catálogo separado de
plantillas renderizables (`design-templates/`). No se vendorea: se integra vía
MCP y aquí solo vive el procedimiento.

## Variables del proyecto

| Variable | Descripción | Ejemplo |
|----------|-------------|---------|
| `{{agente}}` | Coding agent donde se instala el MCP | `opencode` |
| `{{sistema_diseno}}` | Contrato de marca del proyecto | `DESIGN.md` en la raíz |
| `{{plantilla}}` | Plantilla de partida | `saas-landing`, `dashboard`, `web-prototype` |
| `{{proyecto_id}}` | Proyecto OpenDesign (para `od files ...`) | ver `od project list --json` |

## Instalación / uso

```bash
od mcp install {{agente}}   # opencode soportado; --print previsualiza, --uninstall retira
```

Flujo verificado (upstream): brief → plugin → dirección → design system
(`DESIGN.md`) → artefacto → handoff a ingeniería → memoria.

1. Fijar la dirección (brief o screenshot/URL → `DESIGN.md` reutilizable).
2. El agente compone skill funcional o plantilla + `DESIGN.md` y escribe los
   archivos canónicos del proyecto (la preview los sigue, no al revés).
3. Para prototipos: artefactos HTML de una sola página que leen `DESIGN.md`.
4. Para refrescar código existente: entregar repo git + `DESIGN.md` y el agente
   refactoriza los componentes al spec de marca.

## Reglas

- `DESIGN.md` es el contrato: ningún render lo contradice.
- MCP en solo-lectura por defecto; el daemon liga a `127.0.0.1`.
- Skills funcionales (`skills/`) dan comportamiento; plantillas
  (`design-templates/`) dan el plano de render: no mezclarlos.

## Adaptadores conocidos

- _(ninguno aún — v0.1.0 en evaluación, pendiente de primer uso real)_

## Referencias

| Recurso | Ruta |
|---------|------|
| Upstream (Apache-2.0, conservar avisos si se vendorea) | https://github.com/nexu-io/open-design |
| Changelog | `CHANGELOG.md` (junto a este archivo) |
| Skills hermanas | `skill/diseno/ui-ux-pro-max`, `skill/diseno/awesome-ai-tools-for-ui` |
