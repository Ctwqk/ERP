-- Production-style PostgreSQL schema (UUID PK + business codes as VARCHAR/TEXT)
-- Tables: uom, item (SKU), bom, bom_line
-- Requires pgcrypto for gen_random_uuid()

create extension if not exists pgcrypto;

-- =====================
-- UOM
-- =====================
create table if not exists uom (
  id         uuid primary key default gen_random_uuid(),
  code       text not null unique,     -- e.g., EA, KG, BOX
  name       text not null,
  created_at timestamptz not null default now()
);

-- =====================
-- ITEM (SKU lives here)
-- =====================
create table if not exists item (
  id          uuid primary key default gen_random_uuid(),
  sku_code    text not null unique,    -- business identifier (what humans see)
  name        text not null,
  item_type   text not null check (item_type in ('RAW','WIP','FG','PACK','MRO')),
  base_uom_id uuid not null references uom(id),
  active      boolean not null default true,
  
  description text,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);

create index if not exists idx_item_type   on item(item_type);
create index if not exists idx_item_active on item(active);

-- =====================
-- BOM (Header)
-- One product can have multiple BOM versions; only one ACTIVE per (product, revision).
-- =====================
create table if not exists bom (
  id              uuid primary key default gen_random_uuid(),
  product_item_id uuid not null references item(id),
  revision        text not null default 'A',   -- A/B/C or 1/2/3
  status          text not null check (status in ('DRAFT','ACTIVE','OBSOLETE')),
  effective_from  timestamptz not null default now(),
  effective_to    timestamptz,
  note            text,
  created_at      timestamptz not null default now(),
  updated_at      timestamptz not null default now(),

  constraint chk_bom_effective_range
    check (effective_to is null or effective_to > effective_from)
);

-- At most one ACTIVE BOM per product+revision
create unique index if not exists ux_bom_active_per_product_rev
  on bom(product_item_id, revision)
  where status = 'ACTIVE';

create index if not exists idx_bom_product on bom(product_item_id);
create index if not exists idx_bom_status  on bom(status);

-- =====================
-- BOM Line (Components)
-- qty_per is "per 1 unit of product" in the given uom_id
-- =====================
create table if not exists bom_line (
  id                uuid primary key default gen_random_uuid(),
  bom_id            uuid not null references bom(id) on delete cascade,
  line_no           int  not null,              
  component_item_id uuid not null references item(id),
  qty_per           numeric(18,6) not null check (qty_per > 0),
  uom_id            uuid not null references uom(id),
  scrap_rate        numeric(9,6) not null default 0 check (scrap_rate >= 0 and scrap_rate < 1),
  note              text,
  created_at        timestamptz not null default now(),

  constraint ux_bom_line_line_no unique (bom_id, line_no),
  constraint ux_bom_line_component unique (bom_id, component_item_id)
);

create index if not exists idx_bom_line_bom       on bom_line(bom_id);
create index if not exists idx_bom_line_component on bom_line(component_item_id);

-- =====================
-- View: active BOM lines
-- =====================
create or replace view v_active_bom_lines as
select
  b.product_item_id,
  b.id as bom_id,
  b.revision,
  bl.line_no,
  bl.component_item_id,
  bl.qty_per,
  bl.uom_id,
  bl.scrap_rate
from bom b
join bom_line bl on bl.bom_id = b.id
where b.status = 'ACTIVE';




create table if not exists document (
  id uuid primary key default gen_random_uuid(),

  doc_type text not null check (doc_type in ('DRAWING','SPEC','WORK_INSTRUCTION','CERT')),
  title text not null,
  original_filename text,
  mime_type text,
  size_bytes bigint not null check (size_bytes >= 0),
  checksum_sha256 char(64) not null,

  classification text not null check (classification in ('INTERNAL','CONFIDENTIAL','SECRET')),
  revision text not null,
  status text not null check (status in ('DRAFT','APPROVED','OBSOLETE','PENDING')),

  effective_from timestamptz,
  effective_to timestamptz,

  bucket text not null,
  object_key text not null,
  version_id text,
  etag text,
  encryption text not null check (encryption in ('SSE_S3','SSE_KMS','CLIENT_SIDE')),
  kms_key_id text,

  created_by_user_id uuid,
  -- created_by_user_id uuid references app_user(id),

  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),

  constraint chk_doc_effective_time
    check (effective_to is null or effective_from is null or effective_to > effective_from)
);

create unique index if not exists ux_document_object on document(bucket, object_key, coalesce(version_id, ''));
create unique index if not exists ux_document_checksum_rev on document(checksum_sha256, revision);

create table if not exists document_link (
  link_id uuid primary key default gen_random_uuid(),
  document_id uuid not null references document(id) on delete cascade,
  link_type text not null check (link_type in ('ITEM','ORDER')),
  purpose text not null check (purpose in ('PRIMARY','REFERENCE')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (document_id, link_id)
);

create index if not exists idx_document_link_document on document_link(document_id);
create index if not exists idx_document_link_link_type on document_link(link_type);
create index if not exists idx_document_link_purpose on document_link(purpose);



create table if not exists item_document (
  item_id uuid not null references item(id) on delete cascade,
  document_id uuid not null references document(id) on delete cascade,
  relation_type text not null check (relation_type in ('PRIMARY','REFERENCE')),
  is_current boolean not null default true,
  created_at timestamptz not null default now(),
  primary key (item_id, document_id)
);

create index if not exists idx_item_document_item on item_document(item_id);
create index if not exists idx_item_document_doc on item_document(document_id);


