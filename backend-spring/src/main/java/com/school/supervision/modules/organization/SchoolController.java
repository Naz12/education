package com.school.supervision.modules.organization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.supervision.common.grades.GradeCodes;
import com.school.supervision.common.tenant.TenantContext;
import com.school.supervision.modules.importexport.ExcelWorkbookService;
import com.school.supervision.modules.assignments.Assignment;
import com.school.supervision.modules.assignments.AssignmentRepository;
import com.school.supervision.modules.reports.AuditService;
import com.school.supervision.modules.users.User;
import com.school.supervision.modules.users.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/schools")
public class SchoolController {
    private static final UUID DEFAULT_CLUSTER_ID = UUID.fromString("51111111-1111-1111-1111-111111111111");
    private final SchoolRepository schoolRepository;
    private final UserRepository userRepository;
    private final TeacherRepository teacherRepository;
    private final AssignmentRepository assignmentRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final ExcelWorkbookService excelWorkbookService;

    public SchoolController(SchoolRepository schoolRepository,
                             UserRepository userRepository,
                             TeacherRepository teacherRepository,
                             AssignmentRepository assignmentRepository,
                             AuditService auditService,
                             ObjectMapper objectMapper,
                             ExcelWorkbookService excelWorkbookService) {
        this.schoolRepository = schoolRepository;
        this.userRepository = userRepository;
        this.teacherRepository = teacherRepository;
        this.assignmentRepository = assignmentRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.excelWorkbookService = excelWorkbookService;
    }

    public record SchoolSummary(UUID id, String name, double latitude, double longitude, int allowedRadiusInMeters, List<String> supportedGradeCodes) {}
    public record CreateSchoolRequest(
            @NotBlank String name,
            @NotNull Double latitude,
            @NotNull Double longitude,
            Integer allowedRadiusInMeters,
            /** Canonical codes (KG1…12); empty or omitted stores []. */
            List<String> supportedGradeCodes
    ) {}

    public record UpdateSchoolRequest(
            @NotBlank String name,
            @NotNull Double latitude,
            @NotNull Double longitude,
            Integer allowedRadiusInMeters,
            /** When non-null, replaces supported grades. Omit to leave unchanged. */
            List<String> supportedGradeCodes
    ) {}

    public record BulkImportResult(int created, int skipped, int failed, List<Map<String, String>> errors) {}

    @GetMapping
    public List<SchoolSummary> list(Authentication authentication, @RequestParam(required = false) String q) {
        User current = requireCurrentUser(authentication);
        requireAdminOrCoordinator(current);
        UUID orgId = requireTenant();
        List<School> schools;
        boolean hasQ = q != null && !q.isBlank();
        if (hasQ) {
            String fragment = q.trim();
            schools = isSuperAdmin(current)
                    ? schoolRepository.findAllByOrganizationIdAndNameContainingIgnoreCase(orgId, fragment)
                    : schoolRepository.findAllByOrganizationIdAndCoordinatorUserIdAndNameContainingIgnoreCase(
                            orgId, current.getId(), fragment);
        } else {
            schools = isSuperAdmin(current)
                    ? schoolRepository.findAllByOrganizationId(orgId)
                    : schoolRepository.findAllByOrganizationIdAndCoordinatorUserId(orgId, current.getId());
        }
        return schools.stream()
                .map(s -> new SchoolSummary(
                        s.getId(),
                        s.getName(),
                        s.getLatitude(),
                        s.getLongitude(),
                        s.getAllowedRadiusInMeters(),
                        GradeCodes.sortForDisplay(GradeCodes.normalize(GradeCodes.parseJsonArray(objectMapper, s.getSupportedGradeCodesJson())))))
                .toList();
    }

    @PostMapping
    public UUID create(Authentication authentication, @Valid @RequestBody CreateSchoolRequest request) {
        User current = requireCurrentUser(authentication);
        requireAdminOrCoordinator(current);
        School school = new School();
        school.setOrganizationId(requireTenant());
        school.setClusterId(DEFAULT_CLUSTER_ID);
        school.setName(request.name());
        school.setLatitude(request.latitude());
        school.setLongitude(request.longitude());
        school.setAllowedRadiusInMeters(request.allowedRadiusInMeters() == null ? 150 : request.allowedRadiusInMeters());
        try {
            writeSupportedGrades(school, request.supportedGradeCodes());
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Could not store supported grades");
        }
        if (!isSuperAdmin(current)) {
            school.setCoordinatorUserId(current.getId());
        }
        UUID id = schoolRepository.save(school).getId();
        auditService.record(
                requireTenant(),
                current.getId(),
                "SCHOOL_CREATED",
                "SCHOOL",
                id,
                java.util.Map.of("name", request.name())
        );
        return id;
    }

