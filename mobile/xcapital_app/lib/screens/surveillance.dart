import 'dart:convert';
import 'package:flutter/material.dart';
import '../widgets/module_feedback.dart';
import '../services/api.dart';

class SurveillanceScreen extends StatefulWidget {
  const SurveillanceScreen({super.key});

  @override
  State<SurveillanceScreen> createState() => _SurveillanceScreenState();
}

class _SurveillanceScreenState extends State<SurveillanceScreen> {
  final limitCtrl = TextEditingController(text: '50');
  List<dynamic> alerts = const [];
  String msg = '';

  Future<void> loadAlerts() async {
    final limit = int.tryParse(limitCtrl.text.trim()) ?? 50;
    setState(() => msg = 'Loading surveillance alerts...');
    try {
      final res = await Api.surveillanceAlerts(limit: limit);
      setState(() {
        alerts = res;
        msg = 'Loaded ${alerts.length} alerts.';
      });
    } catch (e) {
      setState(() => msg = e.toString());
    }
  }

  @override
  void initState() {
    super.initState();
    loadAlerts();
  }

  @override
  void dispose() {
    limitCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('xCAPITAL — Surveillance')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          const Text('Market-abuse and policy surveillance alerts.',
              style: TextStyle(color: Colors.black54)),
          const SizedBox(height: 8),
          Row(
            children: [
              Expanded(
                child: TextField(
                  controller: limitCtrl,
                  keyboardType: TextInputType.number,
                  decoration: const InputDecoration(labelText: 'Limit'),
                ),
              ),
              const SizedBox(width: 8),
              ElevatedButton(onPressed: loadAlerts, child: const Text('Refresh')),
            ],
          ),
          const SizedBox(height: 8),
          ModuleFeedback(message: msg),
          if (alerts.isNotEmpty) ...[
            const SizedBox(height: 12),
            Text(const JsonEncoder.withIndent('  ').convert(alerts)),
          ],
        ],
      ),
    );
  }
}
