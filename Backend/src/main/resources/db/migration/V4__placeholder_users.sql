-- V4 — placeholder local logins, one per role, for demos/QA.
--
-- Swap-in note for the real taxonomy: role NAMES and RANKS live in the roles
-- table (V2/V3), privileges are derived from rank via app.rbac.* thresholds
-- (application.yml), and both the API and UI read roles from /api/v1/auth.
-- So onboarding the teammate's real roles means: a new migration that
-- inserts/updates roles (+ ranks/colors), optional threshold tweaks, and
-- matching demo users here — no application code changes.
--
-- Passwords below are demo-only and hashed at migration time with pgcrypto's
-- bcrypt so the stored hashes are salted (never a fixed committed hash).
-- LOCAL DEV ONLY.
WITH dept AS (SELECT id FROM departments WHERE name = 'Coal India HQ' LIMIT 1),
     r AS (SELECT id, name FROM roles)
INSERT INTO users (email, password_hash, full_name, role_id, department_id, is_active)
SELECT x.email, crypt(x.pw, gen_salt('bf', 10)), x.full_name, r.id, dept.id, TRUE
FROM dept CROSS JOIN (VALUES
    ('corrector@minex.local',     'Corrector123!',     'Demo Corrector',      'DATA_CORRECTOR'),
    ('subsupervisor@minex.local', 'SubSupervisor123!', 'Demo Sub Supervisor', 'SUB_SUPERVISOR'),
    ('supervisor@minex.local',    'Supervisor123!',    'Demo Supervisor',     'SUPERVISOR'),
    ('manager@minex.local',       'Manager123!',       'Demo Manager',        'MANAGER'),
    ('admin@minex.local',         'Admin123!',         'Platform Admin',      'ADMIN')
) AS x(email, pw, full_name, role)
JOIN r ON r.name = x.role
ON CONFLICT (email) DO NOTHING;
