import 'package:flutter/material.dart';
import '../../../core/auth/session_store.dart';
import '../../../core/network/api_client.dart';
import '../../auth/presentation/login_screen.dart';
import '../../assignments/presentation/assignments_screen.dart';
import '../../profile/presentation/profile_screen.dart';

class DashboardScreen extends StatefulWidget {
  const DashboardScreen({super.key});

  @override
  State<DashboardScreen> createState() => _DashboardScreenState();
}

class _DashboardScreenState extends State<DashboardScreen> {
  Map<String, dynamic>? _workload;
  String? _workloadError;

  @override
  void initState() {
    super.initState();
    _loadWorkload();
  }

  Future<void> _loadWorkload() async {
    try {
      final w = await ApiClient.fetchMyWorkload();
      if (mounted) {
        setState(() {
          _workload = w;
          _workloadError = null;
        });
      }
    } catch (_) {
      if (mounted) {
        setState(() {
          _workload = null;
          _workloadError = 'No supervisor workload for this account (expected for coordinators and admins).';
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(SessionStore.username ?? 'Dashboard'),
        actions: [
          IconButton(
            icon: const Icon(Icons.person_outline),
            tooltip: 'Profile',
            onPressed: () => Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => const ProfileScreen()),
            ),
          ),
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: _loadWorkload,
          ),
          IconButton(
            icon: const Icon(Icons.logout),
            onPressed: () {
              SessionStore.accessToken = null;
              SessionStore.username = null;
              Navigator.of(context).pushReplacement(
                MaterialPageRoute(builder: (_) => const LoginScreen()),
              );
            },
          )
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          if (_workload != null)
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text('My supervision workload', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
                    const SizedBox(height: 10),
                    Text('Total assignments: ${_workload!['totalAssignments'] ?? 0}'),
                    Text('Completed: ${_workload!['completedAssignments'] ?? 0}'),
                    Text('Pending: ${_workload!['pendingAssignments'] ?? 0}'),
                    Text('In progress: ${_workload!['inProgressAssignments'] ?? 0}'),
                    Text('Overdue: ${_workload!['overdueAssignments'] ?? 0}'),
                    Text('Visits completed: ${_workload!['visitsCompleted'] ?? 0}'),
                  ],
                ),
              ),
            )
          else if (_workloadError != null)
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Text(_workloadError!, style: TextStyle(color: Colors.grey.shade700)),
              ),
            ),
          const SizedBox(height: 12),
          Card(
            child: ListTile(
              leading: const Icon(Icons.person_outline),
              title: const Text('Profile & settings'),
              subtitle: const Text('Edit details, password, and role status'),
              trailing: const Icon(Icons.arrow_forward_ios, size: 16),
              onTap: () => Navigator.push(
                context,
                MaterialPageRoute(builder: (_) => const ProfileScreen()),
              ),
            ),
          ),
          const SizedBox(height: 8),
          Card(
            child: ListTile(
              leading: const Icon(Icons.assignment_outlined),
              title: const Text('Assignments'),
              subtitle: const Text('Supervision tasks and checklists'),
              trailing: const Icon(Icons.arrow_forward_ios, size: 16),
              onTap: () => Navigator.push(
                context,
                MaterialPageRoute(builder: (_) => const AssignmentsScreen()),
              ),
            ),
          ),
          const SizedBox(height: 12),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Tips', style: TextStyle(fontWeight: FontWeight.w600, color: Colors.grey.shade800)),
                  const SizedBox(height: 8),
                  Text('Pull to refresh on Assignments.', style: TextStyle(color: Colors.grey.shade700, height: 1.4)),
                  Text('Enable location before starting a visit.', style: TextStyle(color: Colors.grey.shade700, height: 1.4)),
                  Text('Complete signatures after the checklist.', style: TextStyle(color: Colors.grey.shade700, height: 1.4)),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}
