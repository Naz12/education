package com.school.supervision.modules.reviews;

import com.school.supervision.common.domain.DomainEnums.LocationPolicy;
import com.school.supervision.common.domain.DomainEnums.LocationStatus;
import com.school.supervision.modules.organization.School;
import org.springframework.stereotype.Service;

@Service
public class GeoValidationService {

    public record ValidationResult(double distanceMeters, boolean withinRange, LocationStatus status) {}

    public ValidationResult validate(double currentLat, double currentLon, School school, LocationPolicy policy) {
        double distance = haversineMeters(currentLat, currentLon, school.getLatitude(), school.getLongitude());
        boolean withinRange = distance <= school.getAllowedRadiusInMeters();
        if (withinRange) {
            return new ValidationResult(distance, true, LocationStatus.WITHIN_RANGE);
        }
        if (policy == LocationPolicy.BLOCK_SUBMISSION) {
            return new ValidationResult(distance, false, LocationStatus.BLOCKED);
        }
        return new ValidationResult(distance, false, LocationStatus.OUT_OF_RANGE);
    }

    public double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double earthRadius = 6371000d;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadius * c;
    }
}
