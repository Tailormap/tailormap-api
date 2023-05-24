
    create table application (
       id int8 generated by default as identity,
       version int8,
       name varchar(255) not null,
       title varchar(255),
       admin_comments text,
       preview_text text,
       crs varchar(255) not null,
       content_root jsonb not null,
       settings jsonb not null,
       components jsonb not null,
       styling jsonb not null,
       authorization_rules jsonb not null,
       initial_maxx float8,
       initial_maxy float8,
       initial_minx float8,
       initial_miny float8,
       max_maxx float8,
       max_maxy float8,
       max_minx float8,
       max_miny float8,

       primary key (id)
    );

    create table catalog (
       id varchar(255) not null,
       version int8,
       nodes jsonb not null,
       primary key (id)
    );

    create table configuration (
       key varchar(255) not null,
       version int8,
       value text,
       json_value jsonb,
       primary key (key)
    );

    create table feature_source (
       id int8 generated by default as identity,
       version int8,
       protocol varchar(255) not null,
       title varchar(255),
       url varchar(2048),
       jdbc_connection jsonb,
       authentication jsonb,
       notes text,
       service_capabilities jsonb,
       linked_service varchar(255),
       primary key (id)
    );

    create table feature_source_feature_types (
       feature_source int8 not null,
       list_index int4 not null,
       feature_type int8 not null,
       primary key (feature_source, list_index)
    );

    create table feature_type (
       id int8 generated by default as identity,
       version int8,
       feature_source int8 not null,
       name varchar(255) not null,
       title varchar(255),
       comment text,
       info jsonb not null,
       owner varchar(255),
       writeable boolean not null,
       primary_key_attribute varchar(255),
       default_geometry_attribute varchar(255),
       attributes jsonb not null,
       settings jsonb not null,
       primary key (id)
    );

    create table geo_service (
       id varchar(255) not null,
       version int8,
       protocol varchar(255) not null,
       url varchar(2048) not null,
       title varchar(2048) not null,
       authentication jsonb,
       notes text,
       published boolean not null,
       advertised_url varchar(2048),
       layers jsonb not null,
       settings jsonb not null,
       service_capabilities jsonb,
       authorization_rules jsonb not null,
       capabilities_content_type varchar(255),
       capabilities_fetched timestamp,
       capabilities bytea,
       primary key (id)
    );

    create table groups (
       name varchar(255) not null,
       version int8,
       system_group boolean not null,
       description varchar(255),
       notes text,
       primary key (name)
    );

    create table user_groups (
       username varchar(255) not null,
       group_name varchar(255) not null,
       primary key (username, group_name)
    );

    create table users (
       username varchar(255) not null,
       version int8,
       name varchar(255),
       email varchar(255),
       enabled boolean not null,
       valid_until timestamp with time zone,
       password varchar(68) not null,
       notes text,
       additional_properties jsonb,
       primary key (username)
    );

    alter table if exists feature_source_feature_types 
       add constraint UK_i3l3i501cei59hl8buqifvrqr unique (feature_type);

    alter table if exists feature_source 
       add constraint FKg90hruoavk77lv4xq54cwtv3 
       foreign key (linked_service) 
       references geo_service;

    alter table if exists feature_source_feature_types 
       add constraint FKialv7u190qacshwe0th4ss03j 
       foreign key (feature_type) 
       references feature_type;

    alter table if exists feature_source_feature_types 
       add constraint FKn1cxq8bek5m0g4f8bgsvqtnvm 
       foreign key (feature_source) 
       references feature_source;

    alter table if exists feature_type 
       add constraint FK4kmha9ln2r1aniq9ydyg8n3fn 
       foreign key (feature_source) 
       references feature_source;

    alter table if exists user_groups 
       add constraint FKke00ajbb3ond1m41ae4cmn4gc 
       foreign key (group_name) 
       references groups;

    alter table if exists user_groups 
       add constraint FKey2vc3tyvcfbm84gn8k3p80te 
       foreign key (username) 
       references users;
insert into groups(name, system_group, description) values ('admin', true, 'Administrators with full access');
insert into groups(name, system_group, description) values ('actuator', true, 'Users authorized for Spring Boot Actuator (monitoring and management)');