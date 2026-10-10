import 'dart:convert';
import 'package:flutter/material.dart';
import '../widgets/module_feedback.dart';
import '../services/api.dart';

class MarketDataScreen extends StatefulWidget {
  const MarketDataScreen({super.key});

  @override
  State<MarketDataScreen> createState() => _MarketDataScreenState();
}

class _MarketDataScreenState extends State<MarketDataScreen> {
  final symbolsCtrl = TextEditingController(text: 'AAPL,MSFT');
  final symbolCtrl = TextEditingController(text: 'AAPL');
  final startCtrl = TextEditingController();
  final endCtrl = TextEditingController();
  final limitCtrl = TextEditingController(text: '0');
  final backfillSymbolsCtrl = TextEditingController(text: 'AAPL,MSFT');
  final backfillStartCtrl = TextEditingController();
  final backfillEndCtrl = TextEditingController();
  final backfillLimitCtrl = TextEditingController(text: '0');
  final backfillSourceCtrl = TextEditingController(text: 'csv');
  final licenseProviderCtrl = TextEditingController(text: 'starter_csv');
  final licensePlanCtrl = TextEditingController(text: 'internal');
  final licenseAssetClassesCtrl = TextEditingController(text: 'EQUITY');
  final licenseExchangesCtrl = TextEditingController();
  final licenseRegionsCtrl = TextEditingController(text: 'US');
  final entitlementValueCtrl = TextEditingController(text: 'AAPL');
  final entitlementSourceCtrl = TextEditingController(text: 'starter_csv');

  String granularity = 'DAY';
  String msg = '';
  String backfillGranularity = 'DAY';
  String backfillMsg = '';
  String licenseStatus = 'ACTIVE';
  String entitlementType = 'SYMBOL';
  String entitlementStatus = 'ACTIVE';
  String licenseMsg = '';
  String entitlementMsg = '';

  Map<String, dynamic>? quoteMeta;
  List<dynamic>? quotes;
  Map<String, dynamic>? history;
  Map<String, dynamic>? backfillResult;
  List<dynamic>? licenses;
  List<dynamic>? entitlements;

  @override
  void dispose() {
    symbolsCtrl.dispose();
    symbolCtrl.dispose();
    startCtrl.dispose();
    endCtrl.dispose();
    limitCtrl.dispose();
    backfillSymbolsCtrl.dispose();
    backfillStartCtrl.dispose();
    backfillEndCtrl.dispose();
    backfillLimitCtrl.dispose();
    backfillSourceCtrl.dispose();
    licenseProviderCtrl.dispose();
    licensePlanCtrl.dispose();
    licenseAssetClassesCtrl.dispose();
    licenseExchangesCtrl.dispose();
    licenseRegionsCtrl.dispose();
    entitlementValueCtrl.dispose();
    entitlementSourceCtrl.dispose();
    super.dispose();
  }

  Future<void> loadQuotes() async {
    final symbols = symbolsCtrl.text.trim();
    if (symbols.isEmpty) {
      setState(() => msg = 'Symbols required.');
      return;
    }
    setState(() {
      msg = 'Loading quotes...';
      quoteMeta = null;
      quotes = null;
    });
    try {
      final res = await Api.latestQuotes(symbols);
      setState(() {
        quoteMeta = res;
        quotes = (res['quotes'] as List<dynamic>?) ?? [];
        msg = 'Quotes loaded.';
      });
    } catch (e) {
      setState(() => msg = e.toString());
    }
  }

  Future<void> loadHistory() async {
    final symbol = symbolCtrl.text.trim();
    if (symbol.isEmpty) {
      setState(() => msg = 'Symbol required.');
      return;
    }
    setState(() {
      msg = 'Loading history...';
      history = null;
    });
    try {
      final limit = int.tryParse(limitCtrl.text.trim()) ?? 0;
      final res = await Api.marketHistory(
        symbol: symbol,
        start: startCtrl.text.trim(),
        end: endCtrl.text.trim(),
        granularity: granularity,
        limit: limit,
      );
      setState(() {
        history = res;
        msg = 'History loaded.';
      });
    } catch (e) {
      setState(() => msg = e.toString());
    }
  }

