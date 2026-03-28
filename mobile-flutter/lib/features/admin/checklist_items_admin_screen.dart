import 'dart:convert';
import 'package:flutter/material.dart';
import '../../core/network/api_client.dart';

/// Mirrors web “Checklist items” — select checklist, edit draft items, publish version.
class ChecklistItemsAdminScreen extends StatefulWidget {
  const ChecklistItemsAdminScreen({super.key, this.initialChecklistId});

  final String? initialChecklistId;

  @override
  State<ChecklistItemsAdminScreen> createState() => _ChecklistItemsAdminScreenState();
}

class _ChecklistItemsAdminScreenState extends State<ChecklistItemsAdminScreen> {
  static const _langs = ['en', 'am'];

  List<dynamic> _checklists = [];
  Map<String, Map<String, dynamic>>? _typeDefaults;
  String _checklistId = '';
  String _editingLang = 'en';
  List<Map<String, dynamic>> _draft = [];
  bool _loading = true;
  bool _busy = false;
  String? _error;
  bool _skipAutoAssignment = false;

  @override
  void initState() {
    super.initState();
    _checklistId = widget.initialChecklistId ?? '';
    _bootstrap();
  }

  Future<void> _bootstrap() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      _checklists = await ApiClient.fetchChecklists();
      final defs = await ApiClient.fetchChecklistTypeDefaults();
      final map = <String, Map<String, dynamic>>{};
      for (final row in defs) {
        final m = row as Map<String, dynamic>;
        final t = m['itemType']?.toString() ?? '';
        map[t] = {
          'options': m['options'] is Map ? Map<String, dynamic>.from(m['options'] as Map) : <String, dynamic>{},
          'validation': m['validation'] is Map ? Map<String, dynamic>.from(m['validation'] as Map) : <String, dynamic>{},
        };
      }
      _typeDefaults = map;
      if (_checklistId.isEmpty && _checklists.isNotEmpty) {
        _checklistId = (_checklists.first as Map)['id']?.toString() ?? '';
      }
      if (_checklistId.isNotEmpty) await _loadDraft();
    } catch (e) {
      _error = ApiClient.messageFromError(e);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Map<String, dynamic> _defaultsForType(String type) {
    final d = _typeDefaults?[type];
    if (d == null) return {'options': <String, dynamic>{}, 'validation': <String, dynamic>{'required': false}};
    return {
      'options': Map<String, dynamic>.from(d['options'] as Map? ?? {}),
      'validation': Map<String, dynamic>.from(d['validation'] as Map? ?? {}),
    };
  }

  Map<String, dynamic> _safeParse(String? txt, Map<String, dynamic> fallback) {
    if (txt == null || txt.trim().isEmpty) return Map<String, dynamic>.from(fallback);
    try {
      final o = jsonDecode(txt);
      if (o is Map) return Map<String, dynamic>.from(o);
    } catch (_) {}
    return Map<String, dynamic>.from(fallback);
  }

  Future<void> _loadDraft() async {
    if (_checklistId.isEmpty) return;
    setState(() => _busy = true);
    try {
      final data = await ApiClient.fetchChecklistRender(_checklistId, lang: 'en');
      final items = (data['items'] as List?) ?? [];
      _draft = items.asMap().entries.map((e) {
        final it = e.value as Map<String, dynamic>;
        final idx = e.key;
        final ql = it['questionLocalized'];
        final enQ = (ql is Map ? ql['en'] : null)?.toString() ?? it['question']?.toString() ?? '';
        final amQ = (ql is Map ? ql['am'] : null)?.toString() ?? '';
        return {
          'question': enQ,
          'questionLocalizedText': jsonEncode({'en': enQ, 'am': amQ}),
          'type': it['type']?.toString() ?? 'TEXT',
          'groupKey': it['groupKey']?.toString() ?? 'General',
          'order': it['order'] ?? idx + 1,
          'optionsText': jsonEncode(it['options'] ?? {}),
          'validationText': jsonEncode(it['validation'] ?? {'required': false}),
        };
      }).toList();
      _error = null;
    } catch (e) {
      _error = ApiClient.messageFromError(e);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  void _addItem() {
    final d = _defaultsForType('TEXT');
    setState(() {
      _draft.add({
        'question': '',
        'questionLocalizedText': jsonEncode({'en': '', 'am': ''}),
        'type': 'TEXT',
        'groupKey': 'General',
        'order': _draft.length + 1,
        'optionsText': jsonEncode(d['options']),
        'validationText': jsonEncode(d['validation']),
      });
    });
  }

  Future<void> _publish() async {
    if (_checklistId.isEmpty) return;
    setState(() => _busy = true);
    try {
      final items = <Map<String, dynamic>>[];
      for (var i = 0; i < _draft.length; i++) {
        final item = _draft[i];
        items.add({
          'question': item['question']?.toString() ?? '',
          'questionLocalized': _safeParse(item['questionLocalizedText']?.toString(), {'en': '', 'am': ''}),
          'type': item['type']?.toString() ?? 'TEXT',
          'options': _safeParse(item['optionsText']?.toString(), {}),
          'validation': _safeParse(item['validationText']?.toString(), {'required': false}),
          'groupKey': item['groupKey']?.toString().isEmpty == true ? null : item['groupKey']?.toString(),
          'order': int.tryParse(item['order']?.toString() ?? '') ?? i + 1,
        });
      }
      await ApiClient.publishChecklistVersion(_checklistId, {
        'items': items,
        'skipAutoAssignment': _skipAutoAssignment,
      });
      if (mounted) {
        setState(() => _skipAutoAssignment = false);
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Version published')));
      }
      await _loadDraft();
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(ApiClient.messageFromError(e))));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  void _syncQuestionFromLang(Map<String, dynamic> item) {
    final loc = _safeParse(item['questionLocalizedText']?.toString(), {'en': '', 'am': ''});
    item['question'] = (loc['en'] ?? '').toString();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Checklist items'),
        actions: [
          IconButton(icon: const Icon(Icons.refresh), onPressed: _bootstrap),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: _busy || _checklistId.isEmpty ? null : _publish,
        icon: const Icon(Icons.publish),
        label: const Text('Publish'),
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _error != null && _draft.isEmpty
              ? Center(child: Padding(padding: const EdgeInsets.all(24), child: Text(_error!)))
              : Column(
                  children: [
                    Padding(
                      padding: const EdgeInsets.fromLTRB(16, 12, 16, 0),
                      child: DropdownButtonFormField<String>(
                        initialValue: _checklistId.isEmpty ? null : _checklistId,
                        decoration: const InputDecoration(labelText: 'Checklist'),
                        isExpanded: true,
                        items: _checklists
                            .map((c) {
                              final m = c as Map<String, dynamic>;
                              return DropdownMenuItem(
                                value: m['id']?.toString(),
                                child: Text(m['title']?.toString() ?? ''),
                              );
                            })
                            .toList(),
                        onChanged: (v) async {
                          setState(() => _checklistId = v ?? '');
                          await _loadDraft();
                          setState(() {});
                        },
                      ),
                    ),
                    Padding(
                      padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
                      child: DropdownButtonFormField<String>(
                        initialValue: _editingLang,
                        decoration: const InputDecoration(labelText: 'Edit language'),
                        items: _langs
                            .map((l) => DropdownMenuItem(value: l, child: Text(l == 'en' ? 'English' : 'Amharic')))
                            .toList(),
                        onChanged: (v) => setState(() => _editingLang = v ?? 'en'),
                      ),
                    ),
                    Padding(
                      padding: const EdgeInsets.fromLTRB(16, 0, 16, 0),
                      child: SwitchListTile(
                        contentPadding: EdgeInsets.zero,
                        title: const Text('Skip auto-assignment for this publish'),
                        subtitle: const Text('Manual assignments still work'),
                        value: _skipAutoAssignment,
                        onChanged: _busy ? null : (v) => setState(() => _skipAutoAssignment = v),
                      ),
                    ),
                    Padding(
                      padding: const EdgeInsets.all(16),
                      child: Row(
                        children: [
                          FilledButton.tonalIcon(
                            onPressed: _addItem,
                            icon: const Icon(Icons.add),
                            label: const Text('Add item'),
                          ),
                          const SizedBox(width: 12),
                          if (_busy) const SizedBox(width: 24, height: 24, child: CircularProgressIndicator(strokeWidth: 2)),
                        ],
                      ),
                    ),
                    Expanded(
                      child: ListView.builder(
                        padding: const EdgeInsets.fromLTRB(12, 0, 12, 88),
                        itemCount: _draft.length,
                        itemBuilder: (context, index) {
                          final item = _draft[index];
                          final loc = _safeParse(item['questionLocalizedText']?.toString(), {'en': '', 'am': ''});
                          final qForLang = (loc[_editingLang] ?? '').toString();
                          return Card(
                            child: ExpansionTile(
                              title: Text('Item ${index + 1} · ${item['type']}'),
                              childrenPadding: const EdgeInsets.all(12),
                              children: [
                                TextFormField(
                                  key: ValueKey('q-$index-$_editingLang'),
                                  initialValue: qForLang,
                                  decoration: InputDecoration(labelText: 'Question ($_editingLang)'),
                                  onChanged: (v) {
                                    final locMap = _safeParse(item['questionLocalizedText']?.toString(), {'en': '', 'am': ''});
                                    locMap[_editingLang] = v;
                                    item['questionLocalizedText'] = jsonEncode(locMap);
                                    _syncQuestionFromLang(item);
                                  },
                                ),
                                const SizedBox(height: 8),
                                DropdownButtonFormField<String>(
                                  initialValue: item['type']?.toString(),
                                  decoration: const InputDecoration(labelText: 'Type'),
                                  isExpanded: true,
                                  items: const [
                                    'TEXT',
                                    'YES_NO',
                                    'SINGLE_CHOICE',
                                    'MULTIPLE_CHOICE',
                                    'RATING',
                                    'PHOTO',
                                  ].map((t) => DropdownMenuItem(value: t, child: Text(t))).toList(),
                                  onChanged: (v) {
                                    if (v == null) return;
                                    final defs = _defaultsForType(v);
                                    setState(() {
                                      item['type'] = v;
                                      item['optionsText'] = jsonEncode(defs['options']);
                                      item['validationText'] = jsonEncode(defs['validation']);
                                    });
                                  },
                                ),
                                TextFormField(
                                  key: ValueKey('gk-$index'),
                                  initialValue: item['groupKey']?.toString() ?? '',
                                  decoration: const InputDecoration(labelText: 'Group'),
                                  onChanged: (v) => item['groupKey'] = v,
                                ),
                                TextFormField(
                                  key: ValueKey('ord-$index'),
                                  initialValue: item['order']?.toString() ?? '',
                                  decoration: const InputDecoration(labelText: 'Order'),
                                  keyboardType: TextInputType.number,
                                  onChanged: (v) => item['order'] = int.tryParse(v) ?? item['order'],
                                ),
                                TextFormField(
                                  key: ValueKey('opt-$index-${item['optionsText']}'),
                                  initialValue: item['optionsText']?.toString() ?? '{}',
                                  decoration: const InputDecoration(labelText: 'Options (JSON)'),
                                  maxLines: 4,
                                  onChanged: (v) => item['optionsText'] = v,
                                ),
                                TextFormField(
                                  key: ValueKey('val-$index-${item['validationText']}'),
                                  initialValue: item['validationText']?.toString() ?? '{}',
                                  decoration: const InputDecoration(labelText: 'Validation (JSON)'),
                                  maxLines: 3,
                                  onChanged: (v) => item['validationText'] = v,
                                ),
                                Align(
                                  alignment: Alignment.centerRight,
                                  child: TextButton.icon(
                                    onPressed: () => setState(() => _draft.removeAt(index)),
                                    icon: const Icon(Icons.delete_outline),
                                    label: const Text('Remove'),
                                  ),
                                ),
                              ],
                            ),
                          );
                        },
                      ),
                    ),
                  ],
                ),
    );
  }
}
