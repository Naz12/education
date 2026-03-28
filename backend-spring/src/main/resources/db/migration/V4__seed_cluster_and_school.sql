INSERT INTO clusters (id, organization_id, wereda_id, name)
VALUES (
    '51111111-1111-1111-1111-111111111111',
    '11111111-1111-1111-1111-111111111111',
    '41111111-1111-1111-1111-111111111111',
    'Default Cluster'
)
ON CONFLICT DO NOTHING;

INSERT INTO schools (
    id, organization_id, cluster_id, name, latitude, longitude, allowed_radius_in_meters
)
VALUES (
    '61111111-1111-1111-1111-111111111111',
    '11111111-1111-1111-1111-111111111111',
    '51111111-1111-1111-1111-111111111111',
    'Default Demonstration School',
    9.0300,
    38.7400,
    200
)
ON CONFLICT DO NOTHING;
