alter table if exists history.oidcconfiguration_revisions
    add column default_authorities jsonb;

alter table if exists history.oidcconfiguration_revisions
    add column roles_claim_filter_regex varchar;
