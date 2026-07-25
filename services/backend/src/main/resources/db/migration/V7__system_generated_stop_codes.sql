CREATE SEQUENCE stop_code_seq;

SELECT setval(
    'stop_code_seq',
    GREATEST(
        COALESCE((
            SELECT max(substring(code FROM '^STOP-([0-9]+)$')::bigint)
            FROM stop
            WHERE code ~ '^STOP-[0-9]+$'
        ), 0) + 1,
        1
    ),
    false
);
