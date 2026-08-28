    alter table if exists groups
       add column label varchar(255);

    alter table if exists history.groups_revisions
        add column label varchar(255);

