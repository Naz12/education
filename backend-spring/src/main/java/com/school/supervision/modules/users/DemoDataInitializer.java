package com.school.supervision.modules.users;

import com.school.supervision.common.domain.DomainEnums;
import com.school.supervision.modules.assignments.Assignment;
import com.school.supervision.modules.assignments.AssignmentRepository;
import com.school.supervision.modules.checklists.Checklist;
import com.school.supervision.modules.checklists.ChecklistItem;
import com.school.supervision.modules.checklists.ChecklistItemRepository;
import com.school.supervision.modules.checklists.ChecklistRepository;
import com.school.supervision.modules.checklists.ChecklistVersion;
import com.school.supervision.modules.checklists.ChecklistVersionRepository;
import com.school.supervision.modules.checklists.GradeGroup;
import com.school.supervision.modules.checklists.GradeGroupRepository;
import com.school.supervision.modules.organization.School;
import com.school.supervision.modules.organization.SchoolRepository;
import com.school.supervision.modules.organization.GeographyService;
import com.school.supervision.modules.organization.Subject;
import com.school.supervision.modules.organization.SubjectRepository;
import com.school.supervision.modules.organization.Teacher;
import com.school.supervision.modules.organization.TeacherRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "app.demo-data", name = "enabled", havingValue = "true")
public class DemoDataInitializer implements ApplicationRunner {
    private static final UUID DEFAULT_ORG_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID DEFAULT_CLUSTER_ID = UUID.fromString("51111111-1111-1111-1111-111111111111");
    /** Wereda "10" under Lemi Kura / Addis Ababa from reference seed. */
    private static final UUID DEFAULT_WEREDA_ID = UUID.fromString("41111111-1111-1111-1111-111111111111");

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final SchoolRepository schoolRepository;
    private final TeacherRepository teacherRepository;
    private final SubjectRepository subjectRepository;
    private final GeographyService geographyService;
    private final ChecklistRepository checklistRepository;
    private final ChecklistVersionRepository checklistVersionRepository;
    private final ChecklistItemRepository checklistItemRepository;
    private final GradeGroupRepository gradeGroupRepository;
    private final AssignmentRepository assignmentRepository;
    private final PasswordEncoder passwordEncoder;

