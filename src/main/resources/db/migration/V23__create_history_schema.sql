create schema if not exists history authorization tailormap;
-- Note this schema is for the default strategy and does not have the org.hibernate.envers.global_with_modified_flag set
-- spring.jpa.properties.org.hibernate.envers.audit_strategy=org.hibernate.envers.strategy.DefaultAuditStrategy
create table history.revisions
(
    id          integer not null,
    timestamp   bigint  not null,
    modified_by varchar(255),
    primary key (id)
);


create table history.modified_entities
(
    revision_number      integer not null
        constraint revision_number_fk references history.revisions (id),
    modified_entity_name varchar(255)
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

create table history.catalog_revisions
(
    id              varchar(255) not null,
    revision_number integer      not null,
    revision_type   smallint,
    nodes           jsonb,
    primary key (id, revision_number)
);

create table history.feature_source_feature_types_revisions
(
    revision_number integer not null,
    feature_source  bigint  not null,
    feature_type    bigint  not null,
    revision_type   smallint,
    primary key (feature_source, feature_type, revision_number)
);

create table history.feature_source_revisions
(
    id                   bigint  not null,
    revision_number      integer not null,
    revision_type        smallint,
    authentication       jsonb,
    jdbc_connection      jsonb,
    notes                text,
    protocol             varchar(255) check (protocol in ('WFS', 'JDBC')),
    service_capabilities jsonb,
    title                varchar(255),
    url                  varchar(2048),
    linked_service       varchar(255),
    primary key (id, revision_number)
);

create table history.feature_type_revisions
(
    id                         bigint  not null,
    revision_number            integer not null,
    revision_type              smallint,
    attributes                 jsonb,
    comment                    text,
    default_geometry_attribute varchar(255),
    info                       jsonb,
    name                       varchar(255),
    owner                      varchar(255),
    primary_key_attribute      varchar(255),
    settings                   jsonb,
    title                      varchar(255),
    writeable                  boolean,
    feature_source             bigint,
    primary key (id, revision_number)
);

create table history.geo_service_revisions
(
    id                        varchar(255) not null,
    revision_number           integer      not null,
    revision_type             smallint,
    advertised_url            varchar(2048),
    authentication            jsonb,
    authorization_rules       jsonb,
    capabilities              bytea,
    capabilities_content_type varchar(255),
    capabilities_fetched      timestamp(6) with time zone,
    layers                    jsonb,
    notes                     text,
    protocol                  varchar(255) check (protocol in ('WMS', 'WMTS', 'XYZ', 'TILES3D', 'QUANTIZEDMESH', 'LEGEND')),
    published                 boolean,
    service_capabilities      jsonb,
    settings                  jsonb,
    title                     varchar(2048),
    url                       varchar(2048),
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

create table history.oidcconfiguration_revisions
(
    id                  bigint  not null,
    revision_number     integer not null,
    revision_type       smallint,
    client_id           varchar(255),
    client_secret       varchar(255),
    image               uuid,
    issuer_url          varchar(255),
    name                varchar(255),
    status              varchar(255),
    user_name_attribute varchar(255),
    primary key (id, revision_number)
);

create table history.page_revisions
(
    id              bigint  not null,
    revision_number integer not null,
    revision_type   smallint,
    class_name      varchar(255),
    content         text,
    name            varchar(255),
    tiles           jsonb,
    title           varchar(255),
    type            varchar(255),
    primary key (id, revision_number)
);

create table history.upload_revisions
(
    id              uuid    not null,
    revision_number integer not null,
    revision_type   smallint,
    category        varchar(255),
    content         bytea,
    filename        varchar(255),
    hash            varchar(255),
    hi_dpi_image    boolean,
    image_height    integer,
    image_width     integer,
    last_modified   timestamp(6) with time zone,
    mime_type       varchar(255),
    primary key (id, revision_number)
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

create sequence revisions_seq start with 1 increment by 50;

alter table if exists history.application_revisions
    add constraint FKnj2d8iq4f7vm6ldph6gqt71v5
        foreign key (revision_number)
            references history.revisions;

alter table if exists history.catalog_revisions
    add constraint FKfi8x9y1ym83jhck384qw6j1be
        foreign key (revision_number)
            references history.revisions;

alter table if exists history.feature_source_feature_types_revisions
    add constraint FKll4i5ltrqcah2t1v8m2cj6r67
        foreign key (revision_number)
            references history.revisions;

alter table if exists history.feature_source_revisions
    add constraint FKmk5v19hcaax9rv2875ey5kwpy
        foreign key (revision_number)
            references history.revisions;

alter table if exists history.feature_type_revisions
    add constraint FKjd7k5etnvh65i3kqc2w34cm59
        foreign key (revision_number)
            references history.revisions;

alter table if exists history.geo_service_revisions
    add constraint FKebqyj8r2t43ynos0d001mulpn
        foreign key (revision_number)
            references history.revisions;

alter table if exists history.groups_revisions
    add constraint FKhvcrhu1i3s1a84csihschoj48
        foreign key (revision_number)
            references history.revisions;


alter table if exists history.oidcconfiguration_revisions
    add constraint FKrauddjlbr0u8y0g44llrdhll1
        foreign key (revision_number)
            references history.revisions;

alter table if exists history.page_revisions
    add constraint FK4ucpv5777n01hrm1xseusnsa9
        foreign key (revision_number)
            references history.revisions;

alter table if exists history.upload_revisions
    add constraint FKlc63x3yhciuyksqxmrf402jqd
        foreign key (revision_number)
            references history.revisions;

alter table if exists history.user_groups_revisions
    add constraint FK2gsuhxst81s4vcaoikqa223qi
        foreign key (revision_number)
            references history.revisions;

alter table if exists history.users_revisions
    add constraint FK4ik14momq4t7vy8wvkm2253h0
        foreign key (revision_number)
            references history.revisions;


