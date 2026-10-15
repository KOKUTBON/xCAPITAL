import 'dart:convert';
import 'package:flutter/material.dart';
import '../widgets/module_feedback.dart';
import '../services/api.dart';

class _LegEntry {
  final symbolCtrl = TextEditingController(text: '');
  final instrumentCtrl = TextEditingController(text: '');
  final qtyCtrl = TextEditingController(text: '1');
  final limitCtrl = TextEditingController(text: '');
  final stopCtrl = TextEditingController(text: '');
  final multiplierCtrl = TextEditingController(text: '');
  final optionStrikeCtrl = TextEditingController(text: '');
  final optionExpiryCtrl = TextEditingController(text: '');

  String side = 'BUY';
  String assetClass = 'EQUITY';
  String optionType = 'CALL';

  void dispose() {
    symbolCtrl.dispose();
    instrumentCtrl.dispose();
    qtyCtrl.dispose();
    limitCtrl.dispose();
    stopCtrl.dispose();
    multiplierCtrl.dispose();
    optionStrikeCtrl.dispose();
    optionExpiryCtrl.dispose();
  }
}

class ManualTradeScreen extends StatefulWidget {
  const ManualTradeScreen({super.key});

  @override
  State<ManualTradeScreen> createState() => _ManualTradeScreenState();
}

class _ManualTradeScreenState extends State<ManualTradeScreen> {
  final symbolCtrl = TextEditingController(text: 'AAPL');
  final clientOrderCtrl = TextEditingController(text: '');
  final instrumentCtrl = TextEditingController(text: '');
  final qtyCtrl = TextEditingController(text: '1');
  final limitCtrl = TextEditingController(text: '');
  final stopCtrl = TextEditingController(text: '');
  final currencyCtrl = TextEditingController(text: 'USD');
  final routingCtrl = TextEditingController(text: '');
  final multiplierCtrl = TextEditingController(text: '');
  final optionStrikeCtrl = TextEditingController(text: '');
  final optionExpiryCtrl = TextEditingController(text: '');
  final notesCtrl = TextEditingController(text: '');
  final horizonCtrl = TextEditingController(text: '1');
  final lookbackCtrl = TextEditingController(text: '120');

  List<Map<String, dynamic>> accounts = [];
  String? accountId;

  String side = 'BUY';
  String orderType = 'MARKET';
  String timeInForce = 'DAY';
  String assetClass = 'EQUITY';
  String optionType = 'CALL';
  bool allowFractional = true;
  bool useLegs = false;
  final List<_LegEntry> legs = [_LegEntry()];

  String msg = '';
  Map<String, dynamic>? review;

  @override
  void initState() {
    super.initState();
    loadAccounts();
  }

  @override
  void dispose() {
    symbolCtrl.dispose();
    clientOrderCtrl.dispose();
    instrumentCtrl.dispose();
    qtyCtrl.dispose();
    limitCtrl.dispose();
    stopCtrl.dispose();
    currencyCtrl.dispose();
    routingCtrl.dispose();
    multiplierCtrl.dispose();
    optionStrikeCtrl.dispose();
    optionExpiryCtrl.dispose();
    notesCtrl.dispose();
    horizonCtrl.dispose();
    lookbackCtrl.dispose();
    for (final leg in legs) {
      leg.dispose();
    }
    super.dispose();
  }

  Future<void> loadAccounts() async {
    setState(() => msg = 'Loading accounts...');
    try {
      final res = await Api.brokerAccounts();
      accounts = res
          .whereType<Map>()
          .map((account) => Map<String, dynamic>.from(account))
          .toList();
      if (accounts.isNotEmpty) {
        accountId ??= accounts.first['id']?.toString();
      }
      msg = '';
    } catch (e) {
      msg = e.toString();
    }
    setState(() {});
  }

  void addLeg() {
    setState(() => legs.add(_LegEntry()));
  }

  void removeLeg(int index) {
    if (legs.length <= 1) return;
    setState(() {
      legs[index].dispose();
      legs.removeAt(index);
    });
  }

