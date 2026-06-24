ALTER TABLE device
ADD COLUMN hardware_serial varchar(100);

UPDATE device
SET hardware_serial = CASE id
    WHEN '74de038e-b194-4b3f-820c-6ee2689ac27f'::uuid
        THEN 'QCM2290-CF8F718B'
    WHEN '00000000-0000-0000-0000-000000000102'::uuid
        THEN 'QCM2290-TEST0002'
    ELSE 'LEGACY-' || upper(replace(id::text, '-', ''))
END;

ALTER TABLE device
ALTER COLUMN hardware_serial SET NOT NULL;

ALTER TABLE device
ADD CONSTRAINT uq_device_hardware_serial UNIQUE (hardware_serial);

ALTER TABLE device
DROP CONSTRAINT IF EXISTS device_api_key_hash_key;

ALTER TABLE device
ALTER COLUMN api_key_hash DROP NOT NULL;
