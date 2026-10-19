-- Inventory service schema: one stock row per item plus an append-only movement ledger.

create table inventory_stock (
    id         uuid primary key,
    item_id    uuid not null,
    quantity   numeric(18,6) not null check (quantity >= 0),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ux_inventory_stock_item unique (item_id)
);

create table inventory_transaction (
    id               uuid primary key,
    stock_id         uuid not null references inventory_stock(id),
    user_id          uuid not null,
    transaction_type text not null check (transaction_type in ('IN', 'OUT')),
    quantity         numeric(18,6) not null check (quantity > 0),
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now()
);

create index idx_inventory_transaction_stock on inventory_transaction (stock_id);
create index idx_inventory_transaction_type on inventory_transaction (transaction_type);
create index idx_inventory_transaction_user on inventory_transaction (user_id);
