CREATE TABLE grade_groups (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    coordinator_user_id UUID REFERENCES users(id),
    display_name VARCHAR(255) NOT NULL,
    grades_description VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_grade_groups_org ON grade_groups(organization_id);
CREATE INDEX idx_grade_groups_org_coord ON grade_groups(organization_id, coordinator_user_id);

ALTER TABLE checklists
    ADD COLUMN IF NOT EXISTS grade_group_id UUID REFERENCES grade_groups(id);
