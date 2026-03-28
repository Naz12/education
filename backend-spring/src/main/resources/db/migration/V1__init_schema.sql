CREATE TABLE organizations (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE roles (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    is_system_role BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (organization_id, name)
);

CREATE TABLE users (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    username VARCHAR(120) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users(id),
    role_id UUID NOT NULL REFERENCES roles(id),
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE cities (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE subcities (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    city_id UUID NOT NULL REFERENCES cities(id),
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE weredas (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    subcity_id UUID NOT NULL REFERENCES subcities(id),
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE clusters (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    wereda_id UUID NOT NULL REFERENCES weredas(id),
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE schools (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    cluster_id UUID NOT NULL REFERENCES clusters(id),
    director_user_id UUID REFERENCES users(id),
    name VARCHAR(255) NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    allowed_radius_in_meters INTEGER NOT NULL DEFAULT 150,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE teachers (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    school_id UUID NOT NULL REFERENCES schools(id),
    user_id UUID REFERENCES users(id),
    name VARCHAR(255) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE checklists (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    title VARCHAR(255) NOT NULL,
    target_type VARCHAR(20) NOT NULL,
    display_mode VARCHAR(20) NOT NULL,
    active_version INT,
    created_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE checklist_versions (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    checklist_id UUID NOT NULL REFERENCES checklists(id),
    version_no INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (checklist_id, version_no)
);

CREATE TABLE checklist_items (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    checklist_version_id UUID NOT NULL REFERENCES checklist_versions(id),
    question TEXT NOT NULL,
    item_type VARCHAR(30) NOT NULL,
    options_json JSONB,
    validation_json JSONB,
    group_key VARCHAR(120),
    display_order INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE assignments (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    checklist_id UUID NOT NULL REFERENCES checklists(id),
    checklist_version_id UUID NOT NULL REFERENCES checklist_versions(id),
    supervisor_id UUID NOT NULL REFERENCES users(id),
    target_type VARCHAR(20) NOT NULL,
    school_id UUID REFERENCES schools(id),
    teacher_id UUID REFERENCES teachers(id),
    due_date TIMESTAMP,
    status VARCHAR(30) NOT NULL,
    created_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE reviews (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    assignment_id UUID NOT NULL REFERENCES assignments(id),
    supervisor_id UUID NOT NULL REFERENCES users(id),
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    start_latitude DOUBLE PRECISION,
    start_longitude DOUBLE PRECISION,
    end_latitude DOUBLE PRECISION,
    end_longitude DOUBLE PRECISION,
    distance_from_school DOUBLE PRECISION,
    is_within_range BOOLEAN,
    location_status VARCHAR(30),
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE review_answers (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    review_id UUID NOT NULL REFERENCES reviews(id),
    checklist_item_id UUID NOT NULL REFERENCES checklist_items(id),
    answer_json JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE file_assets (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    storage_key VARCHAR(500) NOT NULL,
    public_url VARCHAR(1000) NOT NULL,
    mime_type VARCHAR(150) NOT NULL,
    size_bytes BIGINT NOT NULL,
    checksum VARCHAR(128),
    owner_type VARCHAR(80),
    owner_id UUID,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE signatures (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    review_id UUID NOT NULL REFERENCES reviews(id),
    signer_role VARCHAR(30) NOT NULL,
    file_asset_id UUID NOT NULL REFERENCES file_assets(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE school_stamps (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    school_id UUID NOT NULL REFERENCES schools(id),
    file_asset_id UUID NOT NULL REFERENCES file_assets(id),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    actor_user_id UUID REFERENCES users(id),
    event_type VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id UUID,
    payload_json JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_org ON users(organization_id);
CREATE INDEX idx_roles_org ON roles(organization_id);
CREATE INDEX idx_cities_org ON cities(organization_id);
CREATE INDEX idx_subcities_org ON subcities(organization_id);
CREATE INDEX idx_weredas_org ON weredas(organization_id);
CREATE INDEX idx_clusters_org ON clusters(organization_id);
CREATE INDEX idx_schools_org ON schools(organization_id);
CREATE INDEX idx_teachers_org ON teachers(organization_id);
CREATE INDEX idx_checklists_org ON checklists(organization_id);
CREATE INDEX idx_checklist_versions_org ON checklist_versions(organization_id);
CREATE INDEX idx_assignments_org ON assignments(organization_id);
CREATE INDEX idx_reviews_org ON reviews(organization_id);
CREATE INDEX idx_review_answers_org ON review_answers(organization_id);
CREATE INDEX idx_audit_logs_org ON audit_logs(organization_id);
