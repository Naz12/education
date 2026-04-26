package com.school.supervision.modules.assignments;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.supervision.common.domain.DomainEnums.AssignmentStatus;
import com.school.supervision.common.domain.DomainEnums.TargetType;
import com.school.supervision.common.grades.GradeCodes;
import com.school.supervision.modules.checklists.Checklist;
import com.school.supervision.modules.checklists.ChecklistRepository;
import com.school.supervision.modules.checklists.GradeGroupRepository;
import com.school.supervision.modules.organization.School;
import com.school.supervision.modules.organization.SchoolRepository;
import com.school.supervision.modules.organization.Teacher;
import com.school.supervision.modules.organization.TeacherRepository;
import com.school.supervision.modules.users.User;
import com.school.supervision.modules.users.UserRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class AssignmentBulkService {
    private static final EnumSet<AssignmentStatus> OPEN = EnumSet.of(AssignmentStatus.PENDING, AssignmentStatus.IN_PROGRESS);

    private final AssignmentRepository assignmentRepository;
    private final ChecklistRepository checklistRepository;
    private final SchoolRepository schoolRepository;
    private final TeacherRepository teacherRepository;
    private final UserRepository userRepository;
    private final GradeGroupRepository gradeGroupRepository;
    private final ObjectMapper objectMapper;

    public AssignmentBulkService(AssignmentRepository assignmentRepository,
                                 ChecklistRepository checklistRepository,
                                 SchoolRepository schoolRepository,
                                 TeacherRepository teacherRepository,
                                 UserRepository userRepository,
                                 GradeGroupRepository gradeGroupRepository,
                                 ObjectMapper objectMapper) {
        this.assignmentRepository = assignmentRepository;
        this.checklistRepository = checklistRepository;
        this.schoolRepository = schoolRepository;
        this.teacherRepository = teacherRepository;
        this.userRepository = userRepository;
        this.gradeGroupRepository = gradeGroupRepository;
        this.objectMapper = objectMapper;
    }

    public BulkCreateResult createBulk(UUID organizationId,
                                       User actor,
                                       UUID checklistId,
                                       UUID checklistVersionId,
                                       List<UUID> requestedSchoolIds,
                                       List<UUID> requestedSupervisorIds,
                                       Instant dueDateOverride) {
        Checklist checklist = checklistRepository.findByIdAndOrganizationId(checklistId, organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Checklist not found"));
        if (checklist.getTargetType() == TargetType.SCHOOL_STAFF) {
            throw new IllegalArgumentException("Bulk assignment is not supported for SCHOOL_STAFF yet");
        }

        List<School> scopedSchools = scopedSchools(organizationId, actor);
        if (requestedSchoolIds != null && !requestedSchoolIds.isEmpty()) {
            Set<UUID> allowed = new HashSet<>(requestedSchoolIds);
            scopedSchools = scopedSchools.stream().filter(s -> allowed.contains(s.getId())).toList();
        }
        List<User> supervisors = scopedSupervisors(organizationId, actor);
        if (requestedSupervisorIds != null && !requestedSupervisorIds.isEmpty()) {
            Set<UUID> allowedSup = new HashSet<>(requestedSupervisorIds);
            supervisors = supervisors.stream().filter(s -> allowedSup.contains(s.getId())).toList();
        }

        int created = 0;
        int skippedDuplicate = 0;
        int skippedNoEligibleSupervisor = 0;
        int skippedOutOfScope = 0;

        for (School school : scopedSchools) {
            Set<String> visitScope = AssignmentGradeScope.resolve(objectMapper, school, checklist, gradeGroupRepository);
            if (visitScope.isEmpty()) {
                skippedOutOfScope++;
                continue;
            }
            if (checklist.getTargetType() == TargetType.SCHOOL || checklist.getTargetType() == TargetType.DIRECTOR) {
                if (assignmentRepository.existsByOrganizationIdAndChecklistIdAndSchoolIdAndStatusIn(
                        organizationId, checklistId, school.getId(), OPEN)) {
                    skippedDuplicate++;
                    continue;
                }
                User picked = pickSupervisor(supervisors, organizationId, visitScope, null);
                if (picked == null) {
                    skippedNoEligibleSupervisor++;
                    continue;
                }
                Assignment assignment = new Assignment();
                assignment.setOrganizationId(organizationId);
                assignment.setChecklistId(checklistId);
                assignment.setChecklistVersionId(checklistVersionId);
                assignment.setSupervisorId(picked.getId());
                assignment.setTargetType(checklist.getTargetType());
                assignment.setSchoolId(school.getId());
                assignment.setStatus(AssignmentStatus.PENDING);
                assignment.setCreatedBy(actor.getId());
                assignment.setDueDate(dueDateOverride != null ? dueDateOverride : checklist.getAutoAssignDueAt());
                assignmentRepository.save(assignment);
                created++;
                continue;
            }
            if (checklist.getTargetType() == TargetType.TEACHER) {
                List<Teacher> teachers = teacherRepository.findAllByOrganizationIdAndSchoolId(organizationId, school.getId());
                for (Teacher teacher : teachers) {
                    Set<String> teacherGrades = GradeCodes.normalize(
                            GradeCodes.parseJsonArray(objectMapper, teacher.getResponsibleGradeCodesJson()));
                    if (teacherGrades.isEmpty()) continue;
                    List<String> matched = GradeCodes.ORDERED.stream()
                            .filter(visitScope::contains)
                            .filter(teacherGrades::contains)
                            .toList();
                    for (String grade : matched) {
                        if (assignmentRepository.existsByOrganizationIdAndChecklistIdAndSchoolIdAndTeacherIdAndTargetGradeCodeAndStatusIn(
                                organizationId, checklistId, school.getId(), teacher.getId(), grade, OPEN)) {
                            skippedDuplicate++;
                            continue;
                        }
                        User picked = pickSupervisor(supervisors, organizationId, visitScope, grade);
                        if (picked == null) {
                            skippedNoEligibleSupervisor++;
                            continue;
                        }
                        Assignment assignment = new Assignment();
                        assignment.setOrganizationId(organizationId);
                        assignment.setChecklistId(checklistId);
                        assignment.setChecklistVersionId(checklistVersionId);
                        assignment.setSupervisorId(picked.getId());
                        assignment.setTargetType(TargetType.TEACHER);
                        assignment.setSchoolId(school.getId());
                        assignment.setTeacherId(teacher.getId());
                        assignment.setTargetGradeCode(grade);
                        assignment.setStatus(AssignmentStatus.PENDING);
                        assignment.setCreatedBy(actor.getId());
                        assignment.setDueDate(dueDateOverride != null ? dueDateOverride : checklist.getAutoAssignDueAt());
                        assignmentRepository.save(assignment);
                        created++;
                    }
                }
            }
        }
        return new BulkCreateResult(created, skippedDuplicate, skippedNoEligibleSupervisor, skippedOutOfScope);
    }

    private List<School> scopedSchools(UUID organizationId, User actor) {
        if (isSuperAdmin(actor)) {
            return schoolRepository.findAllByOrganizationId(organizationId);
        }
        return schoolRepository.findAllByOrganizationIdAndCoordinatorUserId(organizationId, actor.getId());
    }

    private List<User> scopedSupervisors(UUID organizationId, User actor) {
        if (isSuperAdmin(actor)) {
            return userRepository.findSupervisorsInOrganization(organizationId);
        }
        return userRepository.findSupervisorsForCoordinator(organizationId, actor.getId());
    }

    private User pickSupervisor(List<User> supervisors, UUID organizationId, Set<String> visitScope, String specificGrade) {
        List<User> eligible = supervisors.stream()
                .filter(u -> GradeCodes.overlaps(u.effectiveSupervisedGrades(objectMapper), visitScope))
                .filter(u -> specificGrade == null || u.effectiveSupervisedGrades(objectMapper).contains(specificGrade))
                .toList();
        if (eligible.isEmpty()) return null;
        return eligible.stream()
                .min(Comparator.comparingLong(u -> openAssignmentLoad(u.getId(), organizationId)))
                .orElse(null);
    }

    private long openAssignmentLoad(UUID supervisorId, UUID organizationId) {
        long pending = assignmentRepository.countBySupervisorIdAndOrganizationIdAndStatus(supervisorId, organizationId, AssignmentStatus.PENDING);
        long inProgress = assignmentRepository.countBySupervisorIdAndOrganizationIdAndStatus(supervisorId, organizationId, AssignmentStatus.IN_PROGRESS);
        return pending + inProgress;
    }

    private boolean isSuperAdmin(User user) {
        return user.getRoles().stream().anyMatch(r -> "SUPER_ADMIN".equals(r.getName()));
    }

    public record BulkCreateResult(int created,
                                   int skippedDuplicate,
                                   int skippedNoEligibleSupervisor,
                                   int skippedOutOfScope) {
    }
}
