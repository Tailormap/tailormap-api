create schema if not exists history authorization tailormap;

create table history.admin_revisions
(
    id          integer not null,
    timestamp   bigint  not null,
    modified_by varchar(255),
    summary     text,
    primary key (id)
);

create table history.application_revisions
(
    id                  bigint  not null,
    revision_number     integer not null,
    revision_type       smallint,
    admin_comments      text,
    authorization_rules jsonb,
    components          jsonb,
    content_root        jsonb,
    crs                 varchar(255),
    initial_maxx        float(53),
    initial_maxy        float(53),
    initial_minx        float(53),
    initial_miny        float(53),
    max_maxx            float(53),
    max_maxy            float(53),
    max_minx            float(53),
    max_miny            float(53),
    name                varchar(255),
    preview_text        text,
    settings            jsonb,
    styling             jsonb,
    title               varchar(255),
    primary key (id, revision_number)
);

create table history.groups_revisions
(
    name                  varchar(255) not null,
    revision_number       integer      not null,
    revision_type         smallint,
    additional_properties jsonb,
    alias_for_group       varchar(255),
    description           varchar(255),
    notes                 text,
    system_group          boolean,
    primary key (name, revision_number)
);

create table history.user_groups_revisions
(
    revision_number integer      not null,
    username        varchar(255) not null,
    group_name      varchar(255) not null,
    revision_type   smallint,
    primary key (username, group_name, revision_number)
);

create table history.users_revisions
(
    username              varchar(255) not null,
    revision_number       integer      not null,
    revision_type         smallint,
    additional_properties jsonb,
    email                 varchar(255),
    enabled               boolean,
    name                  varchar(255),
    notes                 text,
    organisation          varchar(255),
    password              varchar(255),
    valid_until           timestamp with time zone,
    primary key (revision_number, username)
);

create sequence admin_revisions_seq start with 1 increment by 50;

alter table if exists history.application_revisions
    add constraint FKra9erth9gnfb98ry4dleg1m8q
        foreign key (revision_number)
            references history.admin_revisions;

alter table if exists history.groups_revisions
    add constraint FKmvxnr04by9ascycf29x9pmf8o
        foreign key (revision_number)
            references history.admin_revisions;

alter table if exists history.user_groups_revisions
    add constraint FKmvdev49eold7f789ci41qli0x
        foreign key (revision_number)
            references history.admin_revisions;

alter table if exists history.users_revisions
    add constraint FKimnb33nluqw9uq4qhwj53rs91
        foreign key (revision_number)
            references history.admin_revisions;
