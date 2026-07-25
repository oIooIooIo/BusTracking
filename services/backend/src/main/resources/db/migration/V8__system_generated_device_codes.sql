CREATE SEQUENCE device_code_seq;

SELECT setval(
    'device_code_seq',
    GREATEST(
        COALESCE((
            SELECT max(substring(device_code FROM '^DEVICE-([0-9]+)$')::bigint)
            FROM device
            WHERE device_code ~ '^DEVICE-[0-9]+$'
        ), 0) + 1,
        1
    ),
    false
);
