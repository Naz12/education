package com.school.supervision.modules.organization;

import com.school.supervision.common.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "schools")
public class School extends TenantScopedEntity {
    @Column(name = "cluster_id", nullable = false)
    private UUID clusterId;
    @Column(name = "director_user_id")
    private UUID directorUserId;
    @Column(name = "coordinator_user_id")
    private UUID coordinatorUserId;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false)
    private double latitude;
    @Column(nullable = false)
    private double longitude;
    @Column(name = "allowed_radius_in_meters", nullable = false)
    private int allowedRadiusInMeters = 150;

    /** JSON array of canonical grade codes, e.g. ["KG1","1","2"]. */
    @Column(name = "supported_grade_codes", columnDefinition = "TEXT")
    private String supportedGradeCodesJson = "[]";

    public String getSupportedGradeCodesJson() {
        return supportedGradeCodesJson;
    }

    public void setSupportedGradeCodesJson(String supportedGradeCodesJson) {
        this.supportedGradeCodesJson = supportedGradeCodesJson == null ? "[]" : supportedGradeCodesJson;
    }

    public double getLatitude() {
        return latitude;
    }

    public String getName() {
        return name;
    }

    public UUID getClusterId() {
        return clusterId;
    }

    public UUID getDirectorUserId() {
        return directorUserId;
    }

    public void setClusterId(UUID clusterId) {
        this.clusterId = clusterId;
    }

    public void setDirectorUserId(UUID directorUserId) {
        this.directorUserId = directorUserId;
    }

    public UUID getCoordinatorUserId() {
        return coordinatorUserId;
    }

    public void setCoordinatorUserId(UUID coordinatorUserId) {
        this.coordinatorUserId = coordinatorUserId;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public int getAllowedRadiusInMeters() {
        return allowedRadiusInMeters;
    }

    public void setAllowedRadiusInMeters(int allowedRadiusInMeters) {
        this.allowedRadiusInMeters = allowedRadiusInMeters;
    }
}
