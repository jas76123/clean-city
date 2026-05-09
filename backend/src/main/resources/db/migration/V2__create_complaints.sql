-- День 4: жалобы и фотографии. Источник правды: SPEC.md §3.4.
-- complaint_photos: до 5 фото на жалобу, S3-ключ + публичный URL хранятся раздельно
-- (URL может смениться при переезде хранилища, ключ — нет).

CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE complaints (
    id BIGSERIAL PRIMARY KEY,
    author_id BIGINT NOT NULL REFERENCES users(id),
    category VARCHAR(30) NOT NULL,
    title VARCHAR(300) NOT NULL,
    description TEXT NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    location GEOGRAPHY(POINT, 4326) NOT NULL,
    address VARCHAR(500) NOT NULL,
    district VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'NEW',
    duplicate_of_id BIGINT REFERENCES complaints(id),
    assigned_admin_id BIGINT REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMPTZ
);

CREATE INDEX idx_complaints_author ON complaints(author_id);
CREATE INDEX idx_complaints_status ON complaints(status);
CREATE INDEX idx_complaints_category ON complaints(category);
CREATE INDEX idx_complaints_district ON complaints(district);
CREATE INDEX idx_complaints_location ON complaints USING GIST(location);
CREATE INDEX idx_complaints_created_at ON complaints(created_at DESC);

-- Триггер: location пересчитывается из latitude/longitude при INSERT/UPDATE.
CREATE OR REPLACE FUNCTION set_complaint_location() RETURNS TRIGGER AS $$
BEGIN
    NEW.location := ST_SetSRID(ST_MakePoint(NEW.longitude, NEW.latitude), 4326)::geography;
    NEW.updated_at := NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER complaints_set_location BEFORE INSERT OR UPDATE ON complaints
    FOR EACH ROW EXECUTE FUNCTION set_complaint_location();

CREATE TABLE complaint_photos (
    id BIGSERIAL PRIMARY KEY,
    complaint_id BIGINT NOT NULL REFERENCES complaints(id) ON DELETE CASCADE,
    storage_key VARCHAR(500) NOT NULL,
    photo_url VARCHAR(500) NOT NULL,
    thumb_url VARCHAR(500) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_photos_complaint ON complaint_photos(complaint_id, sort_order);
