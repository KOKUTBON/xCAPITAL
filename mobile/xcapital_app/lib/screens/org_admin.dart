import 'dart:convert';
import 'package:flutter/material.dart';
import '../widgets/module_feedback.dart';
import '../services/api.dart';

class OrgAdminScreen extends StatefulWidget {
  const OrgAdminScreen({super.key});

  @override
  State<OrgAdminScreen> createState() => _OrgAdminScreenState();
}

class _OrgAdminScreenState extends State<OrgAdminScreen> {
  final limitCtrl = TextEditingController(text: '50');
  Map<String, dynamic>? summary;
  List<dynamic> events = const [];
  String msg = '';

  Future<void> load() async {
    final limit = int.tryParse(limitCtrl.text.trim()) ?? 50;
    setState(() => msg = 'Loading org admin reporting...');
    try {
      final s = await Api.orgAdminSummary();
      final e = await Api.orgAdminAuditEvents(limit: limit);
      setState(() {
        summary = s;
        events = e;
        msg = 'Org reporting loaded.';
      });
    } catch (e) {
      setState(() => msg = e.toString());
    }
  }

  @override
  void initState() {
    super.initState();
    load();
  }

  @override
  void dispose() {
    limitCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('xCAPITAL — Org Admin')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          const Text('Organization-level reporting and audit visibility.',
              style: TextStyle(color: Colors.black54)),
          const SizedBox(height: 8),
          TextField(
            controller: limitCtrl,
            keyboardType: TextInputType.number,
            decoration: const InputDecoration(labelText: 'Audit event limit'),
          ),
          const SizedBox(height: 8),
          Row(
            children: [
              ElevatedButton(onPressed: load, child: const Text('Refresh')),
            ],
          ),
          const SizedBox(height: 8),
          ModuleFeedback(message: msg),
          if (summary != null) ...[
            const SizedBox(height: 12),
            const Text('Summary', style: TextStyle(fontWeight: FontWeight.w600)),
            Text(const JsonEncoder.withIndent('  ').convert(summary)),
          ],
          if (events.isNotEmpty) ...[
            const SizedBox(height: 12),
            const Text('Recent Audit Events', style: TextStyle(fontWeight: FontWeight.w600)),
            Text(const JsonEncoder.withIndent('  ').convert(events)),
          ],
        ],
      ),
    );
  }
}
