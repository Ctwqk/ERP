create extension if not exists pgcrypto;

create table if not exists app_user (
    id uuid primary key default gen_random_uuid(),
    name text not null,
    email text not null unique,
    password_hash varchar(255) not null,
    active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
)



create table is not exists app_roles {
    id uuid primary key default gen_random_uuid(),
    name text not null check (name in ('ADMIN','HR','SALE','FINANCE','MANAGER','EMPLOYEE','USER','GUEST')),
    active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
}

create table is not exists app_user_roles {
    user_id uuid not null references app_user(id) on delete cascade,
    role_id uuid not null references app_roles(id) on delete cascade,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
}

create unique index if not exists ux_app_user_roles_user_role on app_user_roles(user_id, role_id) where active = true;
create index if not exists idx_app_user_roles_user on app_user_roles(user_id);
create index if not exists idx_app_user_roles_role on app_user_roles(role_id);
create index if not exists idx_app_user_roles_active on app_user_roles(active);
create constraint chk_app_user_roles_active on app_user_roles(active = true);
