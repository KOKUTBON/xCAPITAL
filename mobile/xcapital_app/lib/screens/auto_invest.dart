import 'package:flutter/material.dart';
import '../widgets/module_feedback.dart';
import '../services/api.dart';

class AutoInvestScreen extends StatefulWidget {
  const AutoInvestScreen({super.key});

  @override
  State<AutoInvestScreen> createState() => _AutoInvestScreenState();
}

class _AutoInvestScreenState extends State<AutoInvestScreen> {
  final nameCtrl = TextEditingController(text: 'Core Growth Plan');
  final symbolsCtrl = TextEditingController(text: 'AAPL,MSFT,TLT');
  final driftCtrl = TextEditingController(text: '0.05');
  final lookbackCtrl = TextEditingController(text: '90');
  final riskCtrl = TextEditingController(text: '6');
  final minWeightCtrl = TextEditingController(text: '0.0');
  final maxWeightCtrl = TextEditingController(text: '0.6');
  final scheduleTimeCtrl = TextEditingController(text: '09:30');
  final prefTypesCtrl = TextEditingController(text: 'AUTO_INVEST_PROPOSAL,ALERT_TRIGGERED');
  final prefQuietStartCtrl = TextEditingController();
  final prefQuietEndCtrl = TextEditingController();
  final prefTimezoneCtrl = TextEditingController(text: 'UTC');
  final destValueCtrl = TextEditingController();
  final destLabelCtrl = TextEditingController();
  final deliveryStatusCtrl = TextEditingController();

  String schedule = 'DAILY';
  String scheduleTime = '09:30';
  String scheduleDay = 'MONDAY';
  bool useMarketData = true;
  bool useAiForecast = false;
  String prefChannel = 'EMAIL';
  bool prefEnabled = true;
  String destChannel = 'EMAIL';

  String msg = '';
  List<dynamic> plans = [];
  List<dynamic> runs = [];
  List<dynamic> notifications = [];
  List<dynamic> preferences = [];
  List<dynamic> destinations = [];
  List<dynamic> deliveries = [];
  String selectedPlanId = '';
  String prefMsg = '';
  String destMsg = '';
  String deliveryMsg = '';

  @override
  void initState() {
    super.initState();
    loadPlans();
    loadNotifications();
    loadPreferences();
    loadDestinations();
    loadDeliveries();
  }

  @override
  void dispose() {
    nameCtrl.dispose();
    symbolsCtrl.dispose();
    driftCtrl.dispose();
    lookbackCtrl.dispose();
    riskCtrl.dispose();
    minWeightCtrl.dispose();
    maxWeightCtrl.dispose();
    scheduleTimeCtrl.dispose();
    prefTypesCtrl.dispose();
    prefQuietStartCtrl.dispose();
    prefQuietEndCtrl.dispose();
    prefTimezoneCtrl.dispose();
    destValueCtrl.dispose();
    destLabelCtrl.dispose();
    deliveryStatusCtrl.dispose();
    super.dispose();
  }

  Future<void> loadPlans() async {
    setState(() => msg = 'Loading plans...');
    try {
      plans = await Api.autoInvestPlans();
      msg = '';
    } catch (e) {
      msg = e.toString();
    }
    setState(() {});
  }

  Future<void> createPlan() async {
    setState(() => msg = 'Creating plan...');
    try {
      final body = {
        'name': nameCtrl.text.trim(),
        'symbols': symbolsCtrl.text.split(',').map((s) => s.trim()).where((s) => s.isNotEmpty).toList(),
        'schedule': schedule,
        'scheduleTimeUtc': scheduleTimeCtrl.text.trim().isEmpty ? scheduleTime : scheduleTimeCtrl.text.trim(),
        'scheduleDayOfWeek': scheduleDay,
        'driftThreshold': double.tryParse(driftCtrl.text) ?? 0.05,
        'returnsLookback': int.tryParse(lookbackCtrl.text) ?? 90,
        'useMarketData': useMarketData,
        'useAiForecast': useAiForecast,
        'riskAversion': int.tryParse(riskCtrl.text) ?? 6,
        'minWeight': double.tryParse(minWeightCtrl.text) ?? 0.0,
        'maxWeight': double.tryParse(maxWeightCtrl.text) ?? 0.6,
      };
      await Api.createAutoInvestPlan(body);
      msg = 'Plan created.';
      await loadPlans();
    } catch (e) {
      msg = e.toString();
      setState(() {});
    }
  }

