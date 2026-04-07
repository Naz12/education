package com.school.supervision.modules.checklists;

import com.school.supervision.common.tenant.TenantContext;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ChecklistOptionService {
    private final ChecklistTargetOptionRepository targetOptionRepository;
    private final ChecklistPurposeOptionRepository purposeOptionRepository;
    private final ChecklistRepository checklistRepository;

    public ChecklistOptionService(ChecklistTargetOptionRepository targetOptionRepository,
                                  ChecklistPurposeOptionRepository purposeOptionRepository,
                                  ChecklistRepository checklistRepository) {
        this.targetOptionRepository = targetOptionRepository;
        this.purposeOptionRepository = purposeOptionRepository;
        this.checklistRepository = checklistRepository;
    }

    private static UUID requireTenant() {
        UUID organizationId = TenantContext.getOrganizationId();
        if (organizationId == null) {
            throw new IllegalStateException("Missing tenant context");
        }
        return organizationId;
    }

    public List<ChecklistDtos.TargetOptionResponse> listTargets() {
        UUID orgId = requireTenant();
        return targetOptionRepository.findAllByOrganizationIdOrderByNameAsc(orgId).stream()
                .map(t -> new ChecklistDtos.TargetOptionResponse(t.getId(), t.getName(), t.getRoutingKind().name()))
                .toList();
    }

    public List<ChecklistDtos.PurposeOptionResponse> listPurposes() {
        UUID orgId = requireTenant();
        return purposeOptionRepository.findAllByOrganizationIdOrderByNameAsc(orgId).stream()
                .map(p -> new ChecklistDtos.PurposeOptionResponse(p.getId(), p.getName()))
                .toList();
    }

    @Transactional
    public UUID createTarget(ChecklistDtos.CreateTargetOptionRequest request) {
        UUID orgId = requireTenant();
        String name = request.name().trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Name is required");
        }
        targetOptionRepository.findByOrganizationIdAndName(orgId, name).ifPresent(x -> {
            throw new IllegalArgumentException("A target with this name already exists");
        });
        ChecklistTargetOption opt = new ChecklistTargetOption();
        opt.setOrganizationId(orgId);
        opt.setName(name);
        opt.setRoutingKind(request.routingKind());
        return targetOptionRepository.save(opt).getId();
    }

    @Transactional
    public void updateTarget(UUID id, ChecklistDtos.UpdateTargetOptionRequest request) {
        UUID orgId = requireTenant();
        ChecklistTargetOption opt = targetOptionRepository.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Target option not found"));
        String name = request.name().trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Name is required");
        }
        targetOptionRepository.findByOrganizationIdAndName(orgId, name).ifPresent(
                other -> {
                    if (!other.getId().equals(id)) {
                        throw new IllegalArgumentException("A target with this name already exists");
                    }
                });
        opt.setName(name);
        opt.setRoutingKind(request.routingKind());
        targetOptionRepository.save(opt);
    }

    @Transactional
    public void deleteTarget(UUID id) {
        UUID orgId = requireTenant();
        ChecklistTargetOption opt = targetOptionRepository.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Target option not found"));
        long n = checklistRepository.countByOrganizationIdAndTargetOptionId(orgId, id);
        if (n > 0) {
            throw new IllegalArgumentException("Cannot delete target: it is used by one or more checklists");
        }
        targetOptionRepository.delete(opt);
    }

    @Transactional
    public UUID createPurpose(ChecklistDtos.CreatePurposeOptionRequest request) {
        UUID orgId = requireTenant();
        String name = request.name().trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Name is required");
        }
        purposeOptionRepository.findByOrganizationIdAndName(orgId, name).ifPresent(x -> {
            throw new IllegalArgumentException("A purpose with this name already exists");
        });
        ChecklistPurposeOption opt = new ChecklistPurposeOption();
        opt.setOrganizationId(orgId);
        opt.setName(name);
        return purposeOptionRepository.save(opt).getId();
    }

    @Transactional
    public void updatePurpose(UUID id, ChecklistDtos.UpdatePurposeOptionRequest request) {
        UUID orgId = requireTenant();
        ChecklistPurposeOption opt = purposeOptionRepository.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Purpose option not found"));
        String name = request.name().trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Name is required");
        }
        purposeOptionRepository.findByOrganizationIdAndName(orgId, name).ifPresent(
                other -> {
                    if (!other.getId().equals(id)) {
                        throw new IllegalArgumentException("A purpose with this name already exists");
                    }
                });
        opt.setName(name);
        purposeOptionRepository.save(opt);
    }

    @Transactional
    public void deletePurpose(UUID id) {
        UUID orgId = requireTenant();
        ChecklistPurposeOption opt = purposeOptionRepository.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Purpose option not found"));
        long n = checklistRepository.countByOrganizationIdAndPurposeOptionId(orgId, id);
        if (n > 0) {
            throw new IllegalArgumentException("Cannot delete purpose: it is used by one or more checklists");
        }
        purposeOptionRepository.delete(opt);
    }
}
