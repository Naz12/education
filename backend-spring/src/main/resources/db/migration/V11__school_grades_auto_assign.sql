-- Structured grade selection (JSON arrays of canonical codes, e.g. ["KG1","1","2"]) and checklist auto-assignment flag.

ALTER TABLE schools
    ADD COLUMN IF NOT EXISTS supported_grade_codes TEXT;

ALTER TABLE grade_groups
    ADD COLUMN IF NOT EXISTS grade_codes TEXT;

ALTER TABLE checklists
    ADD COLUMN IF NOT EXISTS auto_assign_on_publish BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE schools SET supported_grade_codes = '[]' WHERE supported_grade_codes IS NULL;
UPDATE grade_groups SET grade_codes = '[]' WHERE grade_codes IS NULL;
