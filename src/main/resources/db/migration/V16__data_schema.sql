-- these extensions are installed and initialized in the docker image by default
DROP EXTENSION IF EXISTS postgis_topology;
DROP EXTENSION IF EXISTS postgis_tiger_geocoder;
DROP EXTENSION IF EXISTS fuzzystrmatch;

-- drop postgis template database as well
-- DROP DATABASE IF EXISTS template_postgis;

-- cleanup the default schemas
DROP SCHEMA IF EXISTS tiger_data CASCADE;
DROP SCHEMA IF EXISTS tiger CASCADE;
DROP SCHEMA IF EXISTS topology CASCADE;

-- (re)create/update the postgis extension, as Flyway drops it on clean
CREATE EXTENSION IF NOT EXISTS postgis;
ALTER EXTENSION postgis UPDATE;

CREATE SCHEMA IF NOT EXISTS data AUTHORIZATION tailormap;

CREATE TABLE IF NOT EXISTS data.drawing
(
    id          UUID PRIMARY KEY,
    name        VARCHAR                  NOT NULL,
    description TEXT,
    domainData  JSONB,
    access      VARCHAR                  NOT NULL DEFAULT 'private'
        CONSTRAINT drawing_access CHECK (access IN ('private', 'shared', 'public')),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by  VARCHAR                  NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE,
    updated_by  VARCHAR,
    srid        INT                      NOT NULL,
    version     INTEGER                  NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS drawing_created_by_idx ON data.drawing (created_by);


CREATE TABLE IF NOT EXISTS data.drawing_geometry
(
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    drawing_id UUID     NOT NULL
        CONSTRAINT drawing_id_fk REFERENCES data.drawing (id) ON DELETE CASCADE,
    geometry   GEOMETRY NOT NULL,
    properties JSONB
);

CREATE INDEX IF NOT EXISTS drawing_drawing_id_idx ON data.drawing_geometry (drawing_id);
CREATE INDEX IF NOT EXISTS drawing_geometry_geometry_idx ON data.drawing_geometry USING GIST (geometry);