create table upload
(
    id            uuid not null,
    category      varchar(255),
    content       bytea,
    filename      varchar(255),
    hi_dpi_image  boolean,
    image_height  integer,
    image_width   integer,
    last_modified timestamp(6) with time zone,
    mime_type     varchar(255),
    primary key (id)
);