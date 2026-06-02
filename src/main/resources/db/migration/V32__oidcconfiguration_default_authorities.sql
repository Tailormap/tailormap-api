alter table oidcconfiguration
    add column default_authorities jsonb;
-- do not check if elements are strings, requires a pgplsql function as subqueries are not supported in check constraints
alter table oidcconfiguration
    add constraint default_authorities_is_json_array
        check (default_authorities is null or jsonb_typeof(default_authorities) = 'array');