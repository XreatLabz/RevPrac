create table audit_log (
    audit_id text primary key,
    occurred_at bigint not null,
    actor text not null,
    action text not null,
    details text not null
);

create index idx_audit_log_occurred_at on audit_log (occurred_at desc, audit_id desc);
