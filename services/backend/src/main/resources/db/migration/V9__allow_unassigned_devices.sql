ALTER TABLE device
    ALTER COLUMN bus_id DROP NOT NULL;

ALTER TABLE device
    ADD CONSTRAINT chk_device_active_requires_bus
    CHECK (NOT active OR bus_id IS NOT NULL);
