package com.school.supervision.modules.reports;

import com.school.supervision.common.tenant.TenantContext;
import com.school.supervision.modules.assignments.Assignment;
import com.school.supervision.modules.assignments.AssignmentRepository;
import com.school.supervision.modules.checklists.Checklist;
import com.school.supervision.modules.checklists.ChecklistItem;
import com.school.supervision.modules.checklists.ChecklistItemRepository;
import com.school.supervision.modules.checklists.ChecklistRepository;
import com.school.supervision.modules.checklists.ChecklistVersion;
import com.school.supervision.modules.checklists.ChecklistVersionRepository;
import com.school.supervision.modules.organization.School;
import com.school.supervision.modules.organization.SchoolRepository;
import com.school.supervision.modules.organization.Teacher;
import com.school.supervision.modules.organization.TeacherRepository;
import com.school.supervision.modules.reviews.FileAsset;
import com.school.supervision.modules.reviews.FileAssetRepository;
import com.school.supervision.modules.reviews.Review;
import com.school.supervision.modules.reviews.ReviewAnswer;
import com.school.supervision.modules.reviews.ReviewAnswerRepository;
import com.school.supervision.modules.reviews.ReviewRepository;
import com.school.supervision.modules.reviews.Signature;
import com.school.supervision.modules.reviews.SignatureRepository;
import com.school.supervision.modules.users.User;
import com.school.supervision.modules.users.UserRepository;
import com.school.supervision.modules.users.UserRoleChecks;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class ReportService {
    private final ReviewRepository reviewRepository;
    private final ReviewAnswerRepository reviewAnswerRepository;
    private final SignatureRepository signatureRepository;
    private final FileAssetRepository fileAssetRepository;
    private final AssignmentRepository assignmentRepository;
    private final ChecklistRepository checklistRepository;
    private final ChecklistVersionRepository checklistVersionRepository;
    private final ChecklistItemRepository checklistItemRepository;
    private final SchoolRepository schoolRepository;
    private final TeacherRepository teacherRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public ReportService(ReviewRepository reviewRepository,
                         ReviewAnswerRepository reviewAnswerRepository,
                         SignatureRepository signatureRepository,
                         FileAssetRepository fileAssetRepository,
                         AssignmentRepository assignmentRepository,
                         ChecklistRepository checklistRepository,
                         ChecklistVersionRepository checklistVersionRepository,
                         ChecklistItemRepository checklistItemRepository,
                         SchoolRepository schoolRepository,
                         TeacherRepository teacherRepository,
                         UserRepository userRepository,
                         ObjectMapper objectMapper) {
        this.reviewRepository = reviewRepository;
        this.reviewAnswerRepository = reviewAnswerRepository;
        this.signatureRepository = signatureRepository;
        this.fileAssetRepository = fileAssetRepository;
        this.assignmentRepository = assignmentRepository;
        this.checklistRepository = checklistRepository;
        this.checklistVersionRepository = checklistVersionRepository;
        this.checklistItemRepository = checklistItemRepository;
        this.schoolRepository = schoolRepository;
        this.teacherRepository = teacherRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    public List<SubmittedReportSummary> listSubmittedReviewSummaries(User currentUser) {
        UUID orgId = requireTenant();
        if (!UserRoleChecks.isSuperAdmin(currentUser)
                && !UserRoleChecks.isCoordinator(currentUser)
                && !UserRoleChecks.isSupervisor(currentUser)) {
            throw new AccessDeniedException("Not allowed to list reports");
        }
        List<Review> reviews = reviewRepository.findAllByOrganizationIdAndCompletedAtIsNotNullOrderByCompletedAtDesc(orgId);
        Stream<Review> stream = reviews.stream();
        if (UserRoleChecks.isSuperAdmin(currentUser)) {
            // keep all
        } else if (UserRoleChecks.isCoordinator(currentUser)) {
            stream = stream.filter(r -> {
                User sup = userRepository.findByIdAndOrganizationId(r.getSupervisorId(), orgId).orElse(null);
                return sup != null && currentUser.getId().equals(sup.getCoordinatorUserId());
            });
        } else {
            stream = stream.filter(r -> r.getSupervisorId().equals(currentUser.getId()));
        }
        return stream.map(r -> toSummary(r, orgId)).toList();
    }

    private SubmittedReportSummary toSummary(Review review, UUID orgId) {
        Assignment assignment = assignmentRepository.findByIdAndOrganizationId(review.getAssignmentId(), orgId)
                .orElseThrow(() -> new IllegalStateException("Assignment missing for review"));
        User supervisor = userRepository.findByIdAndOrganizationId(review.getSupervisorId(), orgId)
                .orElse(null);
        String supName = supervisor != null ? supervisor.getFullName() : "—";
        String supUser = supervisor != null ? supervisor.getUsername() : "—";

        String schoolName = null;
        if (assignment.getSchoolId() != null) {
            schoolName = schoolRepository.findByIdAndOrganizationId(assignment.getSchoolId(), orgId)
                    .map(School::getName)
                    .orElse(null);
        }
        String teacherName = null;
        if (assignment.getTeacherId() != null) {
            teacherName = teacherRepository.findByIdAndOrganizationId(assignment.getTeacherId(), orgId)
                    .map(Teacher::getName)
                    .orElse(null);
        }
        String staffName = null;
        if (assignment.getStaffUserId() != null) {
            staffName = userRepository.findByIdAndOrganizationId(assignment.getStaffUserId(), orgId)
                    .map(User::getFullName)
                    .orElse(null);
        }
        String checklistTitle = checklistRepository.findByIdAndOrganizationId(assignment.getChecklistId(), orgId)
                .map(Checklist::getTitle)
                .orElse("—");
        String loc = review.getLocationStatus() == null ? null : review.getLocationStatus().name();

        return new SubmittedReportSummary(
                review.getId(),
                assignment.getId(),
                review.getSupervisorId(),
                supName,
                supUser,
                review.getStartedAt(),
                review.getCompletedAt(),
                assignment.getTargetType().name(),
                assignment.getSchoolId(),
                schoolName,
                assignment.getTeacherId(),
                teacherName,
                assignment.getStaffUserId(),
                staffName,
                assignment.getChecklistId(),
                checklistTitle,
                loc,
                review.getDistanceFromSchool()
        );
    }

    public byte[] generateReviewReport(UUID reviewId, User currentUser) {
        UUID orgId = requireTenant();
        Review review = reviewRepository.findByIdAndOrganizationId(reviewId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Review not found"));
        assertCanReadReview(currentUser, review, orgId);

        Assignment assignment = assignmentRepository.findByIdAndOrganizationId(review.getAssignmentId(), orgId)
                .orElseThrow(() -> new IllegalStateException("Assignment missing for review"));
        List<ReviewAnswer> answers = reviewAnswerRepository.findAllByReviewIdAndOrganizationId(reviewId, orgId);
        List<Signature> signatures = signatureRepository.findAllByReviewIdAndOrganizationId(reviewId, orgId);
        User supervisor = userRepository.findByIdAndOrganizationId(review.getSupervisorId(), orgId).orElse(null);
        Checklist checklist = checklistRepository.findByIdAndOrganizationId(assignment.getChecklistId(), orgId).orElse(null);
        ChecklistVersion checklistVersion = checklistVersionRepository
                .findByIdAndOrganizationId(assignment.getChecklistVersionId(), orgId)
                .orElse(null);
        School school = assignment.getSchoolId() == null ? null
                : schoolRepository.findByIdAndOrganizationId(assignment.getSchoolId(), orgId).orElse(null);
        Teacher teacher = assignment.getTeacherId() == null ? null
                : teacherRepository.findByIdAndOrganizationId(assignment.getTeacherId(), orgId).orElse(null);
        User staffUser = assignment.getStaffUserId() == null ? null
                : userRepository.findByIdAndOrganizationId(assignment.getStaffUserId(), orgId).orElse(null);
        User directorUser = (school != null && school.getDirectorUserId() != null)
                ? userRepository.findByIdAndOrganizationId(school.getDirectorUserId(), orgId).orElse(null)
                : null;

        List<ChecklistItem> checklistItems = checklistVersion == null
                ? List.of()
                : checklistItemRepository.findAllByChecklistVersionIdAndOrganizationIdOrderByDisplayOrder(
                checklistVersion.getId(),
                orgId
        );
        Map<UUID, ChecklistItem> itemById = new LinkedHashMap<>();
        for (ChecklistItem item : checklistItems) {
            itemById.put(item.getId(), item);
        }

        return buildPdfBytes(review, assignment, supervisor, directorUser, checklist, checklistVersion, school, teacher, staffUser, answers, signatures, itemById, orgId);
    }

    private byte[] buildPdfBytes(Review review,
                                 Assignment assignment,
                                 User supervisor,
                                 User directorUser,
                                 Checklist checklist,
                                 ChecklistVersion checklistVersion,
                                 School school,
                                 Teacher teacher,
                                 User staffUser,
                                 List<ReviewAnswer> answers,
                                 List<Signature> signatures,
                                 Map<UUID, ChecklistItem> itemById,
                                 UUID orgId) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document();
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
            Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
            Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 10);
            Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);

            document.add(new Paragraph("School Supervision Review Report", titleFont));
            document.add(new Paragraph(" "));

            PdfPTable summaryTable = new PdfPTable(2);
            summaryTable.setWidthPercentage(100);
            summaryTable.setWidths(new float[]{1.2f, 2.8f});
            summaryTable.setSpacingAfter(10f);
            addSummaryRow(summaryTable, "Review ID", review.getId().toString(), labelFont, bodyFont);
            addSummaryRow(summaryTable, "Assignment ID", assignment.getId().toString(), labelFont, bodyFont);
            addSummaryRow(summaryTable, "Supervisor", displayUser(supervisor), labelFont, bodyFont);
            addSummaryRow(summaryTable, "Director", displayUser(directorUser), labelFont, bodyFont);
            addSummaryRow(summaryTable, "Checklist", displayChecklist(checklist, checklistVersion), labelFont, bodyFont);
            addSummaryRow(summaryTable, "Target", displayTarget(assignment, school, teacher, staffUser), labelFont, bodyFont);
            addSummaryRow(summaryTable, "Started", formatInstant(review.getStartedAt()), labelFont, bodyFont);
            addSummaryRow(summaryTable, "Completed", formatInstant(review.getCompletedAt()), labelFont, bodyFont);
            addSummaryRow(summaryTable, "Location check", displayLocation(review), labelFont, bodyFont);
            addSummaryRow(summaryTable, "Total answers", String.valueOf(answers.size()), labelFont, bodyFont);
            document.add(summaryTable);

            document.add(new Paragraph("Answers", sectionFont));
            document.add(new Paragraph(" "));
            List<ReviewAnswer> orderedAnswers = answers.stream()
                    .sorted(Comparator.comparingInt(a -> Optional.ofNullable(itemById.get(a.getChecklistItemId()))
                            .map(ChecklistItem::getDisplayOrder)
                            .orElse(Integer.MAX_VALUE)))
                    .toList();
            if (orderedAnswers.isEmpty()) {
                document.add(new Paragraph("No answers submitted.", bodyFont));
            } else {
                for (int i = 0; i < orderedAnswers.size(); i++) {
                    ReviewAnswer answer = orderedAnswers.get(i);
                    ChecklistItem item = itemById.get(answer.getChecklistItemId());
                    String question = item != null ? item.getQuestion() : "Checklist item " + answer.getChecklistItemId();
                    document.add(new Paragraph((i + 1) + ". " + question, labelFont));
                    document.add(new Paragraph("Answer: " + formatAnswerJson(answer.getAnswerJson()), bodyFont));
                    document.add(new Paragraph(" "));
                }
            }

            document.add(new Paragraph("Signatures", sectionFont));
            document.add(new Paragraph(" "));
            if (signatures.isEmpty()) {
                document.add(new Paragraph("No signatures captured.", bodyFont));
            } else {
                PdfPTable signTable = new PdfPTable(3);
                signTable.setWidthPercentage(100);
                signTable.setWidths(new float[]{1.3f, 1.8f, 2.9f});
                signTable.setSpacingBefore(4f);
                signTable.setSpacingAfter(4f);
                addSignatureHeader(signTable, "Signer", labelFont);
                addSignatureHeader(signTable, "Name", labelFont);
                addSignatureHeader(signTable, "Signature", labelFont);
                for (Signature signature : signatures) {
                    String roleLabel = humanizeRole(signature.getSignerRole());
                    Optional<FileAsset> assetOpt = fileAssetRepository.findByIdAndOrganizationId(signature.getFileAssetId(), orgId);
                    addSignatureTextCell(signTable, roleLabel, bodyFont);
                    addSignatureTextCell(signTable, signerNameForRole(signature.getSignerRole(), teacher, directorUser), bodyFont);
                    if (assetOpt.isPresent()) {
                        FileAsset fileAsset = assetOpt.get();
                        String publicUrl = fileAsset.getPublicUrl();
                        Path signaturePath = tryResolveSignaturePath(publicUrl, fileAsset.getStorageKey());
                        if (signaturePath != null && Files.exists(signaturePath)) {
                            Image image = Image.getInstance(signaturePath.toAbsolutePath().toString());
                            image.scaleToFit(220f, 120f);
                            PdfPCell imageCell = new PdfPCell(image, true);
                            imageCell.setPadding(6f);
                            imageCell.setHorizontalAlignment(PdfPCell.ALIGN_LEFT);
                            signTable.addCell(imageCell);
                        } else {
                            addSignatureTextCell(signTable, "File missing (" + safeFileDisplay(publicUrl) + ")", bodyFont);
                        }
                    } else {
                        addSignatureTextCell(signTable, "N/A", bodyFont);
                    }
                }
                document.add(signTable);
            }
        } catch (DocumentException e) {
            throw new IllegalStateException("Could not build PDF", e);
        } catch (Exception e) {
            throw new IllegalStateException("Could not format report PDF", e);
        } finally {
            document.close();
        }
        return out.toByteArray();
    }

    private static void addSummaryRow(PdfPTable table, String label, String value, Font labelFont, Font bodyFont) {
        PdfPCell left = new PdfPCell(new Phrase(label, labelFont));
        left.setBorder(Rectangle.NO_BORDER);
        left.setPaddingBottom(5f);
        PdfPCell right = new PdfPCell(new Phrase(value == null || value.isBlank() ? "—" : value, bodyFont));
        right.setBorder(Rectangle.NO_BORDER);
        right.setPaddingBottom(5f);
        table.addCell(left);
        table.addCell(right);
    }

    private static String displayUser(User user) {
        if (user == null) {
            return "—";
        }
        String fullName = user.getFullName() == null || user.getFullName().isBlank() ? "—" : user.getFullName();
        String username = user.getUsername() == null || user.getUsername().isBlank() ? "—" : user.getUsername();
        return fullName + " (" + username + ")";
    }

    private static String displayChecklist(Checklist checklist, ChecklistVersion version) {
        String title = checklist == null || checklist.getTitle() == null || checklist.getTitle().isBlank()
                ? "—"
                : checklist.getTitle();
        if (version == null || version.getVersionNo() == null) {
            return title;
        }
        return title + " (v" + version.getVersionNo() + ")";
    }

    private static String displayTarget(Assignment assignment, School school, Teacher teacher, User staffUser) {
        String targetType = assignment.getTargetType() == null ? "—" : assignment.getTargetType().name();
        String schoolName = school == null || school.getName() == null || school.getName().isBlank() ? "—" : school.getName();
        String teacherName = teacher == null || teacher.getName() == null || teacher.getName().isBlank() ? null : teacher.getName();
        String staffName = staffUser == null || staffUser.getFullName() == null || staffUser.getFullName().isBlank() ? null : staffUser.getFullName();
        if (teacherName != null) {
            return targetType + ": " + teacherName + " @ " + schoolName;
        }
        if (staffName != null) {
            return targetType + ": " + staffName + " @ " + schoolName;
        }
        return targetType + ": " + schoolName;
    }

    private static String formatInstant(java.time.Instant instant) {
        if (instant == null) {
            return "—";
        }
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
        return fmt.format(instant);
    }

    private static String displayLocation(Review review) {
        String status = review.getLocationStatus() == null ? "N/A" : review.getLocationStatus().name();
        if (review.getDistanceFromSchool() == null) {
            return status;
        }
        DecimalFormat df = new DecimalFormat("#,##0.##");
        return status + " (" + df.format(review.getDistanceFromSchool()) + " m)";
    }

    private String formatAnswerJson(String json) {
        if (json == null || json.isBlank()) {
            return "—";
        }
        try {
            Object parsed = objectMapper.readValue(json, Object.class);
            if (parsed instanceof String str) {
                return str;
            }
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsed).replace("\n", " ");
        } catch (JsonProcessingException e) {
            return json;
        }
    }

    private static String humanizeRole(String role) {
        if (role == null || role.isBlank()) {
            return "Signer";
        }
        return role.replace('_', ' ');
    }

    private static Path tryResolveSignaturePath(String publicUrl, String storageKey) {
        Path byPublicUrl = tryResolveLocalFilePath(publicUrl);
        if (byPublicUrl != null) {
            return byPublicUrl;
        }
        if (storageKey != null && !storageKey.isBlank()) {
            Path repoRelative = Path.of("uploads", storageKey);
            if (Files.exists(repoRelative)) {
                return repoRelative;
            }
            Path backendRelative = Path.of("backend-spring", "uploads", storageKey);
            if (Files.exists(backendRelative)) {
                return backendRelative;
            }
        }
        return null;
    }

    private static Path tryResolveLocalFilePath(String publicUrl) {
        if (publicUrl == null || publicUrl.isBlank()) {
            return null;
        }
        try {
            if (publicUrl.startsWith("file:/")) {
                return Path.of(URI.create(publicUrl));
            }
            if (publicUrl.startsWith("C:\\") || publicUrl.startsWith("D:\\")) {
                return Path.of(publicUrl);
            }
        } catch (Exception ignored) {
            // Fallback below.
        }
        return null;
    }

    private static String safeFileDisplay(String publicUrl) {
        if (publicUrl == null || publicUrl.isBlank()) {
            return "N/A";
        }
        String tail = publicUrl;
        int slashIdx = Math.max(tail.lastIndexOf('/'), tail.lastIndexOf('\\'));
        if (slashIdx >= 0 && slashIdx < tail.length() - 1) {
            tail = tail.substring(slashIdx + 1);
        }
        return URLDecoder.decode(tail, StandardCharsets.UTF_8);
    }

    private static String signerNameForRole(String role, Teacher teacher, User directorUser) {
        if ("TEACHER".equalsIgnoreCase(role) && teacher != null && teacher.getName() != null && !teacher.getName().isBlank()) {
            return teacher.getName();
        }
        if ("SCHOOL_DIRECTOR".equalsIgnoreCase(role) && directorUser != null) {
            return displayUser(directorUser);
        }
        return "—";
    }

    private static void addSignatureHeader(PdfPTable table, String title, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(title, font));
        cell.setPadding(6f);
        table.addCell(cell);
    }

    private static void addSignatureTextCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text == null || text.isBlank() ? "—" : text, font));
        cell.setPadding(6f);
        table.addCell(cell);
    }

    private void assertCanReadReview(User current, Review review, UUID orgId) {
        if (UserRoleChecks.isSuperAdmin(current)) {
            return;
        }
        if (UserRoleChecks.isSupervisor(current) && review.getSupervisorId().equals(current.getId())) {
            return;
        }
        if (UserRoleChecks.isCoordinator(current)) {
            User sup = userRepository.findByIdAndOrganizationId(review.getSupervisorId(), orgId)
                    .orElseThrow(() -> new AccessDeniedException("Supervisor not found"));
            if (current.getId().equals(sup.getCoordinatorUserId())) {
                return;
            }
        }
        throw new AccessDeniedException("You cannot access this report");
    }

    private UUID requireTenant() {
        UUID organizationId = TenantContext.getOrganizationId();
        if (organizationId == null) {
            throw new IllegalStateException("Missing tenant context");
        }
        return organizationId;
    }
}