    @GetMapping("/template")
    public ResponseEntity<byte[]> template(Authentication authentication) {
        User current = requireCurrentUser(authentication);
        requireAdminOrCoordinator(current);
        List<String> headers = List.of("name", "latitude", "longitude", "allowedRadiusInMeters", "supportedGradeCodes");
        List<List<String>> sample = List.of(List.of("Sample School", "9.03", "38.74", "150", "KG1,KG2,1,2"));
        List<String> notes = List.of(
                "name, latitude, longitude are required.",
                "allowedRadiusInMeters is optional (defaults to 150).",
                "supportedGradeCodes is comma-separated canonical grades: KG1,KG2,KG3,1..12."
        );
        byte[] bytes = excelWorkbookService.buildTemplate("schools", headers, sample, notes);
        return xlsxAttachment("schools-template.xlsx", bytes);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(Authentication authentication, @RequestParam(required = false) String q) {
        List<SchoolSummary> schools = list(authentication, q);
        List<String> headers = List.of("id", "name", "latitude", "longitude", "allowedRadiusInMeters", "supportedGradeCodes");
        List<List<String>> rows = schools.stream().map(s -> List.of(
                s.id().toString(),
                s.name(),
                String.valueOf(s.latitude()),
                String.valueOf(s.longitude()),
                String.valueOf(s.allowedRadiusInMeters()),
                String.join(",", s.supportedGradeCodes() == null ? List.of() : s.supportedGradeCodes())
        )).toList();
        byte[] bytes = excelWorkbookService.buildExport("schools", headers, rows);
        return xlsxAttachment("schools-export-" + LocalDate.now() + ".xlsx", bytes);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BulkImportResult bulkImport(Authentication authentication, @RequestParam("file") MultipartFile file) {
        User current = requireCurrentUser(authentication);
        requireAdminOrCoordinator(current);
        List<Map<String, String>> rows = excelWorkbookService.parseRows(file);
        int created = 0;
        int skipped = 0;
        List<Map<String, String>> errors = new ArrayList<>();
        for (Map<String, String> row : rows) {
            String rowNum = row.getOrDefault("__rowNum", "?");
            try {
                String schoolName = required(row, "name");
                if (schoolRepository.existsByOrganizationIdAndNameIgnoreCase(requireTenant(), schoolName)) {
                    skipped++;
                    continue;
                }
                CreateSchoolRequest req = new CreateSchoolRequest(
                        schoolName,
                        Double.valueOf(required(row, "latitude")),
                        Double.valueOf(required(row, "longitude")),
                        optionalInteger(row.get("allowedRadiusInMeters")),
                        splitCsv(row.getOrDefault("supportedGradeCodes", ""))
                );
                create(authentication, req);
                created++;
            } catch (Exception e) {
                errors.add(Map.of(
                        "row", rowNum,
                        "field", inferFieldFromMessage(e.getMessage()),
                        "message", safeMessage(e)
                ));
            }
        }
        return new BulkImportResult(created, skipped, errors.size(), errors);
    }

    @PatchMapping("/{schoolId}")
    public UUID update(Authentication authentication,
                        @org.springframework.web.bind.annotation.PathVariable UUID schoolId,
                        @Valid @RequestBody UpdateSchoolRequest request) {
        User current = requireCurrentUser(authentication);
        requireAdminOrCoordinator(current);
        UUID orgId = requireTenant();

        School school = isSuperAdmin(current)
                ? schoolRepository.findByIdAndOrganizationId(schoolId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("School not found"))
                : schoolRepository.findByIdAndOrganizationIdAndCoordinatorUserId(schoolId, orgId, current.getId())
                    .orElseThrow(() -> new IllegalArgumentException("School not found in your scope"));

        school.setName(request.name());
        school.setLatitude(request.latitude());
        school.setLongitude(request.longitude());
        if (request.allowedRadiusInMeters() != null) {
            school.setAllowedRadiusInMeters(request.allowedRadiusInMeters());
        }
        if (request.supportedGradeCodes() != null) {
            try {
                writeSupportedGrades(school, request.supportedGradeCodes());
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Could not store supported grades");
            }
        }
        schoolRepository.save(school);

        auditService.record(
                orgId,
                current.getId(),
                "SCHOOL_UPDATED",
                "SCHOOL",
                schoolId,
                java.util.Map.of("name", request.name())
        );
        return schoolId;
    }

    @DeleteMapping("/{schoolId}")
    public void delete(Authentication authentication,
                        @org.springframework.web.bind.annotation.PathVariable UUID schoolId) {
        User current = requireCurrentUser(authentication);
        requireAdminOrCoordinator(current);
        UUID orgId = requireTenant();

        School school = isSuperAdmin(current)
                ? schoolRepository.findByIdAndOrganizationId(schoolId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("School not found"))
                : schoolRepository.findByIdAndOrganizationIdAndCoordinatorUserId(schoolId, orgId, current.getId())
                    .orElseThrow(() -> new IllegalArgumentException("School not found in your scope"));

        // Basic safety checks to avoid breaking assignment references.
        List<Teacher> linkedTeachers = teacherRepository.findAllByOrganizationIdAndSchoolId(orgId, schoolId);
        if (linkedTeachers != null && !linkedTeachers.isEmpty()) {
            throw new IllegalArgumentException("Cannot delete school that has teachers.");
        }
        List<Assignment> allAssignments = assignmentRepository.findAllByOrganizationId(orgId);
        boolean hasSchoolAssignments = allAssignments.stream().anyMatch(a -> a.getSchoolId() != null && a.getSchoolId().equals(schoolId));
        if (hasSchoolAssignments) {
            throw new IllegalArgumentException("Cannot delete school that has assignments.");
        }

        schoolRepository.delete(school);
        auditService.record(
                orgId,
                current.getId(),
                "SCHOOL_DELETED",
                "SCHOOL",
                schoolId,
                java.util.Map.of("name", school.getName())
        );
    }

    private UUID requireTenant() {
        UUID orgId = TenantContext.getOrganizationId();
        if (orgId == null) {
            throw new IllegalStateException("Missing tenant context");
        }
        return orgId;
    }

    private User requireCurrentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new AccessDeniedException("Authentication required");
        }
        return userRepository.findByUsernameAndOrganizationId(authentication.getName(), requireTenant())
                .orElseThrow(() -> new AccessDeniedException("Current user not found"));
    }

