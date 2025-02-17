CREATE SCHEMA IF NOT EXISTS data AUTHORIZATION tailormap;

-- these extensions are installed and initialized in the docker image by default
DROP EXTENSION IF EXISTS postgis_topology;
DROP EXTENSION IF EXISTS postgis_tiger_geocoder;
DROP EXTENSION IF EXISTS fuzzystrmatch;

-- cleanup the default schemas
DROP SCHEMA IF EXISTS tiger_data CASCADE;
DROP SCHEMA IF EXISTS tiger CASCADE;
DROP SCHEMA IF EXISTS topology CASCADE;

-- (re)create/update the postgis extension, as Flyway drops it on clean
CREATE EXTENSION IF NOT EXISTS postgis;
ALTER EXTENSION postgis UPDATE;


CREATE TABLE IF NOT EXISTS data.drawing
(
    id          UUID PRIMARY KEY                  DEFAULT gen_random_uuid(),
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

CREATE INDEX IF NOT EXISTS drawing_created_by ON data.drawing (created_by);


CREATE TABLE IF NOT EXISTS data.drawing_geometry
(
    drawing_id UUID     NOT NULL
        CONSTRAINT drawing_id_fk REFERENCES drawing (id) ON DELETE CASCADE,
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    geometry       GEOMETRY,
    properties JSONB
);
CREATE INDEX IF NOT EXISTS drawing_drawing_id ON data.drawing_geometry (drawing_id);

-- Flyway does not (re)create geometry column when this script is executed on existing schema...
ALTER TABLE data.drawing_geometry ADD COLUMN IF NOT EXISTS geometry GEOMETRY;
CREATE INDEX IF NOT EXISTS drawing_geometry_geometry ON data.drawing_geometry USING GIST (geometry);
