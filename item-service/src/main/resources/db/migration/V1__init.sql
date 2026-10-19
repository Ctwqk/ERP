-- Item service schema: units of measure, items, BOMs, documents, customer orders.

-- ---------------------------------------------------------------------------------------------------------------------
-- Master data
-- ---------------------------------------------------------------------------------------------------------------------
create table uom (
    id         uuid primary key,
    code       text not null,
    name       text not null,
    created_at timestamptz not null default now(),
    constraint ux_uom_code unique (code)
);

create table item (
    id          uuid primary key,
    sku_code    text not null,
    name        text not null,
    item_type   text not null check (item_type in ('RAW', 'WIP', 'FG', 'PACK', 'MRO')),
    base_uom_id uuid not null references uom (id),
    active      boolean not null default true,
    description text,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    constraint ux_item_sku_code unique (sku_code)
);

create index idx_item_type on item (item_type);
create index idx_item_active on item (active);

-- ---------------------------------------------------------------------------------------------------------------------
-- Bill of materials. A product can have several revisions; at most one ACTIVE BOM per (product, revision).
-- ---------------------------------------------------------------------------------------------------------------------
create table bom (
    id              uuid primary key,
    product_item_id uuid not null references item (id),
    revision        varchar(10) not null default 'A',
    status          text not null check (status in ('DRAFT', 'ACTIVE', 'OBSOLETE')),
    effective_from  timestamptz not null default now(),
    effective_to    timestamptz,
    note            text,
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now(),
    constraint chk_bom_effective_range check (effective_to is null or effective_to > effective_from)
);

create unique index ux_bom_active_per_product_rev on bom (product_item_id, revision) where status = 'ACTIVE';
create index idx_bom_product on bom (product_item_id);
create index idx_bom_status on bom (status);

-- qty_per is "per 1 unit of product" in uom_id; scrap_rate is the expected loss fraction (0 <= rate < 1)
create table bom_line (
    id                uuid primary key,
    bom_id            uuid not null references bom (id) on delete cascade,
    line_no           int not null,
    component_item_id uuid not null references item (id),
    qty_per           numeric(18,6) not null check (qty_per > 0),
    uom_id            uuid not null references uom (id),
    scrap_rate        numeric(9,6) not null default 0 check (scrap_rate >= 0 and scrap_rate < 1),
    note              text,
    created_at        timestamptz not null default now(),
    constraint ux_bom_line_line_no unique (bom_id, line_no),
    constraint ux_bom_line_component unique (bom_id, component_item_id)
);

create index idx_bom_line_bom on bom_line (bom_id);
create index idx_bom_line_component on bom_line (component_item_id);

-- ---------------------------------------------------------------------------------------------------------------------
-- Documents (file metadata; the bytes live in MinIO) and their links to items / orders
-- ---------------------------------------------------------------------------------------------------------------------
create table document (
    id                 uuid primary key,
    doc_type           text not null check (doc_type in ('DRAWING', 'SPEC', 'WORK_INSTRUCTION', 'CERT', 'ORDER', 'ITEM')),
    title              text not null,
    original_filename  text,
    mime_type          text,
    size_bytes         bigint not null check (size_bytes >= 0),
    checksum_sha256    varchar(64),
    classification     text not null check (classification in ('PUBLIC', 'INTERNAL', 'CONFIDENTIAL')),
    revision           text not null,
    status             text not null check (status in ('DRAFT', 'APPROVED', 'ARCHIVED')),
    effective_from     timestamptz,
    effective_to       timestamptz,
    bucket             text not null,
    object_key         text not null,
    version_id         text,
    etag               text,
    encryption         text not null check (encryption in ('NONE', 'SSE_S3', 'SSE_KMS')),
    kms_key_id         text,
    created_by_user_id uuid,
    created_at         timestamptz not null default now(),
    updated_at         timestamptz not null default now(),
    constraint chk_doc_effective_time check (effective_to is null or effective_from is null or effective_to > effective_from)
);

create unique index ux_document_object on document (bucket, object_key, coalesce(version_id, ''));
create unique index ux_document_checksum_rev on document (checksum_sha256, revision);

create table document_link (
    id          uuid primary key,
    document_id uuid not null references document (id) on delete cascade,
    link_id     uuid not null,
    link_type   text not null check (link_type in ('ITEM', 'ORDER')),
    purpose     text not null check (purpose in ('PRIMARY', 'REFERENCE')),
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    constraint ux_document_link unique (document_id, link_id, link_type)
);

create index idx_document_link_document on document_link (document_id);
create index idx_document_link_target on document_link (link_id, link_type);
create index idx_document_link_link_type on document_link (link_type);

-- ---------------------------------------------------------------------------------------------------------------------
-- Customer orders
-- ---------------------------------------------------------------------------------------------------------------------
create table orders (
    id                 uuid primary key,
    user_id            varchar(64) not null,
    status             text not null check (status in ('NEW', 'CONFIRMED', 'CANCELED', 'COMPLETED')),
    total_amount_cents bigint not null check (total_amount_cents >= 0),
    created_at         timestamptz not null default now(),
    updated_at         timestamptz not null default now()
);

create index idx_orders_user_id on orders (user_id);
create index idx_orders_status on orders (status);

create table order_item (
    id                uuid primary key,
    order_id          uuid not null references orders (id) on delete cascade,
    item_id           uuid,
    sku               varchar(64),
    quantity          int not null check (quantity > 0),
    unit_price_cents  bigint not null check (unit_price_cents >= 0),
    line_amount_cents bigint not null check (line_amount_cents >= 0)
);

create index idx_order_item_order on order_item (order_id);

-- Manufacture orders (persistence only for now; see README roadmap)
create table mo (
    id         uuid primary key,
    mo_code    text not null unique,
    status     text not null check (status in ('NEW', 'RELEASED', 'CLOSED')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index idx_mo_status on mo (status);
