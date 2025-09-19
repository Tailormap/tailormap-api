create table temporary_token
(
    id             uuid                        not null,
    username       varchar(255)                not null,
    expiration_time timestamp(6) with time zone not null,
    primary key (id)
)