  Future<void> runBackfill() async {
    final raw = backfillSymbolsCtrl.text.trim();
    if (raw.isEmpty) {
      setState(() => backfillMsg = 'Symbols required.');
      return;
    }
    final symbols = raw.split(',').map((s) => s.trim()).where((s) => s.isNotEmpty).toList();
    if (symbols.isEmpty) {
      setState(() => backfillMsg = 'Symbols required.');
      return;
    }
    setState(() {
      backfillMsg = 'Running backfill...';
      backfillResult = null;
    });
    try {
      final limit = int.tryParse(backfillLimitCtrl.text.trim()) ?? 0;
      final res = await Api.marketBackfill(
        symbols: symbols,
        start: backfillStartCtrl.text.trim(),
        end: backfillEndCtrl.text.trim(),
        granularity: backfillGranularity,
        limit: limit,
        source: backfillSourceCtrl.text.trim().isEmpty ? 'csv' : backfillSourceCtrl.text.trim(),
      );
      setState(() {
        backfillResult = res;
        backfillMsg = 'Backfill complete.';
      });
    } catch (e) {
      setState(() => backfillMsg = e.toString());
    }
  }

  Future<void> loadLicenses() async {
    setState(() => licenseMsg = 'Loading licenses...');
    try {
      final res = await Api.marketDataLicenses();
      setState(() {
        licenses = res;
        licenseMsg = 'Licenses loaded.';
      });
    } catch (e) {
      setState(() => licenseMsg = e.toString());
    }
  }

  Future<void> saveLicense() async {
    final provider = licenseProviderCtrl.text.trim();
    if (provider.isEmpty) {
      setState(() => licenseMsg = 'Provider required.');
      return;
    }
    setState(() => licenseMsg = 'Saving license...');
    final body = <String, dynamic>{
      'provider': provider,
      'plan': licensePlanCtrl.text.trim(),
      'status': licenseStatus,
      'assetClasses': _parseCsvList(licenseAssetClassesCtrl.text),
      'exchanges': _parseCsvList(licenseExchangesCtrl.text),
      'regions': _parseCsvList(licenseRegionsCtrl.text),
    };
    try {
      await Api.upsertMarketDataLicense(body);
      await loadLicenses();
      setState(() => licenseMsg = 'License saved.');
    } catch (e) {
      setState(() => licenseMsg = e.toString());
    }
  }

  Future<void> loadEntitlements() async {
    setState(() => entitlementMsg = 'Loading entitlements...');
    try {
      final res = await Api.marketDataEntitlements();
      setState(() {
        entitlements = res;
        entitlementMsg = 'Entitlements loaded.';
      });
    } catch (e) {
      setState(() => entitlementMsg = e.toString());
    }
  }

  Future<void> saveEntitlement() async {
    final value = entitlementValueCtrl.text.trim();
    if (entitlementType != 'GLOBAL' && value.isEmpty) {
      setState(() => entitlementMsg = 'Value required.');
      return;
    }
    setState(() => entitlementMsg = 'Saving entitlement...');
    final body = <String, dynamic>{
      'entitlementType': entitlementType,
      'status': entitlementStatus,
      'source': entitlementSourceCtrl.text.trim(),
    };
    if (entitlementType != 'GLOBAL') {
      body['entitlementValue'] = value;
    }
    try {
      await Api.upsertMarketDataEntitlement(body);
      await loadEntitlements();
      setState(() => entitlementMsg = 'Entitlement saved.');
    } catch (e) {
      setState(() => entitlementMsg = e.toString());
    }
  }

