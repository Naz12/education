ALTER TABLE checklist_items
    ADD COLUMN IF NOT EXISTS question_localized_json JSONB;

