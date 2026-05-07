-- Audit-лог для security-событий (день 3): входы, lockout, 2FA, инвайты, отзывы сессий.
-- Для пилота держим как обычную таблицу — на проде можно вынести в партиционированную
-- по created_at, как только объём перевалит за миллион записей.
CREATE TABLE audit_log (
    id BIGSERIAL PRIMARY KEY,
    actor_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    action VARCHAR(50) NOT NULL,
    target_type VARCHAR(50),
    target_id VARCHAR(100),
    ip VARCHAR(45),
    user_agent VARCHAR(500),
    metadata TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_actor ON audit_log(actor_user_id);
CREATE INDEX idx_audit_action ON audit_log(action);
CREATE INDEX idx_audit_created_at ON audit_log(created_at DESC);
