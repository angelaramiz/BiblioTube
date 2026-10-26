# ADAPTER — open-design @ bibliotube

Instalado 2026-10-26. Skill estandar v1.0.0 (copia sin editar). Primer uso real.

| Variable | Valor en BiblioTube |
|----------|---------------------|
| `{{agente}}` | `opencode` (sin instalar MCP: decision documentada abajo) |
| `{{sistema_diseno}}` | `DESIGN.md` en la raiz (contrato de marca) |
| `{{plantilla}}` | N/A (app nativa, sin prototipo HTML) |
| `{{proyecto_id}}` | N/A |

Decision: se adopta el contrato (`DESIGN.md` como fuente de verdad) y se
descarta la maquinaria (MCP + daemon + prototipos HTML de una pagina) por
desproporcionada para 5 pantallas Compose nativas. Si un dia hay rediseño
grande, re-evaluar `od mcp install`.
