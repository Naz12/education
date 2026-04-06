import 'package:flutter/material.dart';
import '../../core/auth/session_store.dart';
import '../../core/network/api_client.dart';

class SchoolStuffScreen extends StatefulWidget {
  const SchoolStuffScreen({super.key});

  @override
  State<SchoolStuffScreen> createState() => _SchoolStuffScreenState();
}

class _SchoolStuffScreenState extends State<SchoolStuffScreen> {
  List<dynamic> _types = [];
  List<dynamic> _subjects = [];
  List<dynamic> _items = [];
  List<dynamic> _schools = [];
  String _selectedTypeId = '';
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      _schools = await ApiClient.fetchSchools();
      _types = await ApiClient.fetchSchoolStuffTypes();
      _subjects = await ApiClient.fetchSchoolStuffSubjects();
      _items = await ApiClient.fetchSchoolStuffItems();
      if (_selectedTypeId.isEmpty && _types.isNotEmpty) {
        _selectedTypeId = (_types.first as Map)['id'].toString();
      }
    } catch (e) {
      _error = ApiClient.messageFromError(e);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Map<String, dynamic>? get _selectedType {
    for (final t in _types) {
      final m = t as Map<String, dynamic>;
      if (m['id']?.toString() == _selectedTypeId) return m;
    }
    return null;
  }

  Future<void> _addType() async {
    final name = TextEditingController();
    final desc = TextEditingController();
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('New staff type'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
                controller: name,
                decoration: const InputDecoration(labelText: 'Name')),
            TextField(
                controller: desc,
                decoration: const InputDecoration(labelText: 'Description')),
          ],
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx, false),
              child: const Text('Cancel')),
          FilledButton(
              onPressed: () => Navigator.pop(ctx, true),
              child: const Text('Create')),
        ],
      ),
    );
    if (ok != true) return;
    try {
      await ApiClient.createSchoolStuffType(
          name: name.text.trim(), description: desc.text.trim());
      await _load();
      if (mounted)
        ScaffoldMessenger.of(context)
            .showSnackBar(const SnackBar(content: Text('Type created')));
    } catch (e) {
      if (mounted)
        ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text(ApiClient.messageFromError(e))));
    }
  }

  Future<void> _addStuff() async {
    final roleName = _selectedType?['name']?.toString() ?? '';
    final fullName = TextEditingController();
    String? subjectIdPick;
    String? schoolPick;
    final username = TextEditingController();
    final password = TextEditingController();
    final email = TextEditingController();
    final phone = TextEditingController();
    final city = TextEditingController();
    final subCity = TextEditingController();
    final wereda = TextEditingController();

    await showDialog<void>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (context, setD) {
          return AlertDialog(
            title: const Text('Add school staff'),
            content: SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text('Type: $roleName',
                      style: TextStyle(color: Colors.grey.shade700)),
                  TextField(
                      controller: fullName,
                      decoration:
                          const InputDecoration(labelText: 'Full name')),
                  if (roleName == 'TEACHER') ...[
                    DropdownButtonFormField<String>(
                      initialValue: subjectIdPick,
                      decoration: const InputDecoration(labelText: 'Subject'),
                      items: _subjects.map((sub) {
                        final sm = sub as Map<String, dynamic>;
                        return DropdownMenuItem<String>(
                          value: sm['id']?.toString(),
                          child: Text(sm['name']?.toString() ?? ''),
                        );
                      }).toList(),
                      onChanged: (v) => setD(() => subjectIdPick = v),
                    ),
                    if (_subjects.isEmpty)
                      Padding(
                        padding: const EdgeInsets.only(top: 4),
                        child: Text(
                          'Add subjects via Manage subjects (toolbar).',
                          style: TextStyle(
                              fontSize: 12, color: Colors.grey.shade700),
                        ),
                      ),
                  ],
                  DropdownButtonFormField<String?>(
                    initialValue: schoolPick,
                    decoration: InputDecoration(
                      labelText: roleName == 'TEACHER' ||
                              roleName == 'SCHOOL_DIRECTOR'
                          ? 'School'
                          : 'School (optional)',
                    ),
                    items: [
                      if (roleName != 'TEACHER' &&
                          roleName != 'SCHOOL_DIRECTOR')
                        const DropdownMenuItem<String?>(
                            value: null, child: Text('— none —')),
                      ..._schools.map((s) {
                        final m = s as Map<String, dynamic>;
                        return DropdownMenuItem<String?>(
                          value: m['id']?.toString(),
                          child: Text(m['name']?.toString() ?? ''),
                        );
                      }),
                    ],
                    onChanged: (v) => setD(() => schoolPick = v),
                  ),
                  TextField(
                      controller: username,
                      decoration: const InputDecoration(
                          labelText: 'Username (optional)')),
                  TextField(
                      controller: password,
                      obscureText: true,
                      decoration: const InputDecoration(
                          labelText: 'Password (optional)')),
                  TextField(
                      controller: email,
                      decoration: const InputDecoration(labelText: 'Email')),
                  TextField(
                      controller: phone,
                      decoration: const InputDecoration(labelText: 'Phone')),
                  if (SessionStore.isSuperAdmin) ...[
                    TextField(
                        controller: city,
                        decoration: const InputDecoration(labelText: 'City')),
                    TextField(
                        controller: subCity,
                        decoration:
                            const InputDecoration(labelText: 'Sub city')),
                    TextField(
                        controller: wereda,
                        decoration: const InputDecoration(labelText: 'Wereda')),
                  ],
                ],
              ),
            ),
            actions: [
              TextButton(
                  onPressed: () => Navigator.pop(ctx),
                  child: const Text('Cancel')),
              FilledButton(
                onPressed: () async {
                  if (_selectedTypeId.isEmpty) return;
                  if (roleName == 'TEACHER') {
                    if (subjectIdPick == null || subjectIdPick!.isEmpty) {
                      ScaffoldMessenger.of(ctx).showSnackBar(const SnackBar(
                          content: Text('Select a subject')));
                      return;
                    }
                    if (schoolPick == null || schoolPick!.isEmpty) {
                      ScaffoldMessenger.of(ctx).showSnackBar(const SnackBar(
                          content: Text('Select a school')));
                      return;
                    }
                  }
                  if (roleName == 'SCHOOL_DIRECTOR' &&
                      (schoolPick == null || schoolPick!.isEmpty)) {
                    ScaffoldMessenger.of(ctx).showSnackBar(const SnackBar(
                        content: Text('Select a school')));
                    return;
                  }
                  try {
                    await ApiClient.createSchoolStuff({
                      'roleId': _selectedTypeId,
                      'fullName': fullName.text.trim(),
                      'username': username.text.trim().isEmpty
                          ? null
                          : username.text.trim(),
                      'password': password.text.isEmpty ? null : password.text,
                      'email':
                          email.text.trim().isEmpty ? null : email.text.trim(),
                      'phone':
                          phone.text.trim().isEmpty ? null : phone.text.trim(),
                      'schoolId': schoolPick,
                      'subjectId': roleName == 'TEACHER' ? subjectIdPick : null,
                      'city':
                          city.text.trim().isEmpty ? null : city.text.trim(),
                      'subCity': subCity.text.trim().isEmpty
                          ? null
                          : subCity.text.trim(),
                      'wereda': wereda.text.trim().isEmpty
                          ? null
                          : wereda.text.trim(),
                    });
                    if (ctx.mounted) Navigator.pop(ctx);
                    await _load();
                    if (!context.mounted) return;
                    ScaffoldMessenger.of(context)
                        .showSnackBar(const SnackBar(content: Text('Saved')));
                  } catch (e) {
                    if (ctx.mounted) {
                      ScaffoldMessenger.of(ctx).showSnackBar(SnackBar(
                          content: Text(ApiClient.messageFromError(e))));
                    }
                  }
                },
                child: const Text('Save'),
              ),
            ],
          );
        },
      ),
    );
  }

  Future<void> _editTeacher(Map<String, dynamic> row) async {
    final name = TextEditingController(text: row['fullName']?.toString() ?? '');
    String subjectId = row['subjectId']?.toString() ?? '';
    String schoolId = row['schoolId']?.toString() ?? '';
    final id = row['id']?.toString() ?? '';
    await showDialog<void>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (context, setD) => AlertDialog(
          title: const Text('Edit teacher'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(
                  controller: name,
                  decoration: const InputDecoration(labelText: 'Name')),
              DropdownButtonFormField<String>(
                initialValue: subjectId.isEmpty ? null : subjectId,
                decoration: const InputDecoration(labelText: 'Subject'),
                items: _subjects.map((sub) {
                  final sm = sub as Map<String, dynamic>;
                  return DropdownMenuItem<String>(
                    value: sm['id']?.toString(),
                    child: Text(sm['name']?.toString() ?? ''),
                  );
                }).toList(),
                onChanged: (v) => setD(() => subjectId = v ?? ''),
              ),
              DropdownButtonFormField<String>(
                initialValue: schoolId.isEmpty ? null : schoolId,
                decoration: const InputDecoration(labelText: 'School'),
                items: _schools.map((s) {
                  final m = s as Map<String, dynamic>;
                  return DropdownMenuItem(
                    value: m['id']?.toString(),
                    child: Text(m['name']?.toString() ?? ''),
                  );
                }).toList(),
                onChanged: (v) => setD(() => schoolId = v ?? ''),
              ),
            ],
          ),
          actions: [
            TextButton(
                onPressed: () => Navigator.pop(ctx),
                child: const Text('Cancel')),
            FilledButton(
              onPressed: () async {
                if (schoolId.isEmpty || subjectId.isEmpty) {
                  ScaffoldMessenger.of(ctx).showSnackBar(
                      const SnackBar(
                          content: Text('Select school and subject')));
                  return;
                }
                try {
                  await ApiClient.patchTeacher(
                    teacherId: id,
                    name: name.text.trim(),
                    subjectId: subjectId,
                    schoolId: schoolId,
                  );
                  if (ctx.mounted) Navigator.pop(ctx);
                  await _load();
                } catch (e) {
                  if (ctx.mounted) {
                    ScaffoldMessenger.of(ctx).showSnackBar(
                        SnackBar(content: Text(ApiClient.messageFromError(e))));
                  }
                }
              },
              child: const Text('Save'),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _promptEditType(Map<String, dynamic> m) async {
    final name = TextEditingController(text: m['name']?.toString() ?? '');
    final desc = TextEditingController(text: m['description']?.toString() ?? '');
    final id = m['id']?.toString() ?? '';
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Edit staff type'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
                controller: name,
                decoration: const InputDecoration(labelText: 'Name')),
            TextField(
                controller: desc,
                decoration: const InputDecoration(labelText: 'Description')),
          ],
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx, false),
              child: const Text('Cancel')),
          FilledButton(
              onPressed: () => Navigator.pop(ctx, true),
              child: const Text('Save')),
        ],
      ),
    );
    if (ok != true || id.isEmpty) return;
    try {
      await ApiClient.patchSchoolStuffType(
        typeId: id,
        name: name.text.trim().toUpperCase(),
        description: desc.text.trim(),
      );
      await _load();
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(const SnackBar(content: Text('Type updated')));
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text(ApiClient.messageFromError(e))));
      }
    }
  }

  Future<void> _manageStaffTypes() async {
    await _load();
    if (!mounted) return;
    await showDialog<void>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Staff types'),
        content: SizedBox(
          width: double.maxFinite,
          child: SingleChildScrollView(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  'System types cannot be edited or deleted.',
                  style: TextStyle(fontSize: 12, color: Colors.grey.shade700),
                ),
                const SizedBox(height: 8),
                ..._types.map((t) {
                  final m = t as Map<String, dynamic>;
                  final sys = m['systemRole'] == true;
                  return ListTile(
                    dense: true,
                    title: Text(m['name']?.toString() ?? ''),
                    subtitle: Text(m['description']?.toString() ?? ''),
                    trailing: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        IconButton(
                          icon: const Icon(Icons.edit_outlined, size: 20),
                          onPressed: sys
                              ? null
                              : () async {
                                  Navigator.pop(ctx);
                                  await _promptEditType(m);
                                },
                        ),
                        IconButton(
                          icon: const Icon(Icons.delete_outline, size: 20),
                          onPressed: sys
                              ? null
                              : () async {
                                  final del = await showDialog<bool>(
                                    context: context,
                                    builder: (dCtx) => AlertDialog(
                                      title: const Text('Delete type?'),
                                      content: const Text(
                                          'Only allowed if no users use this type.'),
                                      actions: [
                                        TextButton(
                                            onPressed: () =>
                                                Navigator.pop(dCtx, false),
                                            child: const Text('Cancel')),
                                        FilledButton(
                                            onPressed: () =>
                                                Navigator.pop(dCtx, true),
                                            child: const Text('Delete')),
                                      ],
                                    ),
                                  );
                                  if (del != true) return;
                                  try {
                                    await ApiClient.deleteSchoolStuffType(
                                        m['id'].toString());
                                    if (ctx.mounted) Navigator.pop(ctx);
                                    await _load();
                                    if (mounted) {
                                      ScaffoldMessenger.of(context)
                                          .showSnackBar(const SnackBar(
                                              content: Text('Type deleted')));
                                    }
                                  } catch (e) {
                                    if (mounted) {
                                      ScaffoldMessenger.of(context)
                                          .showSnackBar(SnackBar(
                                              content: Text(ApiClient
                                                  .messageFromError(e))));
                                    }
                                  }
                                },
                        ),
                      ],
                    ),
                  );
                }),
              ],
            ),
          ),
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx),
              child: const Text('Close')),
        ],
      ),
    );
  }

  Future<void> _promptEditSubject(Map<String, dynamic> m) async {
    final name = TextEditingController(text: m['name']?.toString() ?? '');
    final id = m['id']?.toString() ?? '';
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Edit subject'),
        content: TextField(
            controller: name,
            decoration: const InputDecoration(labelText: 'Name')),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx, false),
              child: const Text('Cancel')),
          FilledButton(
              onPressed: () => Navigator.pop(ctx, true),
              child: const Text('Save')),
        ],
      ),
    );
    if (ok != true || id.isEmpty) return;
    try {
      await ApiClient.patchSchoolStuffSubject(
        subjectId: id,
        name: name.text.trim(),
      );
      await _load();
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(const SnackBar(content: Text('Subject updated')));
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text(ApiClient.messageFromError(e))));
      }
    }
  }

  Future<void> _manageSubjects() async {
    await _load();
    if (!mounted) return;
    final addCtrl = TextEditingController();
    await showDialog<void>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (context, setD) => AlertDialog(
          title: const Text('Subjects'),
          content: SizedBox(
            width: double.maxFinite,
            child: SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  TextField(
                    controller: addCtrl,
                    decoration: const InputDecoration(
                      labelText: 'New subject name',
                    ),
                  ),
                  const SizedBox(height: 8),
                  FilledButton(
                    onPressed: () async {
                      final n = addCtrl.text.trim();
                      if (n.isEmpty) return;
                      try {
                        await ApiClient.createSchoolStuffSubject(name: n);
                        addCtrl.clear();
                        final list =
                            await ApiClient.fetchSchoolStuffSubjects();
                        setD(() => _subjects = list);
                        if (mounted) {
                          ScaffoldMessenger.of(context).showSnackBar(
                              const SnackBar(content: Text('Subject added')));
                        }
                      } catch (e) {
                        if (ctx.mounted) {
                          ScaffoldMessenger.of(ctx).showSnackBar(SnackBar(
                              content:
                                  Text(ApiClient.messageFromError(e))));
                        }
                      }
                    },
                    child: const Text('Add subject'),
                  ),
                  const Divider(height: 24),
                  ..._subjects.map((sub) {
                    final sm = sub as Map<String, dynamic>;
                    return ListTile(
                      dense: true,
                      title: Text(sm['name']?.toString() ?? ''),
                      trailing: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          IconButton(
                            icon: const Icon(Icons.edit_outlined, size: 20),
                            onPressed: () async {
                              Navigator.pop(ctx);
                              await _promptEditSubject(sm);
                            },
                          ),
                          IconButton(
                            icon: const Icon(Icons.delete_outline, size: 20),
                            onPressed: () async {
                              final del = await showDialog<bool>(
                                context: context,
                                builder: (dCtx) => AlertDialog(
                                  title: const Text('Delete subject?'),
                                  content: const Text(
                                      'Blocked if any teacher uses this subject.'),
                                  actions: [
                                    TextButton(
                                        onPressed: () =>
                                            Navigator.pop(dCtx, false),
                                        child: const Text('Cancel')),
                                    FilledButton(
                                        onPressed: () =>
                                            Navigator.pop(dCtx, true),
                                        child: const Text('Delete')),
                                  ],
                                ),
                              );
                              if (del != true) return;
                              try {
                                await ApiClient.deleteSchoolStuffSubject(
                                    sm['id'].toString());
                                final list =
                                    await ApiClient.fetchSchoolStuffSubjects();
                                setD(() => _subjects = list);
                                if (mounted) {
                                  ScaffoldMessenger.of(context).showSnackBar(
                                      const SnackBar(
                                          content: Text('Subject deleted')));
                                }
                              } catch (e) {
                                if (ctx.mounted) {
                                  ScaffoldMessenger.of(ctx).showSnackBar(
                                      SnackBar(
                                          content: Text(ApiClient
                                              .messageFromError(e))));
                                }
                              }
                            },
                          ),
                        ],
                      ),
                    );
                  }),
                ],
              ),
            ),
          ),
          actions: [
            TextButton(
                onPressed: () => Navigator.pop(ctx),
                child: const Text('Close')),
          ],
        ),
      ),
    );
  }

  Future<void> _editStaff(Map<String, dynamic> row) async {
    final type = row['type']?.toString() ?? '';
    if (type == 'TEACHER') {
      await _editTeacher(row);
      return;
    }
    final id = row['id']?.toString() ?? '';
    if (id.isEmpty) return;
    final name = TextEditingController(text: row['fullName']?.toString() ?? '');
    final email = TextEditingController(text: row['email']?.toString() ?? '');
    final phone = TextEditingController(text: row['phone']?.toString() ?? '');
    final city = TextEditingController(text: row['city']?.toString() ?? '');
    final subCity =
        TextEditingController(text: row['subCity']?.toString() ?? '');
    final wereda = TextEditingController(text: row['wereda']?.toString() ?? '');
    String schoolId = row['schoolId']?.toString() ?? '';

    await showDialog<void>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (context, setD) => AlertDialog(
          title: Text('Edit ${type.toLowerCase()}'),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                TextField(
                    controller: name,
                    decoration: const InputDecoration(labelText: 'Name')),
                TextField(
                    controller: email,
                    decoration: const InputDecoration(labelText: 'Email')),
                TextField(
                    controller: phone,
                    decoration: const InputDecoration(labelText: 'Phone')),
                if (type == 'SCHOOL_DIRECTOR')
                  DropdownButtonFormField<String>(
                    initialValue: schoolId.isEmpty ? null : schoolId,
                    decoration: const InputDecoration(labelText: 'School'),
                    items: _schools.map((s) {
                      final m = s as Map<String, dynamic>;
                      return DropdownMenuItem(
                        value: m['id']?.toString(),
                        child: Text(m['name']?.toString() ?? ''),
                      );
                    }).toList(),
                    onChanged: (v) => setD(() => schoolId = v ?? ''),
                  ),
                if (SessionStore.isSuperAdmin) ...[
                  TextField(
                      controller: city,
                      decoration: const InputDecoration(labelText: 'City')),
                  TextField(
                      controller: subCity,
                      decoration: const InputDecoration(labelText: 'Sub city')),
                  TextField(
                      controller: wereda,
                      decoration: const InputDecoration(labelText: 'Wereda')),
                ],
              ],
            ),
          ),
          actions: [
            TextButton(
                onPressed: () => Navigator.pop(ctx),
                child: const Text('Cancel')),
            FilledButton(
              onPressed: () async {
                try {
                  await ApiClient.patchSchoolStuff(id, {
                    'type': type,
                    'fullName': name.text.trim(),
                    'email':
                        email.text.trim().isEmpty ? null : email.text.trim(),
                    'phone':
                        phone.text.trim().isEmpty ? null : phone.text.trim(),
                    if (type == 'SCHOOL_DIRECTOR')
                      'schoolId': schoolId.isEmpty ? null : schoolId,
                    if (SessionStore.isSuperAdmin)
                      'city':
                          city.text.trim().isEmpty ? null : city.text.trim(),
                    if (SessionStore.isSuperAdmin)
                      'subCity': subCity.text.trim().isEmpty
                          ? null
                          : subCity.text.trim(),
                    if (SessionStore.isSuperAdmin)
                      'wereda': wereda.text.trim().isEmpty
                          ? null
                          : wereda.text.trim(),
                  });
                  if (ctx.mounted) Navigator.pop(ctx);
                  await _load();
                } catch (e) {
                  if (ctx.mounted) {
                    ScaffoldMessenger.of(ctx).showSnackBar(
                        SnackBar(content: Text(ApiClient.messageFromError(e))));
                  }
                }
              },
              child: const Text('Save'),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _deleteTeacher(String id) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Delete teacher?'),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx, false),
              child: const Text('Cancel')),
          FilledButton(
              onPressed: () => Navigator.pop(ctx, true),
              child: const Text('Delete')),
        ],
      ),
    );
    if (ok != true) return;
    try {
      await ApiClient.deleteTeacher(id);
      await _load();
    } catch (e) {
      if (mounted)
        ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text(ApiClient.messageFromError(e))));
    }
  }

  Future<void> _deleteStaff(Map<String, dynamic> row) async {
    final type = row['type']?.toString() ?? '';
    final id = row['id']?.toString() ?? '';
    if (id.isEmpty) return;
    if (type == 'TEACHER') {
      await _deleteTeacher(id);
      return;
    }
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text('Delete ${type.toLowerCase()}?'),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx, false),
              child: const Text('Cancel')),
          FilledButton(
              onPressed: () => Navigator.pop(ctx, true),
              child: const Text('Delete')),
        ],
      ),
    );
    if (ok != true) return;
    try {
      await ApiClient.deleteSchoolStuff(id, type);
      await _load();
    } catch (e) {
      if (mounted)
        ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text(ApiClient.messageFromError(e))));
    }
  }

  List<dynamic> get _filteredItems {
    final typeName = _selectedType?['name']?.toString();
    if (typeName == null || typeName.isEmpty) return _items;
    return _items
        .where(
            (e) => (e as Map<String, dynamic>)['type']?.toString() == typeName)
        .toList();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('School stuff'),
        actions: [
          IconButton(
              icon: const Icon(Icons.category_outlined), onPressed: _addType),
          IconButton(
              icon: const Icon(Icons.tune),
              tooltip: 'Manage types',
              onPressed: _manageStaffTypes),
          IconButton(
              icon: const Icon(Icons.menu_book_outlined),
              tooltip: 'Manage subjects',
              onPressed: _manageSubjects),
          IconButton(
              icon: const Icon(Icons.person_add_alt_outlined),
              onPressed: _selectedTypeId.isEmpty ? null : _addStuff),
          IconButton(icon: const Icon(Icons.refresh), onPressed: _load),
        ],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
              ? Center(child: Text(_error!))
              : Column(
                  children: [
                    Padding(
                      padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
                      child: DropdownButtonFormField<String>(
                        initialValue:
                            _selectedTypeId.isEmpty ? null : _selectedTypeId,
                        decoration:
                            const InputDecoration(labelText: 'Staff type'),
                        isExpanded: true,
                        items: _types.map((t) {
                          final m = t as Map<String, dynamic>;
                          return DropdownMenuItem(
                            value: m['id']?.toString(),
                            child: Text(m['name']?.toString() ?? ''),
                          );
                        }).toList(),
                        onChanged: (v) =>
                            setState(() => _selectedTypeId = v ?? ''),
                      ),
                    ),
                    Expanded(
                      child: RefreshIndicator(
                        onRefresh: _load,
                        child: ListView.builder(
                          padding: const EdgeInsets.all(12),
                          itemCount: _filteredItems.length,
                          itemBuilder: (context, i) {
                            final m = _filteredItems[i] as Map<String, dynamic>;
                            final type = m['type']?.toString() ?? '';
                            return Card(
                              child: ListTile(
                                title: Text(m['fullName']?.toString() ?? ''),
                                subtitle: Text(
                                  '$type · ${m['schoolName'] ?? ''} · ${m['subject'] ?? ''}',
                                ),
                                trailing: Row(
                                  mainAxisSize: MainAxisSize.min,
                                  children: [
                                    IconButton(
                                        icon: const Icon(Icons.edit_outlined),
                                        onPressed: () => _editStaff(m)),
                                    IconButton(
                                        icon: const Icon(Icons.delete_outline),
                                        onPressed: () => _deleteStaff(m)),
                                  ],
                                ),
                              ),
                            );
                          },
                        ),
                      ),
                    ),
                  ],
                ),
    );
  }
}
