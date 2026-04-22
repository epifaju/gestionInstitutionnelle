-- Mot de passe dev documenté : AdminTest123! (≥ 8 caractères, conforme au formulaire login)
UPDATE utilisateurs
SET password_hash = '$2b$12$5kECGSj4zVYgGca15CewEOit820yWsoJ95crYWo0yqU8sqdWvWOQG'
WHERE email = 'admin@test.com';
