-- seed-analytics-demo.sql — демо-данные для страницы «Аналитика».
-- Цель: заполнить столбцы «Медиана» и «Соблюдение норматива, %» в таблицах
-- по категориям и по районам реалистичным разноцветным набором.
--
-- Вставляет 90 RESOLVED-жалоб (18 категорий × 5) с заданными в прошлом
-- created_at/resolved_at. Маркер всех строк: address = 'SLA-DEMO'.
--
-- ТОЛЬКО для dev: запускать вручную через ops/seed-analytics-demo.sh.
-- Это НЕ Flyway-миграция — прод не затрагивается.
--
-- Идемпотентно: при наличии демо-строк скрипт ничего не делает.
-- Откат: DELETE FROM complaints WHERE address = 'SLA-DEMO';

DO $$
DECLARE
    resident_ids BIGINT[];
    n_residents  INT;
    rec          RECORD;
    j            INT;
    k            INT := 0;
    cat_ord      INT := 0;
    sla_hours    NUMERIC;
    is_within    BOOLEAN;
    res_hours    NUMERIC;
    created_ts   TIMESTAMPTZ;
    resolved_ts  TIMESTAMPTZ;
    demo_count   INT;
BEGIN
    -- Гард: один раз. Повторный запуск — no-op.
    SELECT COUNT(*) INTO demo_count FROM complaints WHERE address = 'SLA-DEMO';
    IF demo_count > 0 THEN
        RAISE NOTICE 'seed-analytics-demo: already applied (% demo rows), skipping', demo_count;
        RETURN;
    END IF;

    -- Авторы — существующие резиденты (созданы V99). Без них вставлять не от кого.
    SELECT ARRAY_AGG(id ORDER BY id) INTO resident_ids
    FROM users WHERE role = 'RESIDENT';
    n_residents := COALESCE(ARRAY_LENGTH(resident_ids, 1), 0);
    IF n_residents = 0 THEN
        RAISE EXCEPTION 'seed-analytics-demo: нет пользователей с ролью RESIDENT — сначала примените V99 dev-seed';
    END IF;

    -- (category, n_within, district). n_within из 5 задаёт тир/цвет.
    FOR rec IN
        SELECT * FROM (VALUES
            -- Зелёные (5/5) → Центральный
            ('GARBAGE',       5, 'Центральный'),
            ('LIGHTING',      5, 'Центральный'),
            ('ROADS',         5, 'Центральный'),
            ('GREENERY',      5, 'Центральный'),
            ('PARKS',         5, 'Центральный'),
            ('ACCESSIBILITY', 5, 'Центральный'),
            -- Жёлтые (3/5) → Адлерский
            ('ECOLOGY',       3, 'Адлерский'),
            ('SEWAGE',        3, 'Адлерский'),
            ('SIDEWALKS',     3, 'Адлерский'),
            -- Жёлтые (3/5) → Лазаревский
            ('WATER_SUPPLY',  3, 'Лазаревский'),
            ('LANDSCAPING',   3, 'Лазаревский'),
            ('TRADE',         3, 'Лазаревский'),
            -- Красные (2/5) → Хостинский
            ('SAFETY',        2, 'Хостинский'),
            ('ELECTRICITY',   2, 'Хостинский'),
            ('PLAYGROUNDS',   2, 'Хостинский'),
            ('BEACHES',       2, 'Хостинский'),
            ('VANDALISM',     2, 'Хостинский'),
            ('OTHER',         2, 'Хостинский')
        ) AS t(category, n_within, district)
    LOOP
        -- SLA-порог категории (зеркалит CategorySla.hoursFor).
        sla_hours := CASE
            WHEN rec.category IN ('GARBAGE','ECOLOGY','SAFETY','LIGHTING',
                                  'SEWAGE','WATER_SUPPLY','ELECTRICITY') THEN 24
            ELSE 120
        END;

        FOR j IN 1..5 LOOP
            k := k + 1;
            is_within := (j <= rec.n_within);

            -- В норме: 0.48..0.80× порога (varied медиана). Просрочка: 1.4..2.2× порога.
            res_hours := CASE
                WHEN is_within THEN sla_hours * (0.40 + 0.08 * j)
                ELSE sla_hours * (1.20 + 0.20 * j)
            END;

            -- created_at: 13..24 дня назад (k % 12), чтобы resolved_at (макс ~+11 дней)
            -- гарантированно был ≤ now и всё попадало в период «Месяц».
            created_ts  := NOW() - ((13 + (k % 12)) * INTERVAL '1 day');
            resolved_ts := created_ts + (res_hours * INTERVAL '1 hour');

            INSERT INTO complaints (
                author_id, category, title, description,
                latitude, longitude, address, district,
                status, created_at, updated_at, resolved_at
            )
            VALUES (
                resident_ids[(k % n_residents) + 1],
                rec.category,
                'Демо-аналитика #' || k,
                'Решённая жалоба для наглядной аналитики (категория ' || rec.category || ').',
                43.5800 + (RANDOM() * 0.08),
                39.7000 + (RANDOM() * 0.10),
                'SLA-DEMO',
                rec.district,
                'RESOLVED',
                created_ts,
                resolved_ts,
                resolved_ts
            );
        END LOOP;
        cat_ord := cat_ord + 1;
    END LOOP;

    RAISE NOTICE 'seed-analytics-demo: inserted % RESOLVED complaints across % categories', k, cat_ord;
END $$;
