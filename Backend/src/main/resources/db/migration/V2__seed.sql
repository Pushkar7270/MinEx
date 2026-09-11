-- V2 — seed data for Phase 1 MVP (PRD §2 RBAC, §4.3 categories).
-- Demo logins (local dev only): admin@minex.local / Admin123!,
-- supervisor@minex.local / Supervisor123!, corrector@minex.local / Corrector123!

-- ---------- Roles (rank: higher = more privilege) ----------
INSERT INTO roles (name, rank) VALUES
    ('DATA_CORRECTOR', 10),
    ('SUB_SUPERVISOR', 20),
    ('SUPERVISOR',     30),
    ('MANAGER',        40),
    ('ADMIN',         100)
ON CONFLICT (name) DO NOTHING;

INSERT INTO departments (name) VALUES ('Coal India HQ')
ON CONFLICT (name) DO NOTHING;

-- ---------- Categories (MVP slice: Yield / Budget / Expense + Safety / Geological Survey) ----------
INSERT INTO categories (name) VALUES
    ('Annual Yield'),
    ('Annual Expense'),
    ('Budget'),
    ('Safety Incidents'),
    ('Geological Survey')
ON CONFLICT (name) DO NOTHING;

-- ---------- Approval graph (configurable, not hardcoded — PRD §2) ----------
-- DATA_CORRECTOR -> SUB_SUPERVISOR -> SUPERVISOR -> MANAGER; ADMIN can approve anything.
WITH r AS (SELECT id, name FROM roles)
INSERT INTO approval_rules (role_id, can_approve_role_id, category_id)
SELECT submitter.id, approver.id, NULL
FROM (VALUES
    ('DATA_CORRECTOR','SUB_SUPERVISOR'),
    ('DATA_CORRECTOR','SUPERVISOR'),
    ('DATA_CORRECTOR','MANAGER'),
    ('SUB_SUPERVISOR','SUPERVISOR'),
    ('SUB_SUPERVISOR','MANAGER'),
    ('SUPERVISOR','MANAGER')
) AS chain(sub_name, app_name)
JOIN r AS submitter ON submitter.name = chain.sub_name
JOIN r AS approver  ON approver.name  = chain.app_name
ON CONFLICT DO NOTHING;

-- ---------- Demo users (LOCAL DEV ONLY — replace in real deploys) ----------
WITH dept AS (SELECT id FROM departments WHERE name = 'Coal India HQ' LIMIT 1),
     r AS (SELECT id, name FROM roles)
INSERT INTO users (email, password_hash, full_name, role_id, department_id, is_active)
SELECT x.email, x.hash, x.full_name, r.id, dept.id, TRUE
FROM dept CROSS JOIN (VALUES
    ('admin@minex.local',      '$2b$10$fVoAbCALNqHh1i6t5Y0qRuYJXA3iBvrlOjMpLyoVcNqouQB.qrtMS', 'Platform Admin',  'ADMIN'),
    ('supervisor@minex.local', '$2b$10$xF2so8WHpN07b/bioIMH0.fo./OY5D58x9fcIHj7i7fleGUXL0ARe', 'Demo Supervisor', 'SUPERVISOR'),
    ('corrector@minex.local',  '$2b$10$IcwYj3WYpZpqCWIIAaRhOus5AbyvAcJgFBcjOpatYpSJi81y8k7LS', 'Demo Corrector',  'DATA_CORRECTOR')
) AS x(email, hash, full_name, role)
JOIN r ON r.name = x.role
ON CONFLICT (email) DO NOTHING;
