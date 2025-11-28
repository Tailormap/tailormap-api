alter table page
    add column authorization_rules jsonb not null default '[{"decisions": {"read": "allow"}, "groupName": "anonymous"}]';

alter table history.page_revisions
    add column authorization_rules jsonb not null default '[{"decisions": {"read": "allow"}, "groupName": "anonymous"}]';