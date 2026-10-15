import 'dart:convert';
import 'package:flutter/material.dart';
import '../widgets/module_feedback.dart';
import '../services/api.dart';

class RewardsScreen extends StatefulWidget {
  const RewardsScreen({super.key});

  @override
  State<RewardsScreen> createState() => _RewardsScreenState();
}

class _RewardsScreenState extends State<RewardsScreen> {
  List<dynamic> offers = const [];
  List<dynamic> enrollments = const [];
  String? selectedOfferId;
  String msg = '';

  @override
  void initState() {
    super.initState();
    refresh();
  }

  Future<void> refresh() async {
    setState(() => msg = 'Loading rewards...');
    try {
      final o = await Api.rewardOffers();
      final e = await Api.rewardEnrollments();
      setState(() {
        offers = o;
        enrollments = e;
        if (selectedOfferId == null && offers.isNotEmpty) {
          selectedOfferId = '${offers.first['id']}';
        }
        msg = 'Rewards loaded.';
      });
    } catch (e) {
      setState(() => msg = e.toString());
    }
  }

  Future<void> enroll() async {
    if (selectedOfferId == null || selectedOfferId!.isEmpty) {
      setState(() => msg = 'Select an offer first.');
      return;
    }
    setState(() => msg = 'Enrolling...');
    try {
      await Api.rewardEnroll(selectedOfferId!);
      await refresh();
      setState(() => msg = 'Enrollment created.');
    } catch (e) {
      setState(() => msg = e.toString());
    }
  }

  Future<void> evaluate() async {
    setState(() => msg = 'Evaluating rewards...');
    try {
      final updated = await Api.rewardEvaluate();
      setState(() {
        enrollments = updated;
        msg = 'Evaluation complete.';
      });
    } catch (e) {
      setState(() => msg = e.toString());
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('xCAPITAL — Rewards')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          const Text('Campaign offers + enrollment qualification tracking.',
              style: TextStyle(color: Colors.black54)),
          const SizedBox(height: 8),
          ModuleFeedback(message: msg),
          const SizedBox(height: 8),
          DropdownButtonFormField<String>(
            value: selectedOfferId,
            items: offers.map((o) {
              final id = '${o['id']}';
              final name = '${o['name'] ?? o['title'] ?? id}';
              return DropdownMenuItem(value: id, child: Text(name));
            }).toList(),
            onChanged: (v) => setState(() => selectedOfferId = v),
            decoration: const InputDecoration(labelText: 'Offer'),
          ),
          const SizedBox(height: 8),
          Row(
            children: [
              ElevatedButton(onPressed: enroll, child: const Text('Enroll')),
              const SizedBox(width: 8),
              OutlinedButton(onPressed: evaluate, child: const Text('Evaluate')),
              const SizedBox(width: 8),
              OutlinedButton(onPressed: refresh, child: const Text('Refresh')),
            ],
          ),
          if (offers.isNotEmpty) ...[
            const SizedBox(height: 12),
            const Text('Offers', style: TextStyle(fontWeight: FontWeight.w600)),
            Text(const JsonEncoder.withIndent('  ').convert(offers)),
          ],
          if (enrollments.isNotEmpty) ...[
            const SizedBox(height: 12),
            const Text('Enrollments', style: TextStyle(fontWeight: FontWeight.w600)),
            Text(const JsonEncoder.withIndent('  ').convert(enrollments)),
          ],
        ],
      ),
    );
  }
}
