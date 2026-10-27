CREATE TABLE IF NOT EXISTS app_versions (
  clave TEXT PRIMARY KEY,
  valor JSONB NOT NULL
);

INSERT INTO app_versions (clave, valor) VALUES
  ('app_version_bibliotube', '{"versionCode": 1, "versionName": "1.0.0", "apkUrl": "/public/bibliotube.apk"}')
ON CONFLICT (clave) DO NOTHING;

-- Lectura publica del endpoint de version (la app la consulta sin login)
ALTER TABLE app_versions ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "public read" ON app_versions;
CREATE POLICY "public read" ON app_versions FOR SELECT USING (true);

-- ================= Boveda BiblioTube v1 =================
CREATE TABLE IF NOT EXISTS profiles (
  id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS categories (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  nombre TEXT NOT NULL CHECK (char_length(nombre) BETWEEN 1 AND 80),
  color TEXT NOT NULL DEFAULT '#7C3AED',
  orden INT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ DEFAULT now(),
  updated_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS videos (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  category_id UUID REFERENCES categories(id) ON DELETE SET NULL,
  titulo TEXT NOT NULL CHECK (char_length(titulo) BETWEEN 1 AND 200),
  descripcion TEXT NOT NULL DEFAULT '',
  plataforma TEXT NOT NULL DEFAULT 'otra'
    CHECK (plataforma IN ('youtube', 'instagram', 'tiktok', 'otra')),
  url TEXT NOT NULL,
  thumbnail_url TEXT NOT NULL DEFAULT '',
  fecha_guardado TIMESTAMPTZ NOT NULL DEFAULT now(),
  prioridad INT NOT NULL DEFAULT 0,
  recordatorio_at TIMESTAMPTZ,
  recordatorio_repeat TEXT NOT NULL DEFAULT 'none'
    CHECK (recordatorio_repeat IN ('none', 'daily', 'weekly')),
  created_at TIMESTAMPTZ DEFAULT now(),
  updated_at TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX IF NOT EXISTS videos_user_cat_idx ON videos (user_id, category_id);

ALTER TABLE profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE categories ENABLE ROW LEVEL SECURITY;
ALTER TABLE videos ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "own rows" ON profiles;
CREATE POLICY "own rows" ON profiles FOR ALL USING (auth.uid() = id) WITH CHECK (auth.uid() = id);
DROP POLICY IF EXISTS "own rows" ON categories;
CREATE POLICY "own rows" ON categories FOR ALL USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
DROP POLICY IF EXISTS "own rows" ON videos;
CREATE POLICY "own rows" ON videos FOR ALL USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);

-- ================= Descargas + Drive (v2) =================
ALTER TABLE videos ADD COLUMN IF NOT EXISTS drive_file_id TEXT NOT NULL DEFAULT '';
ALTER TABLE videos ADD COLUMN IF NOT EXISTS drive_status TEXT NOT NULL DEFAULT 'none';
ALTER TABLE videos ADD COLUMN IF NOT EXISTS local_path TEXT NOT NULL DEFAULT '';
