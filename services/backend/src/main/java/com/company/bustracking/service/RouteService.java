package com.company.bustracking.service;

import com.company.bustracking.domain.Device;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RouteService {
    private final JdbcTemplate jdbc;

    public RouteService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<RouteView> routes() {
        return jdbc.query("""
                SELECT route.id, route.code, route.name, route.active, route.permission_version,
                       count(DISTINCT permission.employee_id) AS employee_count,
                       count(DISTINCT assignment.bus_id) FILTER (WHERE assignment.active) AS bus_count
                FROM route
                LEFT JOIN route_employee_permission permission ON permission.route_id = route.id
                LEFT JOIN bus_route_assignment assignment ON assignment.route_id = route.id
                GROUP BY route.id
                ORDER BY route.code
                """, (rs, row) -> new RouteView(
                    rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name"),
                    rs.getBoolean("active"), rs.getLong("permission_version"),
                    rs.getLong("employee_count"), rs.getLong("bus_count"),
                    stops(rs.getObject("id", UUID.class))));
    }

    @Transactional
    public RouteView create(RouteInput input) {
        validateIdentity(null, input.code());
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO route (id, code, name, active) VALUES (?, ?, ?, ?)",
                id, input.code().trim(), input.name().trim(), input.active());
        replaceStops(id, input.stopIds());
        return route(id);
    }

    @Transactional
    public RouteView update(UUID id, RouteInput input) {
        requireRoute(id);
        validateIdentity(id, input.code());
        jdbc.update("UPDATE route SET code = ?, name = ?, active = ?, updated_at = now() WHERE id = ?",
                input.code().trim(), input.name().trim(), input.active(), id);
        replaceStops(id, input.stopIds());
        bumpRouteAndAssignedBuses(id);
        return route(id);
    }

    @Transactional(readOnly = true)
    public List<EmployeeView> permissions(UUID routeId) {
        requireRoute(routeId);
        return jdbc.query("""
                SELECT employee.id, employee.employee_no, employee.name, employee.department,
                       employee.card_sn, employee.active
                FROM route_employee_permission permission
                JOIN employee ON employee.id = permission.employee_id
                WHERE permission.route_id = ?
                ORDER BY employee.employee_no
                """, (rs, row) -> new EmployeeView(
                    rs.getObject("id", UUID.class), rs.getString("employee_no"),
                    rs.getString("name"), rs.getString("department"), rs.getString("card_sn"),
                    rs.getBoolean("active")), routeId);
    }

    @Transactional
    public void grant(UUID routeId, List<UUID> employeeIds) {
        requireRoute(routeId);
        for (UUID employeeId : new LinkedHashSet<>(employeeIds)) {
            Boolean active = jdbc.query("SELECT active FROM employee WHERE id = ?",
                    rs -> rs.next() ? rs.getBoolean(1) : null, employeeId);
            if (active == null) throw new EntityNotFoundException("Employee not found: " + employeeId);
            if (!active) throw new IllegalArgumentException("Inactive employees cannot be granted boarding permission");
            jdbc.update("""
                    INSERT INTO route_employee_permission (route_id, employee_id)
                    VALUES (?, ?) ON CONFLICT DO NOTHING
                    """, routeId, employeeId);
        }
        bumpRouteAndAssignedBuses(routeId);
    }

    @Transactional
    public void revoke(UUID routeId, UUID employeeId) {
        requireRoute(routeId);
        if (jdbc.update("DELETE FROM route_employee_permission WHERE route_id = ? AND employee_id = ?",
                routeId, employeeId) > 0) {
            bumpRouteAndAssignedBuses(routeId);
        }
    }

    @Transactional(readOnly = true)
    public List<StopView> catalogStops(String query, Boolean active) {
        String pattern = "%" + (query == null ? "" : query.trim()) + "%";
        return jdbc.query("""
                SELECT stop.id, stop.code, stop.name, stop.latitude, stop.longitude,
                       stop.radius_meters, stop.active, count(route_stop.route_id) AS route_count
                FROM stop LEFT JOIN route_stop ON route_stop.stop_id = stop.id
                WHERE (CAST(? AS boolean) IS NULL OR stop.active = CAST(? AS boolean))
                  AND (stop.code ILIKE ? OR stop.name ILIKE ?)
                GROUP BY stop.id ORDER BY stop.code LIMIT 200
                """, (rs, row) -> new StopView(rs.getObject("id", UUID.class), rs.getString("code"),
                    rs.getString("name"), rs.getDouble("latitude"), rs.getDouble("longitude"),
                    rs.getFloat("radius_meters"), rs.getBoolean("active"), rs.getLong("route_count"), 0),
                active, active, pattern, pattern);
    }

    @Transactional
    public StopView createStop(StopInput input) {
        validateStop(input);
        UUID id = UUID.randomUUID();
        long sequence = jdbc.queryForObject("SELECT nextval('stop_code_seq')", Long.class);
        String code = "STOP-%06d".formatted(sequence);
        jdbc.update("INSERT INTO stop (id, code, name, latitude, longitude, radius_meters, active) VALUES (?, ?, ?, ?, ?, ?, ?)",
                id, code, input.name().trim(), input.latitude(), input.longitude(), input.radiusMeters(), input.active());
        return catalogStop(id);
    }

    @Transactional
    public StopView updateStop(UUID id, StopInput input) {
        validateStop(input);
        if (jdbc.update("UPDATE stop SET name = ?, latitude = ?, longitude = ?, radius_meters = ?, active = ?, updated_at = now() WHERE id = ?",
                input.name().trim(), input.latitude(), input.longitude(), input.radiusMeters(), input.active(), id) == 0) {
            throw new EntityNotFoundException("Stop not found: " + id);
        }
        bumpForStop(id);
        return catalogStop(id);
    }

    @Transactional(readOnly = true)
    public List<RouteSummary> stopRoutes(UUID stopId) {
        requireStop(stopId);
        return jdbc.query("SELECT route.id, route.code, route.name FROM route_stop JOIN route ON route.id = route_stop.route_id WHERE route_stop.stop_id = ? ORDER BY route.code",
                (rs, row) -> new RouteSummary(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)), stopId);
    }

    @Transactional(readOnly = true)
    public List<UUID> assignedRoutes(UUID busId) {
        return jdbc.query("""
                SELECT route_id FROM bus_route_assignment
                WHERE bus_id = ? AND active ORDER BY assigned_at, route_id
                """, (rs, row) -> rs.getObject(1, UUID.class), busId);
    }

    @Transactional
    public void assignRoutes(UUID busId, List<UUID> routeIds) {
        if (jdbc.queryForObject("SELECT count(*) FROM bus WHERE id = ?", Long.class, busId) == 0) {
            throw new EntityNotFoundException("Bus not found: " + busId);
        }
        LinkedHashSet<UUID> desired = new LinkedHashSet<>(routeIds);
        for (UUID routeId : desired) {
            requireActiveRoute(routeId);
        }
        List<UUID> current = assignedRoutes(busId);
        for (UUID routeId : current) {
            if (!desired.contains(routeId)) {
                jdbc.update("""
                        UPDATE bus_route_assignment SET active = false, unassigned_at = now()
                        WHERE bus_id = ? AND route_id = ? AND active
                        """, busId, routeId);
            }
        }
        for (UUID routeId : desired) {
            if (!current.contains(routeId)) {
                jdbc.update("""
                        INSERT INTO bus_route_assignment (id, bus_id, route_id, active)
                        VALUES (?, ?, ?, true)
                        """, UUID.randomUUID(), busId, routeId);
            }
        }
        if (!new LinkedHashSet<>(current).equals(desired)) bumpBus(busId);
    }

    @Transactional(readOnly = true)
    public DeviceConfiguration configuration(Device device) {
        UUID busId = device.getBus().getId();
        long version = jdbc.queryForObject(
                "SELECT configuration_version FROM bus WHERE id = ?", Long.class, busId);
        List<RouteSummary> routeRows = jdbc.query("""
                SELECT route.id, route.code, route.name
                FROM bus_route_assignment assignment
                JOIN route ON route.id = assignment.route_id
                WHERE assignment.bus_id = ? AND assignment.active AND route.active
                ORDER BY route.code
                """, (rs, row) -> new RouteSummary(rs.getObject("id", UUID.class),
                    rs.getString("code"), rs.getString("name")), busId);
        List<UUID> routeIds = routeRows.stream().map(RouteSummary::id).toList();
        if (routeIds.isEmpty()) return new DeviceConfiguration(version, routeRows, List.of(), List.of());

        String placeholders = String.join(",", routeIds.stream().map(id -> "?").toList());
        List<Object> args = new ArrayList<>(routeIds);
        Map<UUID, MutableEmployee> employees = new LinkedHashMap<>();
        jdbc.query("""
                SELECT employee.id, employee.employee_no, employee.name, employee.department,
                       employee.card_sn, permission.route_id
                FROM route_employee_permission permission
                JOIN employee ON employee.id = permission.employee_id
                WHERE employee.active AND permission.route_id IN (%s)
                ORDER BY employee.employee_no
                """.formatted(placeholders), rs -> {
                    UUID id = rs.getObject("id", UUID.class);
                    String employeeNo = rs.getString("employee_no");
                    String name = rs.getString("name");
                    String department = rs.getString("department");
                    String cardSn = rs.getString("card_sn");
                    MutableEmployee item = employees.computeIfAbsent(id, ignored -> new MutableEmployee(
                            id, employeeNo, name, department, cardSn));
                    item.routeIds.add(rs.getObject("route_id", UUID.class));
                }, args.toArray());
        List<PermissionEmployee> permissionRows = employees.values().stream()
                .map(item -> new PermissionEmployee(item.id, item.employeeNo, item.name,
                        item.department, item.cardSn, List.copyOf(item.routeIds)))
                .toList();
        List<StopView> stopRows = jdbc.query("""
                SELECT DISTINCT stop.id, stop.code, stop.name, stop.latitude, stop.longitude,
                       stop.radius_meters, stop.active, route_stop.stop_order
                FROM route_stop
                JOIN stop ON stop.id = route_stop.stop_id
                WHERE stop.active AND route_stop.route_id IN (%s)
                ORDER BY stop.code, route_stop.stop_order
                """.formatted(placeholders), (rs, row) -> new StopView(
                    rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name"),
                    rs.getDouble("latitude"), rs.getDouble("longitude"),
                    rs.getFloat("radius_meters"), rs.getBoolean("active"), 0, rs.getInt("stop_order")), args.toArray());
        return new DeviceConfiguration(version, routeRows, permissionRows, stopRows);
    }

    @Transactional
    public void acknowledge(Device device, long version) {
        long desired = jdbc.queryForObject("SELECT configuration_version FROM bus WHERE id = ?",
                Long.class, device.getBus().getId());
        if (version > desired) throw new IllegalArgumentException("Configuration version is newer than server version");
        jdbc.update("""
                INSERT INTO device_configuration_sync (device_id, applied_version, applied_at)
                VALUES (?, ?, now())
                ON CONFLICT (device_id) DO UPDATE
                SET applied_version = excluded.applied_version, applied_at = excluded.applied_at
                """, device.getId(), version);
    }

    @Transactional(readOnly = true)
    public SyncView sync(UUID busId) {
        return jdbc.query("""
                SELECT bus.configuration_version,
                       sync.applied_version, sync.applied_at
                FROM bus
                LEFT JOIN device ON device.bus_id = bus.id AND device.active
                LEFT JOIN device_configuration_sync sync ON sync.device_id = device.id
                WHERE bus.id = ?
                """, rs -> rs.next() ? new SyncView(rs.getLong(1),
                    (Long) rs.getObject(2), rs.getTimestamp(3) == null ? null : rs.getTimestamp(3).toInstant())
                    : new SyncView(0, null, null), busId);
    }

    @Transactional(readOnly = true)
    public List<RouteSummary> routeSummaries(UUID busId) {
        return jdbc.query("""
                SELECT route.id, route.code, route.name
                FROM bus_route_assignment assignment JOIN route ON route.id = assignment.route_id
                WHERE assignment.bus_id = ? AND assignment.active ORDER BY route.code
                """, (rs, row) -> new RouteSummary(rs.getObject(1, UUID.class),
                    rs.getString(2), rs.getString(3)), busId);
    }

    @Transactional
    public void bumpForEmployee(UUID employeeId) {
        jdbc.update("""
                UPDATE route SET permission_version = permission_version + 1, updated_at = now()
                WHERE id IN (SELECT route_id FROM route_employee_permission WHERE employee_id = ?)
                """, employeeId);
        jdbc.update("""
                UPDATE bus SET configuration_version = configuration_version + 1, updated_at = now()
                WHERE id IN (
                    SELECT DISTINCT assignment.bus_id
                    FROM bus_route_assignment assignment
                    JOIN route_employee_permission permission ON permission.route_id = assignment.route_id
                    WHERE assignment.active AND permission.employee_id = ?
                )
                """, employeeId);
    }

    @Transactional(readOnly = true)
    public List<RouteSummary> eventRoutes(UUID eventId) {
        return jdbc.query("SELECT route.id, route.code, route.name FROM boarding_event_route link JOIN route ON route.id = link.route_id WHERE link.boarding_event_id = ? ORDER BY route.code",
                (rs, row) -> new RouteSummary(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)), eventId);
    }

    @Transactional(readOnly = true)
    public String stopName(UUID stopId) {
        if (stopId == null) return null;
        return jdbc.query("SELECT name FROM stop WHERE id = ?", rs -> rs.next() ? rs.getString(1) : null, stopId);
    }

    @Transactional(readOnly = true)
    public List<UUID> matchingRoutes(UUID busId, UUID employeeId) {
        if (employeeId == null) return List.of();
        return jdbc.query("""
                SELECT assignment.route_id
                FROM bus_route_assignment assignment
                JOIN route_employee_permission permission ON permission.route_id = assignment.route_id
                JOIN route ON route.id = assignment.route_id
                WHERE assignment.bus_id = ? AND assignment.active AND route.active
                  AND permission.employee_id = ?
                """, (rs, row) -> rs.getObject(1, UUID.class), busId, employeeId);
    }

    @Transactional
    public void linkEventRoutes(UUID eventId, List<UUID> routeIds) {
        for (UUID routeId : routeIds) {
            jdbc.update("INSERT INTO boarding_event_route (boarding_event_id, route_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                    eventId, routeId);
        }
    }

    @Transactional
    public UUID nearestStop(List<UUID> routeIds, Double latitude, Double longitude,
            Float accuracyMeters, Instant scannedAt, Instant locationRecordedAt) {
        if (routeIds.isEmpty() || latitude == null || longitude == null || locationRecordedAt == null
                || accuracyMeters != null && accuracyMeters > 100
                || Math.abs(scannedAt.toEpochMilli() - locationRecordedAt.toEpochMilli()) > 30_000) {
            return null;
        }
        String placeholders = String.join(",", routeIds.stream().map(id -> "?").toList());
        List<Object> args = new ArrayList<>(routeIds);
        args.add(longitude);
        args.add(latitude);
        args.add(longitude);
        args.add(latitude);
        return jdbc.query("""
                SELECT stop.id
                FROM route_stop JOIN stop ON stop.id = route_stop.stop_id
                WHERE route_stop.route_id IN (%s) AND stop.active
                  AND ST_Distance(
                      ST_SetSRID(ST_MakePoint(stop.longitude, stop.latitude), 4326)::geography,
                      ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography
                  ) <= stop.radius_meters
                ORDER BY ST_Distance(
                    ST_SetSRID(ST_MakePoint(stop.longitude, stop.latitude), 4326)::geography,
                    ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography
                )
                LIMIT 1
                """.formatted(placeholders), rs -> rs.next() ? rs.getObject(1, UUID.class) : null,
                args.toArray());
    }

    private RouteView route(UUID id) {
        return routes().stream().filter(route -> route.id().equals(id)).findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Route not found: " + id));
    }

    private List<StopView> stops(UUID routeId) {
        return jdbc.query("""
                SELECT stop.id, stop.code, stop.name, stop.latitude, stop.longitude,
                       stop.radius_meters, stop.active, route_stop.stop_order
                FROM route_stop JOIN stop ON stop.id = route_stop.stop_id
                WHERE route_stop.route_id = ? ORDER BY route_stop.stop_order
                """, (rs, row) -> new StopView(rs.getObject("id", UUID.class), rs.getString("code"),
                    rs.getString("name"), rs.getDouble("latitude"), rs.getDouble("longitude"),
                    rs.getFloat("radius_meters"), rs.getBoolean("active"), 0, rs.getInt("stop_order")), routeId);
    }

    private void replaceStops(UUID routeId, List<UUID> stopIds) {
        LinkedHashSet<UUID> unique = new LinkedHashSet<>(stopIds);
        if (unique.size() != stopIds.size()) {
            throw new IllegalArgumentException("A stop cannot be added to the same route more than once");
        }
        List<UUID> current = jdbc.query("SELECT stop_id FROM route_stop WHERE route_id = ?",
                (rs, row) -> rs.getObject(1, UUID.class), routeId);
        for (UUID stopId : stopIds) {
            Boolean active = jdbc.query("SELECT active FROM stop WHERE id = ?",
                    rs -> rs.next() ? rs.getBoolean(1) : null, stopId);
            if (active == null) throw new EntityNotFoundException("Stop not found: " + stopId);
            if (!active && !current.contains(stopId)) {
                throw new IllegalArgumentException("Inactive stops cannot be added to a route");
            }
        }
        jdbc.update("DELETE FROM route_stop WHERE route_id = ?", routeId);
        int order = 1;
        for (UUID stopId : stopIds) {
            jdbc.update("INSERT INTO route_stop (route_id, stop_id, stop_order) VALUES (?, ?, ?)",
                    routeId, stopId, order++);
        }
    }

    private StopView catalogStop(UUID id) {
        StopView stop = jdbc.query("""
                SELECT stop.id, stop.code, stop.name, stop.latitude, stop.longitude,
                       stop.radius_meters, stop.active, count(route_stop.route_id) AS route_count
                FROM stop LEFT JOIN route_stop ON route_stop.stop_id = stop.id
                WHERE stop.id = ? GROUP BY stop.id
                """, rs -> rs.next() ? new StopView(rs.getObject("id", UUID.class), rs.getString("code"),
                    rs.getString("name"), rs.getDouble("latitude"), rs.getDouble("longitude"),
                    rs.getFloat("radius_meters"), rs.getBoolean("active"), rs.getLong("route_count"), 0) : null, id);
        if (stop == null) throw new EntityNotFoundException("Stop not found: " + id);
        return stop;
    }

    private void validateStop(StopInput input) {
        if (input.name() == null || input.name().isBlank()) throw new IllegalArgumentException("Stop name is required");
        if (input.latitude() < -90 || input.latitude() > 90) throw new IllegalArgumentException("Latitude must be between -90 and 90");
        if (input.longitude() < -180 || input.longitude() > 180) throw new IllegalArgumentException("Longitude must be between -180 and 180");
        if (input.radiusMeters() <= 0) throw new IllegalArgumentException("Stop radius must be greater than zero");
    }

    private void requireStop(UUID id) {
        Long count = jdbc.queryForObject("SELECT count(*) FROM stop WHERE id = ?", Long.class, id);
        if (count == null || count == 0) throw new EntityNotFoundException("Stop not found: " + id);
    }

    private void bumpForStop(UUID stopId) {
        jdbc.update("UPDATE route SET permission_version = permission_version + 1, updated_at = now() WHERE id IN (SELECT route_id FROM route_stop WHERE stop_id = ?)", stopId);
        jdbc.update("""
                UPDATE bus SET configuration_version = configuration_version + 1, updated_at = now()
                WHERE id IN (
                    SELECT DISTINCT assignment.bus_id FROM bus_route_assignment assignment
                    JOIN route_stop ON route_stop.route_id = assignment.route_id
                    WHERE assignment.active AND route_stop.stop_id = ?
                )
                """, stopId);
    }

    private void validateIdentity(UUID id, String code) {
        Long count = id == null
                ? jdbc.queryForObject("SELECT count(*) FROM route WHERE lower(code) = lower(?)", Long.class, code.trim())
                : jdbc.queryForObject("SELECT count(*) FROM route WHERE lower(code) = lower(?) AND id <> ?", Long.class, code.trim(), id);
        if (count != null && count > 0) throw new IllegalArgumentException("Route code already exists");
    }

    private void requireRoute(UUID id) {
        Long count = jdbc.queryForObject("SELECT count(*) FROM route WHERE id = ?", Long.class, id);
        if (count == null || count == 0) throw new EntityNotFoundException("Route not found: " + id);
    }

    private void requireActiveRoute(UUID id) {
        Boolean active = jdbc.query("SELECT active FROM route WHERE id = ?",
                rs -> rs.next() ? rs.getBoolean(1) : null, id);
        if (active == null) throw new EntityNotFoundException("Route not found: " + id);
        if (!active) throw new IllegalArgumentException("Inactive routes cannot be assigned to a bus");
    }

    private void bumpRouteAndAssignedBuses(UUID routeId) {
        jdbc.update("UPDATE route SET permission_version = permission_version + 1, updated_at = now() WHERE id = ?", routeId);
        jdbc.update("""
                UPDATE bus SET configuration_version = configuration_version + 1, updated_at = now()
                WHERE id IN (SELECT bus_id FROM bus_route_assignment WHERE route_id = ? AND active)
                """, routeId);
    }

    private void bumpBus(UUID busId) {
        jdbc.update("UPDATE bus SET configuration_version = configuration_version + 1, updated_at = now() WHERE id = ?", busId);
    }

    private static final class MutableEmployee {
        private final UUID id;
        private final String employeeNo;
        private final String name;
        private final String department;
        private final String cardSn;
        private final List<UUID> routeIds = new ArrayList<>();

        private MutableEmployee(UUID id, String employeeNo, String name, String department, String cardSn) {
            this.id = id;
            this.employeeNo = employeeNo;
            this.name = name;
            this.department = department;
            this.cardSn = cardSn;
        }
    }

    public record RouteInput(String code, String name, boolean active, List<UUID> stopIds) {
        public RouteInput { stopIds = stopIds == null ? List.of() : stopIds; }
    }
    public record StopInput(String name, double latitude, double longitude, float radiusMeters, boolean active) {}
    public record StopView(UUID id, String code, String name, double latitude, double longitude,
            float radiusMeters, boolean active, long routeCount, int order) {}
    public record RouteView(UUID id, String code, String name, boolean active, long permissionVersion,
            long employeeCount, long busCount, List<StopView> stops) {}
    public record RouteSummary(UUID id, String code, String name) {}
    public record EmployeeView(UUID id, String employeeNo, String name, String department,
            String cardSn, boolean active) {}
    public record PermissionEmployee(UUID id, String employeeNo, String name, String department,
            String cardSn, List<UUID> routeIds) {}
    public record DeviceConfiguration(long version, List<RouteSummary> routes,
            List<PermissionEmployee> employees, List<StopView> stops) {}
    public record SyncView(long desiredVersion, Long appliedVersion, Instant appliedAt) {
        public boolean synced() { return appliedVersion != null && appliedVersion == desiredVersion; }
    }
}
