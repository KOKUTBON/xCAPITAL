import 'dart:convert';
import 'package:flutter/material.dart';
import '../widgets/module_feedback.dart';
import '../services/api.dart';

class ScreenerScreen extends StatefulWidget {
  const ScreenerScreen({super.key});

  @override
  State<ScreenerScreen> createState() => _ScreenerScreenState();
}

class _ScreenerScreenState extends State<ScreenerScreen> {
  final sectorCtrl = TextEditingController();
  final minPeCtrl = TextEditingController();
  final maxPeCtrl = TextEditingController();
  final minDivCtrl = TextEditingController();
  final limitCtrl = TextEditingController(text: '25');
  String rating = '';
  bool? focusList;
  String msg = '';
  Map<String, dynamic>? result;

  Future<void> runQuery() async {
    setState(() {
      msg = 'Running screener...';
      result = null;
    });
    final body = <String, dynamic>{
      'limit': int.tryParse(limitCtrl.text.trim()) ?? 25,
    };
    if (sectorCtrl.text.trim().isNotEmpty) body['sector'] = sectorCtrl.text.trim();
    final minPe = double.tryParse(minPeCtrl.text.trim());
    final maxPe = double.tryParse(maxPeCtrl.text.trim());
    final minDiv = double.tryParse(minDivCtrl.text.trim());
    if (minPe != null) body['minPeRatio'] = minPe;
    if (maxPe != null) body['maxPeRatio'] = maxPe;
    if (minDiv != null) body['minDividendYield'] = minDiv;
    if (rating.isNotEmpty) body['rating'] = rating;
    if (focusList != null) body['focusList'] = focusList;
    try {
      final res = await Api.screenerQuery(body);
      setState(() {
        result = res;
        msg = 'Screener completed.';
      });
    } catch (e) {
      setState(() => msg = e.toString());
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('xCAPITAL — Screeners')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          const Text('Filter securities by valuation and research criteria.',
              style: TextStyle(color: Colors.black54)),
          const SizedBox(height: 8),
          ModuleFeedback(message: msg),
          const SizedBox(height: 8),
          TextField(controller: sectorCtrl, decoration: const InputDecoration(labelText: 'Sector')),
          const SizedBox(height: 8),
          TextField(controller: minPeCtrl, keyboardType: TextInputType.number, decoration: const InputDecoration(labelText: 'Min P/E')),
          const SizedBox(height: 8),
          TextField(controller: maxPeCtrl, keyboardType: TextInputType.number, decoration: const InputDecoration(labelText: 'Max P/E')),
          const SizedBox(height: 8),
          TextField(controller: minDivCtrl, keyboardType: TextInputType.number, decoration: const InputDecoration(labelText: 'Min Dividend Yield')),
          const SizedBox(height: 8),
          TextField(controller: limitCtrl, keyboardType: TextInputType.number, decoration: const InputDecoration(labelText: 'Limit')),
          const SizedBox(height: 8),
          DropdownButtonFormField<String>(
            value: rating.isEmpty ? null : rating,
            items: const [
              DropdownMenuItem(value: 'OVERWEIGHT', child: Text('OVERWEIGHT')),
              DropdownMenuItem(value: 'NEUTRAL', child: Text('NEUTRAL')),
              DropdownMenuItem(value: 'UNDERWEIGHT', child: Text('UNDERWEIGHT')),
            ],
            onChanged: (v) => setState(() => rating = v ?? ''),
            decoration: const InputDecoration(labelText: 'J.P. Morgan Rating'),
          ),
          const SizedBox(height: 8),
          DropdownButtonFormField<String>(
            value: focusList == null ? null : (focusList! ? 'YES' : 'NO'),
            items: const [
              DropdownMenuItem(value: 'YES', child: Text('Focus List Only')),
              DropdownMenuItem(value: 'NO', child: Text('Exclude Focus List')),
            ],
            onChanged: (v) => setState(() => focusList = v == null ? null : v == 'YES'),
            decoration: const InputDecoration(labelText: 'Focus List'),
          ),
          const SizedBox(height: 8),
          ElevatedButton(onPressed: runQuery, child: const Text('Run Screener')),
          if (result != null) ...[
            const SizedBox(height: 12),
            Text(const JsonEncoder.withIndent('  ').convert(result)),
          ]
        ],
      ),
    );
  }
}
