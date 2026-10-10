import 'dart:convert';
import 'package:flutter/material.dart';
import '../widgets/module_feedback.dart';
import '../services/api.dart';

class PortfolioLabScreen extends StatefulWidget {
  const PortfolioLabScreen({super.key});
  @override
  State<PortfolioLabScreen> createState() => _PortfolioLabScreenState();
}

class _PortfolioLabScreenState extends State<PortfolioLabScreen> {
  final muCtrl = TextEditingController(text: '[0.10, 0.07, 0.04]');
  final covCtrl = TextEditingController(text: '[[0.20,0.05,0.02],[0.05,0.12,0.01],[0.02,0.01,0.06]]');
  String msg = 'Tap Login to authenticate.';
  Map<String, dynamic>? result;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('xCAPITAL — Portfolio Lab'),
        actions: [
          IconButton(icon: const Icon(Icons.auto_graph), onPressed: () => Navigator.pushNamed(context, '/auto-invest')),
          IconButton(icon: const Icon(Icons.show_chart), onPressed: () => Navigator.pushNamed(context, '/market-data')),
          IconButton(icon: const Icon(Icons.swap_horiz), onPressed: () => Navigator.pushNamed(context, '/manual-trade')),
          IconButton(icon: const Icon(Icons.bookmark_border), onPressed: () => Navigator.pushNamed(context, '/watchlists')),
          IconButton(icon: const Icon(Icons.notifications_active), onPressed: () => Navigator.pushNamed(context, '/alerts')),
          IconButton(icon: const Icon(Icons.receipt_long), onPressed: () => Navigator.pushNamed(context, '/statements')),
          IconButton(icon: const Icon(Icons.article_outlined), onPressed: () => Navigator.pushNamed(context, '/research')),
          IconButton(icon: const Icon(Icons.timeline), onPressed: () => Navigator.pushNamed(context, '/simulation')),
          IconButton(icon: const Icon(Icons.psychology), onPressed: () => Navigator.pushNamed(context, '/ai')),
          IconButton(icon: const Icon(Icons.history), onPressed: () => Navigator.pushNamed(context, '/audit')),
          IconButton(icon: const Icon(Icons.login), onPressed: () => Navigator.pushNamed(context, '/login')),
        ],
      ),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: ListView(
          children: [
            const Text('Portfolio optimizer with risk constraints.', style: TextStyle(color: Colors.black54)),
            const SizedBox(height: 12),
            TextField(controller: muCtrl, maxLines: 2, decoration: const InputDecoration(labelText: 'mu (JSON array)')),
            const SizedBox(height: 8),
            TextField(controller: covCtrl, maxLines: 3, decoration: const InputDecoration(labelText: 'cov (JSON matrix)')),
            const SizedBox(height: 12),
            ElevatedButton(
              onPressed: () async {
                setState(() { msg = 'Running...'; result = null; });
                try {
                  final mu = (jsonDecode(muCtrl.text) as List).map((e) => (e as num).toDouble()).toList();
                  final cov = (jsonDecode(covCtrl.text) as List).map((row) =>
                      (row as List).map((e) => (e as num).toDouble()).toList()).toList();
                  final r = await Api.optimize(mu, cov);
                  setState(() { result = r; msg = 'Done.'; });
                } catch (e) {
                  setState(() => msg = e.toString());
                }
              },
              child: const Text('Run Optimizer'),
            ),
            const SizedBox(height: 8),
            ModuleFeedback(message: msg),
            if (result != null) ...[
              const SizedBox(height: 12),
              Text(const JsonEncoder.withIndent('  ').convert(result)),
            ]
          ],
        ),
      ),
    );
  }
}
