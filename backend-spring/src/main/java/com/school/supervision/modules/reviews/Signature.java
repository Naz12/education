package com.school.supervision.modules.reviews;

import com.school.supervision.common.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "signatures")
public class Signature extends TenantScopedEntity {
    @Column(name = "review_id", nullable = false)
    private UUID reviewId;
    @Column(name = "signer_role", nullable = false)
    private String signerRole;
    @Column(name = "file_asset_id", nullable = false)
    private UUID fileAssetId;

    public void setReviewId(UUID reviewId) {
        this.reviewId = reviewId;
    }

    public void setSignerRole(String signerRole) {
        this.signerRole = signerRole;
    }

    public void setFileAssetId(UUID fileAssetId) {
        this.fileAssetId = fileAssetId;
    }

    public UUID getReviewId() {
        return reviewId;
    }

    public String getSignerRole() {
        return signerRole;
    }

    public UUID getFileAssetId() {
        return fileAssetId;
    }
}
