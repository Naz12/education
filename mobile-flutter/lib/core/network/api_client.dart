import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'dart:ui' as ui;
import '../auth/session_store.dart';

class ApiClient {
  /// Synced with app language toggle (see [setAppLanguageCode]).
  static String? _appLanguageCode;

  static void setAppLanguageCode(String code) {
    _appLanguageCode =
        code.toLowerCase().startsWith('am') ? 'am' : 'en';
  }

  static String _resolveLang() {
    if (_appLanguageCode != null) return _appLanguageCode!;
    final code =
        ui.PlatformDispatcher.instance.locale.languageCode.toLowerCase();
    return code.startsWith('am') ? 'am' : 'en';
  }

  static String messageFromError(Object e) {
    if (e is DioException) {
      final data = e.response?.data;
      if (data is Map) {
        final msg = data['message'];
        if (msg != null) return msg.toString();
        final err = data['error'];
        if (err != null) return err.toString();
      }
      return e.message ?? e.toString();
    }
    return e.toString();
  }

  static Options _authOptions({ResponseType? responseType}) {
    final token = SessionStore.accessToken;
    if (token == null || token.isEmpty) {
      throw Exception('User is not logged in');
    }
    return Options(
      headers: {'Authorization': 'Bearer $token'},
      responseType: responseType,
    );
  }

  static String _resolveBaseUrl() {
    if (kIsWeb) {
      return 'http://localhost:8080/api';
    }
    switch (defaultTargetPlatform) {
      case TargetPlatform.android:
        return 'http://10.0.2.2:8080/api';
      default:
        return 'http://localhost:8080/api';
    }
  }

  static final Dio _dio = Dio(
    BaseOptions(
      baseUrl: _resolveBaseUrl(),
      connectTimeout: const Duration(seconds: 30),
      receiveTimeout: const Duration(seconds: 60),
      headers: {'Content-Type': 'application/json'},
    ),
  );

  static Future<void> login(String username, String password) async {
    final response = await _dio.post('/auth/login', data: {
      'username': username,
      'password': password,
    });
    final token = response.data['accessToken'] as String?;
    if (token == null || token.isEmpty) {
      throw Exception('No access token returned');
    }
    SessionStore.accessToken = token;
    SessionStore.username = username;
  }

  static Future<Map<String, dynamic>> fetchMe() async {
    final response = await _dio.get('/users/me', options: _authOptions());
    return Map<String, dynamic>.from(response.data as Map);
  }

  static Future<Map<String, dynamic>> fetchMyStatus() async {
    final response =
        await _dio.get('/users/me/status', options: _authOptions());
    return Map<String, dynamic>.from(response.data as Map);
  }

  static Future<Map<String, dynamic>> patchMyProfile({
    String? fullName,
    String? email,
    String? city,
    String? subCity,
    String? wereda,
  }) async {
    final body = <String, dynamic>{};
    if (fullName != null) body['fullName'] = fullName;
    if (email != null) body['email'] = email;
    if (city != null) body['city'] = city;
    if (subCity != null) body['subCity'] = subCity;
    if (wereda != null) body['wereda'] = wereda;
    final response = await _dio.patch(
      '/users/me',
      data: body,
      options: _authOptions(),
    );
    return Map<String, dynamic>.from(response.data as Map);
  }

  static Future<void> changePassword({
    required String currentPassword,
    required String newPassword,
  }) async {
    await _dio.post(
      '/users/me/change-password',
      data: {
        'currentPassword': currentPassword,
        'newPassword': newPassword,
      },
      options: _authOptions(),
    );
  }

  static Future<Map<String, dynamic>> fetchMyWorkload() async {
    final response = await _dio.get(
      '/supervision/my-workload',
      options: _authOptions(),
    );
    return Map<String, dynamic>.from(response.data as Map);
  }

  static Future<List<dynamic>> fetchMyAssignments() async {
    final response = await _dio.get(
      '/assignments/my',
      options: _authOptions(),
    );
    return response.data as List<dynamic>;
  }

  static Future<Map<String, dynamic>> fetchAssignmentRender(
      String assignmentId) async {
    final lang = _resolveLang();
    final response = await _dio.get(
      '/assignments/$assignmentId/render?lang=$lang',
      options: _authOptions(),
    );
    return Map<String, dynamic>.from(response.data as Map);
  }

