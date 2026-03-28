CREATE TABLE IF NOT EXISTS checklist_item_type_defaults (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    organization_id UUID NOT NULL,
    item_type VARCHAR(64) NOT NULL,
    options_json JSONB NOT NULL,
    validation_json JSONB NOT NULL,
    CONSTRAINT uq_checklist_item_type_defaults UNIQUE (organization_id, item_type)
);

