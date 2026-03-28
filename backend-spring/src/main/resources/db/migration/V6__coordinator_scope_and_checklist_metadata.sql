ALTER TABLE users
    ADD COLUMN IF NOT EXISTS coordinator_user_id UUID REFERENCES users(id);

ALTER TABLE schools
    ADD COLUMN IF NOT EXISTS coordinator_user_id UUID REFERENCES users(id);

ALTER TABLE checklists
    ADD COLUMN IF NOT EXISTS purpose VARCHAR(40),
    ADD COLUMN IF NOT EXISTS grade_scope VARCHAR(120),
    ADD COLUMN IF NOT EXISTS coordinator_user_id UUID REFERENCES users(id);