  static Future<void> startReview(
      String assignmentId, double latitude, double longitude) async {
    await _dio.post(
      '/assignments/$assignmentId/start',
      data: {'latitude': latitude, 'longitude': longitude},
      options: _authOptions(),
    );
  }

  static Future<String> submitReview(
    String assignmentId,
    double latitude,
    double longitude,
    List<Map<String, dynamic>> answers,
  ) async {
    final response = await _dio.post(
      '/assignments/$assignmentId/submit',
      data: {
        'latitude': latitude,
        'longitude': longitude,
        'policy': 'ALLOW_AND_FLAG_OUT_OF_RANGE',
        'answers': answers,
      },
      options: _authOptions(),
    );
    final data = response.data;
    if (data is String) {
      return data;
    }
    if (data is Map && data['message'] != null) {
      return data['message'].toString();
    }
    return data?.toString() ?? '';
  }

  static Future<void> submitSignature(
      String reviewId, String signerRole, String imageBase64) async {
    await _dio.post(
      '/reviews/$reviewId/signatures',
      data: {'signerRole': signerRole, 'imageBase64': imageBase64},
      options: _authOptions(),
    );
  }

  // --- Super admin: geography (city → sub city → wereda) ---

  static Future<List<dynamic>> fetchGeographyCities() async {
    final response =
        await _dio.get('/admin/geography/cities', options: _authOptions());
    return response.data as List<dynamic>;
  }

  static Future<List<dynamic>> fetchGeographySubcities(String cityId) async {
    final response = await _dio.get(
      '/admin/geography/subcities',
      queryParameters: {'cityId': cityId},
      options: _authOptions(),
    );
    return response.data as List<dynamic>;
  }

  static Future<List<dynamic>> fetchGeographyWeredas(String subcityId) async {
    final response = await _dio.get(
      '/admin/geography/weredas',
      queryParameters: {'subcityId': subcityId},
      options: _authOptions(),
    );
    return response.data as List<dynamic>;
  }

  // --- Admin / coordinator: users ---

  static Future<List<dynamic>> fetchClusterCoordinators() async {
    final response =
        await _dio.get('/users/cluster-coordinators', options: _authOptions());
    return response.data as List<dynamic>;
  }

  static Future<List<dynamic>> fetchSupervisorsDirectory() async {
    final response =
        await _dio.get('/users/supervisors', options: _authOptions());
    return response.data as List<dynamic>;
  }

  static Future<void> createClusterCoordinator(
      Map<String, dynamic> body) async {
    await _dio.post('/users/cluster-coordinators',
        data: body, options: _authOptions());
  }

  static Future<void> patchClusterCoordinator(
      String userId, Map<String, dynamic> body) async {
    await _dio.patch('/users/cluster-coordinators/$userId',
        data: body, options: _authOptions());
  }

  static Future<void> deleteClusterCoordinator(String userId) async {
    await _dio.delete('/users/cluster-coordinators/$userId',
        options: _authOptions());
  }

  static Future<void> createSupervisorUser(Map<String, dynamic> body) async {
    await _dio.post('/users/supervisors', data: body, options: _authOptions());
  }

  static Future<void> patchSupervisorUser(
      String userId, Map<String, dynamic> body) async {
    await _dio.patch('/users/supervisors/$userId',
        data: body, options: _authOptions());
  }

  static Future<void> deleteSupervisorUser(String userId) async {
    await _dio.delete('/users/supervisors/$userId', options: _authOptions());
  }

  // --- Schools ---

  static Future<List<dynamic>> fetchSchools({String? q}) async {
    final path = (q != null && q.trim().isNotEmpty)
        ? '/schools?q=${Uri.encodeQueryComponent(q.trim())}'
        : '/schools';
    final response = await _dio.get(path, options: _authOptions());
    return response.data as List<dynamic>;
  }

  static Future<void> createSchool({
    required String name,
    required double latitude,
    required double longitude,
    required int allowedRadiusInMeters,
    List<String> supportedGradeCodes = const [],
  }) async {
    await _dio.post(
      '/schools',
      data: {
        'name': name,
        'latitude': latitude,
        'longitude': longitude,
        'allowedRadiusInMeters': allowedRadiusInMeters,
        'supportedGradeCodes': supportedGradeCodes,
      },
      options: _authOptions(),
    );
  }

