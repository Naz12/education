import 'package:flutter/material.dart';
import 'package:open_file/open_file.dart';
import 'package:path_provider/path_provider.dart';
import 'dart:io';
import '../../core/grades/grade_codes.dart';
import '../../core/network/api_client.dart';

class AssignmentsAdminScreen extends StatefulWidget {
  const AssignmentsAdminScreen({super.key});

  @override
  State<AssignmentsAdminScreen> createState() => _AssignmentsAdminScreenState();
}

class _AssignmentsAdminScreenState extends State<AssignmentsAdminScreen> {
  List<dynamic> _items = [];
  List<dynamic> _checklists = [];
  List<dynamic> _versions = [];
  List<dynamic> _supervisors = [];
  List<dynamic> _schools = [];
  List<dynamic> _teachers = [];
  List<dynamic> _schoolStuff = [];
  bool _loading = true;
  String? _error;

  String _formChecklistId = '';
  String _formVersionId = '';
  String _formSupervisorId = '';
  String _targetType = 'SCHOOL';
  String _schoolId = '';
  String _teacherId = '';
  String _targetGradeCode = '';
  String _staffUserId = '';
  String _dueLocal = '';
  String? _editingId;
  String _bulkChecklistId = '';
  String _bulkVersionId = '';
  final Set<String> _bulkSchoolIds = <String>{};
  final Set<String> _bulkSupervisorIds = <String>{};
  String _bulkDueLocal = '';

  @override
  void initState() {
    super.initState();
    _bootstrap();
  }

