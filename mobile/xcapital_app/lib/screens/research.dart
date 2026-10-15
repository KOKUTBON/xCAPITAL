import 'dart:convert';
import 'package:flutter/material.dart';
import '../widgets/module_feedback.dart';
import '../services/api.dart';

class ResearchScreen extends StatefulWidget {
  const ResearchScreen({super.key});

  @override
  State<ResearchScreen> createState() => _ResearchScreenState();
}

class _ResearchScreenState extends State<ResearchScreen> {
  final sourceCtrl = TextEditingController(text: 'internal');
  final headlineCtrl = TextEditingController();
  final summaryCtrl = TextEditingController();
  final symbolsCtrl = TextEditingController(text: 'AAPL');
  final sentimentCtrl = TextEditingController(text: '0.2');
  final confidenceCtrl = TextEditingController(text: '0.6');
  final publishedCtrl = TextEditingController();

  final filterSourceCtrl = TextEditingController();
  final limitCtrl = TextEditingController(text: '50');
  final lookbackCtrl = TextEditingController(text: '120');
  final horizonCtrl = TextEditingController(text: '1');

  String msg = '';
  List<dynamic> notes = [];

  @override
  void initState() {
    super.initState();
    loadNotes();
  }

  @override
  void dispose() {
    sourceCtrl.dispose();
    headlineCtrl.dispose();
    summaryCtrl.dispose();
    symbolsCtrl.dispose();
    sentimentCtrl.dispose();
    confidenceCtrl.dispose();
    publishedCtrl.dispose();
    filterSourceCtrl.dispose();
    limitCtrl.dispose();
    lookbackCtrl.dispose();
    horizonCtrl.dispose();
    super.dispose();
  }

  double? _parseDouble(String value) {
    final trimmed = value.trim();
    if (trimmed.isEmpty) return null;
    return double.tryParse(trimmed);
  }

  int _parseInt(String value, int fallback) {
    final trimmed = value.trim();
    if (trimmed.isEmpty) return fallback;
    return int.tryParse(trimmed) ?? fallback;
  }

  Future<void> createNote() async {
    setState(() => msg = 'Saving note...');
    try {
      final symbols = symbolsCtrl.text
          .split(',')
          .map((s) => s.trim())
          .where((s) => s.isNotEmpty)
          .toList();
      await Api.createResearchNote({
        'source': sourceCtrl.text.trim(),
        'headline': headlineCtrl.text.trim(),
        'summary': summaryCtrl.text.trim(),
        'symbols': symbols,
        'sentimentScore': _parseDouble(sentimentCtrl.text),
        'confidence': _parseDouble(confidenceCtrl.text),
        'publishedAt': publishedCtrl.text.trim().isEmpty ? null : publishedCtrl.text.trim(),
      });
      await loadNotes();
      setState(() => msg = 'Note saved.');
    } catch (e) {
      setState(() => msg = e.toString());
    }
  }

  Future<void> loadNotes() async {
    setState(() => msg = 'Loading notes...');
    try {
      final data = await Api.listResearchNotes(
        source: filterSourceCtrl.text.trim(),
        limit: _parseInt(limitCtrl.text, 0),
      );
      setState(() { notes = data; msg = ''; });
    } catch (e) {
      setState(() => msg = e.toString());
    }
  }

  Future<void> refreshNote(String id) async {
    setState(() => msg = 'Refreshing AI...');
    try {
      await Api.refreshResearchNote(
        id,
        lookback: _parseInt(lookbackCtrl.text, 120),
        horizon: _parseInt(horizonCtrl.text, 1),
      );
      await loadNotes();
      setState(() => msg = 'AI refreshed.');
    } catch (e) {
      setState(() => msg = e.toString());
    }
  }

  Future<void> refreshAll() async {
    setState(() => msg = 'Refreshing all notes...');
    try {
      final data = await Api.refreshAllResearchNotes(
        lookback: _parseInt(lookbackCtrl.text, 120),
        horizon: _parseInt(horizonCtrl.text, 1),
      );
      setState(() { notes = data; msg = 'AI refreshed.'; });
    } catch (e) {
      setState(() => msg = e.toString());
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('xCAPITAL - Research')),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: ListView(
          children: [
            const Text('Research notes with AI summaries and scoring.',
                style: TextStyle(color: Colors.black54)),
            const SizedBox(height: 12),
            const Text('Create Note', style: TextStyle(fontWeight: FontWeight.w600)),
            const SizedBox(height: 8),
            TextField(controller: sourceCtrl, decoration: const InputDecoration(labelText: 'Source')),
            const SizedBox(height: 8),
            TextField(controller: headlineCtrl, decoration: const InputDecoration(labelText: 'Headline')),
            const SizedBox(height: 8),
            TextField(controller: summaryCtrl, decoration: const InputDecoration(labelText: 'Summary'), maxLines: 3),
            const SizedBox(height: 8),
            TextField(controller: symbolsCtrl, decoration: const InputDecoration(labelText: 'Symbols (comma-separated)')),
            const SizedBox(height: 8),
            TextField(controller: sentimentCtrl, decoration: const InputDecoration(labelText: 'Sentiment Score'), keyboardType: TextInputType.number),
            const SizedBox(height: 8),
            TextField(controller: confidenceCtrl, decoration: const InputDecoration(labelText: 'Confidence'), keyboardType: TextInputType.number),
            const SizedBox(height: 8),
            TextField(controller: publishedCtrl, decoration: const InputDecoration(labelText: 'Published At (ISO)')),
            const SizedBox(height: 8),
            ElevatedButton(onPressed: createNote, child: const Text('Add Note')),
            const SizedBox(height: 12),
            const Text('AI Refresh', style: TextStyle(fontWeight: FontWeight.w600)),
            const SizedBox(height: 8),
            TextField(controller: lookbackCtrl, decoration: const InputDecoration(labelText: 'Lookback Days'), keyboardType: TextInputType.number),
            const SizedBox(height: 8),
            TextField(controller: horizonCtrl, decoration: const InputDecoration(labelText: 'Horizon'), keyboardType: TextInputType.number),
            const SizedBox(height: 8),
            ElevatedButton(onPressed: refreshAll, child: const Text('Refresh All Notes')),
            const SizedBox(height: 12),
            const Text('Notes', style: TextStyle(fontWeight: FontWeight.w600)),
            const SizedBox(height: 8),
            TextField(controller: filterSourceCtrl, decoration: const InputDecoration(labelText: 'Source Filter')),
            const SizedBox(height: 8),
            TextField(controller: limitCtrl, decoration: const InputDecoration(labelText: 'Limit'), keyboardType: TextInputType.number),
            const SizedBox(height: 8),
            ElevatedButton(onPressed: loadNotes, child: const Text('Refresh List')),
            const SizedBox(height: 8),
            ModuleFeedback(message: msg),
            const SizedBox(height: 8),
            if (notes.isNotEmpty) ...[
              ...notes.map((n) => Card(
                child: ListTile(
                  title: Text(n['headline'] ?? 'Untitled'),
                  subtitle: Text(n['aiSummary'] ?? n['summary'] ?? ''),
                  trailing: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Text(n['aiScore']?.toString() ?? '-', style: const TextStyle(fontWeight: FontWeight.w600)),
                      IconButton(
                        icon: const Icon(Icons.refresh, size: 18),
                        onPressed: () => refreshNote(n['id']),
                      ),
                    ],
                  ),
                ),
              ))
            ] else ...[
              Text(const JsonEncoder.withIndent('  ').convert(notes)),
            ]
          ],
        ),
      ),
    );
  }
}
