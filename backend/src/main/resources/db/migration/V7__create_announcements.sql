-- Spec 2: объявления. Источник правды: SPEC.md §3.4, §4.4, §5.4.
--
-- DELETE-семантика: soft через expires_at = NOW() (см. решение от 2026-05-13).
-- Это сохраняет уведомления у жителей при «снятии с публикации».
--
-- districts: CSV районов или 'ALL'. Триггер push при INSERT (через
-- AnnouncementService) выбирает recipients по этому полю.
--
-- Эта миграция также добавляет FK на notifications.announcement_id —
-- колонка создана в V6 без FK (см. V6__create_notifications.sql).

CREATE TABLE announcements (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(300) NOT NULL,
    body TEXT NOT NULL,
    icon_style VARCHAR(20) NOT NULL DEFAULT 'INFO',
    category VARCHAR(30),
    districts TEXT NOT NULL DEFAULT 'ALL',
    author_id BIGINT NOT NULL REFERENCES users(id),
    published_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ,

    CONSTRAINT announcements_icon_style_check
        CHECK (icon_style IN ('INFO', 'SUCCESS', 'WARNING'))
);

CREATE INDEX idx_announcements_published
    ON announcements(published_at DESC);

ALTER TABLE notifications
    ADD CONSTRAINT notifications_announcement_fk
    FOREIGN KEY (announcement_id) REFERENCES announcements(id) ON DELETE CASCADE;
