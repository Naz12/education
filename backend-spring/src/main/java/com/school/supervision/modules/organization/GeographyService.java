package com.school.supervision.modules.organization;

import com.school.supervision.modules.users.User;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class GeographyService {
    private final CityRepository cityRepository;
    private final SubcityRepository subcityRepository;
    private final WeredaRepository weredaRepository;

    public GeographyService(
            CityRepository cityRepository,
            SubcityRepository subcityRepository,
            WeredaRepository weredaRepository) {
        this.cityRepository = cityRepository;
        this.subcityRepository = subcityRepository;
        this.weredaRepository = weredaRepository;
    }

    public void applyWeredaToUser(User user, UUID weredaId) {
        UUID orgId = user.getOrganizationId();
        Wereda w = weredaRepository.findByIdAndOrganizationId(weredaId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Wereda not found"));
        Subcity s = subcityRepository.findByIdAndOrganizationId(w.getSubcityId(), orgId)
                .orElseThrow(() -> new IllegalArgumentException("Subcity not found"));
        City c = cityRepository.findByIdAndOrganizationId(s.getCityId(), orgId)
                .orElseThrow(() -> new IllegalArgumentException("City not found"));
        user.setWeredaId(w.getId());
        user.setSubcityId(s.getId());
        user.setCityId(c.getId());
        user.setWereda(w.getName());
        user.setSubCity(s.getName());
        user.setCity(c.getName());
    }
}
