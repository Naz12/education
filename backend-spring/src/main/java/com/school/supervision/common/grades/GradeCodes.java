package com.school.supervision.common.grades;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Canonical grade codes for schools, grade groups, and auto-assignment matching.
 * Order is stable for display (KG early, then numeric grades).
 */
public final class GradeCodes {
    private GradeCodes() {}

    public static final List<String> ORDERED = List.of(
            "KG1", "KG2", "KG3",
            "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12"
    );

    private static final Set<String> ALLOWED = Set.copyOf(ORDERED);

    /** Normalize user input: trim, uppercase KG*, keep numeric strings, drop unknown. */
    public static Set<String> normalize(Iterable<String> raw) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (raw == null) {
            return out;
        }
        for (String s : raw) {
            if (s == null) continue;
            String t = s.trim();
            if (t.isEmpty()) continue;
            String u = t.toUpperCase().replace(" ", "");
            if (u.startsWith("KG")) {
                if (ALLOWED.contains(u)) {
                    out.add(u);
                }
            } else if (ALLOWED.contains(t)) {
                out.add(t);
            }
        }
        return out;
    }

    public static List<String> sortForDisplay(Set<String> codes) {
        return ORDERED.stream().filter(codes::contains).collect(Collectors.toList());
    }

    public static boolean overlaps(Set<String> a, Set<String> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
            return false;
        }
        for (String x : a) {
            if (b.contains(x)) {
                return true;
            }
        }
        return false;
    }

    /** Short human-readable summary for grade_groups.grades_description. */
    public static String toSummary(Set<String> codes) {
        List<String> sorted = sortForDisplay(codes);
        if (sorted.isEmpty()) {
            return "";
        }
        return String.join(", ", sorted);
    }

    public static List<String> parseJsonArray(com.fasterxml.jackson.databind.ObjectMapper om, String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<?> list = om.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<List<?>>() {});
            List<String> out = new ArrayList<>();
            for (Object o : list) {
                if (o != null) {
                    out.add(o.toString());
                }
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }
}
