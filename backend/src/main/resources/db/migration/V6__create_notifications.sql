-- День 6: In-app уведомления. Источник правды: SPEC.md §3.4, §4.6.
--
-- announcement_id колонка создаётся без FK — таблица announcements появится
-- в миграции V7 (Spec 2). FK добавит V7 через ALTER TABLE.
--
-- CHECK (target) — defense-in-depth: гарантирует ровно один FK-таргет для kind.

CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    kind VARCHAR(40) NOT NULL,
    title VARCHAR(300) NOT NULL,
    body TEXT NOT NULL,
    icon_style VARCHAR(20),
    complaint_id BIGINT REFERENCES complaints(id) ON DELETE CASCADE,
    announcement_id BIGINT,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT notifications_kind_check
        CHECK (kind IN ('COMPLAINT_STATUS', 'ANNOUNCEMENT')),
    CONSTRAINT notifications_target_check
        CHECK (
            (kind = 'COMPLAINT_STATUS' AND complaint_id IS NOT NULL AND announcement_id IS NULL)
            OR (kind = 'ANNOUNCEMENT' AND announcement_id IS NOT NULL AND complaint_id IS NULL)
        )
);

CREATE INDEX idx_notifications_user_created
    ON notifications(user_id, created_at DESC);

CREATE INDEX idx_notifications_user_unread
    ON notifications(user_id)
    WHERE read_at IS NULL;
