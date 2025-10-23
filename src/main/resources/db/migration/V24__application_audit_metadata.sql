-- Add auditing metadata columns to selected tables
alter table if exists application
    add column created_by varchar(255);
alter table if exists application
    add column created_date timestamp with time zone;
alter table if exists application
    add column last_modified_by varchar(255);
alter table if exists application
    add column last_modified_date timestamp with time zone;

-- it seems we do not need to add these columns for Envers auditing
-- alter table if exists history.application add column created_by varchar(255);
-- alter table if exists history.application add column created_date timestamp with time zone;
-- alter table if exists history.application add column last_modified_by varchar(255);
-- alter table if exists history.application add column last_modified_date timestamp with time zone;

alter table if exists catalog
    add column created_by varchar(255);
alter table if exists catalog
    add column created_date timestamp with time zone;
alter table if exists catalog
    add column last_modified_by varchar(255);
alter table if exists catalog
    add column last_modified_date timestamp with time zone;

alter table if exists configuration
    add column created_by varchar(255);
alter table if exists configuration
    add column created_date timestamp with time zone;
alter table if exists configuration
    add column last_modified_by varchar(255);
alter table if exists configuration
    add column last_modified_date timestamp with time zone;

alter table if exists form
    add column created_by varchar(255);
alter table if exists form
    add column created_date timestamp with time zone;
alter table if exists form
    add column last_modified_by varchar(255);
alter table if exists form
    add column last_modified_date timestamp with time zone;

alter table if exists geo_service
    add column created_by varchar(255);
alter table if exists geo_service
    add column created_date timestamp with time zone;
alter table if exists geo_service
    add column last_modified_by varchar(255);
alter table if exists geo_service
    add column last_modified_date timestamp with time zone;

alter table if exists groups
    add column created_by varchar(255);
alter table if exists groups
    add column created_date timestamp with time zone;
alter table if exists groups
    add column last_modified_by varchar(255);
alter table if exists groups
    add column last_modified_date timestamp with time zone;

alter table if exists page
    add column created_by varchar(255);
alter table if exists page
    add column created_date timestamp with time zone;
alter table if exists page
    add column last_modified_by varchar(255);
alter table if exists page
    add column last_modified_date timestamp with time zone;

alter table if exists search_index
    add column created_by varchar(255);
alter table if exists search_index
    add column created_date timestamp with time zone;
alter table if exists search_index
    add column last_modified_by varchar(255);
alter table if exists search_index
    add column last_modified_date timestamp with time zone;

alter table if exists feature_source
    add column created_by varchar(255);
alter table if exists feature_source
    add column created_date timestamp with time zone;
alter table if exists feature_source
    add column last_modified_by varchar(255);
alter table if exists feature_source
    add column last_modified_date timestamp with time zone;

alter table if exists feature_type
    add column created_by varchar(255);
alter table if exists feature_type
    add column created_date timestamp with time zone;
alter table if exists feature_type
    add column last_modified_by varchar(255);
alter table if exists feature_type
    add column last_modified_date timestamp with time zone;

alter table if exists upload
    add column created_by varchar(255);
alter table if exists upload
    add column created_date timestamp with time zone;
alter table if exists upload
    add column last_modified_by varchar(255);
alter table if exists upload
    add column last_modified_date timestamp with time zone;

alter table if exists users
    add column created_by varchar(255);
alter table if exists users
    add column created_date timestamp with time zone;
alter table if exists users
    add column last_modified_by varchar(255);
alter table if exists users
    add column last_modified_date timestamp with time zone;

alter table if exists oidcconfiguration
    add column created_by varchar(255);
alter table if exists oidcconfiguration
    add column created_date timestamp with time zone;
alter table if exists oidcconfiguration
    add column last_modified_by varchar(255);
alter table if exists oidcconfiguration
    add column last_modified_date timestamp with time zone;

