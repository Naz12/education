package com.school.supervision.modules.reviews;

import com.school.supervision.common.tenant.TenantContext;
import com.school.supervision.modules.reports.AuditService;
import com.school.supervision.modules.reports.StorageService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/reviews")
public class SignatureController {
    private final ReviewRepository reviewRepository;
    private final FileAssetRepository fileAssetRepository;
    private final SignatureRepository signatureRepository;
    private final StorageService storageService;
    private final AuditService auditService;

    public SignatureController(ReviewRepository reviewRepository,
                               FileAssetRepository fileAssetRepository,
                               SignatureRepository signatureRepository,
                               StorageService storageService,
                               AuditService auditService) {
        this.reviewRepository = reviewRepository;
        this.fileAssetRepository = fileAssetRepository;
        this.signatureRepository = signatureRepository;
        this.storageService = storageService;
        this.auditService = auditService;
    }

    public record SignatureRequest(@NotBlank String signerRole, @NotBlank String imageBase64) {}

    @PostMapping("/{reviewId}/signatures")
    public Map<String, UUID> uploadSignature(@PathVariable UUID reviewId, @RequestBody SignatureRequest request) {
        UUID orgId = requireTenant();
        reviewRepository.findByIdAndOrganizationId(reviewId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Review not found"));
        byte[] bytes = Base64.getDecoder().decode(request.imageBase64());
        String hash = sha256(bytes);
        String key = "signatures/" + orgId + "/" + reviewId + "/" + request.signerRole() + "-" + hash + ".png";
        String url = storageService.upload(key, bytes, "image/png");

        FileAsset fileAsset = new FileAsset();
        fileAsset.setOrganizationId(orgId);
        fileAsset.setStorageKey(key);
        fileAsset.setPublicUrl(url);
        fileAsset.setMimeType("image/png");
        fileAsset.setSizeBytes((long) bytes.length);
        fileAsset.setChecksum(hash);
        fileAsset.setOwnerType("REVIEW_SIGNATURE");
        fileAsset.setOwnerId(reviewId);
        fileAsset = fileAssetRepository.save(fileAsset);

        Signature signature = new Signature();
        signature.setOrganizationId(orgId);
        signature.setReviewId(reviewId);
        signature.setSignerRole(request.signerRole());
        signature.setFileAssetId(fileAsset.getId());
        signature = signatureRepository.save(signature);

        auditService.record(
                orgId,
                null,
                "SIGNATURE_SUBMITTED",
                "REVIEW_SIGNATURE",
                signature.getId(),
                Map.of("reviewId", reviewId.toString(), "signerRole", request.signerRole())
        );

        return Map.of("signatureId", signature.getId(), "fileAssetId", fileAsset.getId());
    }

    private UUID requireTenant() {
        UUID organizationId = TenantContext.getOrganizationId();
        if (organizationId == null) {
            throw new IllegalStateException("Missing tenant context");
        }
        return organizationId;
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash file", e);
        }
    }
}
