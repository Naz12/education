package com.school.supervision.modules.supervision;

import com.school.supervision.common.domain.DomainEnums.AssignmentStatus;
import com.school.supervision.modules.assignments.Assignment;
import com.school.supervision.modules.assignments.AssignmentRepository;
import com.school.supervision.modules.organization.SchoolRepository;
import com.school.supervision.modules.organization.TeacherRepository;
import com.school.supervision.modules.reviews.ReviewRepository;
import com.school.supervision.modules.users.User;
import com.school.supervision.modules.users.UserRepository;
import com.school.supervision.modules.users.UserRoleChecks;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class SupervisionStatsService {
    private final AssignmentRepository assignmentRepository;
    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final SchoolRepository schoolRepository;
    private final TeacherRepository teacherRepository;

    public SupervisionStatsService(AssignmentRepository assignmentRepository,
                                   ReviewRepository reviewRepository,
                                   UserRepository userRepository,
                                   SchoolRepository schoolRepository,
                                   TeacherRepository teacherRepository) {
        this.assignmentRepository = assignmentRepository;
        this.reviewRepository = reviewRepository;
        this.userRepository = userRepository;
        this.schoolRepository = schoolRepository;
        this.teacherRepository = teacherRepository;
    }

    public record MyWorkloadResponse(
            long totalAssignments,
            long completedAssignments,
            long pendingAssignments,
            long inProgressAssignments,
            long overdueAssignments,
            long visitsCompleted
    ) {}

    public record CoordinatorScopeStats(
            long supervisorsCount,
            long schoolsCount,
            long teachersCount,
            long activeAssignmentsCount
    ) {}

    public record AdminScopeStats(
            long usersCount,
            long schoolsCount,
            long supervisorsCount,
            long coordinatorsCount
    ) {}

    public MyWorkloadResponse buildWorkload(UUID supervisorId, UUID orgId) {
        List<Assignment> mine = assignmentRepository.findAllBySupervisorIdAndOrganizationId(supervisorId, orgId);
        Instant now = Instant.now();
        long pending = mine.stream().filter(a -> a.getStatus() == AssignmentStatus.PENDING).count();
        long inProgress = mine.stream().filter(a -> a.getStatus() == AssignmentStatus.IN_PROGRESS).count();
        long completed = mine.stream().filter(a -> a.getStatus() == AssignmentStatus.COMPLETED).count();
        long overdue = mine.stream()
                .filter(a -> a.getStatus() != AssignmentStatus.COMPLETED
                        && a.getDueDate() != null
                        && a.getDueDate().isBefore(now))
                .count();
        long visits = reviewRepository.countByOrganizationIdAndSupervisorIdAndCompletedAtIsNotNull(orgId, supervisorId);
        return new MyWorkloadResponse(mine.size(), completed, pending, inProgress, overdue, visits);
    }

    public CoordinatorScopeStats coordinatorScope(User coordinator, UUID orgId) {
        List<User> mySupervisors = userRepository.findAllByOrganizationIdAndCoordinatorUserId(orgId, coordinator.getId())
                .stream()
                .filter(u -> u.getRoles().stream().anyMatch(r -> "SUPERVISOR".equals(r.getName())))
                .toList();
        List<UUID> schoolIds = schoolRepository.findAllByOrganizationIdAndCoordinatorUserId(orgId, coordinator.getId())
                .stream()
                .map(s -> s.getId())
                .toList();
        long teachers = schoolIds.isEmpty()
                ? 0
                : teacherRepository.findAllByOrganizationIdAndSchoolIdIn(orgId, schoolIds).size();
        long activeAssignments = 0;
        for (User sup : mySupervisors) {
            activeAssignments += assignmentRepository.findAllBySupervisorIdAndOrganizationId(sup.getId(), orgId).stream()
                    .filter(a -> a.getStatus() == AssignmentStatus.PENDING || a.getStatus() == AssignmentStatus.IN_PROGRESS)
                    .count();
        }
        return new CoordinatorScopeStats(mySupervisors.size(), schoolIds.size(), teachers, activeAssignments);
    }

    public AdminScopeStats adminScope(UUID orgId) {
        List<User> all = userRepository.findAllByOrganizationId(orgId);
        long supervisors = all.stream().filter(u -> u.getRoles().stream().anyMatch(r -> "SUPERVISOR".equals(r.getName()))).count();
        long coordinators = all.stream().filter(u -> u.getRoles().stream().anyMatch(r -> "CLUSTER_COORDINATOR".equals(r.getName()))).count();
        long schools = schoolRepository.findAllByOrganizationId(orgId).size();
        return new AdminScopeStats(all.size(), schools, supervisors, coordinators);
    }

    public UserStatusPayload statusFor(User user, UUID orgId) {
        if (UserRoleChecks.isSupervisor(user)) {
            return new UserStatusPayload(buildWorkload(user.getId(), orgId), null, null);
        }
        if (UserRoleChecks.isCoordinator(user)) {
            return new UserStatusPayload(null, coordinatorScope(user, orgId), null);
        }
        if (UserRoleChecks.isSuperAdmin(user)) {
            return new UserStatusPayload(null, null, adminScope(orgId));
        }
        return new UserStatusPayload(null, null, null);
    }

    public record UserStatusPayload(
            MyWorkloadResponse supervisorWorkload,
            CoordinatorScopeStats coordinatorScope,
            AdminScopeStats adminScope
    ) {}
}
