-- these extensions are installed and initialized in the docker image by default
DROP EXTENSION IF EXISTS postgis_topology;
DROP EXTENSION IF EXISTS postgis_tiger_geocoder;
DROP EXTENSION IF EXISTS fuzzystrmatch;

-- cleanup the default schemas
DROP SCHEMA IF EXISTS tiger_data CASCADE;
DROP SCHEMA IF EXISTS tiger CASCADE;
DROP SCHEMA IF EXISTS topology CASCADE;

-- (re)create the postgis extension
CREATE EXTENSION IF NOT EXISTS postgis;
