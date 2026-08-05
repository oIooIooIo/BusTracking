package com.company.bustracking;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DirtiesContext
class RouteIntegrationTests {
    private static final String BUS_ID = "218930ff-2bf3-4f9f-867c-97537a8e4132";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void busCanHaveMultipleActiveRoutesAndDeviceDownloadsTheirUnion() throws Exception {
        String createdStop = mvc.perform(post("/api/admin/v1/stops")
                        .with(httpBasic("admin", "admin123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Stop A","latitude":10.8,"longitude":106.6,
                                 "radiusMeters":100,"active":true}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code", matchesPattern("STOP-[0-9]+")))
                .andExpect(jsonPath("$.routeCount", is(0)))
                .andReturn().getResponse().getContentAsString();
        JsonNode stop = objectMapper.readTree(createdStop);

        String created = mvc.perform(post("/api/admin/v1/routes")
                        .with(httpBasic("admin", "admin123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"ROUTE-SECOND","name":"Second Route","active":true,
                                 "stopIds":["%s"]}
                                """.formatted(stop.get("id").asText())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stops[0].id", is(stop.get("id").asText())))
                .andReturn().getResponse().getContentAsString();
        JsonNode route = objectMapper.readTree(created);

        mvc.perform(get("/api/admin/v1/stops").param("q", "Stop A")
                        .with(httpBasic("admin", "admin123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].routeCount", is(1)));

        String legacyRouteId = jdbc.queryForObject("""
                SELECT route.id::text FROM route
                JOIN bus_route_assignment assignment ON assignment.route_id = route.id
                WHERE assignment.bus_id = ?::uuid AND assignment.active
                ORDER BY assignment.assigned_at LIMIT 1
                """, String.class, BUS_ID);

        mvc.perform(put("/api/admin/v1/buses/{busId}/routes", BUS_ID)
                        .with(httpBasic("admin", "admin123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"routeIds\":[\"%s\",\"%s\"]}"
                                .formatted(legacyRouteId, route.get("id").asText())))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/admin/v1/buses/{busId}/routes", BUS_ID)
                        .with(httpBasic("admin", "admin123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        mvc.perform(get("/api/device/v1/permissions")
                        .header("Authorization", "Bearer demo-device-key")
                        .header("X-Device-Hardware-Serial", "QCM2290-CF8F718B"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.routes", hasSize(2)))
                .andExpect(jsonPath("$.employees[0].department", is("Unassigned")));
    }

    @Test
    void routeExcelImportPreviewsErrorsAndCommitsValidRowsIdempotently() throws Exception {
        MockMultipartFile file = routeImportFile();

        mvc.perform(multipart("/api/admin/v1/routes/import/preview")
                        .file(file)
                        .with(httpBasic("admin", "admin123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.committed", is(false)))
                .andExpect(jsonPath("$.totalRows", is(2)))
                .andExpect(jsonPath("$.newRouteCount", is(1)))
                .andExpect(jsonPath("$.newPermissionCount", is(1)))
                .andExpect(jsonPath("$.errorCount", is(1)))
                .andExpect(jsonPath("$.issues[*].code", hasItem("EMPLOYEE_NOT_FOUND")))
                .andExpect(jsonPath("$.issues[*].code",
                        hasItem("EMPLOYEE_DEPARTMENT_WILL_UPDATE")));

        Long previewRouteCount = jdbc.queryForObject(
                "SELECT count(*) FROM route WHERE code = '99'", Long.class);
        org.junit.jupiter.api.Assertions.assertEquals(0L, previewRouteCount);
        org.junit.jupiter.api.Assertions.assertEquals("Unassigned", jdbc.queryForObject(
                "SELECT department FROM employee WHERE employee_no = 'E00201'", String.class));

        mvc.perform(multipart("/api/admin/v1/routes/import")
                        .file(routeImportFile())
                        .with(httpBasic("admin", "admin123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.committed", is(true)))
                .andExpect(jsonPath("$.newRouteCount", is(1)))
                .andExpect(jsonPath("$.newPermissionCount", is(1)))
                .andExpect(jsonPath("$.errorCount", is(1)));

        Long permissionCount = jdbc.queryForObject("""
                SELECT count(*) FROM route_employee_permission permission
                JOIN route ON route.id = permission.route_id
                JOIN employee ON employee.id = permission.employee_id
                WHERE route.code = '99' AND employee.employee_no = 'E00201'
                """, Long.class);
        org.junit.jupiter.api.Assertions.assertEquals(1L, permissionCount);
        org.junit.jupiter.api.Assertions.assertEquals("Engineering", jdbc.queryForObject(
                "SELECT department FROM employee WHERE employee_no = 'E00201'", String.class));

        mvc.perform(multipart("/api/admin/v1/routes/import")
                        .file(routeImportFile())
                        .with(httpBasic("admin", "admin123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newRouteCount", is(0)))
                .andExpect(jsonPath("$.existingRouteCount", is(1)))
                .andExpect(jsonPath("$.newPermissionCount", is(0)))
                .andExpect(jsonPath("$.existingPermissionCount", is(1)));
    }

    private static MockMultipartFile routeImportFile() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Master Data");
            var header = sheet.createRow(1);
            header.createCell(1).setCellValue("ID\n(Số ID)");
            header.createCell(2).setCellValue("Name\n(Ho Ten)");
            header.createCell(5).setCellValue("Function");
            header.createCell(7).setCellValue("Số tuyến");
            header.createCell(8).setCellValue("Bus Route\n(Tuyến Xe)");

            var existingEmployee = sheet.createRow(2);
            existingEmployee.createCell(1).setCellValue("E00201");
            existingEmployee.createCell(2).setCellValue("Nguyen An");
            existingEmployee.createCell(5).setCellValue("Engineering");
            existingEmployee.createCell(7).setCellValue(99);
            existingEmployee.createCell(8).setCellValue("HC Test");

            var missingEmployee = sheet.createRow(3);
            missingEmployee.createCell(1).setCellValue("V99999");
            missingEmployee.createCell(2).setCellValue("Missing Employee");
            missingEmployee.createCell(5).setCellValue("Engineering");
            missingEmployee.createCell(7).setCellValue(99);
            missingEmployee.createCell(8).setCellValue("HC Test");

            workbook.write(output);
            return new MockMultipartFile("file", "routes.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    output.toByteArray());
        }
    }
}
