ALTER TABLE checklists
    ADD COLUMN IF NOT EXISTS auto_assign_due_at TIMESTAMP;
