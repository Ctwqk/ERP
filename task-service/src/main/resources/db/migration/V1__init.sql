create extension if not exists pgcrypto;

create table if not exists task (
    id uuid primary key default gen_random_uuid(),
    title text not null,
    description text,
    status text not null check (status in ('OPEN','IN_PROGRESS','DONE','CANCELED')),
    priority text not null check (priority in ('LOW','MEDIUM','HIGH')),
    due_at timestamptz,
    assignee_user_id uuid,
    created_by_user_id uuid not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    completed_at timestamptz
);

create index if not exists idx_task_status on task(status);
create index if not exists idx_task_assignee on task(assignee_user_id);

create table if not exists task_link (
    id uuid primary key default gen_random_uuid(),
    task_id uuid not null references task(id) on delete cascade,
    ref_type text not null check (ref_type in ('USER','INVENTORY','ORDER','ITEM','DOCUMENT','MO')),
    ref_id text not null,
    ref_meta jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_task_link_task on task_link(task_id);
create index if not exists idx_task_link_reftype on task_link(ref_type);

create table if not exists mo_task_link (
    id uuid primary key default gen_random_uuid(),
    mo_id uuid not null,
    task_id uuid not null references task(id) on delete cascade,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_mo_task_link_mo on mo_task_link(mo_id);
create index if not exists idx_mo_task_link_task on mo_task_link(task_id);

