package com.school.supervision.modules.users;

public final class UserRoleChecks {
    private UserRoleChecks() {}

    public static boolean isSuperAdmin(User user) {
        return user.getRoles().stream().anyMatch(r -> "SUPER_ADMIN".equals(r.getName()));
    }

    public static boolean isCoordinator(User user) {
        return user.getRoles().stream().anyMatch(r -> "CLUSTER_COORDINATOR".equals(r.getName()));
    }

    public static boolean isSupervisor(User user) {
        return user.getRoles().stream().anyMatch(r -> "SUPERVISOR".equals(r.getName()));
    }

    public static boolean isAdminOrCoordinator(User user) {
        return isSuperAdmin(user) || isCoordinator(user);
    }
}
