alter table if exists upload
    add column description varchar(255) default null;

alter table if exists history.upload_revisions
    add column description varchar(255) default null;