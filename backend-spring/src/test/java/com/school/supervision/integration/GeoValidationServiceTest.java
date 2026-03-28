package com.school.supervision.integration;

import com.school.supervision.common.domain.DomainEnums.LocationPolicy;
import com.school.supervision.common.domain.DomainEnums.LocationStatus;
import com.school.supervision.modules.organization.School;
import com.school.supervision.modules.reviews.GeoValidationService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class GeoValidationServiceTest {
    private final GeoValidationService service = new GeoValidationService();

    @Test
    void flagsOutOfRangeWhenPolicyAllows() {
        School school = new School();
        school.setLatitude(9.03);
        school.setLongitude(38.74);
        school.setAllowedRadiusInMeters(100);
        GeoValidationService.ValidationResult result =
                service.validate(9.05, 38.80, school, LocationPolicy.ALLOW_AND_FLAG_OUT_OF_RANGE);
        Assertions.assertFalse(result.withinRange());
        Assertions.assertEquals(LocationStatus.OUT_OF_RANGE, result.status());
    }
}
