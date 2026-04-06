CREATE TABLE subjects (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (organization_id, name)
);

CREATE INDEX idx_subjects_org ON subjects(organization_id);

ALTER TABLE teachers ADD COLUMN subject_id UUID REFERENCES subjects(id);

INSERT INTO subjects (id, organization_id, name, created_at, updated_at)
SELECT gen_random_uuid(), organization_id, subject, NOW(), NOW()
FROM teachers
GROUP BY organization_id, subject;

UPDATE teachers t
SET subject_id = s.id
FROM subjects s
WHERE t.organization_id = s.organization_id
  AND t.subject = s.name;

ALTER TABLE teachers ALTER COLUMN subject_id SET NOT NULL;
ALTER TABLE teachers DROP COLUMN subject;
