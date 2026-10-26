---
name: ui-ux-pro-max
version: 0.1.0
description: Inteligencia de diseño para UI profesional (estilos, paletas, tipografías, checklists). Usar al construir o retematizar una interfaz y necesitar un design system.
---

# SKILL: ui-ux-pro-max

## Resumen

Referencia al proyecto externo `ui-ux-pro-max-skill` (MIT): motor de
razonamiento que genera un design system completo (patrón + estilo + colores +
tipografía + efectos + anti-patrones + checklist pre-entrega) a partir del tipo
de producto. No se vendorea su contenido en BiblioSkills: se instala con su CLI
oficial y aquí solo vive el procedimiento.

## Variables del proyecto

| Variable | Descripción | Ejemplo |
|----------|-------------|---------|
| `{{asistente}}` | Plataforma destino del instalador | `opencode` |
| `{{stack}}` | Stack del frontend (default upstream: HTML + Tailwind) | `React + Tailwind` |
| `{{estilo}}` | Estilo objetivo del catálogo | `Minimalism & Swiss Style` |
| `{{slug_proyecto}}` | Slug para `design-system/<slug>/MASTER.md` | `miapp` |

## Instalación / uso

```bash
npm install -g ui-ux-pro-max-cli
cd <proyecto>
uipro init --ai {{asistente}}   # opencode soportado; --ai universal instala en .agents/skills/
```

Requiere Python 3.x solo para `search.py` (stdlib, sin red). El agente nunca
instala software en la máquina del usuario: si falta, se pide al humano.

Flujo verificado (upstream):

1. Pedir la tarea en lenguaje natural ("Build a landing page for my SaaS").
2. Generar el design system (patrón + estilo + colores + tipografía +
   anti-patrones + checklist) antes de escribir código.
3. Persistir con `--persist -p "{{slug_proyecto}}"` → `design-system/<slug>/MASTER.md`
   + overrides por página en `pages/`; al construir una página, leer MASTER y,
   si existe su override, priorizarlo.
4. Retematizar apps existentes por lotes pequeños: auditar hardcodeados →
   mapear a tokens semánticos existentes → migrar → verificar
   (contraste, foco, responsive, light/dark).

## Reglas

- Buscar primero en el catálogo antes de proponer estilos propios.
- Sin emojis como iconos (SVG: Heroicons/Lucide); contraste texto 4.5:1;
  foco visible; respetar `prefers-reduced-motion`.
- Verificar responsive en 375/768/1024/1440.

## Adaptadores conocidos

- _(ninguno aún — v0.1.0 en evaluación, pendiente de primer uso real)_

## Referencias

| Recurso | Ruta |
|---------|------|
| Upstream (MIT, atribución obligatoria si se vendorea) | https://github.com/nextlevelbuilder/ui-ux-pro-max-skill |
| Changelog | `CHANGELOG.md` (junto a este archivo) |
| Skills hermanas | `skill/diseno/open-design`, `skill/diseno/awesome-ai-tools-for-ui` |
