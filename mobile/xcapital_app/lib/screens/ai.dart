import 'dart:convert';
import 'package:flutter/material.dart';
import '../widgets/module_feedback.dart';
import '../services/api.dart';

class AiScreen extends StatefulWidget {
  const AiScreen({super.key});

  @override
  State<AiScreen> createState() => _AiScreenState();
}

class _AiScreenState extends State<AiScreen> {
  final returnsCtrl = TextEditingController(text: '[0.01,-0.02,0.005,0.012,-0.003,0.004,0.002,-0.006,0.009,0.003,0.002,0.001,-0.004,0.006,0.004,0.003,-0.002,0.001,0.002,0.003,0.004,0.002,-0.003,0.005,0.006,0.001,-0.001,0.002,0.003,0.004,0.002,0.001,0.002,0.001,0.003,0.002,0.001,0.003,0.004,0.002,0.001,0.002,0.003,0.004,0.002,0.001,0.002,0.003,0.004,0.002,0.001,0.002,0.003,0.004,0.002,0.001,0.002,0.003,0.004,0.002]');
  final horizonCtrl = TextEditingController(text: '1');
  final windowCtrl = TextEditingController(text: '60');

  final modelNameCtrl = TextEditingController(text: 'risk-volatility');
  final modelVersionCtrl = TextEditingController(text: 'v1');
  final metricsCtrl = TextEditingController(text: '{"mae":0.01,"mse":0.0002}');

  String msg = '';
  Map<String, dynamic>? forecast;
  Map<String, dynamic>? risk;
  Map<String, dynamic>? evaluation;
  List<dynamic> models = [];

  @override
  void initState() {
    super.initState();
    loadModels();
  }

  @override
  void dispose() {
    returnsCtrl.dispose();
    horizonCtrl.dispose();
    windowCtrl.dispose();
    modelNameCtrl.dispose();
    modelVersionCtrl.dispose();
    metricsCtrl.dispose();
    super.dispose();
  }

  List<double>? _parseReturns() {
    try {
      return (jsonDecode(returnsCtrl.text) as List)
          .map((e) => (e as num).toDouble()).toList();
    } catch (_) {
      setState(() => msg = 'Invalid returns JSON');
      return null;
    }
  }

  Future<void> runForecast() async {
    final returns = _parseReturns();
    if (returns == null) return;
    setState(() { msg = 'Forecasting...'; forecast = null; });
    try {
      final res = await Api.aiPredict(returns, int.tryParse(horizonCtrl.text) ?? 1);
      setState(() { forecast = res; msg = ''; });
    } catch (e) {
      setState(() => msg = e.toString());
    }
  }

  Future<void> runRisk() async {
    final returns = _parseReturns();
    if (returns == null) return;
    setState(() { msg = 'Risk forecasting...'; risk = null; });
    try {
      final res = await Api.aiRisk(returns, int.tryParse(horizonCtrl.text) ?? 1);
      setState(() { risk = res; msg = ''; });
    } catch (e) {
      setState(() => msg = e.toString());
    }
  }

  Future<void> runEvaluation() async {
    final returns = _parseReturns();
    if (returns == null) return;
    setState(() { msg = 'Evaluating...'; evaluation = null; });
    try {
      final res = await Api.aiEvaluate(
        returns,
        int.tryParse(horizonCtrl.text) ?? 1,
        int.tryParse(windowCtrl.text) ?? 60,
      );
      setState(() { evaluation = res; msg = ''; });
    } catch (e) {
      setState(() => msg = e.toString());
    }
  }

  Future<void> loadModels() async {
    try {
      final res = await Api.aiModels();
      setState(() => models = res);
    } catch (_) {}
  }

  Future<void> registerModel() async {
    setState(() => msg = 'Registering model...');
    Map<String, dynamic> metrics = {};
    if (metricsCtrl.text.trim().isNotEmpty) {
      try {
        metrics = jsonDecode(metricsCtrl.text) as Map<String, dynamic>;
      } catch (_) {
        setState(() => msg = 'Invalid metrics JSON');
        return;
      }
    }
    final body = {
      'modelName': modelNameCtrl.text.trim(),
      'version': modelVersionCtrl.text.trim(),
      'metrics': metrics,
      'status': 'DEPLOYED'
    };
    try {
      await Api.aiRegisterModel(body);
      setState(() => msg = 'Registered.');
      await loadModels();
    } catch (e) {
      setState(() => msg = e.toString());
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('xCAPITAL — AI')),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: ListView(
          children: [
            const Text('Risk-first AI forecasting and evaluation.',
                style: TextStyle(color: Colors.black54)),
            const SizedBox(height: 12),
            TextField(
              controller: returnsCtrl,
              maxLines: 4,
              decoration: const InputDecoration(labelText: 'Returns (JSON array)'),
            ),
            const SizedBox(height: 8),
            TextField(controller: horizonCtrl, decoration: const InputDecoration(labelText: 'Horizon')),
            const SizedBox(height: 8),
            TextField(controller: windowCtrl, decoration: const InputDecoration(labelText: 'Evaluation Window')),
            const SizedBox(height: 8),
            Row(
              children: [
                Expanded(child: ElevatedButton(onPressed: runForecast, child: const Text('Return Forecast'))),
                const SizedBox(width: 8),
                Expanded(child: ElevatedButton(onPressed: runRisk, child: const Text('Risk Forecast'))),
                const SizedBox(width: 8),
                Expanded(child: ElevatedButton(onPressed: runEvaluation, child: const Text('Evaluate'))),
              ],
            ),
            const SizedBox(height: 8),
            ModuleFeedback(message: msg),

            if (forecast != null) ...[
              const SizedBox(height: 8),
              const Text('Return Forecast', style: TextStyle(fontWeight: FontWeight.w600)),
              Text(const JsonEncoder.withIndent('  ').convert(forecast)),
            ],
            if (risk != null) ...[
              const SizedBox(height: 8),
              const Text('Risk Forecast', style: TextStyle(fontWeight: FontWeight.w600)),
              Text(const JsonEncoder.withIndent('  ').convert(risk)),
            ],
            if (evaluation != null) ...[
              const SizedBox(height: 8),
              const Text('Evaluation', style: TextStyle(fontWeight: FontWeight.w600)),
              Text(const JsonEncoder.withIndent('  ').convert(evaluation)),
            ],

            const SizedBox(height: 16),
            const Text('Model Registry', style: TextStyle(fontWeight: FontWeight.w600)),
            const SizedBox(height: 8),
            TextField(controller: modelNameCtrl, decoration: const InputDecoration(labelText: 'Model Name')),
            const SizedBox(height: 8),
            TextField(controller: modelVersionCtrl, decoration: const InputDecoration(labelText: 'Version')),
            const SizedBox(height: 8),
            TextField(controller: metricsCtrl, decoration: const InputDecoration(labelText: 'Metrics (JSON)')),
            const SizedBox(height: 8),
            Row(
              children: [
                Expanded(child: ElevatedButton(onPressed: registerModel, child: const Text('Register'))),
                const SizedBox(width: 8),
                Expanded(child: ElevatedButton(onPressed: loadModels, child: const Text('Refresh'))),
              ],
            ),
            const SizedBox(height: 8),
            ...models.map((m) => ListTile(
              dense: true,
              title: Text('${m['modelName']} ${m['version']} (${m['status']})'),
              subtitle: Text('Created: ${m['createdAt'] ?? ''}'),
            )),
          ],
        ),
      ),
    );
  }
}
