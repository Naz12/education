package com.school.supervision.modules.reports;

import com.school.supervision.common.tenant.TenantContext;
import com.school.supervision.modules.users.User;
import com.school.supervision.modules.users.UserRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/reports")
public class ReportController {
    private final ReportService reportService;
    private final UserRepository userRepository;

    public ReportController(ReportService reportService, UserRepository userRepository) {
        this.reportService = reportService;
        this.userRepository = userRepository;
    }

    @GetMapping("/submitted-reviews")
    public List<SubmittedReportSummary> listSubmittedReviews(Authentication authentication) {
        User user = requireCurrentUser(authentication);
        return reportService.listSubmittedReviewSummaries(user);
    }

    @GetMapping(value = "/reviews/{reviewId}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> generateReviewPdf(Authentication authentication, @PathVariable UUID reviewId) {
        User user = requireCurrentUser(authentication);
        byte[] content = reportService.generateReviewReport(reviewId, user);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=review-" + reviewId + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(content);
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
}
