-- User-requested cleanup: remove all existing grade groups.
-- Keep historical checklist text scope, but detach grade group foreign keys first.

UPDATE checklists
SET grade_group_id = NULL
WHERE grade_group_id IS NOT NULL;

DELETE FROM grade_groups;
