-- Task service schema.

create table task (
    id                         uuid primary key,
    title                      text not null,
    description                text,
    status                     text not null check (status in ('OPEN', 'IN_PROGRESS', 'DONE', 'CANCELED')),
    priority                   text not null check (priority in ('LOW', 'MEDIUM', 'HIGH')),
    due_at                     timestamptz,
    prerequisite_item_id       uuid,
    prerequisite_item_quantity numeric(18,6) check (prerequisite_item_quantity > 0),
    assignee_user_id           uuid,
    created_by_user_id         uuid not null,
    created_at                 timestamptz not null default now(),
    updated_at                 timestamptz not null default now(),
    completed_at               timestamptz,
    constraint chk_task_prerequisite_pair
        check ((prerequisite_item_id is null) = (prerequisite_item_quantity is null))
);

create index idx_task_status on task (status);
create index idx_task_assignee on task (assignee_user_id);

create table task_link (
    id         uuid primary key,
    task_id    uuid not null references task (id) on delete cascade,
    ref_type   text not null check (ref_type in ('USER', 'INVENTORY', 'ORDER', 'ITEM', 'DOCUMENT', 'MO')),
    ref_id     text not null,
    ref_meta   jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index idx_task_link_task on task_link (task_id);
create index idx_task_link_reftype on task_link (ref_type);

create table mo_task_link (
    id         uuid primary key,
    mo_id      uuid not null,
    task_id    uuid not null references task (id) on delete cascade,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index idx_mo_task_link_mo on mo_task_link (mo_id);
create index idx_mo_task_link_task on mo_task_link (task_id);
