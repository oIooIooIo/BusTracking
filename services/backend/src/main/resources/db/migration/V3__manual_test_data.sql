-- Stable reference data plus records dated for the current day, so the Admin Web
-- date picker can display route and boarding-event examples after every reset.
INSERT INTO bus (id, code, name, active, permission_version)
VALUES
    ('00000000-0000-0000-0000-000000000002', 'BUS-02', 'District 7 Shuttle', true, 2),
    ('00000000-0000-0000-0000-000000000003', 'BUS-03', 'Maintenance Spare Bus', false, 0);

INSERT INTO device (id, device_code, bus_id, api_key_hash, active)
VALUES (
    '00000000-0000-0000-0000-000000000102',
    'ANDROID-TEST-02',
    '00000000-0000-0000-0000-000000000002',
    'f496901c647aac3150a606dad8efb7a1f588c7e33cda164d9fad5508eba9a629',
    true
);

INSERT INTO employee (id, employee_no, name, card_sn, active)
VALUES
    ('00000000-0000-0000-0000-000000000201', 'E00201', 'Nguyen An', 'TESTCARD0001', true),
    ('00000000-0000-0000-0000-000000000202', 'E00202', 'Tran Binh', 'TESTCARD0002', true),
    ('00000000-0000-0000-0000-000000000203', 'E00203', 'Le Chi', 'TESTCARD0003', true),
    ('00000000-0000-0000-0000-000000000204', 'E00204', 'Pham Dung', 'TESTCARD0004', false);

INSERT INTO bus_employee_permission (bus_id, employee_id)
VALUES
    ('218930ff-2bf3-4f9f-867c-97537a8e4132', '00000000-0000-0000-0000-000000000201'),
    ('00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000202'),
    ('00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000203');

INSERT INTO gps_point (
    device_id, sequence_no, bus_id, recorded_at, position, accuracy_meters
)
SELECT
    '74de038e-b194-4b3f-820c-6ee2689ac27f'::uuid,
    point_no,
    '218930ff-2bf3-4f9f-867c-97537a8e4132'::uuid,
    CURRENT_DATE + TIME '07:30:00' + (point_no - 1) * INTERVAL '2 minutes',
    ST_SetSRID(
        ST_MakePoint(
            106.6297 + (point_no - 1) * 0.0020,
            10.8231 + (point_no - 1) * 0.0011
        ),
        4326
    )::geography,
    5.0 + (point_no % 4)
FROM generate_series(1, 12) AS point_no;

INSERT INTO gps_point (
    device_id, sequence_no, bus_id, recorded_at, position, accuracy_meters
)
SELECT
    '00000000-0000-0000-0000-000000000102'::uuid,
    point_no,
    '00000000-0000-0000-0000-000000000002'::uuid,
    CURRENT_DATE + TIME '17:10:00' + (point_no - 1) * INTERVAL '3 minutes',
    ST_SetSRID(
        ST_MakePoint(
            106.7009 + (point_no - 1) * 0.0016,
            10.7298 + (point_no - 1) * 0.0010
        ),
        4326
    )::geography,
    6.0 + (point_no % 3)
FROM generate_series(1, 10) AS point_no;

INSERT INTO boarding_event (
    id, bus_id, device_id, employee_id, card_sn, result, scanned_at, permission_version
)
VALUES
    (
        '00000000-0000-0000-0000-000000000301',
        '218930ff-2bf3-4f9f-867c-97537a8e4132',
        '74de038e-b194-4b3f-820c-6ee2689ac27f',
        '9d5308a7-3394-4f68-8cd9-60ce2d9da91f',
        '04A1B2C3D4',
        'ALLOWED',
        CURRENT_DATE + TIME '07:25:00',
        1
    ),
    (
        '00000000-0000-0000-0000-000000000302',
        '218930ff-2bf3-4f9f-867c-97537a8e4132',
        '74de038e-b194-4b3f-820c-6ee2689ac27f',
        NULL,
        'UNKNOWN-CARD-01',
        'DENIED_UNKNOWN_CARD',
        CURRENT_DATE + TIME '07:27:00',
        1
    ),
    (
        '00000000-0000-0000-0000-000000000303',
        '218930ff-2bf3-4f9f-867c-97537a8e4132',
        '74de038e-b194-4b3f-820c-6ee2689ac27f',
        '00000000-0000-0000-0000-000000000202',
        'TESTCARD0002',
        'DENIED_NO_PERMISSION',
        CURRENT_DATE + TIME '07:28:00',
        1
    ),
    (
        '00000000-0000-0000-0000-000000000304',
        '00000000-0000-0000-0000-000000000002',
        '00000000-0000-0000-0000-000000000102',
        '00000000-0000-0000-0000-000000000202',
        'TESTCARD0002',
        'ALLOWED',
        CURRENT_DATE + TIME '17:05:00',
        2
    ),
    (
        '00000000-0000-0000-0000-000000000305',
        '00000000-0000-0000-0000-000000000002',
        '00000000-0000-0000-0000-000000000102',
        NULL,
        'OFFLINE-CARD-01',
        'AUTH_DATA_NOT_READY',
        CURRENT_DATE + TIME '17:07:00',
        NULL
    );
