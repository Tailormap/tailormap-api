-- Existing indexes require rebuilding before geographic distance search.
ALTER TABLE search_index
    ADD COLUMN spatial_index_version integer NOT NULL DEFAULT 1;

ALTER TABLE search_index
    ADD CONSTRAINT search_index_spatial_version_check
    CHECK (spatial_index_version IN (1, 2));
