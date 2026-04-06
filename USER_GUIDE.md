# Quick user guide

## Start the stack

1. Run PostgreSQL and create DB `supervision` (see `backend-spring/src/main/resources/application.yml` for URL/user/password).
2. Start the **backend** (`backend-spring`), then the **web portal** (`web-portal`), and optionally the **mobile app** (`mobile-flutter`) pointed at the same API.

## Roles (who does what)

| Role | Web portal | Mobile app |
|------|------------|------------|
| **SUPER_ADMIN** | Full admin: coordinators, supervisors, schools, school stuff, checklists, assignments, supervision activity, reports | Same as web if you use it there |
| **CLUSTER_COORDINATOR** | Supervisors, schools, school stuff, checklists, assignments, supervision, reports (no “cluster coordinators” list) | Optional |
| **SUPERVISOR** | Profile, **My assignments**, **Reports** | **Assignments**, run visits (GPS), fill checklist, **signatures** |

Default seed login (if unchanged): **`superadmin`** / **`Admin@12345`**.

---

## 1. Accounts (web → **Users**)

- **SUPER_ADMIN** → **Add coordinator** (needs city / sub city / wereda).
- **SUPER_ADMIN** or **CLUSTER_COORDINATOR** → **Add supervisor** (coordinator’s location is applied automatically for coordinators). Choose **which grades** that supervisor may supervise; new assignments and auto-assignment only use supervisors whose grades **overlap** the school’s grades and the checklist’s grade group.
- Coordinators and supervisors can be **edited** or **deleted** from the same table when allowed (e.g. no blocking schools/assignments). Supervisors created before this field existed may show as **all grades (legacy)** until you edit and save an explicit list.

Mobile: **Users** screen mirrors coordinator/supervisor creation for admins (including grade selection for supervisors).

---

## 2. Schools (web → **Schools**)

- **Add school**: name, grades served, map point (lat/lon), check-in radius.
- **Edit** / **Delete** from the row actions (delete may be blocked if teachers or assignments still reference the school).

Mobile: **Schools** admin screen — same idea.

---

## 3. School staff (web → **School stuff**)

- Pick or **Add type** (role) if needed.
- **Add stuff**: teachers (school + subject), directors (school + login), or other staff types per role rules.
- **Edit** / **Delete** from the list (rules depend on type; some deletes block if assignments exist).

---

## 4. Checklists (web → **Checklists** + **Checklist items**)

- **Checklists**: create checklist, set target type (school, teacher, director, staff, etc.), purpose, **grade group**, and publish/version as your workflow allows.
- **Checklist items**: open a checklist and add questions (types like yes/no, text, photo, rating, etc.) and ordering/groups.

Grade groups live under the checklist UI flow; keep **grade groups** consistent with what schools support.

---

## 5. Assignments (web → **Assignments**)

- **New assignment**: choose checklist (published version), **supervisor**, target (school / teacher / director / staff per checklist), due date.
- Supervisors only see work that is assigned to them.

---

## 6. Review & mobile flow (**Supervisor**)

1. Log in on the **mobile app** (or use web **My assignments** where available).
2. Open an assignment, allow **location**, **start** the visit (geo check-in).
3. Answer the checklist, **submit** the review.
4. Capture **signatures** when prompted.

---

## 7. Reports

- **Web → Reports**: submitted reviews; download PDF where offered.
- Admins can use **Supervision** for activity summaries; supervisors see their own assignment/report scope.

---

## 8. Database reset (optional)

- **`clean-database-keep-superadmin.bat`** (with `psql` available) runs `scripts/postgres/clean_default_org_keep_superadmin.sql` to wipe org data but keep `superadmin` and **grade groups**.
- With **`app.demo-data.enabled: false`** (default), the backend does **not** re-seed demo schools/users after restart.

---

## Tips

- Build **grade groups** and **school grade lists** before relying on auto-assignment flags on checklists.
- If something “won’t delete,” the API is usually blocking broken references (assignments, teachers, etc.)—remove or reassign those first.
