ALTER TABLE employee
    ADD COLUMN department varchar(100) NOT NULL DEFAULT 'Unassigned';

ALTER TABLE bus
    ADD COLUMN configuration_version bigint NOT NULL DEFAULT 1 CHECK (configuration_version >= 0);

CREATE TABLE route (
    id uuid PRIMARY KEY,
    code varchar(50) NOT NULL UNIQUE,
    name varchar(100) NOT NULL,
    active boolean NOT NULL DEFAULT true,
    permission_version bigint NOT NULL DEFAULT 0 CHECK (permission_version >= 0),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE stop (
    id uuid PRIMARY KEY,
    code varchar(50) NOT NULL UNIQUE,
    name varchar(100) NOT NULL,
    latitude double precision NOT NULL CHECK (latitude BETWEEN -90 AND 90),
    longitude double precision NOT NULL CHECK (longitude BETWEEN -180 AND 180),
    radius_meters real NOT NULL DEFAULT 100 CHECK (radius_meters > 0),
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE route_stop (
    route_id uuid NOT NULL REFERENCES route(id) ON DELETE CASCADE,
    stop_id uuid NOT NULL REFERENCES stop(id) ON DELETE RESTRICT,
    stop_order integer NOT NULL CHECK (stop_order > 0),
    PRIMARY KEY (route_id, stop_id),
    UNIQUE (route_id, stop_order)
);

CREATE TABLE route_employee_permission (
    route_id uuid NOT NULL REFERENCES route(id) ON DELETE CASCADE,
    employee_id uuid NOT NULL REFERENCES employee(id) ON DELETE RESTRICT,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (route_id, employee_id)
);

CREATE INDEX idx_route_permission_employee
    ON route_employee_permission(employee_id);

CREATE TABLE bus_route_assignment (
    id uuid PRIMARY KEY,
    bus_id uuid NOT NULL REFERENCES bus(id) ON DELETE RESTRICT,
    route_id uuid NOT NULL REFERENCES route(id) ON DELETE RESTRICT,
    active boolean NOT NULL DEFAULT true,
    assigned_at timestamptz NOT NULL DEFAULT now(),
    unassigned_at timestamptz,
    CHECK (unassigned_at IS NULL OR unassigned_at >= assigned_at)
);

CREATE UNIQUE INDEX uq_bus_route_active
    ON bus_route_assignment(bus_id, route_id) WHERE active = true;

CREATE INDEX idx_bus_route_assignment_bus
    ON bus_route_assignment(bus_id, active);

CREATE TABLE device_configuration_sync (
    device_id uuid PRIMARY KEY REFERENCES device(id) ON DELETE CASCADE,
    applied_version bigint,
    applied_at timestamptz
);

ALTER TABLE boarding_event
    ADD COLUMN event_type varchar(20) NOT NULL DEFAULT 'BOARDING'
        CHECK (event_type IN ('BOARDING', 'ALIGHTING')),
    ADD COLUMN employee_no_snapshot varchar(50),
    ADD COLUMN employee_name_snapshot varchar(100),
    ADD COLUMN employee_department_snapshot varchar(100),
    ADD COLUMN latitude double precision CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
    ADD COLUMN longitude double precision CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180),
    ADD COLUMN location_recorded_at timestamptz,
    ADD COLUMN location_source varchar(20) NOT NULL DEFAULT 'UNAVAILABLE'
        CHECK (location_source IN ('CURRENT', 'SNAPSHOT', 'UNAVAILABLE')),
    ADD COLUMN accuracy_meters real CHECK (accuracy_meters IS NULL OR accuracy_meters >= 0),
    ADD COLUMN stop_id uuid REFERENCES stop(id) ON DELETE SET NULL;

CREATE TABLE boarding_event_route (
    boarding_event_id uuid NOT NULL REFERENCES boarding_event(id) ON DELETE CASCADE,
    route_id uuid NOT NULL REFERENCES route(id) ON DELETE RESTRICT,
    PRIMARY KEY (boarding_event_id, route_id)
);

UPDATE boarding_event event
SET employee_no_snapshot = employee.employee_no,
    employee_name_snapshot = employee.name,
    employee_department_snapshot = employee.department
FROM employee
WHERE event.employee_id = employee.id;

-- Preserve the old bus-based permissions as one initial route per bus.
INSERT INTO route (id, code, name, active, permission_version)
SELECT gen_random_uuid(), LEFT(code || '-LEGACY', 50), name || ' Legacy Route', active, permission_version
FROM bus;

INSERT INTO route_employee_permission (route_id, employee_id, created_at)
SELECT route.id, permission.employee_id, permission.created_at
FROM bus_employee_permission permission
JOIN bus ON bus.id = permission.bus_id
JOIN route ON route.code = LEFT(bus.code || '-LEGACY', 50);

INSERT INTO bus_route_assignment (id, bus_id, route_id, active, assigned_at)
SELECT gen_random_uuid(), bus.id, route.id, true, now()
FROM bus
JOIN route ON route.code = LEFT(bus.code || '-LEGACY', 50)
WHERE bus.active;

INSERT INTO device_configuration_sync (device_id)
SELECT id FROM device;

INSERT INTO boarding_event_route (boarding_event_id, route_id)
SELECT event.id, route.id
FROM boarding_event event
JOIN bus ON bus.id = event.bus_id
JOIN route ON route.code = LEFT(bus.code || '-LEGACY', 50);

