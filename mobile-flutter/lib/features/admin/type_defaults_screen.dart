import 'dart:convert';
import 'package:flutter/material.dart';
import '../../core/auth/session_store.dart';
import '../../core/network/api_client.dart';

/// Super-admin / coordinator: edit checklist item type defaults (matches web capability).
class TypeDefaultsScreen extends StatefulWidget {
  const TypeDefaultsScreen({super.key});

  @override
  State<TypeDefaultsScreen> createState() => _TypeDefaultsScreenState();
}

class _TypeDefaultsScreenState extends State<TypeDefaultsScreen> {
  List<dynamic> _rows = [];
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    if (SessionStore.isSuperAdmin) {
      _load();
    } else {
      _loading = false;
    }
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      _rows = await ApiClient.fetchChecklistTypeDefaults();
    } catch (e) {
      _error = ApiClient.messageFromError(e);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _edit(Map<String, dynamic> row) async {
    final type = row['itemType']?.toString() ?? '';
    final optCtrl = TextEditingController(text: const JsonEncoder.withIndent('  ').convert(row['options'] ?? {}));
    final valCtrl = TextEditingController(text: const JsonEncoder.withIndent('  ').convert(row['validation'] ?? {}));
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text('Defaults · $type'),
        content: SingleChildScrollView(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            mainAxisSize: MainAxisSize.min,
            children: [
              const Text('Options JSON'),
              TextField(controller: optCtrl, maxLines: 8),
              const SizedBox(height: 12),
              const Text('Validation JSON'),
              TextField(controller: valCtrl, maxLines: 6),
            ],
          ),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Save')),
        ],
      ),
    );
    if (ok != true) {
      optCtrl.dispose();
      valCtrl.dispose();
      return;
    }
    try {
      final options = jsonDecode(optCtrl.text);
      final validation = jsonDecode(valCtrl.text);
      await ApiClient.patchChecklistTypeDefault(type, {
        'options': options is Map ? Map<String, dynamic>.from(options) : <String, dynamic>{},
        'validation': validation is Map ? Map<String, dynamic>.from(validation) : <String, dynamic>{},
      });
      await _load();
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Saved')));
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(ApiClient.messageFromError(e))));
    } finally {
      optCtrl.dispose();
      valCtrl.dispose();
    }
  }

  @override
  Widget build(BuildContext context) {
    if (!SessionStore.isSuperAdmin) {
      return Scaffold(
        appBar: AppBar(title: const Text('Item type defaults')),
        body: Center(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Text(
              'Sign in as super admin to edit default options and validation for each checklist item type.',
              textAlign: TextAlign.center,
              style: TextStyle(color: Colors.grey.shade700),
            ),
          ),
        ),
      );
    }

    return Scaffold(
      appBar: AppBar(
        title: const Text('Item type defaults'),
        actions: [IconButton(icon: const Icon(Icons.refresh), onPressed: _load)],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
              ? Center(child: Text(_error!))
              : ListView.builder(
                  padding: const EdgeInsets.all(12),
                  itemCount: _rows.length,
                  itemBuilder: (context, i) {
                    final m = _rows[i] as Map<String, dynamic>;
                    final t = m['itemType']?.toString() ?? '';
                    return Card(
                      child: ListTile(
                        title: Text(t),
                        subtitle: Text(const JsonEncoder().convert(m['options'] ?? {}), maxLines: 2, overflow: TextOverflow.ellipsis),
                        trailing: const Icon(Icons.chevron_right),
                        onTap: () => _edit(m),
                      ),
                    );
                  },
                ),
    );
  }
}
