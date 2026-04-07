package com.school.supervision.modules.checklists;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.supervision.common.domain.DomainEnums;
import com.school.supervision.common.tenant.TenantContext;
import com.school.supervision.modules.assignments.AssignmentAutoAssignmentService;
import com.school.supervision.modules.assignments.AssignmentRepository;
import com.school.supervision.modules.reviews.ReviewAnswerRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ChecklistService {
    private final ChecklistRepository checklistRepository;
    private final ChecklistVersionRepository versionRepository;
    private final ChecklistItemRepository itemRepository;
    private final GradeGroupRepository gradeGroupRepository;
    private final ChecklistTargetOptionRepository targetOptionRepository;
    private final ChecklistPurposeOptionRepository purposeOptionRepository;
    private final AssignmentRepository assignmentRepository;
    private final AssignmentAutoAssignmentService assignmentAutoAssignmentService;
    private final ReviewAnswerRepository reviewAnswerRepository;
    private final ObjectMapper objectMapper;

    public ChecklistService(ChecklistRepository checklistRepository,
                            ChecklistVersionRepository versionRepository,
                            ChecklistItemRepository itemRepository,
                            GradeGroupRepository gradeGroupRepository,
                            ChecklistTargetOptionRepository targetOptionRepository,
                            ChecklistPurposeOptionRepository purposeOptionRepository,
                            AssignmentRepository assignmentRepository,
                            AssignmentAutoAssignmentService assignmentAutoAssignmentService,
                            ReviewAnswerRepository reviewAnswerRepository,
                            ObjectMapper objectMapper) {
        this.checklistRepository = checklistRepository;
        this.versionRepository = versionRepository;
        this.itemRepository = itemRepository;
        this.gradeGroupRepository = gradeGroupRepository;
        this.targetOptionRepository = targetOptionRepository;
        this.purposeOptionRepository = purposeOptionRepository;
        this.assignmentRepository = assignmentRepository;
        this.assignmentAutoAssignmentService = assignmentAutoAssignmentService;
        this.reviewAnswerRepository = reviewAnswerRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public UUID createChecklist(ChecklistDtos.CreateChecklistRequest request, UUID actorUserId, UUID coordinatorUserId) {
        Checklist checklist = new Checklist();
        checklist.setOrganizationId(requireTenant());
        set(checklist, request, actorUserId, coordinatorUserId);
        return checklistRepository.save(checklist).getId();
    }

    @Transactional
    public UUID publishVersion(UUID checklistId, ChecklistDtos.PublishVersionRequest request, UUID actorUserId) {
        UUID orgId = requireTenant();
        Checklist checklist = checklistRepository.findByIdAndOrganizationId(checklistId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Checklist not found"));
        int nextVersion = Optional.ofNullable(checklist.getActiveVersion()).orElse(0) + 1;
        ChecklistVersion version = new ChecklistVersion();
        version.setOrganizationId(orgId);
        set(version, checklistId, nextVersion, actorUserId);
        ChecklistVersion savedVersion = versionRepository.save(version);

        for (ChecklistDtos.ItemRequest itemRequest : request.items()) {
            ChecklistItem item = new ChecklistItem();
            item.setOrganizationId(orgId);
            set(item, savedVersion.getId(), itemRequest);
            itemRepository.save(item);
        }
        checklist.setActiveVersion(nextVersion);
        checklistRepository.save(checklist);
        if (!Boolean.TRUE.equals(request.skipAutoAssignment())) {
            assignmentAutoAssignmentService.assignAfterPublish(orgId, checklist, savedVersion.getId(), actorUserId);
        }
        return savedVersion.getId();
    }

    public ChecklistDtos.RenderResponse render(UUID checklistId, String lang) {
        UUID orgId = requireTenant();
        Checklist checklist = checklistRepository.findByIdAndOrganizationId(checklistId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Checklist not found"));
        ChecklistVersion version = versionRepository.findByChecklistIdAndVersionNoAndOrganizationId(
                        checklistId, checklist.getActiveVersion(), orgId)
                .orElseThrow(() -> new IllegalArgumentException("Checklist version not found"));
        return renderChecklistAndVersion(checklist, version, lang);
    }

    /**
     * Renders a specific published checklist version (used for assignments pinned to that version).
     */
    public ChecklistDtos.RenderResponse renderChecklistVersion(UUID checklistVersionId, String lang) {
        UUID orgId = requireTenant();
        ChecklistVersion version = versionRepository.findByIdAndOrganizationId(checklistVersionId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Checklist version not found"));
        Checklist checklist = checklistRepository.findByIdAndOrganizationId(version.getChecklistId(), orgId)
                .orElseThrow(() -> new IllegalArgumentException("Checklist not found"));
        return renderChecklistAndVersion(checklist, version, lang);
    }

    private ChecklistDtos.RenderResponse renderChecklistAndVersion(Checklist checklist, ChecklistVersion version, String lang) {
        UUID orgId = requireTenant();
        String effectiveLang = (lang == null || lang.isBlank()) ? "en" : lang;
        List<ChecklistDtos.RenderItem> items = itemRepository
                .findAllByChecklistVersionIdAndOrganizationIdOrderByDisplayOrder(version.getId(), orgId)
                .stream()
                .map(i -> new ChecklistDtos.RenderItem(
                        i.getId(),
                        localizedQuestion(i.getQuestion(), i.getQuestionLocalizedJson(), effectiveLang),
                        readJson(i.getQuestionLocalizedJson()),
                        i.getItemType().name(),
                        localizedOptions(i.getOptionsJson(), effectiveLang),
                        readJson(i.getValidationJson()),
                        i.getGroupKey(),
                        i.getDisplayOrder()))
                .toList();
        return new ChecklistDtos.RenderResponse(checklist.getId(), version.getVersionNo(), checklist.getDisplayMode().name(), items);
    }

    @Transactional
    public void updateChecklist(UUID checklistId, ChecklistDtos.UpdateChecklistRequest request, UUID actorUserId, UUID coordinatorUserId) {
        UUID orgId = requireTenant();
        Checklist checklist = checklistRepository.findByIdAndOrganizationId(checklistId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Checklist not found"));

        // If grade scope changes, the existing published items may no longer match what the UI expects.
        // We invalidate the currently-enabled version so the coordinator can republish.
        GradeGroup gradeGroup = gradeGroupRepository.findByIdAndOrganizationId(request.gradeGroupId(), orgId)
                .orElseThrow(() -> new IllegalArgumentException("Grade group not found"));
        assertGradeGroupInCoordinatorScope(gradeGroup, coordinatorUserId);

        ChecklistTargetOption targetOpt = targetOptionRepository
                .findByIdAndOrganizationId(request.targetOptionId(), orgId)
                .orElseThrow(() -> new IllegalArgumentException("Target option not found"));
        ChecklistPurposeOption purposeOpt = purposeOptionRepository
                .findByIdAndOrganizationId(request.purposeOptionId(), orgId)
                .orElseThrow(() -> new IllegalArgumentException("Purpose option not found"));
        checklist.setTitle(request.title());
        checklist.setTargetOption(targetOpt);
        checklist.setPurposeOption(purposeOpt);
        checklist.setGradeGroupId(gradeGroup.getId());
        checklist.setGradeScope(gradeGroup.getGradesDescription());
        checklist.setCoordinatorUserId(coordinatorUserId);
        if (request.autoAssignOnPublish() != null) {
            checklist.setAutoAssignOnPublish(request.autoAssignOnPublish());
        }

        // Force republish after metadata changes (title/target/purpose/grade scope).
        checklist.setActiveVersion(null);
        checklistRepository.save(checklist);
    }

    @Transactional
    public void disableChecklist(UUID checklistId) {
        UUID orgId = requireTenant();
        Checklist checklist = checklistRepository.findByIdAndOrganizationId(checklistId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Checklist not found"));
        checklist.setActiveVersion(null);
        checklistRepository.save(checklist);
    }

    @Transactional
    public void enableChecklist(UUID checklistId) {
        UUID orgId = requireTenant();
        Checklist checklist = checklistRepository.findByIdAndOrganizationId(checklistId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Checklist not found"));

        // checklist.activeVersion stores versionNo; choose the latest published version.
        Integer latestPublished = versionRepository
                .findAllByChecklistIdAndOrganizationIdOrderByVersionNoDesc(checklistId, orgId)
                .stream()
                .filter(v -> v.getStatus() == com.school.supervision.common.domain.DomainEnums.ChecklistVersionStatus.PUBLISHED)
                .map(ChecklistVersion::getVersionNo)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No published version found"));

        checklist.setActiveVersion(latestPublished);
        checklistRepository.save(checklist);
    }

    @Transactional
    public void deleteChecklist(UUID checklistId) {
        UUID orgId = requireTenant();
        Checklist checklist = checklistRepository.findByIdAndOrganizationId(checklistId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Checklist not found"));

        if (assignmentRepository.existsByOrganizationIdAndChecklistId(orgId, checklistId)) {
            throw new IllegalArgumentException("Cannot delete checklist: it is already used by assignments");
        }

        List<ChecklistVersion> versions = versionRepository.findAllByChecklistIdAndOrganizationIdOrderByVersionNoDesc(checklistId, orgId);
        List<UUID> itemIds = new ArrayList<>();
        for (ChecklistVersion v : versions) {
            List<ChecklistItem> items = itemRepository.findAllByChecklistVersionIdAndOrganizationIdOrderByDisplayOrder(v.getId(), orgId);
            items.forEach(i -> itemIds.add(i.getId()));
        }

        if (!itemIds.isEmpty() && reviewAnswerRepository.existsByOrganizationIdAndChecklistItemIdIn(orgId, itemIds)) {
            throw new IllegalArgumentException("Cannot delete checklist: it has submitted review answers");
        }

        for (ChecklistVersion v : versions) {
            // Delete items belonging to this version after confirming no review references.
            List<ChecklistItem> items = itemRepository.findAllByChecklistVersionIdAndOrganizationIdOrderByDisplayOrder(v.getId(), orgId);
            if (!items.isEmpty()) {
                itemRepository.deleteAll(items);
            }
        }
        if (!versions.isEmpty()) {
            versionRepository.deleteAll(versions);
        }
        checklistRepository.delete(checklist);
    }

    public List<String> validateAnswers(ChecklistDtos.RenderResponse render, Map<UUID, Object> answers) {
        List<String> errors = new ArrayList<>();
        for (ChecklistDtos.RenderItem item : render.items()) {
            Object answer = answers.get(item.id());
            Map<String, Object> rules = Optional.ofNullable(item.validation()).orElse(Map.of());
            boolean required = Boolean.TRUE.equals(rules.get("required"));
            if (required && isAnswerMissing(answer)) {
                errors.add(item.id() + ": required");
                continue;
            }
            if (answer == null || isAnswerMissing(answer)) {
                continue;
            }
            if (item.type().equals("RATING")) {
                int min = ((Number) rules.getOrDefault("min", 1)).intValue();
                int max = ((Number) rules.getOrDefault("max", 5)).intValue();
                int value;
                try {
                    value = ((Number) answer).intValue();
                } catch (ClassCastException ex) {
                    errors.add(item.id() + ": invalid rating");
                    continue;
                }
                if (value < min || value > max) {
                    errors.add(item.id() + ": rating out of range");
                }
            }
            if (item.type().equals("SINGLE_CHOICE") || item.type().equals("MULTIPLE_CHOICE") || item.type().equals("YES_NO")) {
                // Accept answers for any supported language by validating against the union of all choices.
                Set<Object> allowed = new HashSet<>();
                Map<String, Object> opts = Optional.ofNullable(item.options()).orElse(Map.of());
                Object choicesObj = opts.get("choices");
                if (choicesObj instanceof List<?> list) {
                    allowed.addAll(list);
                }
                Object choicesLocalizedObj = opts.get("choicesLocalized");
                if (choicesLocalizedObj instanceof Map<?, ?> localizedMap) {
                    for (Object v : localizedMap.values()) {
                        if (v instanceof List<?> localizedList) {
                            allowed.addAll(localizedList);
                        }
                    }
                }

                if (item.type().equals("SINGLE_CHOICE") && !allowed.isEmpty() && !allowed.contains(answer)) {
                    errors.add(item.id() + ": invalid option");
                }

                if (item.type().equals("MULTIPLE_CHOICE") && answer instanceof List<?> list) {
                    if (!allowed.isEmpty()) {
                        for (Object a : list) {
                            if (!allowed.contains(a)) {
                                errors.add(item.id() + ": invalid option");
                                break;
                            }
                        }
                    }
                }

                if (item.type().equals("YES_NO")) {
                    // Legacy flow uses boolean answers; modern flow may use string choice labels (3+ options).
                    if (answer instanceof String s) {
                        if (!allowed.isEmpty() && !allowed.contains(s)) {
                            errors.add(item.id() + ": invalid option");
                        }
                    }
                }
            }
        }
        return errors;
    }

    private static boolean isAnswerMissing(Object answer) {
        if (answer == null) {
            return true;
        }
        if (answer instanceof String s) {
            return s.trim().isEmpty();
        }
        if (answer instanceof List<?> list) {
            return list.isEmpty();
        }
        return false;
    }

    private UUID requireTenant() {
        UUID organizationId = TenantContext.getOrganizationId();
        if (organizationId == null) {
            throw new IllegalStateException("Missing tenant context");
        }
        return organizationId;
    }

    private Map<String, Object> readJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    private String localizedQuestion(String fallbackQuestion, String questionLocalizedJson, String lang) {
        Map<String, Object> localized = readJson(questionLocalizedJson);
        Object val = localized.get(lang);
        if (val != null) {
            String s = val.toString();
            if (s.trim().isEmpty()) {
                return fallbackQuestion;
            }
            return s;
        }
        return fallbackQuestion;
    }

    private Map<String, Object> localizedOptions(String optionsJson, String lang) {
        Map<String, Object> options = readJson(optionsJson);
        Object localizedChoicesObj = options.get("choicesLocalized");
        if (localizedChoicesObj instanceof Map<?, ?> localizedChoicesMap) {
            Object langChoices = localizedChoicesMap.get(lang);
            if (langChoices instanceof List<?> langChoicesList && !langChoicesList.isEmpty()) {
                options.put("choices", langChoicesList);
            }
        }
        return options;
    }

    private String writeJson(Object data) {
        if (data == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid JSON payload", e);
        }
    }

    private void set(Checklist checklist, ChecklistDtos.CreateChecklistRequest request, UUID actorUserId, UUID coordinatorUserId) {
        DomainEnums.DisplayMode mode = request.displayMode() != null
                ? request.displayMode()
                : DomainEnums.DisplayMode.GROUPED;
        GradeGroup gradeGroup = gradeGroupRepository.findByIdAndOrganizationId(request.gradeGroupId(), requireTenant())
                .orElseThrow(() -> new IllegalArgumentException("Grade group not found"));
        assertGradeGroupInCoordinatorScope(gradeGroup, coordinatorUserId);
        ChecklistTargetOption targetOpt = targetOptionRepository
                .findByIdAndOrganizationId(request.targetOptionId(), requireTenant())
                .orElseThrow(() -> new IllegalArgumentException("Target option not found"));
        ChecklistPurposeOption purposeOpt = purposeOptionRepository
                .findByIdAndOrganizationId(request.purposeOptionId(), requireTenant())
                .orElseThrow(() -> new IllegalArgumentException("Purpose option not found"));
        checklist.setTitle(request.title());
        checklist.setTargetOption(targetOpt);
        checklist.setDisplayMode(mode);
        checklist.setPurposeOption(purposeOpt);
        checklist.setGradeGroupId(gradeGroup.getId());
        checklist.setGradeScope(gradeGroup.getGradesDescription());
        checklist.setCoordinatorUserId(coordinatorUserId);
        checklist.setCreatedBy(actorUserId);
        boolean auto = request.autoAssignOnPublish() == null || Boolean.TRUE.equals(request.autoAssignOnPublish());
        checklist.setAutoAssignOnPublish(auto);
    }

    private void assertGradeGroupInCoordinatorScope(GradeGroup gradeGroup, UUID coordinatorUserId) {
        if (coordinatorUserId == null) {
            return;
        }
        if (gradeGroup.getCoordinatorUserId() == null || gradeGroup.getCoordinatorUserId().equals(coordinatorUserId)) {
            return;
        }
        throw new IllegalArgumentException("Grade group not available in your scope");
    }

    private void set(ChecklistVersion version, UUID checklistId, int versionNo, UUID actorUserId) {
        version.setChecklistId(checklistId);
        version.setVersionNo(versionNo);
        version.setStatus(DomainEnums.ChecklistVersionStatus.PUBLISHED);
        version.setCreatedBy(actorUserId);
    }

    private void set(ChecklistItem item, UUID versionId, ChecklistDtos.ItemRequest request) {
        item.setChecklistVersionId(versionId);
        item.setQuestion(request.question());
        if (request.questionLocalized() != null) {
            item.setQuestionLocalizedJson(writeJson(request.questionLocalized()));
        } else {
            item.setQuestionLocalizedJson(null);
        }
        item.setItemType(request.type());
        item.setOptionsJson(writeJson(request.options()));
        item.setValidationJson(writeJson(request.validation()));
        item.setGroupKey(request.groupKey());
        item.setDisplayOrder(request.order());
    }
}
