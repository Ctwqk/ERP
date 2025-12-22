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


-- =====================
-- app_user
-- =====================
create table if not exists app_user (
    id uuid primary key default gen_random_uuid(),
    name text not null,
    email text not null unique,
    password_hash varchar(255) not null,
    role text not null check (role in ('ADMIN','app_user')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
)


-- Revised DDL with necessary fixes + minimal structural improvements
-- Key changes:
-- 1) Fix syntax errors: trailing commas, missing semicolons, CHECK syntax.
-- 2) Avoid reserved keyword "order" -> use "erp_order".
-- 3) Create tables in dependency order (app_user assumed to exist).
-- 4) Fix wrong index column names (assignee_app_user_id, status).
-- 5) Remove duplicate/ambiguous columns on manufacture_order (order_code vs code).
-- 6) Make manufacture_order.order_line_id nullable (MTS/internal MO allowed).
-- 7) Add core MO fields: product_item_id, bom_id, qty_planned (MO owns production truth).
-- 8) Task should not duplicate MO quantities unless you mean "operation-level output".
--    Here I keep qty_* on task but also enforce consistency, and make them default 0.
-- 9) Add basic indexes and checks.

create extension if not exists pgcrypto;

-- =====================
-- ORDER (rename from "order" to avoid reserved keyword)
-- =====================
create table if not exists erp_order (
  id               uuid primary key default gen_random_uuid(),
  order_code       text not null unique,
  order_type       text not null check (order_type in ('PURCHASE','SALE')),
  order_date       timestamptz not null default now(),
  order_status     text not null check (order_status in ('PENDING','CONFIRMED','SHIPPED','DELIVERED','CANCELLED')),
  order_note       text,
  assignee_user_id uuid not null references app_user(id),
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now()
);

create index if not exists idx_order_assignee on erp_order(assignee_user_id);
create index if not exists idx_order_status on erp_order(order_status);
create index if not exists idx_order_date on erp_order(order_date);

-- =====================
-- ORDER LINE
-- =====================
create table if not exists order_line (
  id              uuid primary key default gen_random_uuid(),
  order_id        uuid not null references erp_order(id) on delete cascade,
  product_item_id uuid not null references item(id),
  qty             numeric(18,6) not null check (qty > 0),
  uom_id          uuid not null references uom(id),
  note            text,
  created_at      timestamptz not null default now(),
  updated_at      timestamptz not null default now()
);

create index if not exists idx_order_line_order on order_line(order_id);
create index if not exists idx_order_line_product_item on order_line(product_item_id);

-- =====================
-- MANUFACTURE ORDER (MO)
-- =====================
create table if not exists manufacture_order (
  id                     uuid primary key default gen_random_uuid(),
  manufacture_order_code text not null unique,

  -- Demand link (nullable to allow MTS / internal MO)
  order_line_id          uuid references order_line(id),

  -- What to produce (single SKU)
  product_item_id        uuid not null references item(id),

  -- Freeze BOM selection (nullable until released)
  bom_id                 uuid references bom(id),

  -- Quantity belongs to MO (truth)
  qty_planned            numeric(18,6) not null check (qty_planned > 0),
  qty_completed          numeric(18,6) not null default 0 check (qty_completed >= 0),
  qty_scrapped           numeric(18,6) not null default 0 check (qty_scrapped >= 0),

  status                 text not null check (status in ('DRAFT','RELEASED','IN_PROGRESS','HOLD','CLOSED','CANCELLED')),

  planned_start          timestamptz,
  planned_end            timestamptz,
  actual_start           timestamptz,
  actual_end             timestamptz,

  note                   text,
  created_at             timestamptz not null default now(),
  updated_at             timestamptz not null default now(),

  constraint chk_mo_planned_time check (
    planned_end is null or planned_start is null or planned_end > planned_start
  ),
  constraint chk_mo_actual_time check (
    actual_end is null or actual_start is null or actual_end > actual_start
  ),
  constraint chk_mo_qty_consistency check (
    qty_completed + qty_scrapped <= qty_planned
  )
);

create index if not exists idx_mo_order_line on manufacture_order(order_line_id);
create index if not exists idx_mo_product_item on manufacture_order(product_item_id);
create index if not exists idx_mo_status on manufacture_order(status);
create index if not exists idx_mo_planned_start on manufacture_order(planned_start);
create index if not exists idx_mo_planned_end on manufacture_order(planned_end);
create index if not exists idx_mo_actual_start on manufacture_order(actual_start);
create index if not exists idx_mo_actual_end on manufacture_order(actual_end);

-- =====================
-- TASK (operation / assignment under an MO)
-- NOTE: If tasks are "operations", task-level qty_* should usually be removed
-- and only tracked on MO + inventory_txn. Keeping here because you had them,
-- but with safer defaults and a consistency check.
-- =====================
create table if not exists task (
  id                   uuid primary key default gen_random_uuid(),
  manufacture_order_id uuid not null references manufacture_order(id) on delete cascade,

  task_code            text not null unique,

  task_type            text not null check (task_type in ('PRODUCE','REPAIR','MAINTENANCE')),
  task_status          text not null check (task_status in ('PENDING','CONFIRMED','IN_PROGRESS','DONE','CANCELLED')),
  task_note            text,

  -- Usually you only need start/end; keeping task_date as "planned date"
  task_date            timestamptz not null,

  -- If you keep task-level quantities, give defaults and enforce consistency
  qty_planned          numeric(18,6) not null check (qty_planned > 0),
  qty_produced         numeric(18,6) not null default 0 check (qty_produced >= 0),
  qty_scrap            numeric(18,6) not null default 0 check (qty_scrap >= 0),
  qty_scrap_rate       numeric(9,6)  not null default 0 check (qty_scrap_rate >= 0 and qty_scrap_rate < 1),

  assignee_user_id     uuid not null references app_user(id),

  start_time           timestamptz not null,
  end_time             timestamptz,

  created_at           timestamptz not null default now(),
  updated_at           timestamptz not null default now(),

  constraint chk_task_time check (end_time is null or end_time > start_time),
  constraint chk_task_qty_consistency check (qty_produced + qty_scrap <= qty_planned)
);

create index if not exists idx_task_assignee on task(assignee_user_id);
create index if not exists idx_task_status on task(task_status);
create index if not exists idx_task_date on task(task_date);
create index if not exists idx_task_start_time on task(start_time);
create index if not exists idx_task_end_time on task(end_time);
create index if not exists idx_task_mo on task(manufacture_order_id);



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
  status text not null check (status in ('DRAFT','APPROVED','OBSOLETE')),

  effective_from timestamptz,
  effective_to timestamptz,

  bucket text not null,
  object_key text not null,
  version_id text,
  etag text,
  encryption text not null check (encryption in ('SSE_S3','SSE_KMS','CLIENT_SIDE')),
  kms_key_id text,

  created_by_user_id uuid references app_user(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),

  constraint chk_doc_effective_time
    check (effective_to is null or effective_from is null or effective_to > effective_from)
);

create unique index if not exists ux_document_object on document(bucket, object_key, coalesce(version_id, ''));
create unique index if not exists ux_document_checksum_rev on document(checksum_sha256, revision);


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