    private void requireAdminOrCoordinator(User user) {
        boolean allowed = user.getRoles().stream()
                .anyMatch(r -> "SUPER_ADMIN".equals(r.getName()) || "CLUSTER_COORDINATOR".equals(r.getName()));
        if (!allowed) {
            throw new AccessDeniedException("Only SUPER_ADMIN or CLUSTER_COORDINATOR can manage schools");
        }
    }

    private boolean isSuperAdmin(User user) {
        return user.getRoles().stream().anyMatch(r -> "SUPER_ADMIN".equals(r.getName()));
    }

    private void writeSupportedGrades(School school, List<String> raw) throws JsonProcessingException {
        Set<String> norm = raw == null ? Set.of() : GradeCodes.normalize(raw);
        school.setSupportedGradeCodesJson(objectMapper.writeValueAsString(GradeCodes.sortForDisplay(norm)));
    }

    private ResponseEntity<byte[]> xlsxAttachment(String filename, byte[] content) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(content);
    }

    private String required(Map<String, String> row, String field) {
        String value = row.getOrDefault(field, "").trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private Integer optionalInteger(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return Integer.valueOf(raw.trim());
    }

    private List<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return java.util.Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    private String inferFieldFromMessage(String msg) {
        if (msg == null) return "row";
        String normalized = msg.toLowerCase();
        if (normalized.contains("name")) return "name";
        if (normalized.contains("latitude")) return "latitude";
        if (normalized.contains("longitude")) return "longitude";
        if (normalized.contains("radius")) return "allowedRadiusInMeters";
        if (normalized.contains("grade")) return "supportedGradeCodes";
        return "row";
    }

    private String safeMessage(Exception e) {
        return (e.getMessage() == null || e.getMessage().isBlank()) ? "Invalid row data" : e.getMessage();
    }
}
