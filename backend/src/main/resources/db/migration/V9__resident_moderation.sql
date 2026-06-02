-- Модерация жителей: дата последнего предупреждения (для обнуления счётчика
-- отклонений) + новый вид уведомления MODERATION_WARNING.
ALTER TABLE users ADD COLUMN warned_at TIMESTAMPTZ;

-- Расширяем CHECK-констрейнты notifications под новый kind.
-- MODERATION_WARNING привязан к жалобе-нарушению (complaint_id NOT NULL).
ALTER TABLE notifications DROP CONSTRAINT notifications_kind_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_kind_check
    CHECK (kind IN ('COMPLAINT_STATUS', 'ANNOUNCEMENT', 'MODERATION_WARNING'));

ALTER TABLE notifications DROP CONSTRAINT notifications_target_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_target_check CHECK (
    (kind = 'COMPLAINT_STATUS'   AND complaint_id IS NOT NULL AND announcement_id IS NULL)
    OR (kind = 'ANNOUNCEMENT'        AND announcement_id IS NOT NULL AND complaint_id IS NULL)
    OR (kind = 'MODERATION_WARNING'  AND complaint_id IS NOT NULL AND announcement_id IS NULL)
);
