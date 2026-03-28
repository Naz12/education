-- Staff member (user) targeted by SCHOOL_STAFF assignments — directors use school-only DIRECTOR target.
ALTER TABLE assignments ADD COLUMN IF NOT EXISTS staff_user_id UUID REFERENCES users(id);
