package com.company.bustracking.service;

import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class RouteExcelImportService {
    private static final String MASTER_DATA_SHEET = "Master Data";
    private static final int MAX_DATA_ROWS = 10_000;
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");

    private final JdbcTemplate jdbc;

    public RouteExcelImportService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public ImportResult importWorkbook(MultipartFile file, boolean commit) {
        return apply(parse(file), commit);
    }

    private ParsedWorkbook parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("An Excel file is required");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new IllegalArgumentException("Only .xlsx files are supported");
        }

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = findSheet(workbook, MASTER_DATA_SHEET);
            Header header = findHeader(sheet);
            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            List<ImportRow> rows = new ArrayList<>();
            List<ImportIssue> issues = new ArrayList<>();
            int lastRow = Math.min(sheet.getLastRowNum(), header.rowIndex() + MAX_DATA_ROWS);
            for (int index = header.rowIndex() + 1; index <= lastRow; index++) {
                Row excelRow = sheet.getRow(index);
                if (excelRow == null) continue;
                ImportRow row = new ImportRow(index + 1,
                        cellText(excelRow.getCell(header.routeCodeColumn()), formatter),
                        cellText(excelRow.getCell(header.routeNameColumn()), formatter),
                        cellText(excelRow.getCell(header.employeeNoColumn()), formatter),
                        cellText(excelRow.getCell(header.employeeNameColumn()), formatter),
                        cellText(excelRow.getCell(header.departmentColumn()), formatter));
                if (!row.isEmpty()) rows.add(row);
            }
            if (sheet.getLastRowNum() > lastRow) {
                issues.add(new ImportIssue(lastRow + 2, "ERROR", "TOO_MANY_ROWS",
                        "Only the first " + MAX_DATA_ROWS + " data rows can be imported", null, null));
            }
            if (rows.isEmpty()) {
                throw new IllegalArgumentException("The Master Data sheet has no employee route rows");
            }
            return new ParsedWorkbook(rows, issues);
        } catch (IOException exception) {
            throw new IllegalArgumentException("The Excel file could not be read", exception);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("The Excel workbook is invalid or unsupported", exception);
        }
    }

    private ImportResult apply(ParsedWorkbook parsed, boolean commit) {
        List<ImportIssue> issues = new ArrayList<>(parsed.issues());
        Map<String, RouteDraft> routeDrafts = new LinkedHashMap<>();
        List<PermissionDraft> permissionDrafts = new ArrayList<>();
        int skippedRows = 0;

        for (ImportRow row : parsed.rows()) {
            RouteDraft route = validateRoute(row, routeDrafts, issues);
            if (row.employeeNo().isBlank()) {
                issues.add(issue(row, "ERROR", "MISSING_EMPLOYEE_ID", "Employee ID is required"));
                skippedRows++;
            } else if (row.employeeNo().length() > 50) {
                issues.add(issue(row, "ERROR", "EMPLOYEE_ID_TOO_LONG", "Employee ID exceeds 50 characters"));
                skippedRows++;
            } else if (route == null) {
                skippedRows++;
            } else {
                permissionDrafts.add(new PermissionDraft(row, key(route.code()), key(row.employeeNo())));
            }
        }

        Map<String, ExistingRoute> routes = loadRoutes(routeDrafts.keySet());
        int existingRouteCount = routes.size();
        int newRouteCount = routeDrafts.size() - existingRouteCount;
        addExistingRouteWarnings(routeDrafts, routes, issues);

        Set<String> employeeKeys = new LinkedHashSet<>();
        permissionDrafts.forEach(draft -> employeeKeys.add(draft.employeeKey()));
        Map<String, ExistingEmployee> employees = loadEmployees(employeeKeys);
        Set<PermissionKey> candidates = new LinkedHashSet<>();
        Map<String, DepartmentUpdate> departmentUpdates = new LinkedHashMap<>();
        for (PermissionDraft draft : permissionDrafts) {
            ExistingEmployee employee = employees.get(draft.employeeKey());
            if (employee == null) {
                issues.add(issue(draft.row(), "ERROR", "EMPLOYEE_NOT_FOUND",
                        "Employee does not exist; CardSN is required before the employee can be created"));
                skippedRows++;
                continue;
            }
            if (!employee.active()) {
                issues.add(issue(draft.row(), "ERROR", "EMPLOYEE_INACTIVE",
                        "Inactive employees cannot be granted route permission"));
                skippedRows++;
                continue;
            }
            addEmployeeWarningsAndDepartmentUpdate(
                    draft, employee, departmentUpdates, issues);
            PermissionKey candidate = new PermissionKey(draft.routeKey(), draft.employeeKey());
            if (!candidates.add(candidate)) {
                issues.add(issue(draft.row(), "WARNING", "DUPLICATE_PERMISSION_ROW",
                        "The same employee and route appear more than once in the Excel file"));
                skippedRows++;
            }
        }

        Set<PermissionKey> existingPermissions = loadPermissions(candidates, routes);
        int existingPermissionCount = existingPermissions.size();
        int newPermissionCount = candidates.size() - existingPermissionCount;

        if (commit) {
            createMissingRoutes(routeDrafts, routes);
            applyDepartmentUpdates(departmentUpdates);
            Set<UUID> changedRoutes = new LinkedHashSet<>();
            int inserted = insertMissingPermissions(candidates, existingPermissions, routes, employees, changedRoutes);
            newPermissionCount = inserted;
            existingPermissionCount = candidates.size() - inserted;
            changedRoutes.forEach(this::bumpRouteAndAssignedBuses);
        }

        int errorCount = Math.toIntExact(issues.stream()
                .filter(item -> item.severity().equals("ERROR")).count());
        int warningCount = Math.toIntExact(issues.stream()
                .filter(item -> item.severity().equals("WARNING")).count());
        return new ImportResult(commit, parsed.rows().size(), candidates.size(), skippedRows,
                newRouteCount, existingRouteCount, newPermissionCount, existingPermissionCount,
                errorCount, warningCount, List.copyOf(issues));
    }

    private RouteDraft validateRoute(ImportRow row, Map<String, RouteDraft> routeDrafts,
            List<ImportIssue> issues) {
        boolean valid = true;
        if (row.routeCode().isBlank()) {
            issues.add(issue(row, "ERROR", "MISSING_ROUTE_CODE", "Số tuyến is required"));
            valid = false;
        } else if (row.routeCode().length() > 50) {
            issues.add(issue(row, "ERROR", "ROUTE_CODE_TOO_LONG", "Số tuyến exceeds 50 characters"));
            valid = false;
        }
        if (row.routeName().isBlank()) {
            issues.add(issue(row, "ERROR", "MISSING_ROUTE_NAME", "Bus Route is required"));
            valid = false;
        } else if (row.routeName().length() > 100) {
            issues.add(issue(row, "ERROR", "ROUTE_NAME_TOO_LONG", "Bus Route exceeds 100 characters"));
            valid = false;
        }
        if (!valid) return null;

        String routeKey = key(row.routeCode());
        RouteDraft route = routeDrafts.get(routeKey);
        if (route == null) {
            route = new RouteDraft(row.routeCode(), row.routeName());
            routeDrafts.put(routeKey, route);
        } else if (!route.name().equals(row.routeName())) {
            issues.add(issue(row, "WARNING", "ROUTE_NAME_CONFLICT",
                    "The same Số tuyến has multiple Bus Route values; the first value will be used"));
        }
        return route;
    }

    private static void addExistingRouteWarnings(Map<String, RouteDraft> drafts,
            Map<String, ExistingRoute> existingRoutes, List<ImportIssue> issues) {
        for (Map.Entry<String, RouteDraft> entry : drafts.entrySet()) {
            ExistingRoute existing = existingRoutes.get(entry.getKey());
            if (existing != null && !existing.name().equals(entry.getValue().name())) {
                issues.add(new ImportIssue(0, "WARNING", "EXISTING_ROUTE_NAME_MISMATCH",
                        "Route " + existing.code() + " already exists as '" + existing.name()
                                + "'; Excel name '" + entry.getValue().name() + "' was not applied",
                        null, existing.code()));
            }
        }
    }

    private static void addEmployeeWarningsAndDepartmentUpdate(PermissionDraft draft,
            ExistingEmployee employee, Map<String, DepartmentUpdate> departmentUpdates,
            List<ImportIssue> issues) {
        ImportRow row = draft.row();
        if (!row.employeeName().isBlank() && !employee.name().equals(row.employeeName())) {
            issues.add(issue(row, "WARNING", "EMPLOYEE_NAME_MISMATCH",
                    "Employee name differs from the existing employee and was not overwritten"));
        }
        if (!row.department().isBlank() && !employee.department().equals(row.department())) {
            if ("Unassigned".equalsIgnoreCase(employee.department())) {
                DepartmentUpdate existingUpdate = departmentUpdates.get(draft.employeeKey());
                if (existingUpdate == null) {
                    departmentUpdates.put(draft.employeeKey(),
                            new DepartmentUpdate(employee.id(), row.department()));
                    issues.add(issue(row, "WARNING", "EMPLOYEE_DEPARTMENT_WILL_UPDATE",
                            "Existing department is Unassigned; it will be overwritten by Excel Function '"
                                    + row.department() + "'"));
                } else if (!existingUpdate.department().equals(row.department())) {
                    issues.add(issue(row, "WARNING", "EMPLOYEE_FUNCTION_CONFLICT",
                            "This employee has multiple Function values; the first value '"
                                    + existingUpdate.department() + "' will be used"));
                }
            } else {
                issues.add(issue(row, "WARNING", "EMPLOYEE_DEPARTMENT_MISMATCH",
                        "Function differs from the existing department and was not overwritten"));
            }
        }
    }

    private void applyDepartmentUpdates(Map<String, DepartmentUpdate> updates) {
        for (DepartmentUpdate update : updates.values()) {
            jdbc.update("UPDATE employee SET department = ?, updated_at = now() "
                            + "WHERE id = ? AND lower(department) = lower('Unassigned')",
                    update.department(), update.employeeId());
        }
    }

    private Map<String, ExistingRoute> loadRoutes(Set<String> routeKeys) {
        Map<String, ExistingRoute> result = new HashMap<>();
        if (routeKeys.isEmpty()) return result;
        jdbc.query("SELECT id, code, name FROM route WHERE lower(code) IN ("
                        + placeholders(routeKeys.size()) + ")",
                (rs, row) -> new ExistingRoute(rs.getObject("id", UUID.class),
                        rs.getString("code"), rs.getString("name")),
                routeKeys.toArray()).forEach(route -> result.put(key(route.code()), route));
        return result;
    }

    private Map<String, ExistingEmployee> loadEmployees(Set<String> employeeKeys) {
        Map<String, ExistingEmployee> result = new HashMap<>();
        if (employeeKeys.isEmpty()) return result;
        jdbc.query("SELECT id, employee_no, name, department, active FROM employee "
                        + "WHERE lower(employee_no) IN (" + placeholders(employeeKeys.size()) + ")",
                (rs, row) -> Map.entry(key(rs.getString("employee_no")), new ExistingEmployee(
                        rs.getObject("id", UUID.class), rs.getString("name"),
                        rs.getString("department"), rs.getBoolean("active"))),
                employeeKeys.toArray()).forEach(entry -> result.put(entry.getKey(), entry.getValue()));
        return result;
    }

    private Set<PermissionKey> loadPermissions(Set<PermissionKey> candidates,
            Map<String, ExistingRoute> routes) {
        Set<PermissionKey> result = new HashSet<>();
        if (candidates.isEmpty()) return result;
        Set<UUID> routeIds = new LinkedHashSet<>();
        candidates.forEach(candidate -> {
            ExistingRoute route = routes.get(candidate.routeKey());
            if (route != null) routeIds.add(route.id());
        });
        if (routeIds.isEmpty()) return result;
        jdbc.query("""
                SELECT lower(route.code) AS route_code, lower(employee.employee_no) AS employee_no
                FROM route_employee_permission permission
                JOIN route ON route.id = permission.route_id
                JOIN employee ON employee.id = permission.employee_id
                WHERE permission.route_id IN (%s)
                """.formatted(placeholders(routeIds.size())),
                (rs, row) -> new PermissionKey(
                        rs.getString("route_code"), rs.getString("employee_no")),
                routeIds.toArray()).forEach(result::add);
        result.retainAll(candidates);
        return result;
    }

    private void createMissingRoutes(Map<String, RouteDraft> drafts, Map<String, ExistingRoute> routes) {
        for (Map.Entry<String, RouteDraft> entry : drafts.entrySet()) {
            if (routes.containsKey(entry.getKey())) continue;
            UUID id = UUID.randomUUID();
            RouteDraft draft = entry.getValue();
            jdbc.update("INSERT INTO route (id, code, name, active) VALUES (?, ?, ?, true)",
                    id, draft.code(), draft.name());
            routes.put(entry.getKey(), new ExistingRoute(id, draft.code(), draft.name()));
        }
    }

    private int insertMissingPermissions(Set<PermissionKey> candidates,
            Set<PermissionKey> existingPermissions, Map<String, ExistingRoute> routes,
            Map<String, ExistingEmployee> employees, Set<UUID> changedRoutes) {
        int inserted = 0;
        for (PermissionKey candidate : candidates) {
            if (existingPermissions.contains(candidate)) continue;
            ExistingRoute route = routes.get(candidate.routeKey());
            ExistingEmployee employee = employees.get(candidate.employeeKey());
            int changed = jdbc.update("""
                    INSERT INTO route_employee_permission (route_id, employee_id)
                    VALUES (?, ?) ON CONFLICT DO NOTHING
                    """, route.id(), employee.id());
            if (changed > 0) {
                inserted++;
                changedRoutes.add(route.id());
            }
        }
        return inserted;
    }

    private void bumpRouteAndAssignedBuses(UUID routeId) {
        jdbc.update("UPDATE route SET permission_version = permission_version + 1, "
                + "updated_at = now() WHERE id = ?", routeId);
        jdbc.update("""
                UPDATE bus SET configuration_version = configuration_version + 1, updated_at = now()
                WHERE id IN (SELECT bus_id FROM bus_route_assignment WHERE route_id = ? AND active)
                """, routeId);
    }

    private static Sheet findSheet(Workbook workbook, String expectedName) {
        for (Sheet sheet : workbook) {
            if (sheet.getSheetName().trim().equalsIgnoreCase(expectedName)) return sheet;
        }
        throw new IllegalArgumentException(
                "The workbook must contain a '" + expectedName + "' sheet");
    }

    private static Header findHeader(Sheet sheet) {
        DataFormatter formatter = new DataFormatter(Locale.ROOT);
        int end = Math.min(sheet.getLastRowNum(), 20);
        for (int rowIndex = sheet.getFirstRowNum(); rowIndex <= end; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) continue;
            Map<String, Integer> columns = new HashMap<>();
            for (Cell cell : row) {
                columns.put(headerKey(formatter.formatCellValue(cell)), cell.getColumnIndex());
            }
            Integer employeeNo = findColumn(columns, "id so id");
            Integer employeeName = findColumn(columns, "name ho ten");
            Integer department = findColumn(columns, "function");
            Integer routeCode = findColumn(columns, "so tuyen");
            Integer routeName = findColumn(columns, "bus route tuyen xe");
            if (employeeNo != null && employeeName != null && department != null
                    && routeCode != null && routeName != null) {
                return new Header(rowIndex, employeeNo, employeeName, department, routeCode, routeName);
            }
        }
        throw new IllegalArgumentException(
                "The Master Data header must contain ID, Name, Function, Số tuyến and Bus Route");
    }

    private static Integer findColumn(Map<String, Integer> columns, String expected) {
        return columns.entrySet().stream()
                .filter(entry -> entry.getKey().equals(expected)
                        || entry.getKey().startsWith(expected + " "))
                .map(Map.Entry::getValue).findFirst().orElse(null);
    }

    private static String headerKey(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .toLowerCase(Locale.ROOT);
        normalized = DIACRITICS.matcher(normalized).replaceAll("").replace('đ', 'd');
        return NON_ALPHANUMERIC.matcher(normalized).replaceAll(" ").trim();
    }

    private static String cellText(Cell cell, DataFormatter formatter) {
        if (cell == null || cell.getCellType() == CellType.BLANK) return "";
        return clean(formatter.formatCellValue(cell));
    }

    private static String clean(String value) {
        if (value == null) return "";
        String replaced = value.replace('\u00a0', ' ').replace('\u200b', ' ');
        int start = 0;
        int end = replaced.length();
        while (start < end && isWhitespace(replaced.charAt(start))) start++;
        while (end > start && isWhitespace(replaced.charAt(end - 1))) end--;
        return replaced.substring(start, end);
    }

    private static boolean isWhitespace(char value) {
        return Character.isWhitespace(value) || Character.isSpaceChar(value);
    }

    private static String key(String value) {
        return clean(value).toLowerCase(Locale.ROOT);
    }

    private static String placeholders(int count) {
        return String.join(",", Collections.nCopies(count, "?"));
    }

    private static ImportIssue issue(ImportRow row, String severity, String code, String message) {
        return new ImportIssue(row.rowNumber(), severity, code, message,
                row.employeeNo().isBlank() ? null : row.employeeNo(),
                row.routeCode().isBlank() ? null : row.routeCode());
    }

    private record Header(int rowIndex, int employeeNoColumn, int employeeNameColumn,
            int departmentColumn, int routeCodeColumn, int routeNameColumn) {}
    private record ImportRow(int rowNumber, String routeCode, String routeName, String employeeNo,
            String employeeName, String department) {
        boolean isEmpty() {
            return routeCode.isBlank() && routeName.isBlank() && employeeNo.isBlank()
                    && employeeName.isBlank() && department.isBlank();
        }
    }
    private record ParsedWorkbook(List<ImportRow> rows, List<ImportIssue> issues) {}
    private record RouteDraft(String code, String name) {}
    private record PermissionDraft(ImportRow row, String routeKey, String employeeKey) {}
    private record PermissionKey(String routeKey, String employeeKey) {}
    private record ExistingRoute(UUID id, String code, String name) {}
    private record ExistingEmployee(UUID id, String name, String department, boolean active) {}
    private record DepartmentUpdate(UUID employeeId, String department) {}

    public record ImportIssue(int rowNumber, String severity, String code, String message,
            String employeeNo, String routeCode) {}
    public record ImportResult(boolean committed, int totalRows, int validPermissionRows,
            int skippedRows, int newRouteCount, int existingRouteCount, int newPermissionCount,
            int existingPermissionCount, int errorCount, int warningCount,
            List<ImportIssue> issues) {}
}
