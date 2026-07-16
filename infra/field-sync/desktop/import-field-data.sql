BEGIN;

CREATE TEMP TABLE field_gps_import (
    device_id uuid,
    sequence_no bigint,
    bus_id uuid,
    recorded_at timestamptz,
    longitude double precision,
    latitude double precision,
    accuracy_meters real,
    received_at timestamptz
) ON COMMIT DROP;

\copy field_gps_import FROM '/tmp/field-gps.csv' WITH (FORMAT csv, HEADER true)

WITH inserted AS (
    INSERT INTO gps_point (
        device_id, sequence_no, bus_id, recorded_at, position,
        accuracy_meters, received_at
    )
    SELECT
        device_id,
        sequence_no,
        bus_id,
        recorded_at,
        ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::geography,
        accuracy_meters,
        received_at
    FROM field_gps_import
    ON CONFLICT (device_id, sequence_no) DO NOTHING
    RETURNING 1
)
SELECT count(*) AS inserted_gps_points FROM inserted;

CREATE TEMP TABLE field_event_import (
    id uuid,
    bus_id uuid,
    device_id uuid,
    employee_id uuid,
    card_sn varchar(100),
    result varchar(40),
    scanned_at timestamptz,
    permission_version bigint,
    received_at timestamptz
) ON COMMIT DROP;

\copy field_event_import FROM '/tmp/field-boarding-events.csv' WITH (FORMAT csv, HEADER true)

WITH inserted AS (
    INSERT INTO boarding_event (
        id, bus_id, device_id, employee_id, card_sn, result,
        scanned_at, permission_version, received_at
    )
    SELECT
        id, bus_id, device_id, employee_id, card_sn, result,
        scanned_at, permission_version, received_at
    FROM field_event_import
    ON CONFLICT (id) DO NOTHING
    RETURNING 1
)
SELECT count(*) AS inserted_boarding_events FROM inserted;

COMMIT;