  static Future<void> patchSchool({
    required String schoolId,
    required String name,
    required double latitude,
    required double longitude,
    required int allowedRadiusInMeters,
    required List<String> supportedGradeCodes,
  }) async {
    await _dio.patch(
      '/schools/$schoolId',
      data: {
        'name': name,
        'latitude': latitude,
        'longitude': longitude,
        'allowedRadiusInMeters': allowedRadiusInMeters,
        'supportedGradeCodes': supportedGradeCodes,
      },
      options: _authOptions(),
    );
  }

  static Future<void> deleteSchool(String schoolId) async {
    await _dio.delete('/schools/$schoolId', options: _authOptions());
  }

  // --- Teachers (assignments + school stuff teacher edits) ---

  static Future<List<dynamic>> fetchTeachers() async {
    final response = await _dio.get('/teachers', options: _authOptions());
    return response.data as List<dynamic>;
  }

  static Future<void> patchTeacher({
    required String teacherId,
    required String name,
    required String subjectId,
    required String schoolId,
    required List<String> responsibleGradeCodes,
  }) async {
    await _dio.patch(
      '/teachers/$teacherId',
      data: {
        'name': name,
        'subjectId': subjectId,
        'schoolId': schoolId,
        'responsibleGradeCodes': responsibleGradeCodes,
      },
      options: _authOptions(),
    );
  }

  static Future<void> deleteTeacher(String teacherId) async {
    await _dio.delete('/teachers/$teacherId', options: _authOptions());
  }

  // --- School stuff ---

  static Future<List<dynamic>> fetchSchoolStuffTypes() async {
    final response =
        await _dio.get('/school-stuff/types', options: _authOptions());
    return response.data as List<dynamic>;
  }

  static Future<List<dynamic>> fetchSchoolStuffItems() async {
    final response = await _dio.get('/school-stuff', options: _authOptions());
    return response.data as List<dynamic>;
  }

  static Future<void> createSchoolStuffType(
      {required String name, required String description}) async {
    await _dio.post(
      '/school-stuff/types',
      data: {'name': name, 'description': description},
      options: _authOptions(),
    );
  }

  static Future<void> patchSchoolStuffType({
    required String typeId,
    required String name,
    required String description,
  }) async {
    await _dio.patch(
      '/school-stuff/types/$typeId',
      data: {'name': name, 'description': description},
      options: _authOptions(),
    );
  }

  static Future<void> deleteSchoolStuffType(String typeId) async {
    await _dio.delete('/school-stuff/types/$typeId', options: _authOptions());
  }

  static Future<List<dynamic>> fetchSchoolStuffSubjects() async {
    final response =
        await _dio.get('/school-stuff/subjects', options: _authOptions());
    return response.data as List<dynamic>;
  }

  static Future<void> createSchoolStuffSubject({required String name}) async {
    await _dio.post(
      '/school-stuff/subjects',
      data: {'name': name},
      options: _authOptions(),
    );
  }

  static Future<void> patchSchoolStuffSubject({
    required String subjectId,
    required String name,
  }) async {
    await _dio.patch(
      '/school-stuff/subjects/$subjectId',
      data: {'name': name},
      options: _authOptions(),
    );
  }

  static Future<void> deleteSchoolStuffSubject(String subjectId) async {
    await _dio.delete('/school-stuff/subjects/$subjectId',
        options: _authOptions());
  }

  static Future<void> createSchoolStuff(Map<String, dynamic> body) async {
    await _dio.post('/school-stuff', data: body, options: _authOptions());
  }

  static Future<void> patchSchoolStuff(
      String entryId, Map<String, dynamic> body) async {
    await _dio.patch('/school-stuff/$entryId',
        data: body, options: _authOptions());
  }

  static Future<void> deleteSchoolStuff(String entryId, String type) async {
    await _dio.delete(
        '/school-stuff/$entryId?type=${Uri.encodeQueryComponent(type)}',
        options: _authOptions());
  }

  // --- Checklists ---

  static Future<List<dynamic>> fetchChecklists() async {
    final response = await _dio.get('/checklists', options: _authOptions());
    return response.data as List<dynamic>;
  }

  static Future<List<dynamic>> fetchGradeGroups() async {
    final response = await _dio.get('/grade-groups', options: _authOptions());
    return response.data as List<dynamic>;
  }

  static Future<void> createGradeGroup({
    required String displayName,
    required List<String> gradeCodes,
  }) async {
    await _dio.post(
      '/grade-groups',
      data: {'displayName': displayName, 'gradeCodes': gradeCodes},
      options: _authOptions(),
    );
  }

