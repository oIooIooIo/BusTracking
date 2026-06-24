INSERT INTO bus (id, code, name, active, permission_version)
VALUES ('218930ff-2bf3-4f9f-867c-97537a8e4132', 'BUS-01', 'Factory Bus 01', true, 1);

INSERT INTO device (id, device_code, bus_id, api_key_hash, active)
VALUES (
    '74de038e-b194-4b3f-820c-6ee2689ac27f',
    'ANDROID-DEMO-01',
    '218930ff-2bf3-4f9f-867c-97537a8e4132',
    '66c55afb13fe2d5443e0ee648a4225fd9b1b011b6bd7ce87ba7e2056e15eb38f',
    true
);

INSERT INTO employee (id, employee_no, name, card_sn, active)
VALUES (
    '9d5308a7-3394-4f68-8cd9-60ce2d9da91f',
    'E00123',
    'Demo Employee',
    '04A1B2C3D4',
    true
);

INSERT INTO bus_employee_permission (bus_id, employee_id)
VALUES (
    '218930ff-2bf3-4f9f-867c-97537a8e4132',
    '9d5308a7-3394-4f68-8cd9-60ce2d9da91f'
);