  Map<String, dynamic> buildOrder() {
    final qty = double.tryParse(qtyCtrl.text) ?? 0.0;
    final limitPrice = double.tryParse(limitCtrl.text);
    final stopPrice = double.tryParse(stopCtrl.text);
    final metadata = <String, dynamic>{};
    if (routingCtrl.text.trim().isNotEmpty) {
      metadata['routing'] = routingCtrl.text.trim();
    }
    if (notesCtrl.text.trim().isNotEmpty) {
      metadata['notes'] = notesCtrl.text.trim();
    }
    if (!useLegs && (assetClass == 'OPTIONS' || assetClass == 'FUTURES') && multiplierCtrl.text.trim().isNotEmpty) {
      final multiplier = double.tryParse(multiplierCtrl.text);
      if (multiplier != null) {
        metadata['contractMultiplier'] = multiplier;
      }
    }
    if (!useLegs && assetClass == 'OPTIONS') {
      metadata['optionType'] = optionType;
      final strike = double.tryParse(optionStrikeCtrl.text);
      if (strike != null) {
        metadata['optionStrike'] = strike;
      }
      if (optionExpiryCtrl.text.trim().isNotEmpty) {
        metadata['optionExpiry'] = optionExpiryCtrl.text.trim();
      }
    }
    final order = <String, dynamic>{
      'orderType': orderType,
      'timeInForce': timeInForce,
      'currency': currencyCtrl.text.trim(),
      'allowFractional': allowFractional,
    };
    if (useLegs) {
      final legPayload = <Map<String, dynamic>>[];
      double totalQty = 0.0;
      for (final leg in legs) {
        final legQty = double.tryParse(leg.qtyCtrl.text) ?? 0.0;
        final legLimit = double.tryParse(leg.limitCtrl.text);
        final legStop = double.tryParse(leg.stopCtrl.text);
        final legMultiplier = double.tryParse(leg.multiplierCtrl.text);
        final legStrike = double.tryParse(leg.optionStrikeCtrl.text);
        final legMeta = <String, dynamic>{};
        if (legMultiplier != null) {
          legMeta['contractMultiplier'] = legMultiplier;
        }
        final entry = <String, dynamic>{
          'symbol': leg.symbolCtrl.text.trim(),
          'instrumentId': leg.instrumentCtrl.text.trim(),
          'side': leg.side,
          'quantity': legQty,
          'assetClass': leg.assetClass,
          'limitPrice': legLimit,
          'stopPrice': legStop,
          'optionType': leg.optionType,
          'strike': legStrike,
          'expiry': leg.optionExpiryCtrl.text.trim().isEmpty ? null : leg.optionExpiryCtrl.text.trim(),
        };
        if (leg.assetClass != 'OPTIONS') {
          entry.remove('optionType');
          entry.remove('strike');
          entry.remove('expiry');
        }
        if ((entry['symbol'] as String).isEmpty) {
          entry.remove('symbol');
        }
        if ((entry['instrumentId'] as String).isEmpty) {
          entry.remove('instrumentId');
        }
        if (legMeta.isNotEmpty) {
          entry['metadata'] = legMeta;
        }
        if (!entry.containsKey('symbol') && !entry.containsKey('instrumentId')) {
          continue;
        }
        legPayload.add(entry);
        totalQty += legQty;
      }
      order['legs'] = legPayload;
      order['symbol'] = legPayload.isEmpty ? '' : (legPayload.first['symbol'] ?? '');
      order['assetClass'] = legPayload.isEmpty ? assetClass : (legPayload.first['assetClass'] ?? assetClass);
      order['side'] = side;
      order['quantity'] = totalQty;
    } else {
      order['symbol'] = symbolCtrl.text.trim();
      order['side'] = side;
      order['quantity'] = qty;
      order['assetClass'] = assetClass;
      order['limitPrice'] = limitPrice;
      order['stopPrice'] = stopPrice;
    }
    if (clientOrderCtrl.text.trim().isNotEmpty) {
      order['clientOrderId'] = clientOrderCtrl.text.trim();
    }
    if (!useLegs && instrumentCtrl.text.trim().isNotEmpty) {
      order['instrumentId'] = instrumentCtrl.text.trim();
    }
    if (metadata.isNotEmpty) {
      order['metadata'] = metadata;
    }
    return order;
  }

