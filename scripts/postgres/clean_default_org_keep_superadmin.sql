-- Reset default organization data: keep only superadmin, organizations, roles (system),
-- geography (cities/clusters), grade_groups (+ grade_codes), and checklist_item_type_defaults.
-- Default org id matches application seed data (DefaultUserInitializer / Flyway).

BEGIN;

-- Default organization UUID
-- 11111111-1111-1111-1111-111111111111

-- 1) Reviews / reports (deepest children first)
DELETE FROM review_answers WHERE organization_id = '11111111-1111-1111-1111-111111111111';
DELETE FROM signatures WHERE organization_id = '11111111-1111-1111-1111-111111111111';
DELETE FROM reviews WHERE organization_id = '11111111-1111-1111-1111-111111111111';

-- 2) Assignments and checklist content
DELETE FROM assignments WHERE organization_id = '11111111-1111-1111-1111-111111111111';
DELETE FROM checklist_items WHERE organization_id = '11111111-1111-1111-1111-111111111111';
DELETE FROM checklist_versions WHERE organization_id = '11111111-1111-1111-1111-111111111111';
DELETE FROM checklists WHERE organization_id = '11111111-1111-1111-1111-111111111111';

-- 3) School-linked entities
DELETE FROM school_stamps WHERE organization_id = '11111111-1111-1111-1111-111111111111';
DELETE FROM teachers WHERE organization_id = '11111111-1111-1111-1111-111111111111';
DELETE FROM schools WHERE organization_id = '11111111-1111-1111-1111-111111111111';

-- 4) File assets (signatures already removed)
DELETE FROM file_assets WHERE organization_id = '11111111-1111-1111-1111-111111111111';

-- 5) Audit trail for this org
DELETE FROM audit_logs WHERE organization_id = '11111111-1111-1111-1111-111111111111';

-- 6) Grade groups: keep rows and grade_codes; clear coordinator link
UPDATE grade_groups SET coordinator_user_id = NULL WHERE organization_id = '11111111-1111-1111-1111-111111111111';

-- 7) Users: remove everyone except superadmin
DELETE FROM user_roles
WHERE user_id IN (
    SELECT id FROM users
    WHERE organization_id = '11111111-1111-1111-1111-111111111111'
      AND username <> 'superadmin'
);

DELETE FROM users
WHERE organization_id = '11111111-1111-1111-1111-111111111111'
  AND username <> 'superadmin';

-- 8) Custom (non-system) roles, e.g. school-staff types created in the UI
DELETE FROM roles
WHERE organization_id = '11111111-1111-1111-1111-111111111111'
  AND is_system_role = FALSE;

-- 9) Tidy superadmin row
UPDATE users
SET coordinator_user_id = NULL
WHERE organization_id = '11111111-1111-1111-1111-111111111111'
  AND username = 'superadmin';

COMMIT;
