-- V8: нормализация complaints.district к 4 каноничным районам Сочи.
-- District хранился как свободный текст геокодера («Адлерский внутригородской
-- район» и т.п.) — приводим к каноничным label для точного фильтра в веб-админке.
UPDATE complaints
SET district = CASE
    WHEN district ILIKE '%центральн%'  THEN 'Центральный'
    WHEN district ILIKE '%адлер%'      THEN 'Адлерский'
    WHEN district ILIKE '%хост%'       THEN 'Хостинский'
    WHEN district ILIKE '%лазаревск%'  THEN 'Лазаревский'
    ELSE NULL
END
WHERE district IS NOT NULL;