    public DemoDataInitializer(UserRepository userRepository,
                               RoleRepository roleRepository,
                               SchoolRepository schoolRepository,
                               TeacherRepository teacherRepository,
                               SubjectRepository subjectRepository,
                               GeographyService geographyService,
                               ChecklistRepository checklistRepository,
                               ChecklistVersionRepository checklistVersionRepository,
                               ChecklistItemRepository checklistItemRepository,
                               GradeGroupRepository gradeGroupRepository,
                               AssignmentRepository assignmentRepository,
                               PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.schoolRepository = schoolRepository;
        this.teacherRepository = teacherRepository;
        this.subjectRepository = subjectRepository;
        this.geographyService = geographyService;
        this.checklistRepository = checklistRepository;
        this.checklistVersionRepository = checklistVersionRepository;
        this.checklistItemRepository = checklistItemRepository;
        this.gradeGroupRepository = gradeGroupRepository;
        this.assignmentRepository = assignmentRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (schoolRepository.findAllByOrganizationId(DEFAULT_ORG_ID).size() >= 8) {
            return;
        }

        Role supervisorRole = roleRepository.findByOrganizationIdAndName(DEFAULT_ORG_ID, "SUPERVISOR")
                .orElseThrow(() -> new IllegalStateException("SUPERVISOR role not found"));
        Role teacherRole = roleRepository.findByOrganizationIdAndName(DEFAULT_ORG_ID, "TEACHER")
                .orElseThrow(() -> new IllegalStateException("TEACHER role not found"));
        Role directorRole = roleRepository.findByOrganizationIdAndName(DEFAULT_ORG_ID, "SCHOOL_DIRECTOR")
                .orElseThrow(() -> new IllegalStateException("SCHOOL_DIRECTOR role not found"));
        Role coordinatorRole = roleRepository.findByOrganizationIdAndName(DEFAULT_ORG_ID, "CLUSTER_COORDINATOR")
                .orElseThrow(() -> new IllegalStateException("CLUSTER_COORDINATOR role not found"));

        User superAdmin = userRepository.findByUsernameAndOrganizationId("superadmin", DEFAULT_ORG_ID)
                .orElseThrow(() -> new IllegalStateException("superadmin not found"));

        User coordinator1 = ensureUser("clustercoordinator1", "Cluster Coordinator One", "Coordinator1@12345", coordinatorRole);
        User coordinator2 = ensureUser("clustercoordinator2", "Cluster Coordinator Two", "Coordinator2@12345", coordinatorRole);
        for (User c : List.of(coordinator1, coordinator2)) {
            geographyService.applyWeredaToUser(c, DEFAULT_WEREDA_ID);
            userRepository.save(c);
        }

        User supervisor2 = ensureUser("supervisor2", "Supervisor Two", "Supervisor2@12345", supervisorRole);
        User supervisor3 = ensureUser("supervisor3", "Supervisor Three", "Supervisor3@12345", supervisorRole);
        User supervisor4 = ensureUser("supervisor4", "Supervisor Four", "Supervisor4@12345", supervisorRole);
        List<User> supervisors = List.of(
                userRepository.findByUsernameAndOrganizationId("supervisor1", DEFAULT_ORG_ID).orElseThrow(),
                supervisor2, supervisor3, supervisor4
        );

        List<User> directors = List.of(
                ensureUser("director2", "Director Two", "Director2@12345", directorRole),
                ensureUser("director3", "Director Three", "Director3@12345", directorRole),
                ensureUser("director4", "Director Four", "Director4@12345", directorRole)
        );

        List<User> teachers = List.of(
                ensureUser("teacher2", "Teacher Two", "Teacher2@12345", teacherRole),
                ensureUser("teacher3", "Teacher Three", "Teacher3@12345", teacherRole),
                ensureUser("teacher4", "Teacher Four", "Teacher4@12345", teacherRole),
                ensureUser("teacher5", "Teacher Five", "Teacher5@12345", teacherRole),
                ensureUser("teacher6", "Teacher Six", "Teacher6@12345", teacherRole)
        );

        List<School> schools = ensureSchools(directors);
        ensureTeachersForSchools(schools, teachers);
        GradeGroup defaultGrades = ensureDefaultGradeGroup();
        Checklist checklist = ensureChecklist(superAdmin.getId(), defaultGrades.getId());
        ChecklistVersion version = ensureChecklistVersion(checklist, superAdmin.getId());
        ensureChecklistItems(version);
        ensureAssignments(checklist, version, schools, supervisors, coordinator1.getId(), coordinator2.getId());
    }

    private User ensureUser(String username, String fullName, String rawPassword, Role role) {
        return userRepository.findByUsernameAndOrganizationId(username, DEFAULT_ORG_ID).orElseGet(() -> {
            User user = new User();
            user.setOrganizationId(DEFAULT_ORG_ID);
            user.setUsername(username);
            user.setFullName(fullName);
            user.setEmail(username + "@supervision.local");
            user.setPasswordHash(passwordEncoder.encode(rawPassword));
            user.getRoles().add(role);
            return userRepository.save(user);
        });
    }

    private Subject ensureSubject(String name) {
        return subjectRepository.findByOrganizationIdAndName(DEFAULT_ORG_ID, name)
                .orElseGet(() -> {
                    Subject s = new Subject();
                    s.setOrganizationId(DEFAULT_ORG_ID);
                    s.setName(name);
                    return subjectRepository.save(s);
                });
    }

    private List<School> ensureSchools(List<User> directors) {
        List<Map<String, Object>> schoolSeeds = List.of(
                Map.of("name", "Sunrise Primary School", "lat", 9.031, "lon", 38.742, "radius", 180),
                Map.of("name", "Hope Secondary School", "lat", 9.028, "lon", 38.737, "radius", 200),
                Map.of("name", "Unity Elementary School", "lat", 9.033, "lon", 38.746, "radius", 150),
                Map.of("name", "Knowledge Academy", "lat", 9.026, "lon", 38.731, "radius", 220),
                Map.of("name", "Abiyot Community School", "lat", 9.024, "lon", 38.729, "radius", 170),
                Map.of("name", "Lighthouse Preparatory", "lat", 9.036, "lon", 38.748, "radius", 160),
                Map.of("name", "Future Leaders School", "lat", 9.038, "lon", 38.751, "radius", 190)
        );

        for (int i = 0; i < schoolSeeds.size(); i++) {
            String name = (String) schoolSeeds.get(i).get("name");
            boolean exists = schoolRepository.findAllByOrganizationId(DEFAULT_ORG_ID).stream()
                    .anyMatch(s -> name.equalsIgnoreCase(s.getName()));
            if (exists) {
                continue;
            }
            School school = new School();
            school.setOrganizationId(DEFAULT_ORG_ID);
            school.setClusterId(DEFAULT_CLUSTER_ID);
            school.setName(name);
            school.setLatitude((Double) schoolSeeds.get(i).get("lat"));
            school.setLongitude((Double) schoolSeeds.get(i).get("lon"));
            school.setAllowedRadiusInMeters((Integer) schoolSeeds.get(i).get("radius"));
            if (i < directors.size()) {
                school.setDirectorUserId(directors.get(i).getId());
            }
            schoolRepository.save(school);
        }
        return schoolRepository.findAllByOrganizationId(DEFAULT_ORG_ID);
    }

