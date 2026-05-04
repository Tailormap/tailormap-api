    alter table if exists oidcconfiguration
       add column client_secret_expiry date;

    alter table history.oidcconfiguration_revisions
        add column client_secret_expiry date;
