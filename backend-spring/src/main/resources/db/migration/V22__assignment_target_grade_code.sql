ALTER TABLE assignments
    ADD COLUMN IF NOT EXISTS target_grade_code VARCHAR(20);

CREATE INDEX IF NOT EXISTS idx_assignments_org_teacher_grade_status
    ON assignments(organization_id, checklist_id, school_id, teacher_id, target_grade_code, status);
