CREATE TABLE device_assignment_history (
    id uuid PRIMARY KEY,
    device_id uuid NOT NULL REFERENCES device(id) ON DELETE RESTRICT,
    bus_id uuid NOT NULL REFERENCES bus(id) ON DELETE RESTRICT,
    installed_at timestamptz NOT NULL,
    removed_at timestamptz,
    CHECK (removed_at IS NULL OR removed_at >= installed_at)
);

CREATE INDEX idx_device_assignment_history_device_installed
    ON device_assignment_history(device_id, installed_at DESC);

CREATE UNIQUE INDEX uq_device_open_assignment
    ON device_assignment_history(device_id) WHERE removed_at IS NULL;

INSERT INTO device_assignment_history (id, device_id, bus_id, installed_at)
SELECT gen_random_uuid(), id, bus_id, COALESCE(created_at, now())
FROM device;
