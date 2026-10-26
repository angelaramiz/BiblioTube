---
name: awesome-ai-tools-for-ui
version: 0.1.0
description: Lista curada de herramientas IA para UI/UX (skills, apps, MCP, recursos). Usar al elegir librería o herramienta para un desafío de interfaz complejo.
---

# SKILL: awesome-ai-tools-for-ui

## Resumen

Referencia a la lista curada `awesome-ai-tools-for-ui`: catálogo por categorías
(Skills, Apps, MCP Servers & Plugins, Design Tools, Resources) para seleccionar
la mejor herramienta según el desafío de interfaz. Es solo un README curado, no
instalable: aquí vive el procedimiento de selección. Ojo: en la raíz del
upstream no hay archivo LICENSE visible — solo referenciar, nunca copiar.

## Variables del proyecto

| Variable | Descripción | Ejemplo |
|----------|-------------|---------|
| `{{desafio_ui}}` | Problema concreto de interfaz a resolver | `auditar un dashboard contra anti-patrones` |
| `{{categoria}}` | Dónde buscar primero | `Skills`, `Apps`, `MCP Servers & Plugins`, `Design Tools`, `Resources` |
| `{{stack}}` | Stack del proyecto (filtra candidatas) | `React + Tailwind` |

## Uso

1. Definir `{{desafio_ui}}` en una línea (qué duele, en qué pantalla/flujo).
2. Recorrer `{{categoria}}` en este orden: Skills → MCP → Apps → Design Tools →
   Resources. Verificadas en el upstream (oct-2026): `ui-ux-pro-max-skill`
   (design systems), `open-design` (app de prototipos), Taste (anti-diseño
   genérico), Material Design 3, `shadcn/ui` Skills, Magic MCP, Laws of UX.
3. Elegir 1–3 candidatas compatibles con `{{stack}}`; verificar en su repo
   licencia, instalación y mantenimiento antes de proponerlas.
4. Registrar la elección y el porqué en el `ADAPTER.md` del proyecto (qué se
   evaluó, qué se descartó).

## Reglas

- La lista es un punto de partida, no una recomendación: cada candidata se
  verifica en su fuente antes de usarse.
- Sin LICENSE visible en el upstream: enlazar, no copiar contenido.
- Si la elegida es `ui-ux-pro-max` u `open-design`, delegar a esas skills.

## Adaptadores conocidos

- _(ninguno aún — v0.1.0 en evaluación, pendiente de primer uso real)_

## Referencias

| Recurso | Ruta |
|---------|------|
| Upstream (lista curada, solo referenciar) | https://github.com/maxbogo/awesome-ai-tools-for-ui |
| Changelog | `CHANGELOG.md` (junto a este archivo) |
| Skills hermanas | `skill/diseno/ui-ux-pro-max`, `skill/diseno/open-design` |
