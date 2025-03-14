CREATE SCHEMA IF NOT EXISTS data AUTHORIZATION tailormap;

CREATE TABLE IF NOT EXISTS data.drawing
(
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR NOT NULL,
    description TEXT,
    domain_data JSONB,
    access      VARCHAR  NOT NULL DEFAULT 'private' CONSTRAINT drawing_access CHECK (access IN ('private', 'shared', 'public')),
    created_by  VARCHAR NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by  VARCHAR,
    updated_at  TIMESTAMP WITH TIME ZONE,
    srid        INT NOT NULL,
    version     INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS drawing_created_by ON data.drawing (created_by);


CREATE TABLE IF NOT EXISTS data.drawing_feature
(
    drawing_id UUID NOT NULL CONSTRAINT drawing_id_fk REFERENCES data.drawing (id) ON DELETE CASCADE,
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    geometry   GEOMETRY NOT NULL,
    properties JSONB
);
CREATE INDEX IF NOT EXISTS drawing_drawing_id ON data.drawing_feature (drawing_id);
CREATE INDEX IF NOT EXISTS drawing_feature_geometry ON data.drawing_feature USING GIST (geometry);
