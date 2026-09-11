-- Shared Postgres bootstrap for CIL IntelliReport.
-- Extensions are best-effort: pgcrypto/pgvector ship with the compose image,
-- PostGIS is picked up automatically once the image providing it is used
-- (nothing in Phase 1 needs geo types yet).
DO $$ BEGIN CREATE EXTENSION "pgcrypto"; EXCEPTION WHEN OTHERS THEN NULL; END $$;
DO $$ BEGIN CREATE EXTENSION "vector"; EXCEPTION WHEN OTHERS THEN NULL; END $$;
DO $$ BEGIN CREATE EXTENSION "postgis"; EXCEPTION WHEN OTHERS THEN NULL; END $$;
