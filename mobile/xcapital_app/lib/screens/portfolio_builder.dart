import 'dart:convert';
import 'package:flutter/material.dart';
import '../widgets/module_feedback.dart';
import '../services/api.dart';

class PortfolioBuilderScreen extends StatefulWidget {
  const PortfolioBuilderScreen({super.key});

  @override
  State<PortfolioBuilderScreen> createState() => _PortfolioBuilderScreenState();
}

class _PortfolioBuilderScreenState extends State<PortfolioBuilderScreen> {
  final holdingsCtrl = TextEditingController(text: 'AAPL:12:185\nMSFT:8:410\nVTI:20:280');
  String msg = '';
  Map<String, dynamic>? analysis;

  List<Map<String, dynamic>> _parseHoldings(String raw) {
    final out = <Map<String, dynamic>>[];
    for (final line in raw.split('\n')) {
      final trimmed = line.trim();
      if (trimmed.isEmpty) continue;
      final parts = trimmed.split(':');
      if (parts.length < 3) continue;
      final symbol = parts[0].trim().toUpperCase();
      final qty = double.tryParse(parts[1].trim());
      final price = double.tryParse(parts[2].trim());
      if (symbol.isEmpty || qty == null || price == null || qty <= 0 || price <= 0) continue;
      out.add({
        'symbol': symbol,
        'quantity': qty,
        'price': price,
      });
    }
    return out;
  }

  Future<void> analyze() async {
    setState(() {
      msg = 'Analyzing portfolio...';
      analysis = null;
    });
    try {
      final holdings = _parseHoldings(holdingsCtrl.text);
      if (holdings.isEmpty) {
        setState(() => msg = 'No valid holdings. Use format SYMBOL:QTY:PRICE.');
        return;
      }
      final res = await Api.portfolioBuilderAnalyze(holdings);
      setState(() {
        analysis = res;
        msg = 'Analysis complete.';
      });
    } catch (e) {
      setState(() => msg = e.toString());
    }
  }

  @override
  void dispose() {
    holdingsCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('xCAPITAL — Portfolio Builder')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          const Text('Diversification and concentration diagnostics.',
              style: TextStyle(color: Colors.black54)),
          const SizedBox(height: 8),
          TextField(
            controller: holdingsCtrl,
            maxLines: 6,
            decoration: const InputDecoration(
              labelText: 'Holdings (one per line: SYMBOL:QTY:PRICE)',
            ),
          ),
          const SizedBox(height: 8),
          ElevatedButton(onPressed: analyze, child: const Text('Analyze')),
          const SizedBox(height: 8),
          ModuleFeedback(message: msg),
          if (analysis != null) ...[
            const SizedBox(height: 12),
            Text(const JsonEncoder.withIndent('  ').convert(analysis)),
          ],
        ],
      ),
    );
  }
}
