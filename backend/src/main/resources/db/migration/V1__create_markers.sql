CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE complaints (
    id BIGSERIAL PRIMARY KEY,
    type VARCHAR(20) NOT NULL,
    description TEXT NOT NULL,
    photo_path VARCHAR(500) NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    location GEOGRAPHY(POINT, 4326) NOT NULL,
    address VARCHAR(500) NOT NULL,
    device_id VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'NEW',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE subbotniks (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(300) NOT NULL,
    description TEXT NOT NULL,
    photo_path VARCHAR(500),
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    location GEOGRAPHY(POINT, 4326) NOT NULL,
    address VARCHAR(500) NOT NULL,
    event_date DATE NOT NULL,
    event_time TIME NOT NULL,
    device_id VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_complaints_location ON complaints USING GIST(location);
CREATE INDEX idx_subbotniks_location ON subbotniks USING GIST(location);

CREATE OR REPLACE FUNCTION set_location() RETURNS TRIGGER AS $$
BEGIN
    NEW.location := ST_SetSRID(ST_MakePoint(NEW.longitude, NEW.latitude), 4326)::geography;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER complaints_set_location BEFORE INSERT OR UPDATE ON complaints
    FOR EACH ROW EXECUTE FUNCTION set_location();

CREATE TRIGGER subbotniks_set_location BEFORE INSERT OR UPDATE ON subbotniks
    FOR EACH ROW EXECUTE FUNCTION set_location();
