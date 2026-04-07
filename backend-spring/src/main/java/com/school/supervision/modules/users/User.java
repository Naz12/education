package com.school.supervision.modules.users;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.supervision.common.domain.TenantScopedEntity;
import com.school.supervision.common.grades.GradeCodes;
import jakarta.persistence.*;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(
        name = "users",
        uniqueConstraints = @UniqueConstraint(name = "users_org_username", columnNames = {"organization_id", "username"})
)
public class User extends TenantScopedEntity {
    @Column(nullable = false)
    private String username;
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;
    @Column(name = "full_name", nullable = false)
    private String fullName;
    private String email;
    private String phone;
    private String city;
    @Column(name = "sub_city")
    private String subCity;
    private String wereda;
    @Column(name = "city_id")
    private UUID cityId;
    @Column(name = "subcity_id")
    private UUID subcityId;
    @Column(name = "wereda_id")
    private UUID weredaId;
    @Column(name = "coordinator_user_id")
    private java.util.UUID coordinatorUserId;
    /** JSON array of grade codes this supervisor may cover; null/empty means all grades (legacy). */
    @Column(name = "supervised_grade_codes", columnDefinition = "TEXT")
    private String supervisedGradeCodesJson;
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getSubCity() {
        return subCity;
    }

    public void setSubCity(String subCity) {
        this.subCity = subCity;
    }

    public String getWereda() {
        return wereda;
    }

    public void setWereda(String wereda) {
        this.wereda = wereda;
    }

    public UUID getCityId() {
        return cityId;
    }

    public void setCityId(UUID cityId) {
        this.cityId = cityId;
    }

    public UUID getSubcityId() {
        return subcityId;
    }

    public void setSubcityId(UUID subcityId) {
        this.subcityId = subcityId;
    }

    public UUID getWeredaId() {
        return weredaId;
    }

    public void setWeredaId(UUID weredaId) {
        this.weredaId = weredaId;
    }

    public java.util.UUID getCoordinatorUserId() {
        return coordinatorUserId;
    }

    public void setCoordinatorUserId(java.util.UUID coordinatorUserId) {
        this.coordinatorUserId = coordinatorUserId;
    }

    public String getSupervisedGradeCodesJson() {
        return supervisedGradeCodesJson;
    }

    public void setSupervisedGradeCodesJson(String supervisedGradeCodesJson) {
        this.supervisedGradeCodesJson = supervisedGradeCodesJson;
    }

    /**
     * Effective grade scope for supervisors. When nothing is stored, all canonical grades are allowed (legacy rows).
     */
    public Set<String> effectiveSupervisedGrades(ObjectMapper objectMapper) {
        Set<String> normalized = GradeCodes.normalize(
                GradeCodes.parseJsonArray(objectMapper, supervisedGradeCodesJson));
        if (normalized.isEmpty()) {
            return Set.copyOf(GradeCodes.ORDERED);
        }
        return normalized;
    }
}
