import 'dart:convert';
import 'package:flutter/material.dart';
import '../widgets/module_feedback.dart';
import '../services/api.dart';

class WealthPlanScreen extends StatefulWidget {
  const WealthPlanScreen({super.key});

  @override
  State<WealthPlanScreen> createState() => _WealthPlanScreenState();
}

class _WealthPlanScreenState extends State<WealthPlanScreen> {
  List<dynamic> plans = const [];
  Map<String, dynamic>? simulation;
  String? selectedPlanId;
  String msg = '';

  final nameCtrl = TextEditingController(text: 'Retirement Plan');
  final targetCtrl = TextEditingController(text: '1000000');
  final startCtrl = TextEditingController(text: '50000');
  final monthlyCtrl = TextEditingController(text: '1200');
  final yearsCtrl = TextEditingController(text: '25');

  @override
  void initState() {
    super.initState();
    loadPlans();
  }

  @override
  void dispose() {
    nameCtrl.dispose();
    targetCtrl.dispose();
    startCtrl.dispose();
    monthlyCtrl.dispose();
    yearsCtrl.dispose();
    super.dispose();
  }

  Future<void> loadPlans() async {
    setState(() => msg = 'Loading plans...');
    try {
      final res = await Api.wealthPlans();
      setState(() {
        plans = res;
        if (selectedPlanId == null && plans.isNotEmpty) {
          selectedPlanId = '${plans.first['id']}';
        }
        msg = 'Plans loaded.';
      });
    } catch (e) {
      setState(() => msg = e.toString());
    }
  }

  Future<void> createPlan() async {
    final target = double.tryParse(targetCtrl.text.trim());
    final starting = double.tryParse(startCtrl.text.trim());
    final monthly = double.tryParse(monthlyCtrl.text.trim());
    final years = int.tryParse(yearsCtrl.text.trim());
    if (target == null || starting == null || monthly == null || years == null) {
      setState(() => msg = 'Invalid plan values.');
      return;
    }
    setState(() => msg = 'Creating plan...');
    try {
      final created = await Api.createWealthPlan({
        'planType': 'RETIREMENT',
        'name': nameCtrl.text.trim(),
        'startingBalance': starting,
        'targetBalance': target,
        'monthlyContribution': monthly,
        'horizonYears': years,
        'expectedReturn': 0.06,
        'volatility': 0.15,
        'simulationCount': 300,
      });
      setState(() => selectedPlanId = '${created['id']}');
      await loadPlans();
    } catch (e) {
      setState(() => msg = e.toString());
    }
  }

  Future<void> runSimulation() async {
    if (selectedPlanId == null || selectedPlanId!.isEmpty) {
      setState(() => msg = 'Select a plan first.');
      return;
    }
    setState(() => msg = 'Running simulation...');
    try {
      final res = await Api.simulateWealthPlan(selectedPlanId!, body: {
        'simulationCount': 400,
      });
      setState(() {
        simulation = res;
        msg = 'Simulation complete.';
      });
    } catch (e) {
      setState(() => msg = e.toString());
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('xCAPITAL — Wealth Plan')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          const Text('Goal-based planning with simulation.', style: TextStyle(color: Colors.black54)),
          const SizedBox(height: 8),
          ModuleFeedback(message: msg),
          const SizedBox(height: 8),
          TextField(controller: nameCtrl, decoration: const InputDecoration(labelText: 'Plan name')),
          const SizedBox(height: 8),
          TextField(controller: startCtrl, keyboardType: TextInputType.number, decoration: const InputDecoration(labelText: 'Starting balance')),
          const SizedBox(height: 8),
          TextField(controller: targetCtrl, keyboardType: TextInputType.number, decoration: const InputDecoration(labelText: 'Target balance')),
          const SizedBox(height: 8),
          TextField(controller: monthlyCtrl, keyboardType: TextInputType.number, decoration: const InputDecoration(labelText: 'Monthly contribution')),
          const SizedBox(height: 8),
          TextField(controller: yearsCtrl, keyboardType: TextInputType.number, decoration: const InputDecoration(labelText: 'Horizon years')),
          const SizedBox(height: 8),
          Row(
            children: [
              ElevatedButton(onPressed: createPlan, child: const Text('Create Plan')),
              const SizedBox(width: 8),
              OutlinedButton(onPressed: loadPlans, child: const Text('Refresh')),
            ],
          ),
          const SizedBox(height: 8),
          DropdownButtonFormField<String>(
            value: selectedPlanId,
            items: plans.map((p) {
              final id = '${p['id']}';
              return DropdownMenuItem(value: id, child: Text('${p['name']} ($id)'));
            }).toList(),
            onChanged: (v) => setState(() => selectedPlanId = v),
            decoration: const InputDecoration(labelText: 'Selected plan'),
          ),
          const SizedBox(height: 8),
          ElevatedButton(onPressed: runSimulation, child: const Text('Run Simulation')),
          if (plans.isNotEmpty) ...[
            const SizedBox(height: 12),
            Text(const JsonEncoder.withIndent('  ').convert(plans)),
          ],
          if (simulation != null) ...[
            const SizedBox(height: 12),
            const Text('Simulation', style: TextStyle(fontWeight: FontWeight.w600)),
            Text(const JsonEncoder.withIndent('  ').convert(simulation)),
          ],
        ],
      ),
    );
  }
}
