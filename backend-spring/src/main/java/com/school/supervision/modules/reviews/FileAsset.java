package com.school.supervision.modules.reviews;

import com.school.supervision.common.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "file_assets")
public class FileAsset extends TenantScopedEntity {
    @Column(name = "storage_key", nullable = false)
    private String storageKey;
    @Column(name = "public_url", nullable = false)
    private String publicUrl;
    @Column(name = "mime_type", nullable = false)
    private String mimeType;
    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;
    @Column(name = "checksum")
    private String checksum;
    @Column(name = "owner_type")
    private String ownerType;
    @Column(name = "owner_id")
    private UUID ownerId;

    public UUID getId() {
        return super.getId();
    }

    public void setStorageKey(String storageKey) {
        this.storageKey = storageKey;
    }

    public void setPublicUrl(String publicUrl) {
        this.publicUrl = publicUrl;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public void setOwnerType(String ownerType) {
        this.ownerType = ownerType;
    }

    public void setOwnerId(UUID ownerId) {
        this.ownerId = ownerId;
    }

    public String getPublicUrl() {
        return publicUrl;
    }

    public String getStorageKey() {
        return storageKey;
    }
}
