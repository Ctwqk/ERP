create table if not exists mo (
    id uuid primary key default gen_random_uuid(),
    mo_code text not null unique,
    status text not null check (status in ('NEW','RELEASED','CLOSED')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_mo_status on mo(status);

