create table temporary_token
(
    token           uuid                        not null primary key,
    token_type      varchar(18)                 not null,
    username        varchar(255)                not null,
    expiration_time timestamp(6) with time zone not null
)