DROP INDEX IF EXISTS idx_identity_profiles_handle_prefix;

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_identity_profiles_handle_trigram
    ON identity_profiles USING gin (handle gin_trgm_ops);