  Future<void> _bootstrap() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      await Future.wait([
        _loadAssignments(),
        _dioLoadRefs(),
      ]);
    } catch (e) {
      _error = ApiClient.messageFromError(e);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _loadAssignments() async {
    _items = await ApiClient.fetchAllAssignments();
  }

  Future<void> _dioLoadRefs() async {
    _checklists = await ApiClient.fetchChecklists();
    _supervisors = await ApiClient.fetchSupervisorsDirectory();
    _schools = await ApiClient.fetchSchools();
    _teachers = await ApiClient.fetchTeachers();
    _schoolStuff = await ApiClient.fetchSchoolStuffItems();
  }

  bool _assignmentNeedsSchool(String t) =>
      t == 'SCHOOL' || t == 'DIRECTOR' || t == 'SCHOOL_STAFF';

  Future<void> _loadVersions(String checklistId) async {
    if (checklistId.isEmpty) {
      setState(() => _versions = []);
      return;
    }
    try {
      _versions = await ApiClient.fetchChecklistVersions(checklistId);
      if (mounted) setState(() {});
    } catch (_) {
      _versions = [];
    }
  }

  void _openForm({Map<String, dynamic>? row}) {
    if (row == null) {
      _editingId = null;
      _formChecklistId = '';
      _formVersionId = '';
      _formSupervisorId = '';
      _targetType = 'SCHOOL';
      _schoolId = '';
      _teacherId = '';
      _targetGradeCode = '';
      _staffUserId = '';
      _dueLocal = '';
    } else {
      _editingId = row['id']?.toString();
      _formChecklistId = row['checklistId']?.toString() ?? '';
      _formVersionId = row['checklistVersionId']?.toString() ?? '';
      _formSupervisorId = row['supervisorId']?.toString() ?? '';
      _targetType = row['targetType']?.toString() ?? 'SCHOOL';
      _schoolId = row['schoolId']?.toString() ?? '';
      _teacherId = row['teacherId']?.toString() ?? '';
      _targetGradeCode = row['targetGradeCode']?.toString() ?? '';
      _staffUserId = row['staffUserId']?.toString() ?? '';
      final due = row['dueDate']?.toString();
      _dueLocal = _toLocalDatetime(due);
    }
    _loadVersions(_formChecklistId);
    setState(() {});
    final dueCtrl = TextEditingController(text: _dueLocal);
    showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      builder: (ctx) => Padding(
        padding: EdgeInsets.only(
          left: 16,
          right: 16,
          top: 16,
          bottom: MediaQuery.of(ctx).viewInsets.bottom + 16,
        ),
        child: StatefulBuilder(
          builder: (context, setModal) {
            return SingleChildScrollView(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Text(_editingId == null ? 'New assignment' : 'Edit assignment',
                      style: Theme.of(context).textTheme.titleMedium),
                  const SizedBox(height: 12),
                  DropdownButtonFormField<String>(
                    initialValue: _formChecklistId.isEmpty ? null : _formChecklistId,
                    decoration: const InputDecoration(labelText: 'Checklist'),
                    items: _checklists
                        .map((c) {
                          final m = c as Map<String, dynamic>;
                          return DropdownMenuItem(
                            value: m['id']?.toString(),
                            child: Text(m['title']?.toString() ?? ''),
                          );
                        })
                        .toList(),
                    onChanged: (v) {
                      setModal(() {
                        _formChecklistId = v ?? '';
                        _formVersionId = '';
                      });
                      _loadVersions(_formChecklistId).then((_) => setModal(() {}));
                    },
                  ),
                  DropdownButtonFormField<String>(
                    initialValue: _formVersionId.isEmpty ? null : _formVersionId,
                    decoration: const InputDecoration(labelText: 'Version'),
                    items: _versions
                        .map((c) {
                          final m = c as Map<String, dynamic>;
                          return DropdownMenuItem(
                            value: m['id']?.toString(),
                            child: Text('v${m['versionNo']} (${m['status']})'),
                          );
                        })
                        .toList(),
                    onChanged: (v) => setModal(() => _formVersionId = v ?? ''),
                  ),
                  DropdownButtonFormField<String>(
                    initialValue: _formSupervisorId.isEmpty ? null : _formSupervisorId,
                    decoration: const InputDecoration(labelText: 'Supervisor'),
                    items: _supervisors
                        .map((c) {
                          final m = c as Map<String, dynamic>;
                          return DropdownMenuItem(
                            value: m['id']?.toString(),
                            child: Text(m['fullName']?.toString() ?? ''),
                          );
                        })
                        .toList(),
                    onChanged: (v) => setModal(() => _formSupervisorId = v ?? ''),
                  ),
                  DropdownButtonFormField<String>(
                    initialValue: _targetType,
                    decoration: const InputDecoration(labelText: 'Target'),
                    items: const [
                      DropdownMenuItem(value: 'SCHOOL', child: Text('School')),
                      DropdownMenuItem(value: 'TEACHER', child: Text('Teacher')),
                      DropdownMenuItem(value: 'DIRECTOR', child: Text('Director')),
                      DropdownMenuItem(value: 'SCHOOL_STAFF', child: Text('School staff')),
                    ],
                    onChanged: (v) => setModal(() {
                      _targetType = v ?? 'SCHOOL';
                      if (!_assignmentNeedsSchool(_targetType)) _schoolId = '';
                      if (_targetType != 'TEACHER') _teacherId = '';
                      if (_targetType != 'SCHOOL_STAFF') _staffUserId = '';
                    }),
                  ),
                  if (_assignmentNeedsSchool(_targetType))
                    DropdownButtonFormField<String>(
                      initialValue: _schoolId.isEmpty ? null : _schoolId,
                      decoration: const InputDecoration(labelText: 'School'),
                      items: _schools
                          .map((c) {
                            final m = c as Map<String, dynamic>;
                            final grades = GradeCodes.parseList(m['supportedGradeCodes']).join(', ');
                            return DropdownMenuItem(
                              value: m['id']?.toString(),
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  Text(m['name']?.toString() ?? ''),
                                  Text(
                                    grades.isEmpty ? 'Grades: —' : 'Grades: $grades',
                                    style: Theme.of(context).textTheme.bodySmall?.copyWith(color: Theme.of(context).hintColor),
                                  ),
                                ],
                              ),
                            );
                          })
                          .toList(),
                      onChanged: (v) => setModal(() => _schoolId = v ?? ''),
                    ),
                  if (_targetType == 'TEACHER')
                    DropdownButtonFormField<String>(
                      initialValue: _teacherId.isEmpty ? null : _teacherId,
                      decoration: const InputDecoration(labelText: 'Teacher'),
                      items: _teachers
                          .map((c) {
                            final m = c as Map<String, dynamic>;
                            return DropdownMenuItem(
                              value: m['id']?.toString(),
                              child: Text('${m['name']} · ${m['subject']}'),
                            );
                          })
                          .toList(),
                      onChanged: (v) => setModal(() => _teacherId = v ?? ''),
                    ),
                  if (_targetType == 'TEACHER')
                    DropdownButtonFormField<String>(
                      initialValue: _targetGradeCode.isEmpty ? null : _targetGradeCode,
                      decoration: const InputDecoration(labelText: 'Target grade'),
                      items: GradeCodes.ordered
                          .map((g) => DropdownMenuItem(value: g, child: Text(g)))
                          .toList(),
                      onChanged: (v) => setModal(() => _targetGradeCode = v ?? ''),
                    ),
                  if (_targetType == 'SCHOOL_STAFF')
                    DropdownButtonFormField<String>(
                      initialValue: _staffUserId.isEmpty ? null : _staffUserId,
                      decoration: const InputDecoration(labelText: 'Staff member'),
                      items: _schoolStuff
                          .where((x) {
                            final m = x as Map<String, dynamic>;
                            final ty = m['type']?.toString() ?? '';
                            return ty.isNotEmpty && ty != 'TEACHER';
                          })
                          .map((x) {
                            final m = x as Map<String, dynamic>;
                            final id = m['id']?.toString() ?? '';
                            final name = m['fullName']?.toString() ?? '';
                            final ty = m['type']?.toString() ?? '';
                            return DropdownMenuItem(value: id, child: Text('$name ($ty)'));
                          })
                          .toList(),
                      onChanged: (v) => setModal(() => _staffUserId = v ?? ''),
                    ),
                  TextFormField(
                    decoration: const InputDecoration(labelText: 'Due (datetime-local)'),
                    controller: dueCtrl,
                  ),
                  const SizedBox(height: 16),
                  FilledButton(
                    onPressed: () async {
                      if (_formChecklistId.isEmpty || _formVersionId.isEmpty || _formSupervisorId.isEmpty) return;
                      if (_assignmentNeedsSchool(_targetType) && _schoolId.isEmpty) return;
                      if (_targetType == 'TEACHER' && _teacherId.isEmpty) return;
                      if (_targetType == 'TEACHER' && _targetGradeCode.isEmpty) return;
                      if (_targetType == 'SCHOOL_STAFF' && _staffUserId.isEmpty) return;
                      final payload = <String, dynamic>{
                        'checklistId': _formChecklistId,
                        'checklistVersionId': _formVersionId,
                        'supervisorId': _formSupervisorId,
                        'targetType': _targetType,
                        'schoolId': _assignmentNeedsSchool(_targetType) ? _schoolId : null,
                        'teacherId': _targetType == 'TEACHER' ? _teacherId : null,
                        'targetGradeCode': _targetType == 'TEACHER' ? _targetGradeCode : null,
                        'staffUserId': _targetType == 'SCHOOL_STAFF' ? _staffUserId : null,
                        'dueDate': dueCtrl.text.trim().isEmpty
                            ? null
                            : DateTime.tryParse(dueCtrl.text.trim())?.toUtc().toIso8601String(),
                      };
                      try {
                        if (_editingId == null) {
                          await ApiClient.createAssignment(payload);
                        } else {
                          await ApiClient.patchAssignment(_editingId!, payload);
                        }
                        if (ctx.mounted) Navigator.pop(ctx);
                        await _loadAssignments();
                        if (mounted) setState(() {});
                        if (!context.mounted) return;
                        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Saved')));
                      } catch (e) {
                        if (ctx.mounted) {
                          ScaffoldMessenger.of(ctx).showSnackBar(SnackBar(content: Text(ApiClient.messageFromError(e))));
                        }
                      }
                    },
                    child: Text(_editingId == null ? 'Create' : 'Save'),
                  ),
                ],
              ),
            );
          },
        ),
      ),
    ).whenComplete(dueCtrl.dispose);
  }

  String _toLocalDatetime(String? iso) {
    if (iso == null || iso.isEmpty) return '';
    final d = DateTime.tryParse(iso);
    if (d == null) return '';
    return d.toUtc().toIso8601String().replaceFirst('Z', '').substring(0, 16);
  }

  Future<void> _delete(String id) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Delete assignment?'),
        content: const Text('Only PENDING assignments can be deleted.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Delete')),
        ],
      ),
    );
    if (ok != true) return;
    try {
      await ApiClient.deleteAssignment(id);
      await _loadAssignments();
      if (mounted) setState(() {});
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(ApiClient.messageFromError(e))));
      }
    }
  }

  Future<void> _openBulkCreate() async {
    _bulkChecklistId = '';
    _bulkVersionId = '';
    _bulkSchoolIds.clear();
    _bulkSupervisorIds.clear();
    _bulkDueLocal = '';
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      builder: (ctx) => Padding(
        padding: EdgeInsets.only(
          left: 16,
          right: 16,
          top: 16,
          bottom: MediaQuery.of(ctx).viewInsets.bottom + 16,
        ),
        child: StatefulBuilder(
          builder: (context, setModal) {
            return SingleChildScrollView(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Text('Bulk create assignments',
                      style: Theme.of(context).textTheme.titleMedium),
                  const SizedBox(height: 12),
                  DropdownButtonFormField<String>(
                    initialValue: _bulkChecklistId.isEmpty ? null : _bulkChecklistId,
                    decoration: const InputDecoration(labelText: 'Checklist'),
                    items: _checklists.map((c) {
                      final m = c as Map<String, dynamic>;
                      return DropdownMenuItem(
                        value: m['id']?.toString(),
                        child: Text(m['title']?.toString() ?? ''),
                      );
                    }).toList(),
                    onChanged: (v) async {
                      setModal(() {
                        _bulkChecklistId = v ?? '';
                        _bulkVersionId = '';
                      });
                      await _loadVersions(_bulkChecklistId);
                      setModal(() {});
                    },
                  ),
                  DropdownButtonFormField<String>(
                    initialValue: _bulkVersionId.isEmpty ? null : _bulkVersionId,
                    decoration: const InputDecoration(labelText: 'Version'),
                    items: _versions.map((c) {
                      final m = c as Map<String, dynamic>;
                      return DropdownMenuItem(
                        value: m['id']?.toString(),
                        child: Text('v${m['versionNo']} (${m['status']})'),
                      );
                    }).toList(),
                    onChanged: (v) => setModal(() => _bulkVersionId = v ?? ''),
                  ),
                  const SizedBox(height: 8),
                  Text('Schools (optional)',
                      style: Theme.of(context).textTheme.titleSmall),
                  ..._schools.map((s) {
                    final m = s as Map<String, dynamic>;
                    final id = m['id']?.toString() ?? '';
                    final checked = _bulkSchoolIds.contains(id);
                    return CheckboxListTile(
                      dense: true,
                      value: checked,
                      title: Text(m['name']?.toString() ?? ''),
                      onChanged: (v) => setModal(() {
                        if (v == true) _bulkSchoolIds.add(id);
                        if (v == false) _bulkSchoolIds.remove(id);
                      }),
                    );
                  }),
                  const SizedBox(height: 8),
                  Text('Supervisors pool (optional)',
                      style: Theme.of(context).textTheme.titleSmall),
                  ..._supervisors.map((u) {
                    final m = u as Map<String, dynamic>;
                    final id = m['id']?.toString() ?? '';
                    final checked = _bulkSupervisorIds.contains(id);
                    return CheckboxListTile(
                      dense: true,
                      value: checked,
                      title: Text(m['fullName']?.toString() ?? ''),
                      onChanged: (v) => setModal(() {
                        if (v == true) _bulkSupervisorIds.add(id);
                        if (v == false) _bulkSupervisorIds.remove(id);
                      }),
                    );
                  }),
                  const SizedBox(height: 8),
                  Text(
                    'Grades follow the checklist grade group; matching uses each school as before.',
                    style: Theme.of(context).textTheme.bodySmall?.copyWith(
                          color: Theme.of(context).colorScheme.onSurfaceVariant,
                        ),
                  ),
                  const SizedBox(height: 12),
                  TextFormField(
                    initialValue: _bulkDueLocal,
                    decoration: const InputDecoration(
                        labelText: 'Due override (datetime-local)'),
                    onChanged: (v) => _bulkDueLocal = v,
                  ),
                  const SizedBox(height: 12),
                  FilledButton(
                    onPressed: () async {
                      if (_bulkChecklistId.isEmpty || _bulkVersionId.isEmpty) return;
                      try {
                        final result = await ApiClient.bulkCreateAssignments({
                          'checklistId': _bulkChecklistId,
                          'checklistVersionId': _bulkVersionId,
                          'schoolIds': _bulkSchoolIds.toList(),
                          'supervisorIds': _bulkSupervisorIds.toList(),
                          'dueDate': _bulkDueLocal.trim().isEmpty
                              ? null
                              : DateTime.tryParse(_bulkDueLocal)?.toUtc().toIso8601String(),
                        });
                        if (ctx.mounted) Navigator.pop(ctx);
                        await _bootstrap();
                        if (!context.mounted) return;
                        ScaffoldMessenger.of(context).showSnackBar(
                          SnackBar(
                            content: Text(
                              'Bulk created ${result['created'] ?? 0}. '
                              'Skipped duplicates: ${result['skippedDuplicate'] ?? 0}, '
                              'no supervisor: ${result['skippedNoEligibleSupervisor'] ?? 0}, '
                              'out of scope: ${result['skippedOutOfScope'] ?? 0}.',
                            ),
                          ),
                        );
                      } catch (e) {
                        if (ctx.mounted) {
                          ScaffoldMessenger.of(ctx).showSnackBar(
                              SnackBar(content: Text(ApiClient.messageFromError(e))));
                        }
                      }
                    },
                    child: const Text('Create bulk assignments'),
                  ),
                ],
              ),
            );
          },
        ),
      ),
    );
  }

  Future<void> _exportAssignments() async {
    try {
      final bytes = await ApiClient.downloadAssignmentsExport();
      final dir = await getTemporaryDirectory();
      final file = File('${dir.path}/assignments-export.xlsx');
      await file.writeAsBytes(bytes, flush: true);
      await OpenFile.open(file.path);
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text(ApiClient.messageFromError(e))));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Assignments'),
        actions: [
          IconButton(icon: const Icon(Icons.add), onPressed: () => _openForm()),
          IconButton(icon: const Icon(Icons.playlist_add_check_circle_outlined), tooltip: 'Bulk assign', onPressed: _openBulkCreate),
          IconButton(icon: const Icon(Icons.download_outlined), tooltip: 'Export', onPressed: _exportAssignments),
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: () async {
              setState(() => _loading = true);
              try {
                await _bootstrap();
              } finally {
                if (mounted) setState(() => _loading = false);
              }
            },
          ),
        ],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
              ? Center(child: Text(_error!))
              : RefreshIndicator(
                  onRefresh: _bootstrap,
                  child: ListView.builder(
                    padding: const EdgeInsets.all(12),
                    itemCount: _items.length,
                    itemBuilder: (context, i) {
                      final m = _items[i] as Map<String, dynamic>;
                      final pending = m['status']?.toString() == 'PENDING';
                      final id = m['id']?.toString() ?? '';
                      return Card(
                        child: ListTile(
                          title: Text(
                            '${m['targetType']}${m['targetGradeCode'] == null ? '' : ' · ${m['targetGradeCode']}'} · ${m['status']}',
                          ),
                          subtitle: Text('Due: ${m['dueDate'] ?? '—'}'),
                          trailing: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              IconButton(
                                icon: const Icon(Icons.edit_outlined),
                                onPressed: pending ? () => _openForm(row: m) : null,
                              ),
                              IconButton(
                                icon: const Icon(Icons.delete_outline),
                                onPressed: pending ? () => _delete(id) : null,
                              ),
                            ],
                          ),
                        ),
                      );
                    },
                  ),
                ),
    );
  }
}
