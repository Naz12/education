package com.school.supervision.modules.organization;

import com.school.supervision.common.tenant.TenantContext;
import com.school.supervision.modules.users.User;
import com.school.supervision.modules.users.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/geography")
public class GeographyAdminController {
    private final CityRepository cityRepository;
    private final SubcityRepository subcityRepository;
    private final WeredaRepository weredaRepository;
    private final ClusterRepository clusterRepository;
    private final UserRepository userRepository;

    public GeographyAdminController(
            CityRepository cityRepository,
            SubcityRepository subcityRepository,
            WeredaRepository weredaRepository,
            ClusterRepository clusterRepository,
            UserRepository userRepository) {
        this.cityRepository = cityRepository;
        this.subcityRepository = subcityRepository;
        this.weredaRepository = weredaRepository;
        this.clusterRepository = clusterRepository;
        this.userRepository = userRepository;
    }

    public record CitySummary(UUID id, String name) {}
    public record SubcitySummary(UUID id, String name, UUID cityId) {}
    public record WeredaSummary(UUID id, String name, UUID subcityId) {}

    public record NameRequest(@NotBlank String name) {}
    public record CreateSubcityRequest(@NotNull UUID cityId, @NotBlank String name) {}
    public record CreateWeredaRequest(@NotNull UUID subcityId, @NotBlank String name) {}

    @GetMapping("/cities")
    public List<CitySummary> listCities(Authentication authentication) {
        requireSuperAdmin(authentication);
        UUID orgId = requireTenant();
        return cityRepository.findAllByOrganizationId(orgId).stream()
                .sorted(Comparator.comparing(City::getName, String.CASE_INSENSITIVE_ORDER))
                .map(c -> new CitySummary(c.getId(), c.getName()))
                .toList();
    }

