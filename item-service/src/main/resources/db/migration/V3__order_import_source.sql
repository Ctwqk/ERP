-- An attachment can be added by any authenticated document reader and is not proof of an import.
-- Previous imports cannot be reliably inferred from those links; reconcile old demo data before re-importing it.
alter table orders
    add column source_document_id uuid references document (id) on delete set null,
    add constraint ux_orders_source_document unique (source_document_id);