    private void ensureTeachersForSchools(List<School> schools, List<User> teachers) {
        String[] subjects = {"Math", "Science", "English", "Civics", "Biology", "Physics", "Chemistry"};
        int teacherIndex = 0;
        for (int i = 0; i < schools.size(); i++) {
            School school = schools.get(i);
            long existing = teacherRepository.findAllByOrganizationId(DEFAULT_ORG_ID).stream()
                    .filter(t -> t.getSchoolId().equals(school.getId()))
                    .count();
            if (existing >= 2) {
                continue;
            }
            for (int j = 0; j < 2; j++) {
                Teacher teacher = new Teacher();
                teacher.setOrganizationId(DEFAULT_ORG_ID);
                teacher.setSchoolId(school.getId());
                User linkedUser = teachers.get(teacherIndex % teachers.size());
                teacher.setUserId(linkedUser.getId());
                teacher.setName(linkedUser.getFullName() + " - " + (j + 1));
                String subjName = subjects[(teacherIndex + j) % subjects.length];
                teacher.setSubjectId(ensureSubject(subjName).getId());
                String gg = school.getSupportedGradeCodesJson();
                if (gg == null || gg.isBlank() || "[]".equals(gg.trim())) {
                    teacher.setResponsibleGradeCodesJson("[\"1\",\"2\",\"3\",\"4\",\"5\"]");
                } else {
                    teacher.setResponsibleGradeCodesJson(gg);
                }
                teacherRepository.save(teacher);
            }
            teacherIndex++;
        }
    }

    private GradeGroup ensureDefaultGradeGroup() {
        return gradeGroupRepository.findAllByOrganizationId(DEFAULT_ORG_ID).stream()
                .filter(g -> "All primary grades".equals(g.getDisplayName()))
                .findFirst()
                .orElseGet(() -> {
                    GradeGroup g = new GradeGroup();
                    g.setOrganizationId(DEFAULT_ORG_ID);
                    g.setDisplayName("All primary grades");
                    g.setGradesDescription("Grades 1–8");
                    return gradeGroupRepository.save(g);
                });
    }

    private Checklist ensureChecklist(UUID actorUserId, UUID gradeGroupId) {
        return checklistRepository.findAllByOrganizationId(DEFAULT_ORG_ID).stream()
                .filter(c -> "School Leadership and Classroom Readiness".equals(c.getTitle()))
                .findFirst()
                .orElseGet(() -> {
                    GradeGroup gg = gradeGroupRepository.findByIdAndOrganizationId(gradeGroupId, DEFAULT_ORG_ID)
                            .orElseThrow();
                    Checklist checklist = new Checklist();
                    checklist.setOrganizationId(DEFAULT_ORG_ID);
                    checklist.setTitle("School Leadership and Classroom Readiness");
                    checklist.setTargetType(DomainEnums.TargetType.SCHOOL);
                    checklist.setDisplayMode(DomainEnums.DisplayMode.GROUPED);
                    checklist.setPurpose(DomainEnums.ChecklistPurpose.CLINICAL_SUPERVISION);
                    checklist.setGradeGroupId(gradeGroupId);
                    checklist.setGradeScope(gg.getGradesDescription());
                    checklist.setCreatedBy(actorUserId);
                    return checklistRepository.save(checklist);
                });
    }