  Future<void> setStatus(String id, String status) async {
    setState(() => msg = 'Updating plan...');
    try {
      await Api.setAutoInvestStatus(id, status);
      msg = 'Updated.';
      await loadPlans();
    } catch (e) {
      msg = e.toString();
      setState(() {});
    }
  }

  Future<void> runPlan(String id) async {
    setState(() => msg = 'Triggering run...');
    try {
      final r = await Api.runAutoInvestPlan(id);
      msg = 'Run triggered.';
      if (id == selectedPlanId) {
        runs = [r, ...runs];
      }
      setState(() {});
    } catch (e) {
      msg = e.toString();
      setState(() {});
    }
  }

  Future<void> loadRuns(String planId) async {
    setState(() => msg = 'Loading runs...');
    try {
      runs = await Api.autoInvestRuns(planId);
      msg = '';
    } catch (e) {
      msg = e.toString();
    }
    setState(() {});
  }

  Future<void> loadNotifications() async {
    try {
      notifications = await Api.notifications(limit: 30);
      setState(() {});
    } catch (_) {}
  }

  Future<void> markNotificationRead(String id) async {
    try {
      await Api.markNotificationRead(id);
      await loadNotifications();
    } catch (_) {}
  }

  Future<void> loadPreferences() async {
    try {
      preferences = await Api.notificationPreferences();
      setState(() {});
    } catch (e) {
      setState(() => prefMsg = e.toString());
    }
  }

  Future<void> savePreference() async {
    setState(() => prefMsg = 'Saving preference...');
    try {
      final types = prefTypesCtrl.text
          .split(',')
          .map((s) => s.trim())
          .where((s) => s.isNotEmpty)
          .toList();
      final body = <String, dynamic>{
        'channel': prefChannel,
        'enabled': prefEnabled,
        'types': types,
      };
      if (prefQuietStartCtrl.text.trim().isNotEmpty) {
        body['quietStartHour'] = int.tryParse(prefQuietStartCtrl.text.trim());
      }
      if (prefQuietEndCtrl.text.trim().isNotEmpty) {
        body['quietEndHour'] = int.tryParse(prefQuietEndCtrl.text.trim());
      }
      if (prefTimezoneCtrl.text.trim().isNotEmpty) {
        body['timezone'] = prefTimezoneCtrl.text.trim();
      }
      await Api.saveNotificationPreference(body);
      await loadPreferences();
      setState(() => prefMsg = 'Preference saved.');
    } catch (e) {
      setState(() => prefMsg = e.toString());
    }
  }

  Future<void> loadDestinations() async {
    try {
      destinations = await Api.notificationDestinations();
      setState(() {});
    } catch (e) {
      setState(() => destMsg = e.toString());
    }
  }

  Future<void> createDestination() async {
    setState(() => destMsg = 'Saving destination...');
    try {
      final body = <String, dynamic>{
        'channel': destChannel,
        'destination': destValueCtrl.text.trim(),
        'label': destLabelCtrl.text.trim(),
      };
      await Api.createNotificationDestination(body);
      await loadDestinations();
      setState(() => destMsg = 'Destination saved.');
    } catch (e) {
      setState(() => destMsg = e.toString());
    }
  }

  Future<void> verifyDestination(String id) async {
    try {
      await Api.verifyNotificationDestination(id);
      await loadDestinations();
    } catch (e) {
      setState(() => destMsg = e.toString());
    }
  }

