package com.company.bustracking.service;

import com.company.bustracking.api.DeviceApi.GpsPointInput;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TrackingStore {
    private final JdbcTemplate jdbc;

    public TrackingStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public InsertResult insert(UUID deviceId, UUID busId, GpsPointInput point) {
        int inserted = jdbc.update("""
                INSERT INTO gps_point (
                    device_id, sequence_no, bus_id, recorded_at, position, accuracy_meters
                )
                VALUES (?, ?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?)
                ON CONFLICT (device_id, sequence_no) DO NOTHING
                """,
                deviceId,
                point.sequenceNo(),
                busId,
                Timestamp.from(point.recordedAt()),
                point.longitude(),
                point.latitude(),
                point.accuracyMeters());
        if (inserted == 1) {
            return InsertResult.INSERTED;
        }

        StoredPoint stored = jdbc.queryForObject("""
                SELECT recorded_at,
                       ST_Y(position::geometry) AS latitude,
                       ST_X(position::geometry) AS longitude,
                       accuracy_meters
                FROM gps_point
                WHERE device_id = ? AND sequence_no = ?
                """, this::mapStoredPoint, deviceId, point.sequenceNo());
        return stored != null && stored.sameAs(point)
                ? InsertResult.IDEMPOTENT
                : InsertResult.CONFLICT;
    }

    public List<RoutePoint> route(UUID busId, Instant from, Instant to) {
        return jdbc.query("""
                SELECT recorded_at,
                       ST_Y(position::geometry) AS latitude,
                       ST_X(position::geometry) AS longitude,
                       accuracy_meters
                FROM gps_point
                WHERE bus_id = ? AND recorded_at >= ? AND recorded_at <= ?
                ORDER BY recorded_at
                LIMIT 10000
                """, (rs, row) -> new RoutePoint(
                    rs.getTimestamp("recorded_at").toInstant(),
                    rs.getDouble("latitude"),
                    rs.getDouble("longitude"),
                    nullableFloat(rs, "accuracy_meters")),
                busId, Timestamp.from(from), Timestamp.from(to));
    }

    private StoredPoint mapStoredPoint(ResultSet rs, int row) throws SQLException {
        return new StoredPoint(
                rs.getTimestamp("recorded_at").toInstant(),
                rs.getDouble("latitude"),
                rs.getDouble("longitude"),
                nullableFloat(rs, "accuracy_meters"));
    }

    private Float nullableFloat(ResultSet rs, String column) throws SQLException {
        float value = rs.getFloat(column);
        return rs.wasNull() ? null : value;
    }

    public enum InsertResult { INSERTED, IDEMPOTENT, CONFLICT }

    private record StoredPoint(
            Instant recordedAt,
            double latitude,
            double longitude,
            Float accuracyMeters) {
        boolean sameAs(GpsPointInput point) {
            return recordedAt.equals(point.recordedAt())
                    && Double.compare(latitude, point.latitude()) == 0
                    && Double.compare(longitude, point.longitude()) == 0
                    && java.util.Objects.equals(accuracyMeters, point.accuracyMeters());
        }
    }

    public record RoutePoint(
            Instant recordedAt,
            double latitude,
            double longitude,
            Float accuracyMeters) {}
}
