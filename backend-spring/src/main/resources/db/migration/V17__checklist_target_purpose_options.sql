-- Tenant-scoped custom labels for checklist targets (with routing kind) and purposes.

CREATE TABLE checklist_target_options (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    name VARCHAR(255) NOT NULL,
    routing_kind VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (organization_id, name)
);

CREATE TABLE checklist_purpose_options (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (organization_id, name)
);

CREATE INDEX idx_checklist_target_options_org ON checklist_target_options(organization_id);
CREATE INDEX idx_checklist_purpose_options_org ON checklist_purpose_options(organization_id);

INSERT INTO checklist_target_options (id, organization_id, name, routing_kind)
SELECT gen_random_uuid(), o.id, v.name, v.rk
FROM organizations o
CROSS JOIN (VALUES
  ('School', 'SCHOOL'),
  ('Teacher', 'TEACHER'),
  ('Director', 'DIRECTOR'),
  ('School staff', 'SCHOOL_STAFF')
) AS v(name, rk);

INSERT INTO checklist_purpose_options (id, organization_id, name)
SELECT gen_random_uuid(), o.id, v.name
FROM organizations o
CROSS JOIN (VALUES
  ('Clinical'),
  ('Administrative')
) AS v(name);

ALTER TABLE checklists
    ADD COLUMN IF NOT EXISTS target_option_id UUID REFERENCES checklist_target_options(id),
    ADD COLUMN IF NOT EXISTS purpose_option_id UUID REFERENCES checklist_purpose_options(id);

UPDATE checklists c
SET target_option_id = t.id
FROM checklist_target_options t
WHERE t.organization_id = c.organization_id
  AND t.routing_kind = c.target_type;

UPDATE checklists c
SET purpose_option_id = p.id
FROM checklist_purpose_options p
WHERE p.organization_id = c.organization_id
  AND (
    (c.purpose = 'CLINICAL_SUPERVISION' AND p.name = 'Clinical')
    OR (c.purpose = 'ADMINISTRATIVE_SUPERVISION' AND p.name = 'Administrative')
  );

UPDATE checklists c
SET purpose_option_id = (
  SELECT p.id FROM checklist_purpose_options p
  WHERE p.organization_id = c.organization_id AND p.name = 'Clinical'
  LIMIT 1
)
WHERE c.purpose_option_id IS NULL;

ALTER TABLE checklists ALTER COLUMN target_option_id SET NOT NULL;
ALTER TABLE checklists ALTER COLUMN purpose_option_id SET NOT NULL;

ALTER TABLE checklists DROP COLUMN IF EXISTS target_type;
ALTER TABLE checklists DROP COLUMN IF EXISTS purpose;

CREATE INDEX idx_checklists_target_option ON checklists(target_option_id);
CREATE INDEX idx_checklists_purpose_option ON checklists(purpose_option_id);
