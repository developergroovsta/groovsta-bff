CREATE TABLE content_locations (
    id UUID PRIMARY KEY,
    provider VARCHAR(30) NOT NULL,
    provider_place_id VARCHAR(255),
    name VARCHAR(200) NOT NULL,
    formatted_address VARCHAR(1000),
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    city VARCHAR(120),
    state VARCHAR(120),
    country VARCHAR(120),
    postal_code VARCHAR(30),
    metadata_json TEXT,
    last_refreshed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_content_locations_provider CHECK (provider IN ('OLA')),
    CONSTRAINT uq_content_locations_provider_place UNIQUE (provider, provider_place_id)
);

CREATE INDEX idx_content_locations_name_lower
    ON content_locations (lower(name));

CREATE INDEX idx_content_locations_city_state
    ON content_locations (lower(coalesce(city, '')), lower(coalesce(state, '')));

CREATE TABLE content_location_search_cache (
    id UUID PRIMARY KEY,
    cache_key VARCHAR(600) NOT NULL UNIQUE,
    location_ids TEXT NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_content_location_search_cache_expires
    ON content_location_search_cache (expires_at);

ALTER TABLE content_items
    ADD COLUMN location_id UUID REFERENCES content_locations(id) ON DELETE SET NULL;

ALTER TABLE content_stories
    ADD COLUMN location_id UUID REFERENCES content_locations(id) ON DELETE SET NULL;

CREATE INDEX idx_content_items_location_feed
    ON content_items(location_id, status, published_at DESC, id DESC)
    WHERE location_id IS NOT NULL;

CREATE INDEX idx_content_stories_location_feed
    ON content_stories(location_id, status, expires_at, published_at DESC, id DESC)
    WHERE location_id IS NOT NULL;
