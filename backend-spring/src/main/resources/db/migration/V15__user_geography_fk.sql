ALTER TABLE users ADD COLUMN city_id UUID REFERENCES cities(id);
ALTER TABLE users ADD COLUMN subcity_id UUID REFERENCES subcities(id);
ALTER TABLE users ADD COLUMN wereda_id UUID REFERENCES weredas(id);

UPDATE users u
SET
    city_id = c.id,
    subcity_id = s.id,
    wereda_id = w.id
FROM weredas w
JOIN subcities s ON s.id = w.subcity_id AND s.organization_id = w.organization_id
JOIN cities c ON c.id = s.city_id AND c.organization_id = s.organization_id
WHERE u.organization_id = w.organization_id
  AND u.city IS NOT NULL AND btrim(u.city) <> ''
  AND u.sub_city IS NOT NULL AND btrim(u.sub_city) <> ''
  AND u.wereda IS NOT NULL AND btrim(u.wereda) <> ''
  AND lower(btrim(u.city)) = lower(btrim(c.name))
  AND lower(btrim(u.sub_city)) = lower(btrim(s.name))
  AND lower(btrim(u.wereda)) = lower(btrim(w.name));

CREATE INDEX idx_users_wereda ON users(wereda_id);