  static Future<String> createChecklist({
    required String title,
    required String targetType,
    required String purpose,
    required String gradeGroupId,
    bool autoAssignOnPublish = true,
  }) async {
    final response = await _dio.post(
      '/checklists',
      data: {
        'title': title,
        'targetType': targetType,
        'purpose': purpose,
        'gradeGroupId': gradeGroupId,
        'autoAssignOnPublish': autoAssignOnPublish,
      },
      options: _authOptions(),
    );
    final data = response.data;
    if (data is String) return data;
    return data.toString();
  }

  static Future<Map<String, dynamic>> fetchChecklistRender(String checklistId,
      {String lang = 'en'}) async {
    final response = await _dio.get(
      '/checklists/$checklistId/render?lang=$lang',
      options: _authOptions(),
    );
    return Map<String, dynamic>.from(response.data as Map);
  }

  static Future<void> patchChecklist({
    required String checklistId,
    required String title,
    required String targetType,
    required String purpose,
    required String gradeGroupId,
    required bool autoAssignOnPublish,
  }) async {
    await _dio.patch(
      '/checklists/$checklistId',
      data: {
        'title': title,
        'targetType': targetType,
        'purpose': purpose,
        'gradeGroupId': gradeGroupId,
        'autoAssignOnPublish': autoAssignOnPublish,
      },
      options: _authOptions(),
    );
  }

  static Future<void> deleteChecklist(String checklistId) async {
    await _dio.delete('/checklists/$checklistId', options: _authOptions());
  }

  static Future<void> setChecklistEnabled(
      String checklistId, bool enable) async {
    await _dio.patch(
      '/checklists/$checklistId/${enable ? 'enable' : 'disable'}',
      options: _authOptions(),
    );
  }

  static Future<void> publishChecklistVersion(
      String checklistId, Map<String, dynamic> body) async {
    await _dio.post(
      '/checklists/$checklistId/versions',
      data: body,
      options: _authOptions(),
    );
  }

  static Future<List<dynamic>> fetchChecklistVersions(
      String checklistId) async {
    final response = await _dio.get('/checklists/$checklistId/versions',
        options: _authOptions());
    return response.data as List<dynamic>;
  }

  static Future<List<dynamic>> fetchChecklistTypeDefaults() async {
    final response =
        await _dio.get('/checklists/type-defaults', options: _authOptions());
    return response.data as List<dynamic>;
  }

  static Future<void> patchChecklistTypeDefault(
      String type, Map<String, dynamic> body) async {
    await _dio.patch('/checklists/type-defaults/$type',
        data: body, options: _authOptions());
  }

  // --- Assignments (org-wide) ---

  static Future<List<dynamic>> fetchAllAssignments() async {
    final response = await _dio.get('/assignments', options: _authOptions());
    return response.data as List<dynamic>;
  }

  static Future<void> createAssignment(Map<String, dynamic> body) async {
    await _dio.post('/assignments', data: body, options: _authOptions());
  }

  static Future<void> patchAssignment(
      String assignmentId, Map<String, dynamic> body) async {
    await _dio.patch('/assignments/$assignmentId',
        data: body, options: _authOptions());
  }

  static Future<void> deleteAssignment(String assignmentId) async {
    await _dio.delete('/assignments/$assignmentId', options: _authOptions());
  }

  // --- Supervision activity ---

  static Future<List<dynamic>> fetchSupervisorSummaries() async {
    final response = await _dio.get('/supervision/supervisor-summaries',
        options: _authOptions());
    return response.data as List<dynamic>;
  }

  static Future<List<dynamic>> fetchSupervisorVisits(
      String supervisorId) async {
    final response = await _dio.get(
        '/supervision/supervisors/$supervisorId/visits',
        options: _authOptions());
    return response.data as List<dynamic>;
  }

  // --- Reports ---

  static Future<List<dynamic>> fetchSubmittedReports() async {
    final response =
        await _dio.get('/reports/submitted-reviews', options: _authOptions());
    return response.data as List<dynamic>;
  }

  static Future<List<int>> downloadReviewPdfBytes(String reviewId) async {
    final response = await _dio.get<List<int>>(
      '/reports/reviews/$reviewId/pdf',
      options: _authOptions(responseType: ResponseType.bytes),
    );
    return response.data ?? [];
  }
}
