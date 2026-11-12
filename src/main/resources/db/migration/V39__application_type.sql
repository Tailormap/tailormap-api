-- PostgreSQL cannot position a column, so it is appended to the table
alter table application
    add column application_type varchar(255) not null default 'application';

alter table if exists history.application_revisions
    add column application_type varchar(255);