  Future<void> disableDestination(String id) async {
    try {
      await Api.disableNotificationDestination(id);
      await loadDestinations();
    } catch (e) {
      setState(() => destMsg = e.toString());
    }
  }

  Future<void> loadDeliveries() async {
    try {
      deliveries = await Api.notificationDeliveries(
        status: deliveryStatusCtrl.text.trim(),
        limit: 50,
      );
      setState(() {});
    } catch (e) {
      setState(() => deliveryMsg = e.toString());
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('xCAPITAL — Auto-Invest')),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: ListView(
          children: [
            const Text('Orchestrated auto-invest plans with idempotent runs.',
                style: TextStyle(color: Colors.black54)),
            const SizedBox(height: 12),

            const Text('Create Plan', style: TextStyle(fontWeight: FontWeight.w600)),
            const SizedBox(height: 8),
            TextField(controller: nameCtrl, decoration: const InputDecoration(labelText: 'Plan Name')),
            const SizedBox(height: 8),
            TextField(controller: symbolsCtrl, decoration: const InputDecoration(labelText: 'Symbols (comma-separated)')),
            const SizedBox(height: 8),
            DropdownButtonFormField<String>(
              value: schedule,
              decoration: const InputDecoration(labelText: 'Schedule'),
              items: const [
                DropdownMenuItem(value: 'DAILY', child: Text('Daily')),
                DropdownMenuItem(value: 'WEEKLY', child: Text('Weekly')),
                DropdownMenuItem(value: 'DRIFT', child: Text('Drift Trigger')),
              ],
              onChanged: (v) => setState(() => schedule = v ?? 'DAILY'),
            ),
            const SizedBox(height: 8),
            TextField(
              controller: scheduleTimeCtrl,
              decoration: const InputDecoration(labelText: 'Schedule Time (UTC)'),
              onChanged: (v) => scheduleTime = v,
            ),
            const SizedBox(height: 8),
            DropdownButtonFormField<String>(
              value: scheduleDay,
              decoration: const InputDecoration(labelText: 'Day of Week'),
              items: const [
                DropdownMenuItem(value: 'MONDAY', child: Text('Monday')),
                DropdownMenuItem(value: 'TUESDAY', child: Text('Tuesday')),
                DropdownMenuItem(value: 'WEDNESDAY', child: Text('Wednesday')),
                DropdownMenuItem(value: 'THURSDAY', child: Text('Thursday')),
                DropdownMenuItem(value: 'FRIDAY', child: Text('Friday')),
              ],
              onChanged: (v) => setState(() => scheduleDay = v ?? 'MONDAY'),
            ),
            const SizedBox(height: 8),
            TextField(controller: driftCtrl, decoration: const InputDecoration(labelText: 'Drift Threshold')),
            const SizedBox(height: 8),
            TextField(controller: lookbackCtrl, decoration: const InputDecoration(labelText: 'Returns Lookback')),
            const SizedBox(height: 8),
            SwitchListTile(
              title: const Text('Use Market Data'),
              value: useMarketData,
              onChanged: (v) => setState(() => useMarketData = v),
            ),
            SwitchListTile(
              title: const Text('Use AI Forecast'),
              value: useAiForecast,
              onChanged: (v) => setState(() => useAiForecast = v),
            ),
            TextField(controller: riskCtrl, decoration: const InputDecoration(labelText: 'Risk Aversion')),
            const SizedBox(height: 8),
            TextField(controller: minWeightCtrl, decoration: const InputDecoration(labelText: 'Min Weight')),
            const SizedBox(height: 8),
            TextField(controller: maxWeightCtrl, decoration: const InputDecoration(labelText: 'Max Weight')),
            const SizedBox(height: 8),
            ElevatedButton(onPressed: createPlan, child: const Text('Create Plan')),
            const SizedBox(height: 6),
            ModuleFeedback(message: msg),

            const SizedBox(height: 16),
            const Text('Plans', style: TextStyle(fontWeight: FontWeight.w600)),
            const SizedBox(height: 8),
            ElevatedButton(onPressed: loadPlans, child: const Text('Refresh Plans')),
            const SizedBox(height: 8),
            ...plans.map((p) => Card(
              child: ListTile(
                title: Text('${p['name']} (${p['status']})'),
                subtitle: Text('Schedule: ${p['schedule']} | Drift: ${p['driftThreshold']}\nSymbols: ${(p['symbols'] as List?)?.join(', ')}'),
                trailing: PopupMenuButton<String>(
                  onSelected: (value) {
                    if (value == 'run') runPlan(p['id']);
                    if (value == 'pause') setStatus(p['id'], 'PAUSED');
                    if (value == 'resume') setStatus(p['id'], 'ACTIVE');
                    if (value == 'runs') {
                      selectedPlanId = p['id'];
                      loadRuns(p['id']);
                    }
                  },
                  itemBuilder: (context) => [
                    const PopupMenuItem(value: 'run', child: Text('Run Now')),
                    const PopupMenuItem(value: 'runs', child: Text('View Runs')),
                    if (p['status'] == 'ACTIVE') const PopupMenuItem(value: 'pause', child: Text('Pause')),
                    if (p['status'] == 'PAUSED') const PopupMenuItem(value: 'resume', child: Text('Resume')),
                  ],
                ),
              ),
            )),

            if (selectedPlanId.isNotEmpty) ...[
              const SizedBox(height: 16),
              Text('Runs for $selectedPlanId', style: const TextStyle(fontWeight: FontWeight.w600)),
              const SizedBox(height: 8),
              ...runs.map((r) => ListTile(
                dense: true,
                title: Text('${r['status']} (${r['trigger']})'),
                subtitle: Text('Proposal: ${r['proposalId'] ?? '-'} | Drift: ${(r['metrics'] as Map?)?['drift'] ?? '-'}'),
              )),
            ],

            const SizedBox(height: 16),
            const Text('Notifications', style: TextStyle(fontWeight: FontWeight.w600)),
            const SizedBox(height: 8),
            ElevatedButton(onPressed: loadNotifications, child: const Text('Refresh Notifications')),
            const SizedBox(height: 8),
            ...notifications.map((n) => ListTile(
              dense: true,
              title: Text('${n['title']} (${n['status']})'),
              subtitle: Text(n['body'] ?? ''),
              trailing: TextButton(
                onPressed: n['status'] == 'READ' ? null : () => markNotificationRead(n['id']),
                child: const Text('Read'),
              ),
            )),

            const SizedBox(height: 16),
            const Text('Notification Settings', style: TextStyle(fontWeight: FontWeight.w600)),
            const SizedBox(height: 8),
            const Text('Preferences', style: TextStyle(fontWeight: FontWeight.w600)),
            const SizedBox(height: 6),
            DropdownButtonFormField<String>(
              value: prefChannel,
              decoration: const InputDecoration(labelText: 'Channel'),
              items: const [
                DropdownMenuItem(value: 'IN_APP', child: Text('In-App')),
                DropdownMenuItem(value: 'EMAIL', child: Text('Email')),
                DropdownMenuItem(value: 'PUSH', child: Text('Push')),
                DropdownMenuItem(value: 'SMS', child: Text('SMS')),
                DropdownMenuItem(value: 'WEBHOOK', child: Text('Webhook')),
              ],
              onChanged: (v) => setState(() => prefChannel = v ?? 'EMAIL'),
            ),
            SwitchListTile(
              title: const Text('Enabled'),
              value: prefEnabled,
              onChanged: (v) => setState(() => prefEnabled = v),
            ),
            TextField(controller: prefTypesCtrl, decoration: const InputDecoration(labelText: 'Types (comma-separated)')),
            const SizedBox(height: 8),
            TextField(controller: prefQuietStartCtrl, decoration: const InputDecoration(labelText: 'Quiet Start Hour (0-23)'), keyboardType: TextInputType.number),
            const SizedBox(height: 8),
            TextField(controller: prefQuietEndCtrl, decoration: const InputDecoration(labelText: 'Quiet End Hour (0-23)'), keyboardType: TextInputType.number),
            const SizedBox(height: 8),
            TextField(controller: prefTimezoneCtrl, decoration: const InputDecoration(labelText: 'Timezone')),
            const SizedBox(height: 8),
            Row(
              children: [
                Expanded(child: ElevatedButton(onPressed: savePreference, child: const Text('Save Preference'))),
                const SizedBox(width: 8),
                Expanded(child: ElevatedButton(onPressed: loadPreferences, child: const Text('Refresh'))),
              ],
            ),
            const SizedBox(height: 6),
            Text(prefMsg, style: const TextStyle(fontSize: 12, color: Colors.black54)),
            if (preferences.isNotEmpty) ...[
              const SizedBox(height: 6),
              ...preferences.map((p) => ListTile(
                dense: true,
                title: Text('${p['channel']} (${p['enabled'] == true ? 'Enabled' : 'Disabled'})'),
                subtitle: Text('Types: ${(p['types'] as List?)?.join(', ') ?? ''}'),
              )),
            ],

            const SizedBox(height: 12),
            const Text('Destinations', style: TextStyle(fontWeight: FontWeight.w600)),
            const SizedBox(height: 6),
            DropdownButtonFormField<String>(
              value: destChannel,
              decoration: const InputDecoration(labelText: 'Channel'),
              items: const [
                DropdownMenuItem(value: 'EMAIL', child: Text('Email')),
                DropdownMenuItem(value: 'PUSH', child: Text('Push')),
                DropdownMenuItem(value: 'SMS', child: Text('SMS')),
                DropdownMenuItem(value: 'WEBHOOK', child: Text('Webhook')),
              ],
              onChanged: (v) => setState(() => destChannel = v ?? 'EMAIL'),
            ),
            TextField(controller: destValueCtrl, decoration: const InputDecoration(labelText: 'Destination')),
            const SizedBox(height: 8),
            TextField(controller: destLabelCtrl, decoration: const InputDecoration(labelText: 'Label')),
            const SizedBox(height: 8),
            Row(
              children: [
                Expanded(child: ElevatedButton(onPressed: createDestination, child: const Text('Add Destination'))),
                const SizedBox(width: 8),
                Expanded(child: ElevatedButton(onPressed: loadDestinations, child: const Text('Refresh'))),
              ],
            ),
            const SizedBox(height: 6),
            Text(destMsg, style: const TextStyle(fontSize: 12, color: Colors.black54)),
            if (destinations.isNotEmpty) ...[
              const SizedBox(height: 6),
              ...destinations.map((d) => ListTile(
                dense: true,
                title: Text('${d['channel']} (${d['status']})'),
                subtitle: Text(d['destination'] ?? ''),
                trailing: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    TextButton(onPressed: () => verifyDestination(d['id']), child: const Text('Verify')),
                    TextButton(onPressed: () => disableDestination(d['id']), child: const Text('Disable')),
                  ],
                ),
              )),
            ],

            const SizedBox(height: 12),
            const Text('Delivery History', style: TextStyle(fontWeight: FontWeight.w600)),
            const SizedBox(height: 6),
            TextField(controller: deliveryStatusCtrl, decoration: const InputDecoration(labelText: 'Status Filter (optional)')),
            const SizedBox(height: 8),
            ElevatedButton(onPressed: loadDeliveries, child: const Text('Refresh Deliveries')),
            const SizedBox(height: 6),
            Text(deliveryMsg, style: const TextStyle(fontSize: 12, color: Colors.black54)),
            if (deliveries.isNotEmpty) ...[
              const SizedBox(height: 6),
              ...deliveries.map((d) => ListTile(
                dense: true,
                title: Text('${d['channel']} (${d['status']})'),
                subtitle: Text('Provider: ${d['provider'] ?? '-'} | Error: ${d['lastError'] ?? '-'}'),
              )),
            ],
          ],
        ),
      ),
    );
  }
}
