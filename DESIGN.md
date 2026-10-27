# DESIGN.md — BiblioTube (contrato de marca y diseño)

Fuente de verdad visual y de arquitectura de interfaz. Ningún render la contradice.

## Marca e Identidad

- **Nombre**: BiblioTube (Biblio = Bóveda / Biblioteca de conocimiento curada; Tube = Multimedia / Video).
- **Logotipo**: Isotipo geométrico minimalista en diseño plano que fusiona las páginas de un libro abierto / bóveda con el triángulo de reproducción multimedia ("Play") central, complementado por tipografía limpia y formal.
- **Iconografía**:
  - `ic_bibliotube_logo.xml` (VectorDrawable de alta fidelidad).
  - Adaptive Icons: `@drawable/ic_launcher_background` y `@drawable/ic_launcher_foreground`.
  - Componente Composable: `BiblioTubeLogoHeader` y `BiblioTubeHeroLogo`.
- **Tono**: Formal, sobrio, minimalista y moderno con diseño plano (Flat Design puro, sin falsos biseles 3D ni gradientes chillones).

## Tokens de Color (Material 3 en `ui/theme/Color.kt`)

| Token | Light | Dark (predeterminado) | Uso |
|---|---|---|---|
| `primary` (acento marca) | `#6750A4` | `#D0BCFF` | Acento principal, llamadas a la acción, estado activo |
| `onPrimary` | `#FFFFFF` | `#381E72` | Texto/iconos sobre color primario |
| `primaryContainer` | `#EADDFF` | `#4F378B` | Contenedor de selección, chips activos |
| `onPrimaryContainer` | `#21005D` | `#EADDFF` | Texto sobre contenedor primario |
| `background` | `#FAF9FD` | `#121116` | Fondo principal de la app |
| `onBackground` | `#1C1B1F` | `#E6E0E9` | Texto principal sobre fondo |
| `surface` | `#FFFFFF` | `#18161D` | Superficie de navegación, modales y header |
| `onSurface` | `#1C1B1F` | `#E6E0E9` | Texto en superficies |
| `surfaceVariant` | `#F1EEF7` | `#23202A` | Fondo de tarjetas planas |
| `outlineVariant` | `#CAC4D0` | `#36323E` | Bordes sutiles de 1dp en tarjetas planas |

- `dynamicColor = false`: la identidad de marca prima sobre los colores dinámicos del sistema operativo.
- Contraste texto/fondo ≥ 4.5:1 rigurosamente verificado.

## Reglas de Interfaz (Flat Design & Minimalismo Formal)

1. **Header superior plano**:
   - Barra limpia `surface` con borde inferior `outlineVariant.copy(alpha = 0.25f)`.
   - Incluye `BiblioTubeLogoHeader` con el isotipo oficial y badge sobrio de versión.
2. **Navegación inferior**:
   - `NavigationBar` plana con elevación tonal `0.dp`, utilizando iconos oficiales vectoriales (`Icons.Filled.VideoLibrary` / `Icons.Outlined.VideoLibrary` y `Icons.Filled.Settings` / `Icons.Outlined.Settings`).
3. **Tarjetas de video planas (`FlatVideoCard`)**:
   - Fondo `surfaceVariant`, borde `1.dp` en `outlineVariant`, esquinas refinadas de 14dp.
   - Jerarquía visual estricta: Badges planos de plataforma (YouTube, Instagram, TikTok) y carpeta -> Thumbnail con proporción cuidada -> Título formal semibold -> Descripción -> Metadatos y acciones rápidas (Abrir enlace externo, Editar, Eliminar).
4. **Búsqueda y filtrado en tiempo real**:
   - Barra de búsqueda plana e integrada en la cabecera de la bóveda para localizar videos al instante.
   - Selector horizontal de carpetas mediante chips planos minimalistas.
5. **Estado vacío elegante (`FlatEmptyState`)**:
   - Mensaje e ilustración vectorial sobria con llamada a la acción clara para agregar el primer enlace.
6. **Contrato Compose obligatorio**:
   - Toda raíz de pantalla fija `background(colorScheme.background)` y provee `LocalContentColor provides onBackground`.