    @PostMapping("/cities")
    public UUID createCity(Authentication authentication, @Valid @RequestBody NameRequest request) {
        requireSuperAdmin(authentication);
        UUID orgId = requireTenant();
        String name = request.name().trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("City name is required");
        }
        if (cityRepository.existsByOrganizationIdAndNameIgnoreCase(orgId, name)) {
            throw new IllegalArgumentException("A city with this name already exists");
        }
        City c = new City();
        c.setOrganizationId(orgId);
        c.setName(name);
        return cityRepository.save(c).getId();
    }

    @PatchMapping("/cities/{cityId}")
    public void updateCity(Authentication authentication,
                           @PathVariable UUID cityId,
                           @Valid @RequestBody NameRequest request) {
        requireSuperAdmin(authentication);
        UUID orgId = requireTenant();
        City c = cityRepository.findByIdAndOrganizationId(cityId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("City not found"));
        String name = request.name().trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("City name is required");
        }
        if (cityRepository.existsByOrganizationIdAndNameIgnoreCaseAndIdNot(orgId, name, cityId)) {
            throw new IllegalArgumentException("A city with this name already exists");
        }
        c.setName(name);
        cityRepository.save(c);
    }

    @DeleteMapping("/cities/{cityId}")
    public void deleteCity(Authentication authentication, @PathVariable UUID cityId) {
        requireSuperAdmin(authentication);
        UUID orgId = requireTenant();
        City c = cityRepository.findByIdAndOrganizationId(cityId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("City not found"));
        if (subcityRepository.countByOrganizationIdAndCityId(orgId, cityId) > 0) {
            throw new IllegalArgumentException("Cannot delete city that has sub cities");
        }
        cityRepository.delete(c);
    }

    @GetMapping("/subcities")
    public List<SubcitySummary> listSubcities(Authentication authentication, @RequestParam UUID cityId) {
        requireSuperAdmin(authentication);
        UUID orgId = requireTenant();
        cityRepository.findByIdAndOrganizationId(cityId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("City not found"));
        return subcityRepository.findAllByOrganizationIdAndCityIdOrderByName(orgId, cityId).stream()
                .map(s -> new SubcitySummary(s.getId(), s.getName(), s.getCityId()))
                .toList();
    }

    @PostMapping("/subcities")
    public UUID createSubcity(Authentication authentication, @Valid @RequestBody CreateSubcityRequest request) {
        requireSuperAdmin(authentication);
        UUID orgId = requireTenant();
        cityRepository.findByIdAndOrganizationId(request.cityId(), orgId)
                .orElseThrow(() -> new IllegalArgumentException("City not found"));
        String name = request.name().trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Sub city name is required");
        }
        if (subcityRepository.countByOrganizationIdAndCityIdAndNameIgnoreCase(orgId, request.cityId(), name) > 0) {
            throw new IllegalArgumentException("A sub city with this name already exists in this city");
        }
        Subcity s = new Subcity();
        s.setOrganizationId(orgId);
        s.setCityId(request.cityId());
        s.setName(name);
        return subcityRepository.save(s).getId();
    }

    @PatchMapping("/subcities/{subcityId}")
    public void updateSubcity(Authentication authentication,
                              @PathVariable UUID subcityId,
                              @Valid @RequestBody NameRequest request) {
        requireSuperAdmin(authentication);
        UUID orgId = requireTenant();
        Subcity s = subcityRepository.findByIdAndOrganizationId(subcityId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Sub city not found"));
        String name = request.name().trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Sub city name is required");
        }
        if (subcityRepository.countByOrganizationIdAndCityIdAndNameIgnoreCaseAndIdNot(
                orgId, s.getCityId(), name, subcityId) > 0) {
            throw new IllegalArgumentException("A sub city with this name already exists in this city");
        }
        s.setName(name);
        subcityRepository.save(s);
    }

    @DeleteMapping("/subcities/{subcityId}")
    public void deleteSubcity(Authentication authentication, @PathVariable UUID subcityId) {
        requireSuperAdmin(authentication);
        UUID orgId = requireTenant();
        Subcity s = subcityRepository.findByIdAndOrganizationId(subcityId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Sub city not found"));
        if (weredaRepository.countByOrganizationIdAndSubcityId(orgId, subcityId) > 0) {
            throw new IllegalArgumentException("Cannot delete sub city that has weredas");
        }
        subcityRepository.delete(s);
    }

    @GetMapping("/weredas")
    public List<WeredaSummary> listWeredas(Authentication authentication, @RequestParam UUID subcityId) {
        requireSuperAdmin(authentication);
        UUID orgId = requireTenant();
        subcityRepository.findByIdAndOrganizationId(subcityId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Sub city not found"));
        return weredaRepository.findAllByOrganizationIdAndSubcityIdOrderByName(orgId, subcityId).stream()
                .map(w -> new WeredaSummary(w.getId(), w.getName(), w.getSubcityId()))
                .toList();
    }

    @PostMapping("/weredas")
    public UUID createWereda(Authentication authentication, @Valid @RequestBody CreateWeredaRequest request) {
        requireSuperAdmin(authentication);
        UUID orgId = requireTenant();
        subcityRepository.findByIdAndOrganizationId(request.subcityId(), orgId)
                .orElseThrow(() -> new IllegalArgumentException("Sub city not found"));
        String name = request.name().trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Wereda name is required");
        }
        if (weredaRepository.countByOrganizationIdAndSubcityIdAndNameIgnoreCase(orgId, request.subcityId(), name) > 0) {
            throw new IllegalArgumentException("A wereda with this name already exists in this sub city");
        }
        Wereda w = new Wereda();
        w.setOrganizationId(orgId);
        w.setSubcityId(request.subcityId());
        w.setName(name);
        return weredaRepository.save(w).getId();
    }

    @PatchMapping("/weredas/{weredaId}")
    public void updateWereda(Authentication authentication,
                             @PathVariable UUID weredaId,
                             @Valid @RequestBody NameRequest request) {
        requireSuperAdmin(authentication);
        UUID orgId = requireTenant();
        Wereda w = weredaRepository.findByIdAndOrganizationId(weredaId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Wereda not found"));
        String name = request.name().trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Wereda name is required");
        }
        if (weredaRepository.countByOrganizationIdAndSubcityIdAndNameIgnoreCaseAndIdNot(
                orgId, w.getSubcityId(), name, weredaId) > 0) {
            throw new IllegalArgumentException("A wereda with this name already exists in this sub city");
        }
        w.setName(name);
        weredaRepository.save(w);
    }

    @DeleteMapping("/weredas/{weredaId}")
    public void deleteWereda(Authentication authentication, @PathVariable UUID weredaId) {
        requireSuperAdmin(authentication);
        UUID orgId = requireTenant();
        Wereda w = weredaRepository.findByIdAndOrganizationId(weredaId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Wereda not found"));
        if (clusterRepository.countByOrganizationIdAndWeredaId(orgId, weredaId) > 0) {
            throw new IllegalArgumentException("Cannot delete wereda that is used by a cluster");
        }
        if (userRepository.countByOrganizationIdAndWeredaId(orgId, weredaId) > 0) {
            throw new IllegalArgumentException("Cannot delete wereda that is assigned to users");
        }
        weredaRepository.delete(w);
    }

    private UUID requireTenant() {
        UUID orgId = TenantContext.getOrganizationId();
        if (orgId == null) {
            throw new IllegalStateException("Missing tenant context");
        }
        return orgId;
    }

    private void requireSuperAdmin(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new AccessDeniedException("Authentication required");
        }
        User u = userRepository.findByUsernameAndOrganizationId(authentication.getName(), requireTenant())
                .orElseThrow(() -> new AccessDeniedException("Current user not found"));
        boolean sa = u.getRoles().stream().anyMatch(r -> "SUPER_ADMIN".equals(r.getName()));
        if (!sa) {
            throw new AccessDeniedException("Only SUPER_ADMIN can manage geography");
        }
    }
}
