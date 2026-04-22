-- Utilisateur FINANCIER pour les tests d'intégration (SecurityIntegrationTest).
-- L'organisation et admin@test.com sont déjà créés dans V1 ; V3 a le mot de passe AdminTest123!
INSERT INTO utilisateurs (id, organisation_id, email, password_hash, role, nom, prenom, actif)
VALUES (
    'b0000000-0000-0000-0000-000000000002'::uuid,
    'a0000000-0000-0000-0000-000000000001'::uuid,
    'fin@test.com',
    '$2b$12$5kECGSj4zVYgGca15CewEOit820yWsoJ95crYWo0yqU8sqdWvWOQG',
    'FINANCIER',
    'Fin',
    'Test',
    TRUE
)
ON CONFLICT (id) DO NOTHING;
