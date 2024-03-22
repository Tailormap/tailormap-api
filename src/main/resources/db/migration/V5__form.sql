
    create table form (
        id bigserial not null,
        feature_source_id bigint,
        feature_type_name varchar(255),
        fields jsonb not null,
        name varchar(255) not null,
        options jsonb not null,
        version bigint,
        primary key (id)
    );
