import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../../../l10n/app_strings.dart';
import '../../../core/location/location_service.dart';
import '../../../core/network/api_client.dart';
import '../../signature/presentation/signature_screen.dart';
import 'widgets/checklist_item_widget.dart';

class ChecklistRendererScreen extends StatefulWidget {
  final Map<String, dynamic> checklistJson;
  final String? assignmentId;
  const ChecklistRendererScreen({super.key, required this.checklistJson, this.assignmentId});

  @override
  State<ChecklistRendererScreen> createState() => _ChecklistRendererScreenState();
}

class _ChecklistRendererScreenState extends State<ChecklistRendererScreen> {
  final Map<String, dynamic> _answers = {};
  final LocationService _locationService = LocationService();
  int _currentIndex = 0;
  int _groupIndex = 0;
  bool _submittingReview = false;
  String? _error;

  String get _checklistTitle {
    final s = AppStrings.of(context);
    final n = widget.checklistJson['checklistName'] ?? widget.checklistJson['name'];
    if (n != null && n.toString().isNotEmpty) return n.toString();
    return s.checklist;
  }

  Future<void> _submitReview() async {
    HapticFeedback.mediumImpact();
    if (widget.assignmentId == null) {
      Navigator.push(
        context,
        MaterialPageRoute(builder: (_) => SignatureScreen(answers: _answers)),
      );
      return;
    }
    setState(() {
      _submittingReview = true;
      _error = null;
    });
    try {
      final position = await _locationService.capturePosition();
      await ApiClient.startReview(
        widget.assignmentId!,
        position.latitude,
        position.longitude,
      );
      final payload = _answers.entries
          .where((entry) => entry.value != null)
          .map((entry) => {
                'checklistItemId': entry.key.toString(),
                'answer': entry.value,
              })
          .toList();
      final reviewId = await ApiClient.submitReview(
        widget.assignmentId!,
        position.latitude,
        position.longitude,
        payload,
      );
      if (!mounted) return;
      HapticFeedback.lightImpact();
      Navigator.push(
        context,
        MaterialPageRoute(
          builder: (_) => SignatureScreen(
            answers: _answers,
            reviewId: reviewId,
          ),
        ),
      );
    } catch (e) {
      HapticFeedback.heavyImpact();
      final msg = ApiClient.messageFromError(e);
      setState(() => _error = AppStrings.of(context).submitFailed(msg));
      if (mounted) {
        final csSnack = Theme.of(context).colorScheme;
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(msg, style: TextStyle(color: csSnack.onErrorContainer)),
            backgroundColor: csSnack.errorContainer,
          ),
        );
      }
    } finally {
      if (mounted) {
        setState(() => _submittingReview = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final s = AppStrings.of(context);
    final mode = widget.checklistJson['displayMode'] as String? ?? 'ALL_AT_ONCE';
    final items = (widget.checklistJson['items'] as List).cast<Map<String, dynamic>>();
    final cs = Theme.of(context).colorScheme;

    return Scaffold(
      appBar: AppBar(
        title: Text(
          _checklistTitle,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
        ),
        bottom: switch (mode) {
          'ONE_BY_ONE' => PreferredSize(
              preferredSize: const Size.fromHeight(5),
              child: LinearProgressIndicator(
                value: items.isEmpty ? 0 : (_currentIndex + 1) / items.length,
                minHeight: 3,
                backgroundColor: cs.surfaceContainerHighest,
              ),
            ),
          'GROUPED' => PreferredSize(
              preferredSize: const Size.fromHeight(5),
              child: Builder(
                builder: (context) {
                  final keys = _groupKeys(items);
                  final n = keys.length;
                  return LinearProgressIndicator(
                    value: n == 0 ? 0 : (_groupIndex + 1) / n,
                    minHeight: 3,
                    backgroundColor: cs.surfaceContainerHighest,
                  );
                },
              ),
            ),
          _ => null,
        },
      ),
      body: switch (mode) {
        'ONE_BY_ONE' => _buildOneByOne(items),
        'GROUPED' => _buildGrouped(items),
        _ => _buildAllAtOnce(items),
      },
      bottomNavigationBar: (_error != null && !_submittingReview)
          ? Material(
              color: cs.errorContainer.withOpacity(0.92),
              child: SafeArea(
                top: false,
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(16, 10, 16, 12),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Icon(Icons.error_outline_rounded, color: cs.onErrorContainer, size: 22),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Text(
                          _error!,
                          style: TextStyle(color: cs.onErrorContainer, fontSize: 13, height: 1.35),
                        ),
                      ),
                      IconButton(
                        onPressed: () => setState(() => _error = null),
                        icon: Icon(Icons.close_rounded, color: cs.onErrorContainer),
                        visualDensity: VisualDensity.compact,
                      ),
                    ],
                  ),
                ),
              ),
            )
          : null,
      floatingActionButton: _submittingReview
          ? FloatingActionButton.extended(
              onPressed: null,
              icon: SizedBox(
                width: 22,
                height: 22,
                child: CircularProgressIndicator(strokeWidth: 2.5, color: cs.onPrimary),
              ),
              label: const Text('Submitting…'),
            )
          : FloatingActionButton.extended(
              onPressed: _submitReview,
              icon: const Icon(Icons.check_rounded),
              label: Text(widget.assignmentId == null ? s.continueLabel : s.submitReview),
            ),
    );
  }

  List<String> _groupKeys(List<Map<String, dynamic>> items) {
    final keys = <String>[];
    for (final item in items) {
      final k = item['groupKey']?.toString() ?? 'General';
      if (!keys.contains(k)) keys.add(k);
    }
    return keys;
  }

  bool _shouldShowAllAtOnceSectionHeaders(List<Map<String, dynamic>> items) {
    final keys = _groupKeys(items);
    if (keys.length > 1) return true;
    if (keys.isNotEmpty && keys.first != 'General') return true;
    return false;
  }

  String? _itemKey(Map<String, dynamic> item) {
    final id = item['id'];
    if (id == null) return null;
    return id.toString();
  }

  void _setAnswer(String? key, dynamic v) {
    if (key == null) return;
    setState(() {
      if (v == null) {
        _answers.remove(key);
      } else {
        _answers[key] = v;
      }
    });
  }

  Widget _buildAllAtOnce(List<Map<String, dynamic>> items) {
    final showHeaders = _shouldShowAllAtOnceSectionHeaders(items);
    String? lastSectionKey;
    final children = <Widget>[];
    for (final item in items) {
      final k = item['groupKey']?.toString() ?? 'General';
      if (showHeaders && k != lastSectionKey) {
        lastSectionKey = k;
        children.add(
          Padding(
            padding: const EdgeInsets.fromLTRB(4, 16, 4, 8),
            child: Text(
              k,
              style: Theme.of(context).textTheme.titleSmall?.copyWith(
                    fontWeight: FontWeight.w700,
                    color: Theme.of(context).colorScheme.primary,
                  ),
            ),
          ),
        );
      }
      final key = _itemKey(item);
      children.add(
        ChecklistItemWidget(
          item: item,
          value: key == null ? null : _answers[key],
          onChanged: (v) => _setAnswer(key, v),
        ),
      );
    }
    return ListView(
      padding: const EdgeInsets.fromLTRB(12, 8, 12, 100),
      children: children,
    );
  }

  Widget _animatedItem({required Object key, required Widget child}) {
    return AnimatedSwitcher(
      duration: const Duration(milliseconds: 280),
      switchInCurve: Curves.easeOutCubic,
      switchOutCurve: Curves.easeInCubic,
      transitionBuilder: (c, anim) {
        return FadeTransition(
          opacity: anim,
          child: SlideTransition(
            position: Tween<Offset>(begin: const Offset(0.06, 0), end: Offset.zero)
                .animate(CurvedAnimation(parent: anim, curve: Curves.easeOutCubic)),
            child: c,
          ),
        );
      },
      child: KeyedSubtree(
        key: ValueKey(key),
        child: child,
      ),
    );
  }

  Widget _navButton({required String label, required VoidCallback? onPressed, required IconData icon}) {
    return TextButton.icon(
      onPressed: onPressed == null
          ? null
          : () {
              HapticFeedback.selectionClick();
              onPressed();
            },
      icon: Icon(icon, size: 20),
      label: Text(label),
    );
  }

  Widget _buildOneByOne(List<Map<String, dynamic>> items) {
    if (items.isEmpty) {
      return const Center(child: Text('No items in this checklist.'));
    }
    final item = items[_currentIndex];
    final key = _itemKey(item);
    final showSec = _shouldShowAllAtOnceSectionHeaders(items);
    final secName = item['groupKey']?.toString() ?? 'General';
    return Column(
      children: [
        if (showSec)
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 4, 16, 0),
            child: Row(
              children: [
                Icon(Icons.folder_outlined, size: 20, color: Theme.of(context).colorScheme.primary),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    secName,
                    style: Theme.of(context).textTheme.titleSmall?.copyWith(
                          fontWeight: FontWeight.w600,
                        ),
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
              ],
            ),
          ),
        Expanded(
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 8),
            child: _animatedItem(
              key: _currentIndex,
              child: ChecklistItemWidget(
                item: item,
                value: key == null ? null : _answers[key],
                onChanged: (v) => _setAnswer(key, v),
              ),
            ),
          ),
        ),
        Padding(
          padding: const EdgeInsets.fromLTRB(12, 0, 12, 12),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              _navButton(
                label: 'Previous',
                icon: Icons.chevron_left_rounded,
                onPressed: _currentIndex > 0 ? () => setState(() => _currentIndex--) : null,
              ),
              Text(
                '${_currentIndex + 1} / ${items.length}',
                style: TextStyle(
                  fontWeight: FontWeight.w600,
                  color: Theme.of(context).colorScheme.onSurfaceVariant,
                ),
              ),
              _navButton(
                label: 'Next',
                icon: Icons.chevron_right_rounded,
                onPressed: _currentIndex < items.length - 1 ? () => setState(() => _currentIndex++) : null,
              ),
            ],
          ),
        ),
      ],
    );
  }

  Widget _buildGrouped(List<Map<String, dynamic>> items) {
    final groups = <String, List<Map<String, dynamic>>>{};
    for (final item in items) {
      final k = item['groupKey']?.toString() ?? 'General';
      groups.putIfAbsent(k, () => []).add(item);
    }
    final keys = groups.keys.toList();
    if (keys.isEmpty) {
      return const Center(child: Text('No items in this checklist.'));
    }
    final selectedKey = keys[_groupIndex.clamp(0, keys.length - 1)];
    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
          child: _animatedItem(
            key: selectedKey,
            child: Row(
              children: [
                Icon(Icons.folder_outlined, size: 22, color: Theme.of(context).colorScheme.primary),
                const SizedBox(width: 10),
                Expanded(
                  child: Text(
                    selectedKey,
                    style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700),
                  ),
                ),
              ],
            ),
          ),
        ),
        Expanded(
          child: ListView(
            padding: const EdgeInsets.fromLTRB(12, 8, 12, 8),
            children: groups[selectedKey]!
                .map((item) {
                  final key = _itemKey(item);
                  return ChecklistItemWidget(
                    item: item,
                    value: key == null ? null : _answers[key],
                    onChanged: (v) => _setAnswer(key, v),
                  );
                })
                .toList(),
          ),
        ),
        Padding(
          padding: const EdgeInsets.fromLTRB(12, 0, 12, 12),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              _navButton(
                label: 'Previous',
                icon: Icons.chevron_left_rounded,
                onPressed: _groupIndex > 0 ? () => setState(() => _groupIndex--) : null,
              ),
              Text(
                'Section ${_groupIndex + 1} / ${keys.length}',
                style: TextStyle(
                  fontWeight: FontWeight.w600,
                  color: Theme.of(context).colorScheme.onSurfaceVariant,
                ),
              ),
              _navButton(
                label: 'Next',
                icon: Icons.chevron_right_rounded,
                onPressed: _groupIndex < keys.length - 1 ? () => setState(() => _groupIndex++) : null,
              ),
            ],
          ),
        ),
      ],
    );
  }
}
