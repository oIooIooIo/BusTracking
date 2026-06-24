package com.company.bustracking;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DirtiesContext
class ApiIntegrationTests {
    private static final String DEMO_BUS_ID = "218930ff-2bf3-4f9f-867c-97537a8e4132";
    private static final String DEMO_EMPLOYEE_ID = "9d5308a7-3394-4f68-8cd9-60ce2d9da91f";
    private static final String DEVICE_API_KEY = "demo-device-key";
    private static final String DEVICE_HARDWARE_SERIAL = "QCM2290-CF8F718B";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void adminEndpointsRequireAuthenticationAndExposeSeedData() throws Exception {
        mvc.perform(get("/api/admin/v1/buses"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/admin/v1/buses").with(httpBasic("admin", "admin123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(3))))
                .andExpect(jsonPath("$[*].code", hasItem("BUS-01")))
                .andExpect(jsonPath("$[?(@.code == 'BUS-01')].hardwareSerial",
                        hasItem(DEVICE_HARDWARE_SERIAL)))
                .andExpect(jsonPath("$[*].code", hasItem("BUS-02")))
                .andExpect(jsonPath("$[*].code", hasItem("BUS-03")));

        mvc.perform(get("/api/admin/v1/employees").with(httpBasic("admin", "admin123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(5))))
                .andExpect(jsonPath("$[*].employeeNo", hasItem("E00204")));

        mvc.perform(get("/api/admin/v1/devices").with(httpBasic("admin", "admin123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].hardwareSerial", hasItem(DEVICE_HARDWARE_SERIAL)))
                .andExpect(jsonPath("$[*].busCode", hasItem("BUS-01")));
    }

    @Test
    void creatingBusAlsoRegistersItsHardwareSerial() throws Exception {
        mvc.perform(post("/api/admin/v1/buses")
                        .with(httpBasic("admin", "admin123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "BUS-NEW",
                                  "name": "New Bus",
                                  "hardwareSerial": "qcm2290-new0001",
                                  "active": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code", is("BUS-NEW")))
                .andExpect(jsonPath("$.hardwareSerial", is("QCM2290-NEW0001")));

        mvc.perform(get("/api/admin/v1/devices").with(httpBasic("admin", "admin123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.busCode == 'BUS-NEW')].hardwareSerial",
                        hasItem("QCM2290-NEW0001")));
    }

    @Test
    void devicePermissionSnapshotReturnsOnlyActiveAllowedEmployees() throws Exception {
        mvc.perform(get("/api/device/v1/permissions")
                        .header("Authorization", "Bearer " + DEVICE_API_KEY)
                        .header("X-Device-Hardware-Serial", DEVICE_HARDWARE_SERIAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bus.code", is("BUS-01")))
                .andExpect(jsonPath("$.employees[*].id", hasItem(DEMO_EMPLOYEE_ID)))
                .andExpect(jsonPath("$.employees[*].employeeNo", hasItem("E00201")))
                .andExpect(jsonPath("$.employees[*].employeeNo").value(
                        org.hamcrest.Matchers.not(hasItem("E00204"))));
    }

    @Test
    void gpsUploadAcceptsValidAndIdempotentPointsAndRejectsBadData() throws Exception {
        String validBatch = """
                {
                  "points": [{
                    "sequenceNo": 900001,
                    "recordedAt": "2026-06-13T01:00:00Z",
                    "latitude": 10.8231,
                    "longitude": 106.6297,
                    "accuracyMeters": 5.5
                  }]
                }
                """;

        mvc.perform(post("/api/device/v1/gps-points/batch")
                        .header("Authorization", "Bearer " + DEVICE_API_KEY)
                        .header("X-Device-Hardware-Serial", DEVICE_HARDWARE_SERIAL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBatch))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acceptedSequenceNos", contains(900001)))
                .andExpect(jsonPath("$.rejected", empty()));

        mvc.perform(post("/api/device/v1/gps-points/batch")
                        .header("Authorization", "Bearer " + DEVICE_API_KEY)
                        .header("X-Device-Hardware-Serial", DEVICE_HARDWARE_SERIAL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBatch))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acceptedSequenceNos", contains(900001)))
                .andExpect(jsonPath("$.rejected", empty()));

        mvc.perform(post("/api/device/v1/gps-points/batch")
                        .header("Authorization", "Bearer " + DEVICE_API_KEY)
                        .header("X-Device-Hardware-Serial", DEVICE_HARDWARE_SERIAL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "points": [{
                                    "sequenceNo": 900002,
                                    "recordedAt": "2026-06-13T01:01:00Z",
                                    "latitude": 91,
                                    "longitude": 106.6297,
                                    "accuracyMeters": 5
                                  }]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acceptedSequenceNos", empty()))
                .andExpect(jsonPath("$.rejected[0].code", is("INVALID_LOCATION")))
                .andExpect(jsonPath("$.rejected[0].retryable", is(false)));
    }

    @Test
    void boardingUploadIsIdempotentAndDetectsEventIdConflict() throws Exception {
        String event = """
                {
                  "events": [{
                    "id": "00000000-0000-0000-0000-000000009001",
                    "cardSn": "04A1B2C3D4",
                    "employeeId": "%s",
                    "result": "ALLOWED",
                    "scannedAt": "2026-06-13T01:02:00Z",
                    "permissionVersion": 1
                  }]
                }
                """.formatted(DEMO_EMPLOYEE_ID);

        mvc.perform(post("/api/device/v1/boarding-events/batch")
                        .header("Authorization", "Bearer " + DEVICE_API_KEY)
                        .header("X-Device-Hardware-Serial", DEVICE_HARDWARE_SERIAL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(event))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acceptedIds[0]", is(
                        "00000000-0000-0000-0000-000000009001")))
                .andExpect(jsonPath("$.rejected", empty()));

        mvc.perform(post("/api/device/v1/boarding-events/batch")
                        .header("Authorization", "Bearer " + DEVICE_API_KEY)
                        .header("X-Device-Hardware-Serial", DEVICE_HARDWARE_SERIAL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(event.replace("\"ALLOWED\"", "\"DENIED_NO_PERMISSION\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acceptedIds", empty()))
                .andExpect(jsonPath("$.rejected[0].code", is("EVENT_ID_CONFLICT")));
    }

    @Test
    void adminCanQueryTodaysSeededRouteAndBoardingEvents() throws Exception {
        Instant firstPoint = jdbc.queryForObject("""
                SELECT min(recorded_at)
                FROM gps_point
                WHERE bus_id = ?::uuid
                """, Timestamp.class, DEMO_BUS_ID).toInstant();
        String from = firstPoint.minus(Duration.ofMinutes(10)).toString();
        String to = firstPoint.plus(Duration.ofHours(23)).toString();

        mvc.perform(get("/api/admin/v1/buses/{busId}/gps-points", DEMO_BUS_ID)
                        .param("from", from)
                        .param("to", to)
                        .with(httpBasic("admin", "admin123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points", hasSize(greaterThanOrEqualTo(12))));

        mvc.perform(get("/api/admin/v1/buses/{busId}/boarding-events", DEMO_BUS_ID)
                        .param("from", from)
                        .param("to", to)
                        .with(httpBasic("admin", "admin123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(3))))
                .andExpect(jsonPath("$[*].result", hasItem("ALLOWED")))
                .andExpect(jsonPath("$[*].result", hasItem("DENIED_UNKNOWN_CARD")));
    }

    @Test
    void deviceEndpointsRejectMissingOrInvalidKeys() throws Exception {
        mvc.perform(get("/api/device/v1/permissions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", is("Missing device API key")));

        mvc.perform(get("/api/device/v1/permissions")
                        .header("Authorization", "Bearer wrong-key"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", is("Invalid device API key")));

        mvc.perform(get("/api/device/v1/permissions")
                        .header("Authorization", "Bearer " + DEVICE_API_KEY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", is("Missing device hardware serial")));

        mvc.perform(get("/api/device/v1/permissions")
                        .header("Authorization", "Bearer " + DEVICE_API_KEY)
                        .header("X-Device-Hardware-Serial", "QCM2290-UNKNOWN"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", is("Unknown or inactive device")));
    }
}
