# ADAPTER — ota-android-generic @ bibliotube

Instalado 2026-10-25. Skill estandar v1.0.0 (copia sin editar). Sin backend aun: MAPEO pendiente.

```yaml
server_file: TBD
version_endpoint: TBD
version_endpoint_params: TBD
db_table: TBD
db_key_column: TBD
db_value_column: TBD
apk_directory: TBD
apps:
  - name: BiblioTube
    package: com.bibliotube.app  # propuesto, TBD
    db_key: TBD
    apk_filename: bibliotube.apk  # propuesto, TBD
    source_path: BiblioTube-Android
    main_activity: MainActivity.kt
comparison_field: versionCode
cache_busting: true
clean_old_apks: true
auto_check_on_start: true
manual_check_button: true
```

Nota: si el proyecto tendra una sola app y backend propio + Supabase, la skill
hermana `android-kotlin-app` (§3–§5) cubre el mismo patron. Elegir una como
principal al definir el backend y dejarlo anotado aqui.
