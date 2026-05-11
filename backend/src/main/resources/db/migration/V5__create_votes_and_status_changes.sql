-- День 5: голоса и история смены статусов. Источник правды: SPEC.md §3.4, §5.2, §5.3.
--
-- value SMALLINT с CHECK (-1, 1) оставлен под будущую совместимость («не проблема»),
-- хотя API в MVP принимает только +1 (см. SPEC §5.3).
--
-- comment в status_changes — NOT NULL на уровне БД: SPEC §5.2 однозначен про обязательность.
-- Первичная запись «NEW» при создании жалобы НЕ пишется; история начинается с первого
-- действия админа.

CREATE TABLE votes (
    id BIGSERIAL PRIMARY KEY,
    complaint_id BIGINT NOT NULL REFERENCES complaints(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id),
    value SMALLINT NOT NULL DEFAULT 1 CHECK (value IN (-1, 1)),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (complaint_id, user_id)
);

CREATE INDEX idx_votes_complaint ON votes(complaint_id);
CREATE INDEX idx_votes_user ON votes(user_id);

CREATE TABLE status_changes (
    id BIGSERIAL PRIMARY KEY,
    complaint_id BIGINT NOT NULL REFERENCES complaints(id) ON DELETE CASCADE,
    from_status VARCHAR(20),
    to_status VARCHAR(20) NOT NULL,
    comment TEXT NOT NULL,
    changed_by_id BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_status_changes_complaint ON status_changes(complaint_id, created_at);

-- Бэкфилл: автоголос автора для жалоб, созданных до Day 5.
-- На Day 5+ автоголос пишется в одной транзакции с созданием жалобы (ComplaintService.create).
INSERT INTO votes (complaint_id, user_id, value)
SELECT id, author_id, 1
FROM complaints
ON CONFLICT (complaint_id, user_id) DO NOTHING;
