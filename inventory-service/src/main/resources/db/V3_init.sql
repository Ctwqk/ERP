create extension if not exists pgcrypto;



create table if not exists inventory_stock (
    id uuid primary key default gen_random_uuid(),
    item_id uuid not null,
    quantity numeric(18,6) not null check (quantity >= 0),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
)

create index if not exists idx_inventory_stock_item on inventory_stock(item_id);

create table if not exists inventory_transaction (
    id uuid primary key default gen_random_uuid(),
    stock_id uuid not null,
    user_id uuid not null,
    transaction_type text not null check (transaction_type in ('IN','OUT')),
    quantity numeric(18,6) not null check (quantity > 0),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
)

create index if not exists idx_inventory_transaction_stock on inventory_transaction(stock_id);
create index if not exists idx_inventory_transaction_type on inventory_transaction(transaction_type);
create index if not exists idx_inventory_transaction_user on inventory_transaction(user_id);