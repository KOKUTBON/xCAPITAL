import 'dart:convert';
import 'package:flutter/material.dart';
import '../widgets/module_feedback.dart';
import '../services/api.dart';

class AuditLogScreen extends StatefulWidget {
  const AuditLogScreen({super.key});

  @override
  State<AuditLogScreen> createState() => _AuditLogScreenState();
}

class _AuditLogScreenState extends State<AuditLogScreen> {
  bool loading = false;
  String msg = '';
  List<dynamic> events = [];

  Future<void> load() async {
    setState(() {
      loading = true;
      msg = 'Loading audit log...';
    });
    try {
      final data = await Api.auditEvents(limit: 50);
      setState(() {
        events = data;
        msg = '';
      });
    } catch (e) {
      setState(() => msg = e.toString());
    } finally {
      setState(() => loading = false);
    }
  }

  @override
  void initState() {
    super.initState();
    load();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Audit Log'),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: loading ? null : load,
          ),
        ],
      ),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: ListView(
          children: [
            const Text('Immutable event stream for approvals and executions.',
                style: TextStyle(color: Colors.black54)),
            const SizedBox(height: 12),
            if (msg.isNotEmpty)
              ModuleFeedback(message: msg),
            if (events.isNotEmpty) ...[
              const SizedBox(height: 12),
              Text(const JsonEncoder.withIndent('  ').convert(events)),
            ]
          ],
        ),
      ),
    );
  }
}
