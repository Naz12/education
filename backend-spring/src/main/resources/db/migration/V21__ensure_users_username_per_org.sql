-- Idempotent: ensure usernames are unique per organization (fixes DBs where V20 did not run).
-- Drops legacy global UNIQUE(username) if present; adds composite UNIQUE(organization_id, username) if missing.
DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM pg_constraint c
    JOIN pg_class t ON c.conrelid = t.oid
    WHERE t.relname = 'users'
      AND c.conname = 'users_username_key'
  ) THEN
    ALTER TABLE users DROP CONSTRAINT users_username_key;
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint c
    JOIN pg_class t ON c.conrelid = t.oid
    WHERE t.relname = 'users'
      AND c.conname = 'users_org_username'
  ) THEN
    ALTER TABLE users ADD CONSTRAINT users_org_username UNIQUE (organization_id, username);
  END IF;
END $$;