  List<String> _parseCsvList(String raw) {
    return raw.split(',').map((s) => s.trim()).where((s) => s.isNotEmpty).toList();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('xCAPITAL — Market Data')),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: ListView(
          children: [
            const Text('Cached quotes + provider history + CSV backfills.',
                style: TextStyle(color: Colors.black54)),
            const SizedBox(height: 16),

            const Text('Latest Quotes (Cached)', style: TextStyle(fontWeight: FontWeight.w600)),
            const SizedBox(height: 8),
            TextField(controller: symbolsCtrl, decoration: const InputDecoration(labelText: 'Symbols (comma-separated)')),
            const SizedBox(height: 8),
            ElevatedButton(onPressed: loadQuotes, child: const Text('Load Quotes')),
            const SizedBox(height: 6),
            ModuleFeedback(message: msg),

            if (quoteMeta != null) ...[
              const SizedBox(height: 8),
              Text(
                'Cache hits: ${quoteMeta?['cacheHits'] ?? 0} | Fetched: ${quoteMeta?['fetched'] ?? 0} | Missing: ${(quoteMeta?['missing'] as List?)?.length ?? 0}',
                style: const TextStyle(fontSize: 12, color: Colors.black54),
              ),
            ],

            if (quotes != null && quotes!.isNotEmpty) ...[
              const SizedBox(height: 8),
              ...quotes!.map((q) => ListTile(
                dense: true,
                title: Text('${q['symbol']}  ${q['price']}'),
                subtitle: Text('ts: ${q['timestamp']} | src: ${q['source']} | cache: ${q['cacheHit'] ? 'HIT' : 'MISS'}'),
              )),
            ],

            const SizedBox(height: 20),
            const Text('Market Data Access', style: TextStyle(fontWeight: FontWeight.w600)),
            const SizedBox(height: 8),
            TextField(controller: licenseProviderCtrl, decoration: const InputDecoration(labelText: 'License provider')),
            const SizedBox(height: 8),
            TextField(controller: licensePlanCtrl, decoration: const InputDecoration(labelText: 'Plan')),
            const SizedBox(height: 8),
            DropdownButtonFormField<String>(
              value: licenseStatus,
              decoration: const InputDecoration(labelText: 'License status'),
              items: const [
                DropdownMenuItem(value: 'ACTIVE', child: Text('ACTIVE')),
                DropdownMenuItem(value: 'SUSPENDED', child: Text('SUSPENDED')),
                DropdownMenuItem(value: 'EXPIRED', child: Text('EXPIRED')),
              ],
              onChanged: (v) => setState(() => licenseStatus = v ?? 'ACTIVE'),
            ),
            const SizedBox(height: 8),
            TextField(controller: licenseAssetClassesCtrl, decoration: const InputDecoration(labelText: 'Asset classes')),
            const SizedBox(height: 8),
            TextField(controller: licenseExchangesCtrl, decoration: const InputDecoration(labelText: 'Exchanges')),
            const SizedBox(height: 8),
            TextField(controller: licenseRegionsCtrl, decoration: const InputDecoration(labelText: 'Regions')),
            const SizedBox(height: 8),
            Row(
              children: [
                Expanded(child: ElevatedButton(onPressed: saveLicense, child: const Text('Save License'))),
                const SizedBox(width: 8),
                Expanded(child: OutlinedButton(onPressed: loadLicenses, child: const Text('Load Licenses'))),
              ],
            ),
            const SizedBox(height: 6),
            Text(licenseMsg, style: const TextStyle(fontSize: 12, color: Colors.black54)),
            if (licenses != null) ...[
              const SizedBox(height: 6),
              Text(const JsonEncoder.withIndent('  ').convert(licenses)),
            ],

            const SizedBox(height: 16),
            const Text('Entitlements', style: TextStyle(fontWeight: FontWeight.w600)),
            const SizedBox(height: 8),
            DropdownButtonFormField<String>(
              value: entitlementType,
              decoration: const InputDecoration(labelText: 'Entitlement type'),
              items: const [
                DropdownMenuItem(value: 'GLOBAL', child: Text('GLOBAL')),
                DropdownMenuItem(value: 'SYMBOL', child: Text('SYMBOL')),
                DropdownMenuItem(value: 'EXCHANGE', child: Text('EXCHANGE')),
                DropdownMenuItem(value: 'ASSET_CLASS', child: Text('ASSET_CLASS')),
                DropdownMenuItem(value: 'REGION', child: Text('REGION')),
              ],
              onChanged: (v) => setState(() => entitlementType = v ?? 'SYMBOL'),
            ),
            const SizedBox(height: 8),
            TextField(controller: entitlementValueCtrl, decoration: const InputDecoration(labelText: 'Value (symbol/exchange/etc)')),
            const SizedBox(height: 8),
            DropdownButtonFormField<String>(
              value: entitlementStatus,
              decoration: const InputDecoration(labelText: 'Entitlement status'),
              items: const [
                DropdownMenuItem(value: 'ACTIVE', child: Text('ACTIVE')),
                DropdownMenuItem(value: 'REVOKED', child: Text('REVOKED')),
              ],
              onChanged: (v) => setState(() => entitlementStatus = v ?? 'ACTIVE'),
            ),
            const SizedBox(height: 8),
            TextField(controller: entitlementSourceCtrl, decoration: const InputDecoration(labelText: 'Source')),
            const SizedBox(height: 8),
            Row(
              children: [
                Expanded(child: ElevatedButton(onPressed: saveEntitlement, child: const Text('Save Entitlement'))),
                const SizedBox(width: 8),
                Expanded(child: OutlinedButton(onPressed: loadEntitlements, child: const Text('Load Entitlements'))),
              ],
            ),
            const SizedBox(height: 6),
            Text(entitlementMsg, style: const TextStyle(fontSize: 12, color: Colors.black54)),
            if (entitlements != null) ...[
              const SizedBox(height: 6),
              Text(const JsonEncoder.withIndent('  ').convert(entitlements)),
            ],

            const SizedBox(height: 20),
            const Text('Provider History', style: TextStyle(fontWeight: FontWeight.w600)),
            const SizedBox(height: 8),
            TextField(controller: symbolCtrl, decoration: const InputDecoration(labelText: 'Symbol')),
            const SizedBox(height: 8),
            TextField(controller: startCtrl, decoration: const InputDecoration(labelText: 'Start (YYYY-MM-DD)')),
            const SizedBox(height: 8),
            TextField(controller: endCtrl, decoration: const InputDecoration(labelText: 'End (YYYY-MM-DD)')),
            const SizedBox(height: 8),
            DropdownButtonFormField<String>(
              value: granularity,
              decoration: const InputDecoration(labelText: 'Granularity'),
              items: const [
                DropdownMenuItem(value: 'MINUTE', child: Text('Minute')),
                DropdownMenuItem(value: 'HOUR', child: Text('Hour')),
                DropdownMenuItem(value: 'DAY', child: Text('Day')),
              ],
              onChanged: (v) => setState(() => granularity = v ?? 'DAY'),
            ),
            const SizedBox(height: 8),
            TextField(controller: limitCtrl, decoration: const InputDecoration(labelText: 'Limit')),
            const SizedBox(height: 8),
            ElevatedButton(onPressed: loadHistory, child: const Text('Load History')),

            if (history != null) ...[
              const SizedBox(height: 12),
              Text('Points: ${history?['points'] ?? 0} | Granularity: ${history?['granularity'] ?? ''}',
                  style: const TextStyle(fontSize: 12, color: Colors.black54)),
              const SizedBox(height: 8),
              Text(const JsonEncoder.withIndent('  ').convert(history)),
            ],

            const SizedBox(height: 20),
            const Text('CSV Backfill', style: TextStyle(fontWeight: FontWeight.w600)),
            const SizedBox(height: 8),
            TextField(controller: backfillSymbolsCtrl, decoration: const InputDecoration(labelText: 'Symbols (comma-separated)')),
            const SizedBox(height: 8),
            TextField(controller: backfillStartCtrl, decoration: const InputDecoration(labelText: 'Start (YYYY-MM-DD)')),
            const SizedBox(height: 8),
            TextField(controller: backfillEndCtrl, decoration: const InputDecoration(labelText: 'End (YYYY-MM-DD)')),
            const SizedBox(height: 8),
            DropdownButtonFormField<String>(
              value: backfillGranularity,
              decoration: const InputDecoration(labelText: 'Granularity'),
              items: const [
                DropdownMenuItem(value: 'MINUTE', child: Text('Minute')),
                DropdownMenuItem(value: 'HOUR', child: Text('Hour')),
                DropdownMenuItem(value: 'DAY', child: Text('Day')),
              ],
              onChanged: (v) => setState(() => backfillGranularity = v ?? 'DAY'),
            ),
            const SizedBox(height: 8),
            TextField(controller: backfillLimitCtrl, decoration: const InputDecoration(labelText: 'Limit')),
            const SizedBox(height: 8),
            TextField(controller: backfillSourceCtrl, decoration: const InputDecoration(labelText: 'Source')),
            const SizedBox(height: 8),
            ElevatedButton(onPressed: runBackfill, child: const Text('Run Backfill')),
            const SizedBox(height: 6),
            Text(backfillMsg, style: const TextStyle(fontSize: 12, color: Colors.black54)),
            if (backfillResult != null) ...[
              const SizedBox(height: 8),
              Text(const JsonEncoder.withIndent('  ').convert(backfillResult)),
            ],
          ],
        ),
      ),
    );
  }
}
