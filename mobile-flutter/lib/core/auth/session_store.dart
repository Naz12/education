class SessionStore {
  static String? accessToken;
  static String? username;
  static Map<String, dynamic>? currentUser;

  static List<String> get roles {
    final r = currentUser?['roles'];
    if (r is List) {
      return r.map((e) => e.toString()).toList();
    }
    return [];
  }

  static bool get isSuperAdmin => roles.contains('SUPER_ADMIN');
  static bool get isCoordinator => roles.contains('CLUSTER_COORDINATOR');
  static bool get canAdmin => isSuperAdmin || isCoordinator;

  static void clear() {
    accessToken = null;
    username = null;
    currentUser = null;
  }
}
