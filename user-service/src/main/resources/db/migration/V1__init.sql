-- User / auth service schema.

create table app_user (
    id            uuid primary key,
    name          text not null,
    email         text not null,
    password_hash varchar(255) not null,
    active        boolean not null default true,
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now(),
    constraint ux_app_user_email unique (email)
);

create table app_roles (
    id         uuid primary key,
    name       varchar(32) not null unique
        check (name in ('ADMIN', 'HR', 'SALE', 'FINANCE', 'MANAGER', 'EMPLOYEE', 'USER', 'GUEST')),
    active     boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table app_user_roles (
    user_id    uuid not null references app_user (id) on delete cascade,
    role_id    uuid not null references app_roles (id) on delete cascade,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    primary key (user_id, role_id)
);

create index idx_app_user_roles_user on app_user_roles (user_id);
create index idx_app_user_roles_role on app_user_roles (role_id);
