package com.school.supervision.modules.reviews;

import com.school.supervision.common.domain.DomainEnums.LocationStatus;
import com.school.supervision.common.domain.TenantScopedEntity;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reviews")
public class Review extends TenantScopedEntity {
    @Column(name = "assignment_id", nullable = false)
    private UUID assignmentId;
    @Column(name = "supervisor_id", nullable = false)
    private UUID supervisorId;
    @Column(name = "started_at", nullable = false)
    private Instant startedAt;
    @Column(name = "completed_at")
    private Instant completedAt;
    @Column(name = "start_latitude")
    private Double startLatitude;
    @Column(name = "start_longitude")
    private Double startLongitude;
    @Column(name = "end_latitude")
    private Double endLatitude;
    @Column(name = "end_longitude")
    private Double endLongitude;
    @Column(name = "distance_from_school")
    private Double distanceFromSchool;
    @Column(name = "is_within_range")
    private Boolean isWithinRange;
    @Enumerated(EnumType.STRING)
    @Column(name = "location_status")
    private LocationStatus locationStatus;
    private String notes;

    public void setAssignmentId(UUID assignmentId) { this.assignmentId = assignmentId; }
    public void setSupervisorId(UUID supervisorId) { this.supervisorId = supervisorId; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public void setStartLatitude(Double startLatitude) { this.startLatitude = startLatitude; }
    public void setStartLongitude(Double startLongitude) { this.startLongitude = startLongitude; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public void setEndLatitude(Double endLatitude) { this.endLatitude = endLatitude; }
    public void setEndLongitude(Double endLongitude) { this.endLongitude = endLongitude; }
    public void setDistanceFromSchool(Double distanceFromSchool) { this.distanceFromSchool = distanceFromSchool; }
    public void setIsWithinRange(Boolean withinRange) { isWithinRange = withinRange; }
    public void setLocationStatus(LocationStatus locationStatus) { this.locationStatus = locationStatus; }
    public UUID getAssignmentId() { return assignmentId; }
    public UUID getSupervisorId() { return supervisorId; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public LocationStatus getLocationStatus() { return locationStatus; }
    public Double getDistanceFromSchool() { return distanceFromSchool; }
    public Boolean getWithinRange() { return isWithinRange; }
}
