CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE bus (
    id uuid PRIMARY KEY,
    code varchar(50) NOT NULL UNIQUE,
    name varchar(100) NOT NULL,
    active boolean NOT NULL DEFAULT true,
    permission_version bigint NOT NULL DEFAULT 0 CHECK (permission_version >= 0),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE device (
    id uuid PRIMARY KEY,
    device_code varchar(100) NOT NULL UNIQUE,
    bus_id uuid NOT NULL REFERENCES bus(id) ON DELETE RESTRICT,
    api_key_hash varchar(64) NOT NULL UNIQUE,
    active boolean NOT NULL DEFAULT true,
    last_seen_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_device_active_bus
    ON device(bus_id) WHERE active = true;

CREATE TABLE employee (
    id uuid PRIMARY KEY,
    employee_no varchar(50) NOT NULL UNIQUE,
    name varchar(100) NOT NULL,
    card_sn varchar(100) NOT NULL UNIQUE,
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE bus_employee_permission (
    bus_id uuid NOT NULL REFERENCES bus(id) ON DELETE RESTRICT,
    employee_id uuid NOT NULL REFERENCES employee(id) ON DELETE RESTRICT,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (bus_id, employee_id)
);

CREATE INDEX idx_permission_employee ON bus_employee_permission(employee_id);

CREATE TABLE gps_point (
    device_id uuid NOT NULL REFERENCES device(id) ON DELETE RESTRICT,
    sequence_no bigint NOT NULL CHECK (sequence_no > 0),
    bus_id uuid NOT NULL REFERENCES bus(id) ON DELETE RESTRICT,
    recorded_at timestamptz NOT NULL,
    position geography(Point, 4326) NOT NULL,
    accuracy_meters real CHECK (accuracy_meters IS NULL OR accuracy_meters >= 0),
    received_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (device_id, sequence_no)
);

CREATE INDEX idx_gps_point_bus_recorded_at
    ON gps_point(bus_id, recorded_at);

CREATE TABLE boarding_event (
    id uuid PRIMARY KEY,
    bus_id uuid NOT NULL REFERENCES bus(id) ON DELETE RESTRICT,
    device_id uuid NOT NULL REFERENCES device(id) ON DELETE RESTRICT,
    employee_id uuid REFERENCES employee(id) ON DELETE RESTRICT,
    card_sn varchar(100) NOT NULL,
    result varchar(40) NOT NULL CHECK (
        result IN (
            'ALLOWED',
            'DENIED_UNKNOWN_CARD',
            'DENIED_NO_PERMISSION',
            'AUTH_DATA_NOT_READY'
        )
    ),
    scanned_at timestamptz NOT NULL,
    permission_version bigint,
    received_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_boarding_event_bus_scanned_at
    ON boarding_event(bus_id, scanned_at);
