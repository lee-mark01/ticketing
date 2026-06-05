-- =============================================
-- V4__demo_users.sql — 시연용 사용자
-- password: password123 (BCrypt 해시)
-- =============================================

INSERT INTO users (email, password_hash, name) VALUES
    ('demo@ticketing.com', '$2b$10$NsDQoJoLEd5DICHJvYYUV.caOEQfFjKMry8zZgIdDd4HQgxBuECni', '시연사용자'),
    ('user1@ticketing.com', '$2b$10$NsDQoJoLEd5DICHJvYYUV.caOEQfFjKMry8zZgIdDd4HQgxBuECni', '사용자1'),
    ('user2@ticketing.com', '$2b$10$NsDQoJoLEd5DICHJvYYUV.caOEQfFjKMry8zZgIdDd4HQgxBuECni', '사용자2')
ON CONFLICT (email) DO NOTHING;
