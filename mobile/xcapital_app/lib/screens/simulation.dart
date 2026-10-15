import 'dart:convert';
import 'package:flutter/material.dart';
import '../widgets/module_feedback.dart';
import '../services/api.dart';

class SimulationScreen extends StatefulWidget {
  const SimulationScreen({super.key});

  @override
  State<SimulationScreen> createState() => _SimulationScreenState();
}

class _SimulationScreenState extends State<SimulationScreen> {
  final returnsCtrl = TextEditingController(text: '[0.01,-0.02,0.005,0.012,-0.003,0.004,0.002,-0.006,0.009,0.003,0.002,0.001,-0.004,0.006,0.004,0.003,-0.002,0.001,0.002,0.003,0.004,0.002,-0.003,0.005,0.006,0.001,-0.001,0.002,0.003,0.004,0.002,0.001]');
  final initialCashCtrl = TextEditingController(text: '10000');
  final contributionCtrl = TextEditingController(text: '0');
  final contributionEveryCtrl = TextEditingController(text: '1');

  String strategy = 'BUY_AND_HOLD';
  String msg = '';
  String jobId = '';
  Map<String, dynamic>? job;
  Map<String, dynamic>? quota;
  String quotaMsg = '';

  @override
  void initState() {
    super.initState();
    loadQuota();
  }

  @override
  void dispose() {
    returnsCtrl.dispose();
    initialCashCtrl.dispose();
    contributionCtrl.dispose();
    contributionEveryCtrl.dispose();
    super.dispose();
  }

  Future<void> submit() async {
    setState(() { msg = 'Submitting...'; job = null; });
    try {
      final returns = (jsonDecode(returnsCtrl.text) as List)
          .map((e) => (e as num).toDouble()).toList();
      final res = await Api.submitBacktest(
        returns: returns,
        strategy: strategy,
        initialCash: double.tryParse(initialCashCtrl.text) ?? 10000.0,
        contribution: double.tryParse(contributionCtrl.text) ?? 0.0,
        contributionEvery: int.tryParse(contributionEveryCtrl.text) ?? 1,
      );
      jobId = res['id'] ?? '';
      job = res;
      msg = 'Job submitted.';
    } catch (e) {
      msg = e.toString();
    }
    setState(() {});
  }

  Future<void> refresh() async {
    if (jobId.isEmpty) return;
    setState(() => msg = 'Refreshing...');
    try {
      final res = await Api.getBacktest(jobId);
      job = res;
      msg = '';
    } catch (e) {
      msg = e.toString();
    }
    setState(() {});
  }

  Future<void> loadQuota() async {
    try {
      final res = await Api.simulationQuota();
      quota = res;
      quotaMsg = 'Quota updated.';
    } catch (e) {
      quotaMsg = e.toString();
    }
    setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('xCAPITAL — Simulation')),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: ListView(
          children: [
            const Text('Submit a backtest job and poll results.',
                style: TextStyle(color: Colors.black54)),
            const SizedBox(height: 8),
            if (quota != null)
              Row(
                children: [
                  Expanded(
                    child: Text(
                      'Quota: pending ${quota?['pending'] ?? 0}/${quota?['maxPending'] ?? 0} • '
                      'running ${quota?['running'] ?? 0}/${quota?['maxRunning'] ?? 0} • '
                      'active ${quota?['active'] ?? 0}/${quota?['maxActive'] ?? 0}',
                      style: const TextStyle(fontSize: 12, color: Colors.black54),
                    ),
                  ),
                  TextButton(onPressed: loadQuota, child: const Text('Refresh quota')),
                ],
              ),
            if (quotaMsg.isNotEmpty)
              Text(quotaMsg, style: const TextStyle(fontSize: 12, color: Colors.black54)),
            const SizedBox(height: 12),
            DropdownButtonFormField<String>(
              value: strategy,
              decoration: const InputDecoration(labelText: 'Strategy'),
              items: const [
                DropdownMenuItem(value: 'BUY_AND_HOLD', child: Text('Buy & Hold')),
                DropdownMenuItem(value: 'DCA', child: Text('DCA')),
              ],
              onChanged: (v) => setState(() => strategy = v ?? 'BUY_AND_HOLD'),
            ),
            const SizedBox(height: 8),
            TextField(controller: initialCashCtrl, decoration: const InputDecoration(labelText: 'Initial Cash')),
            const SizedBox(height: 8),
            TextField(controller: contributionCtrl, decoration: const InputDecoration(labelText: 'Contribution')),
            const SizedBox(height: 8),
            TextField(controller: contributionEveryCtrl, decoration: const InputDecoration(labelText: 'Contribution Every (periods)')),
            const SizedBox(height: 8),
            TextField(
              controller: returnsCtrl,
              maxLines: 4,
              decoration: const InputDecoration(labelText: 'Returns (JSON array)'),
            ),
            const SizedBox(height: 8),
            ElevatedButton(onPressed: submit, child: const Text('Submit Backtest')),
            const SizedBox(height: 8),
            ElevatedButton(onPressed: refresh, child: const Text('Refresh Status')),
            const SizedBox(height: 6),
            ModuleFeedback(message: msg),

            if (job != null) ...[
              const SizedBox(height: 12),
              Text('Status: ${job?['status'] ?? ''}'),
              if (job?['strategyConfigVersion'] != null)
                Text('Config v${job?['strategyConfigVersion']} | Hash ${job?['returnsHash'] ?? ''}',
                    style: const TextStyle(fontSize: 12, color: Colors.black54)),
              const SizedBox(height: 8),
              Text(const JsonEncoder.withIndent('  ').convert(job)),
            ]
          ],
        ),
      ),
    );
  }
}
