import 'dart:convert';
import 'package:flutter/material.dart';
import '../widgets/module_feedback.dart';
import '../services/api.dart';

class BestExecutionScreen extends StatefulWidget {
  const BestExecutionScreen({super.key});

  @override
  State<BestExecutionScreen> createState() => _BestExecutionScreenState();
}

class _BestExecutionScreenState extends State<BestExecutionScreen> {
  final symbolCtrl = TextEditingController();
  final limitCtrl = TextEditingController(text: '50');
  String msg = '';
  List<dynamic> records = const [];

  Future<void> load() async {
    final limit = int.tryParse(limitCtrl.text.trim()) ?? 50;
    setState(() => msg = 'Loading best execution records...');
    try {
      final res = await Api.bestExecution(symbol: symbolCtrl.text.trim(), limit: limit);
      setState(() {
        records = res;
        msg = 'Loaded ${records.length} records.';
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
    symbolCtrl.dispose();
    limitCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('xCAPITAL — Best Execution')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          const Text('Execution quality records by proposal/order.',
              style: TextStyle(color: Colors.black54)),
          const SizedBox(height: 8),
          TextField(controller: symbolCtrl, decoration: const InputDecoration(labelText: 'Symbol (optional)')),
          const SizedBox(height: 8),
          TextField(controller: limitCtrl, keyboardType: TextInputType.number, decoration: const InputDecoration(labelText: 'Limit')),
          const SizedBox(height: 8),
          ElevatedButton(onPressed: load, child: const Text('Refresh')),
          const SizedBox(height: 8),
          ModuleFeedback(message: msg),
          if (records.isNotEmpty) ...[
            const SizedBox(height: 12),
            Text(const JsonEncoder.withIndent('  ').convert(records)),
          ],
        ],
      ),
    );
  }
}
