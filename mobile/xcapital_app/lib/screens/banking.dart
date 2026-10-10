import 'dart:convert';
import 'package:flutter/material.dart';
import '../widgets/module_feedback.dart';
import '../services/api.dart';

class BankingScreen extends StatefulWidget {
  const BankingScreen({super.key});

  @override
  State<BankingScreen> createState() => _BankingScreenState();
}

class _BankingScreenState extends State<BankingScreen> {
  Map<String, dynamic>? account;
  List<dynamic> transfers = const [];
  String direction = 'TO_INVESTING';
  final amountCtrl = TextEditingController(text: '100');
  final noteCtrl = TextEditingController();
  String msg = '';

  @override
  void initState() {
    super.initState();
    refresh();
  }

  @override
  void dispose() {
    amountCtrl.dispose();
    noteCtrl.dispose();
    super.dispose();
  }

  Future<void> refresh() async {
    setState(() => msg = 'Loading banking data...');
    try {
      final acct = await Api.bankingAccount();
      final tx = await Api.bankingTransfers(limit: 50);
      setState(() {
        account = acct;
        transfers = tx;
        msg = 'Banking data loaded.';
      });
    } catch (e) {
      setState(() => msg = e.toString());
    }
  }

  Future<void> submitTransfer() async {
    final amount = double.tryParse(amountCtrl.text.trim());
    if (amount == null || amount <= 0) {
      setState(() => msg = 'Amount must be > 0.');
      return;
    }
    setState(() => msg = 'Submitting transfer...');
    try {
      await Api.bankingTransfer({
        'direction': direction,
        'amount': amount,
        'note': noteCtrl.text.trim(),
      });
      await refresh();
    } catch (e) {
      setState(() => msg = e.toString());
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('xCAPITAL — Banking')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          const Text('Instant banking/investing transfers.',
              style: TextStyle(color: Colors.black54)),
          const SizedBox(height: 8),
          ModuleFeedback(message: msg),
          const SizedBox(height: 8),
          if (account != null) ...[
            Text('Cash: ${account?['cash'] ?? 0} ${account?['currency'] ?? 'USD'}',
                style: const TextStyle(fontWeight: FontWeight.w600)),
            Text('Status: ${account?['status'] ?? '-'}'),
            const SizedBox(height: 12),
          ],
          Row(
            children: [
              Expanded(
                child: DropdownButtonFormField<String>(
                  value: direction,
                  items: const [
                    DropdownMenuItem(value: 'TO_INVESTING', child: Text('To Investing')),
                    DropdownMenuItem(value: 'TO_BANKING', child: Text('To Banking')),
                  ],
                  onChanged: (v) => setState(() => direction = v ?? 'TO_INVESTING'),
                  decoration: const InputDecoration(labelText: 'Direction'),
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          TextField(controller: amountCtrl, keyboardType: TextInputType.number, decoration: const InputDecoration(labelText: 'Amount')),
          const SizedBox(height: 8),
          TextField(controller: noteCtrl, decoration: const InputDecoration(labelText: 'Note')),
          const SizedBox(height: 8),
          Row(
            children: [
              ElevatedButton(onPressed: submitTransfer, child: const Text('Transfer')),
              const SizedBox(width: 8),
              OutlinedButton(onPressed: refresh, child: const Text('Refresh')),
            ],
          ),
          const SizedBox(height: 12),
          if (transfers.isNotEmpty)
            Text(const JsonEncoder.withIndent('  ').convert(transfers)),
        ],
      ),
    );
  }
}
