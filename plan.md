School Supervision MVP Implementation Plan

Scope and Constraints





Two top-level projects:





backend-spring/ for Spring Boot API



mobile-flutter/ for Flutter app



Multi-tenant SaaS from day one using strict organization_id data partitioning.



Architecture: hybrid modular (layered by module + domain-centric boundaries) so MVP is fast while preserving long-term hexagonal migration path.



First implementation milestone: database schema, Spring entities, checklist core logic.

Target Architecture

flowchart LR
  MobileFlutter -->|JWT| ApiGateway
  ApiGateway --> AuthModule
  ApiGateway --> UserRoleModule
  ApiGateway --> OrgHierarchyModule
  ApiGateway --> ChecklistModule
  ApiGateway --> AssignmentModule
  ApiGateway --> ReviewModule
  ApiGateway --> ReportModule

  AuthModule --> PostgresDB
  UserRoleModule --> PostgresDB
  OrgHierarchyModule --> PostgresDB
  ChecklistModule --> PostgresDB
  AssignmentModule --> PostgresDB
  ReviewModule --> PostgresDB
  ReportModule --> PostgresDB

  ReviewModule --> GeoValidationService
  ChecklistModule --> ChecklistRendererContract
  MobileFlutter --> ChecklistRendererContract

Project Structure to Create





backend-spring/





src/main/java/.../common (base entity, tenant context, exceptions, security)



src/main/java/.../modules/auth



src/main/java/.../modules/users



src/main/java/.../modules/organization



src/main/java/.../modules/checklists



src/main/java/.../modules/assignments



src/main/java/.../modules/reviews



src/main/java/.../modules/reports



src/main/resources/db/migration (Flyway migrations)



mobile-flutter/





lib/core (networking, auth, storage, location, error handling)



lib/features/auth



lib/features/dashboard



lib/features/assignments



lib/features/checklist_renderer



lib/features/signature



lib/features/reports



lib/features/offline_sync (prepared structure only)

Milestone 1: Database Foundation (First Build Step)





Create PostgreSQL schema and migrations for core tables:





organizations, users, roles, user_roles



cities, subcities, weredas, clusters, schools, teachers



checklists, checklist_versions, checklist_items



assignments



reviews, review_answers, signatures



audit_logs (lightweight MVP event history)



Include core constraints/indexes:





Composite indexes with organization_id on all query-heavy tables.



FK chains for hierarchy integrity (City -> SubCity -> Wereda -> Cluster -> School -> Teacher).



Unique constraints for role names per org and version numbers per checklist.



Add enum strategy:





DB-safe text enums in app layer for flexibility (no hard DB enum lock-in).

Milestone 2: Spring Boot Core Entities and Security





Implement shared base classes:





BaseEntity (id, created_at, updated_at)



TenantScopedEntity (organization_id)



Implement JPA entities for milestone tables with relationships and validation annotations.



Implement JWT auth flow:





login, token issue, token validation filter.



RBAC model:





system roles (SUPER_ADMIN, CLUSTER_COORDINATOR, SUPERVISOR, TEACHER, SCHOOL_DIRECTOR) + custom roles.



Enforce tenant isolation:





tenant resolver from JWT claims



repository/service-level organization scoping guardrails.

Milestone 3: Dynamic Checklist Core Logic (Critical)





Checklist domain:





Checklist with targetType, displayMode, activeVersionId.



ChecklistVersion for immutable versioning snapshots.



ChecklistItem with type, optionsJson, groupKey, displayOrder.



Checklist APIs:





create checklist



publish new version



fetch render payload by assignment



Render contract from backend (single source of truth):





metadata for mode (ALL_AT_ONCE, ONE_BY_ONE, GROUPED)



grouped sections for GROUPED



validation rules + option payloads.

Milestone 4: Assignment + Review + Geolocation Integrity





Assignment APIs for supervisor workload and due dates.



Review start and submit flow with GPS capture fields:





start_latitude, start_longitude, end_latitude, end_longitude



is_within_range, distance_from_school



Geolocation policy model (chosen): configurable behavior





BLOCK_SUBMISSION



ALLOW_AND_FLAG_OUT_OF_RANGE



Implement Haversine distance service and policy engine at submission time.



Store range decision and reason for auditability.

Milestone 5: Flutter MVP Screens and Dynamic Renderer





Build feature-based clean structure.



Implement screens:





Login, Dashboard, Assignments, Checklist Renderer, Signature Pad, Reports



Checklist renderer engine:





mode switch + reusable question widgets (TEXT, SINGLE_CHOICE, MULTIPLE_CHOICE, YES_NO, RATING, PHOTO)



section/page navigation for GROUPED and ONE_BY_ONE



GPS behavior:





permission flow, automatic start/submit capture, out-of-range warning UI.

Milestone 6: Signatures, Reports, Audit, and Prepared Extras





Signature capture for teacher/director and attachment to review.



PDF report generation endpoint (answers + signatures + school stamp placeholder).



Audit trail entries for checklist publish, assignment actions, review submissions.



Prepared but partial modules:





offline sync scaffolding



biometric placeholder hooks



school stamp upload endpoint/storage abstraction



location audit log extension point.

API Contract Priorities (MVP)





POST /auth/login



POST /users, GET /users, PATCH /users/{id}/roles



POST /checklists, POST /checklists/{id}/versions, GET /assignments/{id}/render



POST /assignments, GET /assignments/my



POST /reviews/{assignmentId}/start, POST /reviews/{assignmentId}/submit



POST /reviews/{id}/signatures



GET /reports/reviews/{id}/pdf

Non-Functional Guardrails





Validation first (DTO + bean validation + global exception handler).



Idempotent submission endpoints where practical.



Consistent naming conventions across backend and Flutter feature modules.



Keep MVP simple: avoid event buses/microservices until usage requires it.

Delivery Sequence





Scaffold two project folders and baseline configs.



Implement Flyway migrations and core entities.



Implement auth/RBAC/tenant scoping.



Implement checklist versioning + render payload.



Implement assignment/review/geovalidation.



Implement Flutter dynamic renderer + GPS/signature flows.



Add report generation and audit logging.



Run integration checks and tighten API contracts.