  Future<void> reviewOrder() async {
    if (accountId == null || accountId!.isEmpty) {
      setState(() => msg = 'Select a broker account.');
      return;
    }
    setState(() { msg = 'Reviewing...'; review = null; });
    try {
      final horizon = int.tryParse(horizonCtrl.text) ?? 1;
      final lookback = int.tryParse(lookbackCtrl.text) ?? 120;
      final res = await Api.brokerOrderReview(accountId!, buildOrder(),
          aiHorizon: horizon, lookback: lookback, includeCompliance: true);
      review = res;
      msg = 'Review ready.';
    } catch (e) {
      msg = e.toString();
    }
    setState(() {});
  }

  Future<void> placeOrder() async {
    if (accountId == null || accountId!.isEmpty) {
      setState(() => msg = 'Select a broker account.');
      return;
    }
    setState(() { msg = 'Placing order...'; });
    try {
      final res = await Api.brokerPlaceOrder(accountId!, buildOrder());
      review = res;
      msg = 'Order submitted.';
    } catch (e) {
      msg = e.toString();
    }
    setState(() {});
  }

  Widget buildLegCard(int index, _LegEntry leg) {
    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Leg ${index + 1}', style: const TextStyle(fontWeight: FontWeight.w600)),
            const SizedBox(height: 8),
            TextField(controller: leg.symbolCtrl, decoration: const InputDecoration(labelText: 'Symbol')),
            const SizedBox(height: 8),
            TextField(controller: leg.instrumentCtrl, decoration: const InputDecoration(labelText: 'Instrument ID')),
            const SizedBox(height: 8),
            DropdownButtonFormField<String>(
              value: leg.side,
              decoration: const InputDecoration(labelText: 'Side'),
              items: const [
                DropdownMenuItem(value: 'BUY', child: Text('Buy')),
                DropdownMenuItem(value: 'SELL', child: Text('Sell')),
              ],
              onChanged: (v) => setState(() => leg.side = v ?? 'BUY'),
            ),
            const SizedBox(height: 8),
            TextField(controller: leg.qtyCtrl, decoration: const InputDecoration(labelText: 'Quantity')),
            const SizedBox(height: 8),
            DropdownButtonFormField<String>(
              value: leg.assetClass,
              decoration: const InputDecoration(labelText: 'Asset Class'),
              items: const [
                DropdownMenuItem(value: 'EQUITY', child: Text('Equity')),
                DropdownMenuItem(value: 'ETF', child: Text('ETF')),
                DropdownMenuItem(value: 'FX', child: Text('FX')),
                DropdownMenuItem(value: 'CRYPTO', child: Text('Crypto')),
                DropdownMenuItem(value: 'OPTIONS', child: Text('Options')),
                DropdownMenuItem(value: 'FUTURES', child: Text('Futures')),
                DropdownMenuItem(value: 'FIXED_INCOME', child: Text('Fixed Income')),
                DropdownMenuItem(value: 'COMMODITIES', child: Text('Commodities')),
                DropdownMenuItem(value: 'MUTUAL_FUND', child: Text('Mutual Fund')),
              ],
              onChanged: (v) => setState(() => leg.assetClass = v ?? 'EQUITY'),
            ),
            const SizedBox(height: 8),
            TextField(controller: leg.multiplierCtrl, decoration: const InputDecoration(labelText: 'Contract Multiplier')),
            if (leg.assetClass == 'OPTIONS') ...[
              const SizedBox(height: 8),
              DropdownButtonFormField<String>(
                value: leg.optionType,
                decoration: const InputDecoration(labelText: 'Option Type'),
                items: const [
                  DropdownMenuItem(value: 'CALL', child: Text('Call')),
                  DropdownMenuItem(value: 'PUT', child: Text('Put')),
                ],
                onChanged: (v) => setState(() => leg.optionType = v ?? 'CALL'),
              ),
              const SizedBox(height: 8),
              TextField(controller: leg.optionStrikeCtrl, decoration: const InputDecoration(labelText: 'Strike')),
              const SizedBox(height: 8),
              TextField(controller: leg.optionExpiryCtrl, decoration: const InputDecoration(labelText: 'Expiry (YYYY-MM-DD)')),
            ],
            const SizedBox(height: 8),
            TextField(controller: leg.limitCtrl, decoration: const InputDecoration(labelText: 'Limit Price')),
            const SizedBox(height: 8),
            TextField(controller: leg.stopCtrl, decoration: const InputDecoration(labelText: 'Stop Price')),
            if (legs.length > 1) ...[
              const SizedBox(height: 8),
              Align(
                alignment: Alignment.centerRight,
                child: OutlinedButton(
                  onPressed: () => removeLeg(index),
                  child: const Text('Remove leg'),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('xCAPITAL — Manual Trading')),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: ListView(
          children: [
            const Text('Manual orders with AI review + compliance checks.',
                style: TextStyle(color: Colors.black54)),
            const SizedBox(height: 12),
            DropdownButtonFormField<String>(
              value: accountId,
              decoration: const InputDecoration(labelText: 'Broker Account'),
              items: accounts.map<DropdownMenuItem<String>>((acct) {
                final accountValue = acct['id']?.toString() ?? '';
                final label = '${acct['providerName'] ?? acct['providerId'] ?? 'Broker'} • ${acct['accountNumber'] ?? acct['id']}';
                return DropdownMenuItem<String>(value: accountValue, child: Text(label));
              }).toList(),
              onChanged: (v) => setState(() => accountId = v),
            ),
            const SizedBox(height: 8),
            SwitchListTile(
              title: const Text('Multi-leg order'),
              value: useLegs,
              onChanged: (v) => setState(() => useLegs = v),
              contentPadding: EdgeInsets.zero,
            ),
            if (!useLegs) ...[
              const SizedBox(height: 8),
              TextField(controller: symbolCtrl, decoration: const InputDecoration(labelText: 'Symbol')),
              const SizedBox(height: 8),
              TextField(controller: instrumentCtrl, decoration: const InputDecoration(labelText: 'Instrument ID')),
              const SizedBox(height: 8),
              DropdownButtonFormField<String>(
                value: side,
                decoration: const InputDecoration(labelText: 'Side'),
                items: const [
                  DropdownMenuItem(value: 'BUY', child: Text('Buy')),
                  DropdownMenuItem(value: 'SELL', child: Text('Sell')),
                ],
                onChanged: (v) => setState(() => side = v ?? 'BUY'),
              ),
              const SizedBox(height: 8),
              TextField(controller: qtyCtrl, decoration: const InputDecoration(labelText: 'Quantity')),
            ],
            if (useLegs) ...[
              const SizedBox(height: 8),
              Row(
                children: [
                  const Expanded(
                    child: Text('Order legs', style: TextStyle(fontWeight: FontWeight.w600)),
                  ),
                  OutlinedButton(onPressed: addLeg, child: const Text('Add leg')),
                ],
              ),
              const SizedBox(height: 8),
              for (var i = 0; i < legs.length; i++) buildLegCard(i, legs[i]),
            ],
            const SizedBox(height: 8),
            TextField(controller: clientOrderCtrl, decoration: const InputDecoration(labelText: 'Client Order ID')),
            const SizedBox(height: 8),
            DropdownButtonFormField<String>(
              value: orderType,
              decoration: const InputDecoration(labelText: 'Order Type'),
              items: const [
                DropdownMenuItem(value: 'MARKET', child: Text('Market')),
                DropdownMenuItem(value: 'LIMIT', child: Text('Limit')),
                DropdownMenuItem(value: 'STOP', child: Text('Stop')),
                DropdownMenuItem(value: 'STOP_LIMIT', child: Text('Stop Limit')),
              ],
              onChanged: (v) => setState(() => orderType = v ?? 'MARKET'),
            ),
            const SizedBox(height: 8),
            DropdownButtonFormField<String>(
              value: timeInForce,
              decoration: const InputDecoration(labelText: 'Time In Force'),
              items: const [
                DropdownMenuItem(value: 'DAY', child: Text('Day')),
                DropdownMenuItem(value: 'GTC', child: Text('GTC')),
                DropdownMenuItem(value: 'IOC', child: Text('IOC')),
                DropdownMenuItem(value: 'FOK', child: Text('FOK')),
              ],
              onChanged: (v) => setState(() => timeInForce = v ?? 'DAY'),
            ),
            if (!useLegs) ...[
              const SizedBox(height: 8),
              DropdownButtonFormField<String>(
                value: assetClass,
                decoration: const InputDecoration(labelText: 'Asset Class'),
                items: const [
                  DropdownMenuItem(value: 'EQUITY', child: Text('Equity')),
                  DropdownMenuItem(value: 'ETF', child: Text('ETF')),
                  DropdownMenuItem(value: 'FX', child: Text('FX')),
                  DropdownMenuItem(value: 'CRYPTO', child: Text('Crypto')),
                  DropdownMenuItem(value: 'OPTIONS', child: Text('Options')),
                  DropdownMenuItem(value: 'FUTURES', child: Text('Futures')),
                  DropdownMenuItem(value: 'FIXED_INCOME', child: Text('Fixed Income')),
                  DropdownMenuItem(value: 'COMMODITIES', child: Text('Commodities')),
                  DropdownMenuItem(value: 'MUTUAL_FUND', child: Text('Mutual Fund')),
                ],
                onChanged: (v) => setState(() => assetClass = v ?? 'EQUITY'),
              ),
            ],
            const SizedBox(height: 8),
            SwitchListTile(
              title: const Text('Allow Fractional'),
              value: allowFractional,
              onChanged: (v) => setState(() => allowFractional = v),
              contentPadding: EdgeInsets.zero,
            ),
            const SizedBox(height: 8),
            TextField(controller: routingCtrl, decoration: const InputDecoration(labelText: 'Routing')),
            if (!useLegs) ...[
              const SizedBox(height: 8),
              TextField(controller: multiplierCtrl, decoration: const InputDecoration(labelText: 'Contract Multiplier')),
              if (assetClass == 'OPTIONS') ...[
                const SizedBox(height: 8),
                DropdownButtonFormField<String>(
                  value: optionType,
                  decoration: const InputDecoration(labelText: 'Option Type'),
                  items: const [
                    DropdownMenuItem(value: 'CALL', child: Text('Call')),
                    DropdownMenuItem(value: 'PUT', child: Text('Put')),
                  ],
                  onChanged: (v) => setState(() => optionType = v ?? 'CALL'),
                ),
                const SizedBox(height: 8),
                TextField(controller: optionStrikeCtrl, decoration: const InputDecoration(labelText: 'Strike')),
                const SizedBox(height: 8),
                TextField(controller: optionExpiryCtrl, decoration: const InputDecoration(labelText: 'Expiry (YYYY-MM-DD)')),
              ],
              const SizedBox(height: 8),
              TextField(controller: limitCtrl, decoration: const InputDecoration(labelText: 'Limit Price')),
              const SizedBox(height: 8),
              TextField(controller: stopCtrl, decoration: const InputDecoration(labelText: 'Stop Price')),
            ],
            const SizedBox(height: 8),
            TextField(controller: currencyCtrl, decoration: const InputDecoration(labelText: 'Currency')),
            const SizedBox(height: 8),
            TextField(
              controller: notesCtrl,
              decoration: const InputDecoration(labelText: 'Notes'),
              maxLines: 2,
            ),
            const SizedBox(height: 8),
            TextField(controller: horizonCtrl, decoration: const InputDecoration(labelText: 'AI Horizon')),
            const SizedBox(height: 8),
            TextField(controller: lookbackCtrl, decoration: const InputDecoration(labelText: 'AI Lookback')),
            const SizedBox(height: 8),
            Row(
              children: [
                Expanded(child: ElevatedButton(onPressed: reviewOrder, child: const Text('Review Order'))),
                const SizedBox(width: 8),
                const Expanded(child: Text('Submit through an approved proposal in Auto Invest. Direct placement is disabled.')),
              ],
            ),
            const SizedBox(height: 8),
            ModuleFeedback(message: msg),
            if (review != null) ...[
              const SizedBox(height: 12),
              if (review?['ai']?['summary'] != null)
                Text(review?['ai']?['summary'] ?? '', style: const TextStyle(fontWeight: FontWeight.w600)),
              if (review?['ai']?['reasons'] != null)
                Text(const JsonEncoder.withIndent('  ').convert(review?['ai']?['reasons']),
                    style: const TextStyle(fontSize: 12, color: Colors.black54)),
              const SizedBox(height: 12),
              Text(const JsonEncoder.withIndent('  ').convert(review)),
            ],
          ],
        ),
      ),
    );
  }
}
