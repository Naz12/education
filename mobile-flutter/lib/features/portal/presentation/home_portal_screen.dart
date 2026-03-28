import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../../../core/auth/session_store.dart';
import '../../../core/network/api_client.dart';
import '../../assignments/presentation/assignments_screen.dart';

/// Home content shown inside the portal shell (mirrors web Home tab).
class HomePortalScreen extends StatefulWidget {
  const HomePortalScreen({super.key});

  @override
  State<HomePortalScreen> createState() => _HomePortalScreenState();
}

class _HomePortalScreenState extends State<HomePortalScreen> {
  Map<String, dynamic>? _workload;
  String? _workloadNote;
  bool _loading = false;

  @override
  void initState() {
    super.initState();
    if (!SessionStore.canAdmin) {
      _loadWorkload();
    }
  }

  Future<void> _loadWorkload() async {
    setState(() {
      _loading = true;
      _workloadNote = null;
    });
    try {
      final w = await ApiClient.fetchMyWorkload();
      if (mounted) {
        setState(() {
          _workload = w;
          _workloadNote = null;
        });
      }
    } catch (_) {
      if (mounted) {
        setState(() {
          _workload = null;
          _workloadNote = 'No supervisor workload for this account.';
        });
      }
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Widget _statChip(BuildContext context, {required IconData icon, required String label, required String value}) {
    final cs = Theme.of(context).colorScheme;
    return Expanded(
      child: Material(
        color: cs.surfaceContainerHighest.withOpacity(0.65),
        borderRadius: BorderRadius.circular(14),
        child: InkWell(
          onTap: () => HapticFeedback.selectionClick(),
          borderRadius: BorderRadius.circular(14),
          child: Padding(
            padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 8),
            child: Column(
              children: [
                Icon(icon, size: 22, color: cs.primary),
                const SizedBox(height: 6),
                Text(
                  value,
                  style: Theme.of(context).textTheme.titleMedium?.copyWith(
                        fontWeight: FontWeight.w800,
                        letterSpacing: -0.5,
                      ),
                ),
                Text(
                  label,
                  textAlign: TextAlign.center,
                  style: TextStyle(fontSize: 11, color: cs.onSurfaceVariant, height: 1.2),
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final me = SessionStore.currentUser;
    final name = me?['fullName']?.toString() ?? SessionStore.username ?? 'User';
    final roles = SessionStore.roles.join(', ');
    final cs = Theme.of(context).colorScheme;

    return RefreshIndicator(
      onRefresh: () async {
        if (!SessionStore.canAdmin) await _loadWorkload();
      },
      edgeOffset: 8,
      child: ListView(
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 24),
        children: [
          Container(
            padding: const EdgeInsets.all(18),
            decoration: BoxDecoration(
              gradient: LinearGradient(
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
                colors: [
                  cs.primary.withOpacity(0.14),
                  cs.tertiaryContainer.withOpacity(0.5),
                ],
              ),
              borderRadius: BorderRadius.circular(20),
              border: Border.all(color: cs.outlineVariant.withOpacity(0.45)),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Hello, $name',
                  style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                        fontWeight: FontWeight.w700,
                        letterSpacing: -0.4,
                      ),
                ),
                if (roles.isNotEmpty)
                  Padding(
                    padding: const EdgeInsets.only(top: 6),
                    child: Text(
                      roles,
                      style: TextStyle(color: cs.onSurfaceVariant, fontSize: 13, height: 1.35),
                    ),
                  ),
              ],
            ),
          ),
          const SizedBox(height: 16),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(18),
              child: Text(
                SessionStore.canAdmin
                    ? 'Use the menu to manage users, checklists, checklist items, assignments, schools, and school staff. Open Activity to audit supervisor visits, and Reports to download review PDFs.'
                    : 'Use Profile for your account and status. Open My assignments to run supervision visits.',
                style: TextStyle(height: 1.5, color: cs.onSurface.withOpacity(0.88), fontSize: 14),
              ),
            ),
          ),
          if (!SessionStore.canAdmin) ...[
            const SizedBox(height: 14),
            if (_loading)
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 28),
                child: Center(child: CircularProgressIndicator()),
              )
            else if (_workload != null)
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Icon(Icons.insights_rounded, color: cs.primary, size: 22),
                          const SizedBox(width: 8),
                          Text(
                            'My supervision workload',
                            style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700),
                          ),
                        ],
                      ),
                      const SizedBox(height: 14),
                      Row(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          _statChip(
                            context,
                            icon: Icons.assignment_rounded,
                            label: 'Total',
                            value: '${_workload!['totalAssignments'] ?? 0}',
                          ),
                          const SizedBox(width: 8),
                          _statChip(
                            context,
                            icon: Icons.check_circle_outline_rounded,
                            label: 'Done',
                            value: '${_workload!['completedAssignments'] ?? 0}',
                          ),
                          const SizedBox(width: 8),
                          _statChip(
                            context,
                            icon: Icons.pending_actions_rounded,
                            label: 'Pending',
                            value: '${_workload!['pendingAssignments'] ?? 0}',
                          ),
                        ],
                      ),
                      const SizedBox(height: 10),
                      Row(
                        children: [
                          _statChip(
                            context,
                            icon: Icons.hourglass_top_rounded,
                            label: 'In progress',
                            value: '${_workload!['inProgressAssignments'] ?? 0}',
                          ),
                          const SizedBox(width: 8),
                          _statChip(
                            context,
                            icon: Icons.event_busy_rounded,
                            label: 'Overdue',
                            value: '${_workload!['overdueAssignments'] ?? 0}',
                          ),
                          const SizedBox(width: 8),
                          _statChip(
                            context,
                            icon: Icons.place_rounded,
                            label: 'Visits',
                            value: '${_workload!['visitsCompleted'] ?? 0}',
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
              )
            else if (_workloadNote != null)
              Card(
                child: ListTile(
                  leading: Icon(Icons.info_outline_rounded, color: cs.primary),
                  title: Text(_workloadNote!),
                ),
              ),
            const SizedBox(height: 12),
            Card(
              clipBehavior: Clip.antiAlias,
              child: Material(
                color: Colors.white,
                child: InkWell(
                  onTap: () {
                    HapticFeedback.lightImpact();
                    Navigator.push(
                      context,
                      PageRouteBuilder<void>(
                        pageBuilder: (_, __, ___) => const AssignmentsScreen(),
                        transitionDuration: const Duration(milliseconds: 320),
                        transitionsBuilder: (_, animation, __, child) {
                          return SlideTransition(
                            position: Tween<Offset>(begin: const Offset(0.06, 0), end: Offset.zero)
                                .animate(CurvedAnimation(parent: animation, curve: Curves.easeOutCubic)),
                            child: FadeTransition(opacity: animation, child: child),
                          );
                        },
                      ),
                    );
                  },
                  child: Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                    child: ListTile(
                      contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                      leading: DecoratedBox(
                        decoration: BoxDecoration(
                          color: cs.primaryContainer.withOpacity(0.65),
                          borderRadius: BorderRadius.circular(12),
                        ),
                        child: Padding(
                          padding: const EdgeInsets.all(10),
                          child: Icon(Icons.assignment_outlined, color: cs.primary),
                        ),
                      ),
                      title: const Text('My assignments', style: TextStyle(fontWeight: FontWeight.w600)),
                      subtitle: const Text('Supervision tasks and checklists'),
                      trailing: Icon(Icons.chevron_right_rounded, color: cs.onSurfaceVariant),
                    ),
                  ),
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }
}
