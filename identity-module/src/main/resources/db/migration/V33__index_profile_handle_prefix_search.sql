CREATE INDEX idx_identity_profiles_handle_prefix
    ON identity_profiles (handle varchar_pattern_ops);
