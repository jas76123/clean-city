-- Day 5 prep: приведение users к решениям 2026-05-07/08.
--   1) phone не используется (auth по email + паролю, не SMS) — удаляем.
--   2) Согласие на ПДн (152-ФЗ): фиксируем accepted_terms_at + accepted_terms_version
--      при регистрации. Поля nullable, т.к. для админов через invite соглашение
--      принимается на отдельном шаге accept-invite.

ALTER TABLE users DROP COLUMN phone;

ALTER TABLE users ADD COLUMN accepted_terms_at TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN accepted_terms_version VARCHAR(10);
