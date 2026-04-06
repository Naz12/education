-- Grades a supervisor is allowed to supervise (JSON array of canonical codes). Null/empty JSON treated as "all grades" for legacy rows.
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS supervised_grade_codes TEXT;
