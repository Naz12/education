-- Rename seeded defaults from longer labels (V17 before rename) to short labels.
-- No-op if names were already short (e.g. fresh DB after V17 update).

UPDATE checklist_target_options SET name = 'School' WHERE name = 'School (whole school)';
UPDATE checklist_target_options SET name = 'Teacher' WHERE name = 'Teacher (classroom)';
UPDATE checklist_target_options SET name = 'Director' WHERE name = 'School director';

UPDATE checklist_purpose_options SET name = 'Clinical' WHERE name = 'Clinical supervision';
UPDATE checklist_purpose_options SET name = 'Administrative' WHERE name = 'Administrative supervision';
