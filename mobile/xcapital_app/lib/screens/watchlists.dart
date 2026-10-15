import 'package:flutter/material.dart';
import '../widgets/module_feedback.dart';
import '../services/api.dart';

class WatchlistsScreen extends StatefulWidget {
  const WatchlistsScreen({super.key});

  @override
  State<WatchlistsScreen> createState() => _WatchlistsScreenState();
}

class _WatchlistsScreenState extends State<WatchlistsScreen> {
  final nameCtrl = TextEditingController(text: 'Global Core');
  final descCtrl = TextEditingController();
  final symbolCtrl = TextEditingController(text: 'AAPL');
  final notesCtrl = TextEditingController();

  String assetClass = 'EQUITY';
  String msg = '';
  List<dynamic> watchlists = [];
  List<dynamic> items = [];
  String selectedId = '';

  @override
  void initState() {
    super.initState();
    loadWatchlists();
  }

  @override
  void dispose() {
    nameCtrl.dispose();
    descCtrl.dispose();
    symbolCtrl.dispose();
    notesCtrl.dispose();
    super.dispose();
  }

  Future<void> loadWatchlists() async {
    try {
      final data = await Api.watchlists();
      setState(() { watchlists = data; msg = ''; });
    } catch (e) {
      setState(() => msg = e.toString());
    }
  }

  Future<void> createWatchlist() async {
    setState(() => msg = 'Creating watchlist...');
    try {
      await Api.createWatchlist(nameCtrl.text.trim(), descCtrl.text.trim());
      await loadWatchlists();
      setState(() => msg = 'Watchlist created.');
    } catch (e) {
      setState(() => msg = e.toString());
    }
  }

  Future<void> deleteWatchlist(String id) async {
    setState(() => msg = 'Deleting watchlist...');
    try {
      await Api.deleteWatchlist(id);
      if (selectedId == id) {
        selectedId = '';
        items = [];
      }
      await loadWatchlists();
      setState(() => msg = 'Deleted.');
    } catch (e) {
      setState(() => msg = e.toString());
    }
  }

  Future<void> loadItems(String id) async {
    setState(() { msg = 'Loading items...'; selectedId = id; });
    try {
      final data = await Api.watchlistItems(id);
      setState(() { items = data; msg = ''; });
    } catch (e) {
      setState(() => msg = e.toString());
    }
  }

  Future<void> addItem() async {
    if (selectedId.isEmpty) return;
    setState(() => msg = 'Adding item...');
    try {
      await Api.addWatchlistItem(selectedId, {
        'symbol': symbolCtrl.text.trim(),
        'assetClass': assetClass,
        'notes': notesCtrl.text.trim(),
      });
      await loadItems(selectedId);
      setState(() => msg = 'Item added.');
    } catch (e) {
      setState(() => msg = e.toString());
    }
  }

  Future<void> removeItem(String itemId) async {
    if (selectedId.isEmpty) return;
    setState(() => msg = 'Removing item...');
    try {
      await Api.removeWatchlistItem(selectedId, itemId);
      await loadItems(selectedId);
      setState(() => msg = 'Item removed.');
    } catch (e) {
      setState(() => msg = e.toString());
    }
  }

  Future<void> refreshInsights() async {
    if (selectedId.isEmpty) return;
    setState(() => msg = 'Refreshing AI insights...');
    try {
      final data = await Api.refreshWatchlistInsights(selectedId);
      setState(() { items = data; msg = 'AI insights refreshed.'; });
    } catch (e) {
      setState(() => msg = e.toString());
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('xCAPITAL — Watchlists')),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: ListView(
          children: [
            const Text('Multi-asset watchlists with AI risk insights.',
                style: TextStyle(color: Colors.black54)),
            const SizedBox(height: 12),
            TextField(controller: nameCtrl, decoration: const InputDecoration(labelText: 'Name')),
            const SizedBox(height: 8),
            TextField(controller: descCtrl, decoration: const InputDecoration(labelText: 'Description')),
            const SizedBox(height: 8),
            ElevatedButton(onPressed: createWatchlist, child: const Text('Create Watchlist')),
            const SizedBox(height: 8),
            ModuleFeedback(message: msg),
            const SizedBox(height: 12),

            if (watchlists.isNotEmpty) ...[
              const Text('Watchlists', style: TextStyle(fontWeight: FontWeight.w600)),
              const SizedBox(height: 8),
              ...watchlists.map((w) => ListTile(
                title: Text(w['name'] ?? ''),
                subtitle: Text(w['description'] ?? ''),
                trailing: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    IconButton(icon: const Icon(Icons.list), onPressed: () => loadItems(w['id'])),
                    IconButton(icon: const Icon(Icons.delete), onPressed: () => deleteWatchlist(w['id'])),
                  ],
                ),
              )),
            ],

            if (selectedId.isNotEmpty) ...[
              const SizedBox(height: 16),
              const Text('Items', style: TextStyle(fontWeight: FontWeight.w600)),
              const SizedBox(height: 8),
              TextField(controller: symbolCtrl, decoration: const InputDecoration(labelText: 'Symbol')),
              const SizedBox(height: 8),
              DropdownButtonFormField<String>(
                value: assetClass,
                decoration: const InputDecoration(labelText: 'Asset Class'),
                items: const [
                  DropdownMenuItem(value: 'EQUITY', child: Text('Equity')),
                  DropdownMenuItem(value: 'ETF', child: Text('ETF')),
                  DropdownMenuItem(value: 'FIXED_INCOME', child: Text('Fixed Income')),
                  DropdownMenuItem(value: 'OPTIONS', child: Text('Options')),
                  DropdownMenuItem(value: 'FUTURES', child: Text('Futures')),
                  DropdownMenuItem(value: 'FX', child: Text('FX')),
                  DropdownMenuItem(value: 'CRYPTO', child: Text('Crypto')),
                  DropdownMenuItem(value: 'COMMODITIES', child: Text('Commodities')),
                  DropdownMenuItem(value: 'MUTUAL_FUND', child: Text('Mutual Fund')),
                ],
                onChanged: (v) => setState(() => assetClass = v ?? 'EQUITY'),
              ),
              const SizedBox(height: 8),
              TextField(controller: notesCtrl, decoration: const InputDecoration(labelText: 'Notes')),
              const SizedBox(height: 8),
              Row(
                children: [
                  Expanded(child: ElevatedButton(onPressed: addItem, child: const Text('Add Item'))),
                  const SizedBox(width: 8),
                  Expanded(child: ElevatedButton(onPressed: refreshInsights, child: const Text('Refresh AI'))),
                ],
              ),
              const SizedBox(height: 8),
              if (items.isNotEmpty) ...[
                ...items.map((i) => ListTile(
                  title: Text('${i['symbol']} (${i['assetClass'] ?? ''})'),
                  subtitle: Text(i['aiSummary'] ?? i['notes'] ?? ''),
                  trailing: IconButton(icon: const Icon(Icons.remove_circle_outline), onPressed: () => removeItem(i['id'])),
                ))
              ]
            ]
          ],
        ),
      ),
    );
  }
}
