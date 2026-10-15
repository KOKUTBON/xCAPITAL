import 'package:flutter/material.dart';
import '../widgets/module_feedback.dart';
import '../services/api.dart';

class AlertsScreen extends StatefulWidget {
  const AlertsScreen({super.key});

  @override
  State<AlertsScreen> createState() => _AlertsScreenState();
}

class _AlertsScreenState extends State<AlertsScreen> {
  final symbolCtrl = TextEditingController(text: 'AAPL');
  final targetCtrl = TextEditingController(text: '180');

  String alertType = 'PRICE';
  String comparison = 'ABOVE';
  String frequency = 'REALTIME';
  String msg = '';
  List<dynamic> alerts = [];

  @override
  void initState() {
    super.initState();
    loadAlerts();
  }

  @override
  void dispose() {
    symbolCtrl.dispose();
    targetCtrl.dispose();
    super.dispose();
  }

  Future<void> loadAlerts() async {
    try {
      final data = await Api.alerts(limit: 50);
      setState(() { alerts = data; msg = ''; });
    } catch (e) {
      setState(() => msg = e.toString());
    }
  }

  Future<void> createAlert() async {
    setState(() => msg = 'Creating alert...');
    try {
      await Api.createAlert({
        'alertType': alertType,
        'symbol': symbolCtrl.text.trim(),
        'comparison': comparison,
        'targetValue': double.tryParse(targetCtrl.text) ?? 0,
        'frequency': frequency,
      });
      await loadAlerts();
      setState(() => msg = 'Alert created.');
    } catch (e) {
      setState(() => msg = e.toString());
    }
  }

  Future<void> setStatus(String id, String status) async {
    setState(() => msg = 'Updating status...');
    try {
      await Api.updateAlertStatus(id, status);
      await loadAlerts();
      setState(() => msg = 'Updated.');
    } catch (e) {
      setState(() => msg = e.toString());
    }
  }

  Future<void> trigger(String id) async {
    setState(() => msg = 'Triggering alert...');
    try {
      await Api.triggerAlert(id);
      await loadAlerts();
      setState(() => msg = 'Triggered.');
    } catch (e) {
      setState(() => msg = e.toString());
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('xCAPITAL — Alerts')),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: ListView(
          children: [
            const Text('Automated alerts with AI context.',
                style: TextStyle(color: Colors.black54)),
            const SizedBox(height: 12),
            DropdownButtonFormField<String>(
              value: alertType,
              decoration: const InputDecoration(labelText: 'Type'),
              items: const [
                DropdownMenuItem(value: 'PRICE', child: Text('Price')),
                DropdownMenuItem(value: 'VOLATILITY', child: Text('Volatility')),
                DropdownMenuItem(value: 'DRAWDOWN', child: Text('Drawdown')),
                DropdownMenuItem(value: 'VOLUME', child: Text('Volume')),
                DropdownMenuItem(value: 'CUSTOM', child: Text('Custom')),
              ],
              onChanged: (v) => setState(() => alertType = v ?? 'PRICE'),
            ),
            const SizedBox(height: 8),
            TextField(controller: symbolCtrl, decoration: const InputDecoration(labelText: 'Symbol')),
            const SizedBox(height: 8),
            DropdownButtonFormField<String>(
              value: comparison,
              decoration: const InputDecoration(labelText: 'Comparison'),
              items: const [
                DropdownMenuItem(value: 'ABOVE', child: Text('Above')),
                DropdownMenuItem(value: 'BELOW', child: Text('Below')),
                DropdownMenuItem(value: 'CROSS', child: Text('Cross')),
              ],
              onChanged: (v) => setState(() => comparison = v ?? 'ABOVE'),
            ),
            const SizedBox(height: 8),
            TextField(controller: targetCtrl, decoration: const InputDecoration(labelText: 'Target Value')),
            const SizedBox(height: 8),
            DropdownButtonFormField<String>(
              value: frequency,
              decoration: const InputDecoration(labelText: 'Frequency'),
              items: const [
                DropdownMenuItem(value: 'REALTIME', child: Text('Realtime')),
                DropdownMenuItem(value: 'HOURLY', child: Text('Hourly')),
                DropdownMenuItem(value: 'DAILY', child: Text('Daily')),
              ],
              onChanged: (v) => setState(() => frequency = v ?? 'REALTIME'),
            ),
            const SizedBox(height: 8),
            ElevatedButton(onPressed: createAlert, child: const Text('Create Alert')),
            const SizedBox(height: 8),
            ModuleFeedback(message: msg),
            const SizedBox(height: 12),
            ElevatedButton(onPressed: loadAlerts, child: const Text('Refresh Alerts')),
            const SizedBox(height: 8),

            if (alerts.isNotEmpty) ...[
              ...alerts.map((a) => ListTile(
                title: Text('${a['alertType']} ${a['symbol'] ?? ''} (${a['status']})'),
                subtitle: Text(a['aiSummary'] ?? ''),
                trailing: Wrap(
                  spacing: 6,
                  children: [
                    TextButton(onPressed: () => setStatus(a['id'], 'ACTIVE'), child: const Text('Activate')),
                    TextButton(onPressed: () => setStatus(a['id'], 'PAUSED'), child: const Text('Pause')),
                    TextButton(onPressed: () => trigger(a['id']), child: const Text('Trigger')),
                  ],
                ),
              )),
            ]
          ],
        ),
      ),
    );
  }
}