    private ChecklistVersion ensureChecklistVersion(Checklist checklist, UUID actorUserId) {
        return checklistVersionRepository.findByChecklistIdAndVersionNoAndOrganizationId(checklist.getId(), 1, DEFAULT_ORG_ID)
                .orElseGet(() -> {
                    ChecklistVersion version = new ChecklistVersion();
                    version.setOrganizationId(DEFAULT_ORG_ID);
                    version.setChecklistId(checklist.getId());
                    version.setVersionNo(1);
                    version.setStatus(DomainEnums.ChecklistVersionStatus.PUBLISHED);
                    version.setCreatedBy(actorUserId);
                    ChecklistVersion saved = checklistVersionRepository.save(version);
                    checklist.setActiveVersion(1);
                    checklistRepository.save(checklist);
                    return saved;
                });
    }

    private void ensureChecklistItems(ChecklistVersion version) {
        if (!checklistItemRepository.findAllByChecklistVersionIdAndOrganizationIdOrderByDisplayOrder(version.getId(), DEFAULT_ORG_ID).isEmpty()) {
            return;
        }
        List<Map<String, Object>> itemSeeds = List.of(
                Map.of("q", "School gate opened on time?", "type", DomainEnums.ChecklistItemType.YES_NO, "group", "Administration", "order", 1, "opt", "{\"choices\":[\"YES\",\"NO\"]}", "val", "{\"required\":true}"),
                Map.of("q", "Teachers present before first period?", "type", DomainEnums.ChecklistItemType.YES_NO, "group", "Administration", "order", 2, "opt", "{\"choices\":[\"YES\",\"NO\"]}", "val", "{\"required\":true}"),
                Map.of("q", "Classroom cleanliness rating (1-5)", "type", DomainEnums.ChecklistItemType.RATING, "group", "Classroom", "order", 3, "opt", "{}", "val", "{\"required\":true,\"min\":1,\"max\":5}"),
                Map.of("q", "Lesson plan evidence photo", "type", DomainEnums.ChecklistItemType.PHOTO, "group", "Classroom", "order", 4, "opt", "{}", "val", "{\"required\":true}"),
                Map.of("q", "Key strengths observed", "type", DomainEnums.ChecklistItemType.TEXT, "group", "Feedback", "order", 5, "opt", "{}", "val", "{\"required\":true}"),
                Map.of("q", "Immediate action required", "type", DomainEnums.ChecklistItemType.SINGLE_CHOICE, "group", "Feedback", "order", 6, "opt", "{\"choices\":[\"None\",\"Minor\",\"Major\"]}", "val", "{\"required\":true}")
        );
        for (Map<String, Object> seed : itemSeeds) {
            ChecklistItem item = new ChecklistItem();
            item.setOrganizationId(DEFAULT_ORG_ID);
            item.setChecklistVersionId(version.getId());
            item.setQuestion((String) seed.get("q"));
            item.setItemType((DomainEnums.ChecklistItemType) seed.get("type"));
            item.setGroupKey((String) seed.get("group"));
            item.setDisplayOrder((Integer) seed.get("order"));
            item.setOptionsJson((String) seed.get("opt"));
            item.setValidationJson((String) seed.get("val"));
            checklistItemRepository.save(item);
        }
    }

    private void ensureAssignments(Checklist checklist,
                                   ChecklistVersion version,
                                   List<School> schools,
                                   List<User> supervisors,
                                   UUID coordinator1Id,
                                   UUID coordinator2Id) {
        if (assignmentRepository.findAllByOrganizationId(DEFAULT_ORG_ID).size() >= 12) {
            return;
        }
        for (int i = 0; i < 12; i++) {
            Assignment assignment = new Assignment();
            assignment.setOrganizationId(DEFAULT_ORG_ID);
            assignment.setChecklistId(checklist.getId());
            assignment.setChecklistVersionId(version.getId());
            assignment.setSupervisorId(supervisors.get(i % supervisors.size()).getId());
            assignment.setTargetType(DomainEnums.TargetType.SCHOOL);
            assignment.setSchoolId(schools.get(i % schools.size()).getId());
            assignment.setDueDate(Instant.now().plusSeconds((long) (i + 1) * 86400));
            assignment.setStatus(DomainEnums.AssignmentStatus.PENDING);
            assignment.setCreatedBy(i % 2 == 0 ? coordinator1Id : coordinator2Id);
            assignmentRepository.save(assignment);
        }
    }
}
