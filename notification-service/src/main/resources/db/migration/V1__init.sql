create extension if not exists pgcrypto;

create table if not exists notification (
    id uuid primary key default gen_random_uuid(),
    title text not null,
    content text,
    type text not null,
    status text not null check (status in ('UNREAD','READ')),
    recipient_user_id uuid not null,
    created_by_user_id uuid not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    read_at timestamptz
);

create index if not exists idx_notification_recipient on notification(recipient_user_id);
create index if not exists idx_notification_status on notification(status);

create table if not exists notification_link (
    id uuid primary key default gen_random_uuid(),
    notification_id uuid not null references notification(id) on delete cascade,
    link_type text not null check (link_type in ('USER','TASK','DOCUMENT','ORDER')),
    ref_id text not null,
    type text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_notification_link_notification on notification_link(notification_id);
create index if not exists idx_notification_link_type on notification_link(link_type);

