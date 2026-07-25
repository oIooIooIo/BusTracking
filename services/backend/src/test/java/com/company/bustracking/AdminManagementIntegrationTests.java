package com.company.bustracking;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DirtiesContext
class AdminManagementIntegrationTests {
    private static final String BUS_01 = "218930ff-2bf3-4f9f-867c-97537a8e4132";
    private static final String BUS_03 = "00000000-0000-0000-0000-000000000003";
    private static final String DEVICE_02 = "00000000-0000-0000-0000-000000000102";
    private static final String INACTIVE_EMPLOYEE = "00000000-0000-0000-0000-000000000204";

    @Autowired private MockMvc mvc;

    @Test
    void activeBusCannotBeRetiredUntilItsDeviceIsInactive() throws Exception {
        mvc.perform(put("/api/admin/v1/buses/{busId}", BUS_01)
                        .with(httpBasic("admin", "admin123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"BUS-01","name":"Factory Bus 01","active":false,"clearPermissions":false}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Deactivate the bus device before retiring this bus")));
    }

    @Test
    void inactiveEmployeesCannotBeGrantedInABatch() throws Exception {
        mvc.perform(post("/api/admin/v1/buses/{busId}/permissions/batch", BUS_01)
                        .with(httpBasic("admin", "admin123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"employeeIds\":[\"%s\"]}".formatted(INACTIVE_EMPLOYEE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Inactive employees cannot be granted boarding permission")));
    }

    @Test
    void reassigningDeviceCreatesHistoryWithoutMovingOldBusData() throws Exception {
        mvc.perform(put("/api/admin/v1/devices/{deviceId}", DEVICE_02)
                        .with(httpBasic("admin", "admin123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"hardwareSerial":"QCM2290-TEST0002",
                                 "busId":"00000000-0000-0000-0000-000000000002","active":false}
                                """))
                .andExpect(status().isOk());

        mvc.perform(put("/api/admin/v1/buses/{busId}", BUS_03)
                        .with(httpBasic("admin", "admin123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"BUS-03\",\"name\":\"Maintenance Spare Bus\",\"active\":true,\"clearPermissions\":false}"))
                .andExpect(status().isOk());

        mvc.perform(put("/api/admin/v1/devices/{deviceId}", DEVICE_02)
                        .with(httpBasic("admin", "admin123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"hardwareSerial":"QCM2290-TEST0002",
                                 "busId":"%s","active":true}
                                """.formatted(BUS_03)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.busCode", is("BUS-03")))
                .andExpect(jsonPath("$.deviceCode", is("ANDROID-TEST-02")));

        mvc.perform(get("/api/admin/v1/devices/{deviceId}/assignment-history", DEVICE_02)
                        .with(httpBasic("admin", "admin123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].busCode", is("BUS-03")))
                .andExpect(jsonPath("$[1].busCode", is("BUS-02")));
    }

    @Test
    void detachingDeviceClosesHistoryAndForcesItInactive() throws Exception {
        mvc.perform(put("/api/admin/v1/devices/{deviceId}", DEVICE_02)
                        .with(httpBasic("admin", "admin123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"hardwareSerial":"QCM2290-TEST0002","active":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deviceCode", is("ANDROID-TEST-02")))
                .andExpect(jsonPath("$.busId").doesNotExist())
                .andExpect(jsonPath("$.busCode").doesNotExist())
                .andExpect(jsonPath("$.active", is(false)));

        mvc.perform(get("/api/admin/v1/devices/{deviceId}/assignment-history", DEVICE_02)
                        .with(httpBasic("admin", "admin123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].busCode", is("BUS-02")))
                .andExpect(jsonPath("$[0].removedAt", notNullValue()));
    }
}
