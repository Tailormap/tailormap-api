    alter table if exists oidcconfiguration
       add column disable_automatic_group_creation boolean not null default false;

    alter table if exists history.oidcconfiguration_revisions
        add column disable_automatic_group_creation boolean not null default false;