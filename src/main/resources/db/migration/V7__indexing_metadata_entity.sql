create table search_index
(
    id                         bigserial                    not null primary key,
    feature_type_id            bigint,
    last_indexed               timestamp(6) with time zone,
    status                     varchar(8) default 'INITIAL' not null check (status in ('INITIAL', 'INDEXING', 'INDEXED', 'ERROR')),
    comment                    text,
    search_fields_used         jsonb,
    search_display_fields_used jsonb
);