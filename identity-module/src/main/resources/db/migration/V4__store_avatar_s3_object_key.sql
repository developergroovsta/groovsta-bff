ALTER TABLE identity_profiles
    RENAME COLUMN avatar_url TO avatar_object_key;

ALTER TABLE identity_profiles
    ALTER COLUMN avatar_object_key TYPE VARCHAR(1024);
