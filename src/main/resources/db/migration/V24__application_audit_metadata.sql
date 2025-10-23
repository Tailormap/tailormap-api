-- Add auditing metadata columns to selected tables
alter table if exists application
    add column created_by varchar(255);
alter table if exists application
    add column created_date timestamp with time zone;
alter table if exists application
    add column last_modified_by varchar(255);
alter table if exists application
    add column last_modified_date timestamp with time zone;

-- it seems these are not needed
-- alter table if exists history.application add column created_by varchar(255);
-- alter table if exists history.application add column created_date timestamp with time zone;
-- alter table if exists history.application add column last_modified_by varchar(255);
-- alter table if exists history.application add column last_modified_date timestamp with time zone;