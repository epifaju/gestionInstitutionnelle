-- Données de fixture pour scripts E2E (API contrats / permissions).
-- Mot de passe pour tous les comptes ci-dessous : AdminTest123! (même bcrypt que V3__admin_dev_password.sql)

INSERT INTO utilisateurs (id, organisation_id, email, password_hash, role, nom, prenom, actif)
SELECT 'b1111111-1111-4111-8111-111111111101'::uuid, o.id, 'rh@test.com',
       '$2b$12$5kECGSj4zVYgGca15CewEOit820yWsoJ95crYWo0yqU8sqdWvWOQG', 'RH', 'Compte', 'RH', true
FROM organisations o
WHERE o.id = 'a0000000-0000-0000-0000-000000000001'::uuid
  AND NOT EXISTS (SELECT 1 FROM utilisateurs u WHERE u.organisation_id = o.id AND lower(u.email) = lower('rh@test.com'));

INSERT INTO utilisateurs (id, organisation_id, email, password_hash, role, nom, prenom, actif)
SELECT 'b1111111-1111-4111-8111-111111111102'::uuid, o.id, 'financier@test.com',
       '$2b$12$5kECGSj4zVYgGca15CewEOit820yWsoJ95crYWo0yqU8sqdWvWOQG', 'FINANCIER', 'Compte', 'Financier', true
FROM organisations o
WHERE o.id = 'a0000000-0000-0000-0000-000000000001'::uuid
  AND NOT EXISTS (SELECT 1 FROM utilisateurs u WHERE u.organisation_id = o.id AND lower(u.email) = lower('financier@test.com'));

INSERT INTO utilisateurs (id, organisation_id, email, password_hash, role, nom, prenom, actif)
SELECT 'b1111111-1111-4111-8111-111111111103'::uuid, o.id, 'employe@test.com',
       '$2b$12$5kECGSj4zVYgGca15CewEOit820yWsoJ95crYWo0yqU8sqdWvWOQG', 'EMPLOYE', 'Compte', 'Employé', true
FROM organisations o
WHERE o.id = 'a0000000-0000-0000-0000-000000000001'::uuid
  AND NOT EXISTS (SELECT 1 FROM utilisateurs u WHERE u.organisation_id = o.id AND lower(u.email) = lower('employe@test.com'));

INSERT INTO salaries (id, organisation_id, matricule, nom, prenom, email, telephone, poste, service, date_embauche, type_contrat, statut)
SELECT 'c0e2e0e0-0000-4000-8000-000000000001'::uuid, o.id, 'E2E-CONTRAT', 'Fixture', 'Contrats', 'e2e-contrat@test.com', NULL,
       'Agent', 'RH', CURRENT_DATE, 'CDI', 'ACTIF'::statut_salarie
FROM organisations o
WHERE o.id = 'a0000000-0000-0000-0000-000000000001'::uuid
  AND NOT EXISTS (SELECT 1 FROM salaries s WHERE s.id = 'c0e2e0e0-0000-4000-8000-000000000001'::uuid);
