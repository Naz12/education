-- Grades this teacher is responsible for (JSON array of canonical codes, same shape as schools.supported_grade_codes).
ALTER TABLE teachers ADD COLUMN IF NOT EXISTS responsible_grade_codes TEXT NOT NULL DEFAULT '[]';

-- Best-effort backfill from the school's configured grades when present.
UPDATE teachers t
SET responsible_grade_codes = s.supported_grade_codes
FROM schools s
WHERE t.school_id = s.id
  AND s.supported_grade_codes IS NOT NULL
  AND trim(s.supported_grade_codes) <> ''
  AND trim(s.supported_grade_codes) <> '[]';
