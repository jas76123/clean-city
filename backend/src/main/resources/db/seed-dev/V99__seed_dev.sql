-- Dev seed: 1 админ + 5 резидентов + 50 жалоб по Сочи.
-- Запускается ТОЛЬКО при STAGE=DEV (см. configureDatabase): Flyway добавляет
-- эту locations поверх db/migration. В prod-классpath эта папка не подключается.
--
-- Идемпотентно: если admin@cleancity.dev уже есть, скрипт ничего не делает.
--
-- Тестовые credentials:
--   admin@cleancity.dev / Admin12345!     (роль ADMIN, 2FA выключена)
--   resident1..5@cleancity.dev / Resident123!   (роль RESIDENT)
--
-- Bcrypt-хеши сгенерированы с cost=12 — совпадают с продакшен-конфигурацией
-- PasswordHasher (at.favre.lib bcrypt).

DO $$
DECLARE
    admin_id BIGINT;
    resident_ids BIGINT[];
    new_complaint_id BIGINT;
    i INT;
    seed_count INT;
BEGIN
    -- Гард: один раз сидим, потом no-op.
    SELECT COUNT(*) INTO seed_count FROM users WHERE email = 'admin@cleancity.dev';
    IF seed_count > 0 THEN
        RAISE NOTICE 'V99 seed: already applied (admin@cleancity.dev exists), skipping';
        RETURN;
    END IF;

    -- Админ.
    INSERT INTO users (email, password_hash, role, full_name, email_verified, is_active,
                       accepted_terms_at, accepted_terms_version, password_changed_at)
    VALUES (
        'admin@cleancity.dev',
        '$2b$12$PM5rmc4fNFO9K0AqwJYm7e/WyOyqLNFHLhavD21zdzn.fvX.60LF.',
        'ADMIN',
        'Dev Admin',
        TRUE, TRUE,
        NOW(), 'v1',
        NOW()
    )
    RETURNING id INTO admin_id;

    -- 5 резидентов. Район проставляем циклически по 4 районам Сочи —
    -- без него фильтр получателей у объявлений с конкретными районами всех отсеет.
    WITH residents AS (
        INSERT INTO users (email, password_hash, role, full_name, district, email_verified, is_active,
                           accepted_terms_at, accepted_terms_version, password_changed_at)
        SELECT
            'resident' || g || '@cleancity.dev',
            '$2b$12$ZDfqomE.4Ox7GQepuUIieeoGZFvN/ELU1XCXxXfGzyG0xfBd4sjNe',
            'RESIDENT',
            'Житель ' || g,
            (ARRAY['Центральный','Адлерский','Хостинский','Лазаревский'])[((g - 1) % 4) + 1],
            TRUE, TRUE,
            NOW(), 'v1',
            NOW()
        FROM generate_series(1, 5) g
        RETURNING id
    )
    SELECT ARRAY_AGG(id ORDER BY id) INTO resident_ids FROM residents;

    -- 50 жалоб, разбросанных вокруг центра Сочи (43.60, 39.73).
    -- author — циклически из 5 резидентов; категория из 18 + Status из 5
    -- (в основном NEW/IN_PROGRESS/RESOLVED — реалистичное распределение).
    FOR i IN 1..50 LOOP
        INSERT INTO complaints (
            author_id, category, title, description,
            latitude, longitude, address, district,
            status, created_at, updated_at, resolved_at
        )
        VALUES (
            resident_ids[((i - 1) % 5) + 1],
            (ARRAY[
                'GARBAGE','ROADS','SIDEWALKS','LIGHTING','GREENERY','LANDSCAPING',
                'PLAYGROUNDS','PARKS','BEACHES','SAFETY','VANDALISM','WATER_SUPPLY',
                'SEWAGE','ELECTRICITY','ECOLOGY','ACCESSIBILITY','TRADE','OTHER'
            ])[((i - 1) % 18) + 1],
            'Жалоба #' || i,
            'Описание тестовой жалобы №' || i || '. Подробности заполнены автоматически в dev seed.',
            -- координаты в радиусе ~5км от центра Сочи
            43.5800 + (RANDOM() * 0.08),
            39.7000 + (RANDOM() * 0.10),
            'ул. Тестовая, ' || i,
            (ARRAY['Центральный','Адлерский','Хостинский','Лазаревский'])[((i - 1) % 4) + 1],
            CASE (i % 7)
                WHEN 0 THEN 'RESOLVED'
                WHEN 1 THEN 'IN_PROGRESS'
                WHEN 2 THEN 'IN_PROGRESS'
                WHEN 3 THEN 'REJECTED'
                WHEN 4 THEN 'DUPLICATE'
                ELSE 'NEW'
            END,
            NOW() - (i * INTERVAL '6 hours'),
            NOW() - (i * INTERVAL '6 hours'),
            CASE WHEN (i % 7) = 0 THEN NOW() - (i * INTERVAL '3 hours') ELSE NULL END
        )
        RETURNING id INTO new_complaint_id;

        -- Автоголос автора.
        INSERT INTO votes (complaint_id, user_id, value, created_at)
        VALUES (new_complaint_id, resident_ids[((i - 1) % 5) + 1], 1, NOW())
        ON CONFLICT (complaint_id, user_id) DO NOTHING;

        -- Дополнительные голоса от других резидентов — чтобы аналитика была наглядной.
        -- 0..3 случайных голоса.
        INSERT INTO votes (complaint_id, user_id, value, created_at)
        SELECT new_complaint_id, r_id, 1, NOW()
        FROM unnest(resident_ids) AS r_id
        WHERE r_id <> resident_ids[((i - 1) % 5) + 1]
          AND (i + r_id) % 3 = 0
        ON CONFLICT (complaint_id, user_id) DO NOTHING;
    END LOOP;

    -- Чиним duplicate_of_id для DUPLICATE-жалоб: указываем на первую NEW-жалобу,
    -- чтобы ссылочная целостность была корректной. Без этого DUPLICATE — «orphan».
    UPDATE complaints
    SET duplicate_of_id = (SELECT id FROM complaints WHERE status = 'NEW' LIMIT 1)
    WHERE status = 'DUPLICATE';

    RAISE NOTICE 'V99 seed: 1 admin + 5 residents + 50 complaints inserted';
END $$;
