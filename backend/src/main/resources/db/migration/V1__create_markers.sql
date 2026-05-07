CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE complaints (
    id BIGSERIAL PRIMARY KEY,
    category VARCHAR(30) NOT NULL,
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

CREATE INDEX idx_complaints_location ON complaints USING GIST(location);
CREATE INDEX idx_complaints_category ON complaints(category);
CREATE INDEX idx_complaints_status ON complaints(status);

CREATE OR REPLACE FUNCTION set_complaint_location() RETURNS TRIGGER AS $$
BEGIN
    NEW.location := ST_SetSRID(ST_MakePoint(NEW.longitude, NEW.latitude), 4326)::geography;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER complaints_set_location BEFORE INSERT OR UPDATE ON complaints
    FOR EACH ROW EXECUTE FUNCTION set_complaint_location();
