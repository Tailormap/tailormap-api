alter table if exists search_index
    drop column comment;
alter table if exists search_index
    add column summary jsonb default null;