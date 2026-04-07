-- Usernames are unique per organization (multi-tenant), not globally.
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_username_key;
ALTER TABLE users ADD CONSTRAINT users_org_username UNIQUE (organization_id, username);
