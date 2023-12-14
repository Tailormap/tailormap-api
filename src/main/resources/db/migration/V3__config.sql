
    alter table if exists configuration 
       add column available_for_viewer boolean not null default false;
