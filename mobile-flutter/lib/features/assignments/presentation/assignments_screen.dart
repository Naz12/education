import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../../../core/network/api_client.dart';
import '../../checklist_renderer/presentation/checklist_renderer_screen.dart';

class AssignmentsScreen extends StatefulWidget {
  const AssignmentsScreen({super.key});

  @override
  State<AssignmentsScreen> createState() => _AssignmentsScreenState();
}

class _AssignmentsScreenState extends State<AssignmentsScreen> {
  bool _loading = true;
  String? _error;
  List<dynamic> _assignments = [];
  Map<String, dynamic>? _workload;

  @override
  void initState() {
    super.initState();
    _loadAssignments();
  }

  Future<void> _loadAssignments() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final items = await ApiClient.fetchMyAssignments();
      Map<String, dynamic>? w;
      try {
        w = await ApiClient.fetchMyWorkload();
      } catch (_) {
        w = null;
      }
      setState(() {
        _assignments = items;
        _workload = w;
      });
    } catch (e) {
      setState(() => _error = 'Failed to load assignments: $e');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _openChecklist(dynamic assignment) async {
    HapticFeedback.lightImpact();
    final id = assignment['id']?.toString();
    if (id == null) return;
    try {
      final render = await ApiClient.fetchAssignmentRender(id);
      if (!mounted) return;
      Navigator.push(
        context,
        PageRouteBuilder<void>(
          pageBuilder: (_, __, ___) => ChecklistRendererScreen(
            checklistJson: render,
            assignmentId: id,
          ),
          transitionDuration: const Duration(milliseconds: 340),
          transitionsBuilder: (_, animation, __, child) {
            return SlideTransition(
              position: Tween<Offset>(begin: const Offset(0.08, 0), end: Offset.zero)
                  .animate(CurvedAnimation(parent: animation, curve: Curves.easeOutCubic)),
              child: FadeTransition(opacity: animation, child: child),
            );
          },
        ),
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Failed to open checklist: $e')),
      );
    }
  }

  (Color bg, Color fg, IconData icon) _statusStyle(String status, ColorScheme cs) {
    switch (status) {
      case 'COMPLETED':
        return (cs.tertiaryContainer, cs.onTertiaryContainer, Icons.check_circle_rounded);
      case 'IN_PROGRESS':
        return (cs.secondaryContainer, cs.onSecondaryContainer, Icons.hourglass_top_rounded);
      case 'OVERDUE':
        return (cs.errorContainer, cs.onErrorContainer, Icons.warning_amber_rounded);
      default:
        return (cs.surfaceContainerHighest, cs.onSurfaceVariant, Icons.assignment_outlined);
    }
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Scaffold(
      appBar: AppBar(title: const Text('Assignments')),
      body: RefreshIndicator(
        onRefresh: _loadAssignments,
        child: _loading
            ? ListView(
                physics: const AlwaysScrollableScrollPhysics(),
                children: const [
                  SizedBox(height: 120),
                  Center(child: CircularProgressIndicator()),
                ],
              )
            : _error != null
                ? ListView(
                    physics: const AlwaysScrollableScrollPhysics(),
                    children: [
                      Padding(
                        padding: const EdgeInsets.all(24),
                        child: Card(
                          child: Padding(
                            padding: const EdgeInsets.all(20),
                            child: Column(
                              children: [
                                Icon(Icons.cloud_off_rounded, size: 48, color: cs.error),
                                const SizedBox(height: 12),
                                Text(_error!, textAlign: TextAlign.center),
                                const SizedBox(height: 16),
                                FilledButton.icon(
                                  onPressed: _loadAssignments,
                                  icon: const Icon(Icons.refresh_rounded),
                                  label: const Text('Try again'),
                                ),
                              ],
                            ),
                          ),
                        ),
                      ),
                    ],
                  )
                : _assignments.isEmpty
                    ? ListView(
                        physics: const AlwaysScrollableScrollPhysics(),
                        children: [
                          if (_workload != null)
                            Padding(
                              padding: const EdgeInsets.fromLTRB(12, 16, 12, 0),
                              child: _WorkloadSummary(workload: _workload!),
                            ),
                          Padding(
                            padding: const EdgeInsets.all(32),
                            child: Column(
                              children: [
                                Icon(Icons.inbox_rounded, size: 64, color: cs.outline),
                                const SizedBox(height: 16),
                                Text(
                                  'No assignments yet',
                                  style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w600),
                                ),
                                const SizedBox(height: 8),
                                Text(
                                  'Ask your coordinator to assign a checklist.',
                                  textAlign: TextAlign.center,
                                  style: TextStyle(color: cs.onSurfaceVariant, height: 1.4),
                                ),
                              ],
                            ),
                          ),
                        ],
                      )
                    : ListView.builder(
                        padding: const EdgeInsets.fromLTRB(12, 8, 12, 24),
                        itemCount: _assignments.length + (_workload != null ? 1 : 0),
                        itemBuilder: (context, index) {
                          if (_workload != null && index == 0) {
                            return Padding(
                              padding: const EdgeInsets.only(bottom: 8),
                              child: _WorkloadSummary(workload: _workload!),
                            );
                          }
                          final aIndex = _workload != null ? index - 1 : index;
                          final a = _assignments[aIndex] as Map<String, dynamic>;
                          final status = (a['status'] ?? 'UNKNOWN').toString();
                          final isCompleted = status == 'COMPLETED';
                          final style = _statusStyle(status, cs);
                          final shortId = a['id']?.toString();
                          final title = shortId != null && shortId.length >= 8
                              ? 'Assignment ${shortId.substring(0, 8)}…'
                              : 'Assignment';

                          return TweenAnimationBuilder<double>(
                            tween: Tween(begin: 0, end: 1),
                            duration: Duration(milliseconds: 280 + (aIndex * 40).clamp(0, 200)),
                            curve: Curves.easeOutCubic,
                            builder: (context, t, child) => Opacity(
                              opacity: t,
                              child: Transform.translate(
                                offset: Offset(0, 8 * (1 - t)),
                                child: child,
                              ),
                            ),
                            child: Padding(
                              padding: const EdgeInsets.only(bottom: 10),
                              child: Card(
                                clipBehavior: Clip.antiAlias,
                                child: Material(
                                  color: Colors.white,
                                  child: InkWell(
                                    onTap: isCompleted ? null : () => _openChecklist(a),
                                    child: Padding(
                                      padding: const EdgeInsets.all(14),
                                      child: Column(
                                        crossAxisAlignment: CrossAxisAlignment.start,
                                        children: [
                                          Row(
                                            children: [
                                              Expanded(
                                                child: Text(
                                                  title,
                                                  style: const TextStyle(
                                                    fontWeight: FontWeight.w700,
                                                    fontSize: 16,
                                                    letterSpacing: -0.2,
                                                  ),
                                                ),
                                              ),
                                              Container(
                                                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                                                decoration: BoxDecoration(
                                                  color: style.$1,
                                                  borderRadius: BorderRadius.circular(20),
                                                ),
                                                child: Row(
                                                  mainAxisSize: MainAxisSize.min,
                                                  children: [
                                                    Icon(style.$3, size: 16, color: style.$2),
                                                    const SizedBox(width: 6),
                                                    Text(
                                                      status.replaceAll('_', ' '),
                                                      style: TextStyle(
                                                        fontSize: 12,
                                                        fontWeight: FontWeight.w600,
                                                        color: style.$2,
                                                      ),
                                                    ),
                                                  ],
                                                ),
                                              ),
                                            ],
                                          ),
                                          const SizedBox(height: 10),
                                          Row(
                                            children: [
                                              Icon(Icons.category_outlined, size: 18, color: cs.onSurfaceVariant),
                                              const SizedBox(width: 8),
                                              Expanded(
                                                child: Text(
                                                  'Target: ${a['targetType'] ?? '—'}',
                                                  style: TextStyle(color: cs.onSurfaceVariant, fontSize: 13),
                                                ),
                                              ),
                                            ],
                                          ),
                                          const SizedBox(height: 6),
                                          Row(
                                            children: [
                                              Icon(Icons.event_rounded, size: 18, color: cs.onSurfaceVariant),
                                              const SizedBox(width: 8),
                                              Expanded(
                                                child: Text(
                                                  'Due: ${a['dueDate'] ?? 'Not set'}',
                                                  style: TextStyle(color: cs.onSurfaceVariant, fontSize: 13),
                                                ),
                                              ),
                                            ],
                                          ),
                                          if (!isCompleted) ...[
                                            const SizedBox(height: 12),
                                            Align(
                                              alignment: Alignment.centerRight,
                                              child: FilledButton.tonalIcon(
                                                onPressed: () => _openChecklist(a),
                                                icon: const Icon(Icons.play_arrow_rounded, size: 20),
                                                label: const Text('Start visit'),
                                              ),
                                            ),
                                          ],
                                        ],
                                      ),
                                    ),
                                  ),
                                ),
                              ),
                            ),
                          );
                        },
                      ),
      ),
    );
  }
}

class _WorkloadSummary extends StatelessWidget {
  const _WorkloadSummary({required this.workload});

  final Map<String, dynamic> workload;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(Icons.pie_chart_outline_rounded, color: cs.primary),
                const SizedBox(width: 8),
                Text(
                  'At a glance',
                  style: Theme.of(context).textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w700),
                ),
              ],
            ),
            const SizedBox(height: 10),
            Text(
              '${workload['completedAssignments'] ?? 0} done · '
              '${workload['pendingAssignments'] ?? 0} pending · '
              '${workload['inProgressAssignments'] ?? 0} in progress · '
              '${workload['overdueAssignments'] ?? 0} overdue · '
              '${workload['visitsCompleted'] ?? 0} visits',
              style: Theme.of(context).textTheme.bodyMedium?.copyWith(height: 1.45),
            ),
          ],
        ),
      ),
    );
  }
}
