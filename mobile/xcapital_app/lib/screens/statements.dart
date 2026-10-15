import 'dart:convert';
import 'package:flutter/material.dart';
import '../widgets/module_feedback.dart';
import '../services/api.dart';

class StatementsScreen extends StatefulWidget {
  const StatementsScreen({super.key});

  @override
  State<StatementsScreen> createState() => _StatementsScreenState();
}

class _StatementsScreenState extends State<StatementsScreen> {
  final accountCtrl = TextEditingController(text: 'acct-001');
  final symbolCtrl = TextEditingController(text: 'AAPL');
  final qtyCtrl = TextEditingController(text: '10');
  final priceCtrl = TextEditingController(text: '180');
  final amountCtrl = TextEditingController(text: '0');
  final currencyCtrl = TextEditingController(text: 'USD');

  final periodStartCtrl = TextEditingController();
  final periodEndCtrl = TextEditingController();
  final startingBalanceCtrl = TextEditingController(text: '0');
  final baseCurrencyCtrl = TextEditingController(text: 'USD');

  final taxSymbolCtrl = TextEditingController(text: 'AAPL');
  final taxQtyCtrl = TextEditingController(text: '10');
  final taxBasisCtrl = TextEditingController(text: '1800');

  final actionSymbolCtrl = TextEditingController(text: 'AAPL');
  final actionRatioCtrl = TextEditingController(text: '2');
  final actionCashCtrl = TextEditingController(text: '0');
  final actionDateCtrl = TextEditingController();
  final importSourceCtrl = TextEditingController(text: 'broker_csv');
  final importDelimiterCtrl = TextEditingController(text: ',');
  final importCurrencyCtrl = TextEditingController(text: 'USD');
  final importCsvCtrl = TextEditingController(
    text: 'date,action,symbol,quantity,price,amount,currency\\n2024-01-05,BUY,AAPL,10,185.80,1858.00,USD',
  );

  String entryType = 'DEPOSIT';
  String taxStatus = 'OPEN';
  String actionType = 'SPLIT';
  bool reconcileApplyPositions = false;
  bool reconcileRebuildTaxLots = false;
  String reconcileLotMethod = 'FIFO';
  bool importHasHeader = true;
  bool importApplyPositions = false;
  bool importRebuildTaxLots = false;
  String importLotMethod = 'FIFO';

  String msg = '';
  String reconcileMsg = '';
  String importMsg = '';
  List<dynamic> ledger = [];
  Map<String, dynamic>? summary;
  Map<String, dynamic>? statement;
  List<dynamic> taxLots = [];
  List<dynamic> actions = [];
  Map<String, dynamic>? reconcileResult;
  Map<String, dynamic>? importResult;

  @override
  void dispose() {
    accountCtrl.dispose();
    symbolCtrl.dispose();
    qtyCtrl.dispose();
    priceCtrl.dispose();
    amountCtrl.dispose();
    currencyCtrl.dispose();
    periodStartCtrl.dispose();
    periodEndCtrl.dispose();
    startingBalanceCtrl.dispose();
    baseCurrencyCtrl.dispose();
    taxSymbolCtrl.dispose();
    taxQtyCtrl.dispose();
    taxBasisCtrl.dispose();
    actionSymbolCtrl.dispose();
    actionRatioCtrl.dispose();
    actionCashCtrl.dispose();
    actionDateCtrl.dispose();
    importSourceCtrl.dispose();
    importDelimiterCtrl.dispose();
    importCurrencyCtrl.dispose();
    importCsvCtrl.dispose();
    super.dispose();
  }

  double? _parseDouble(String value) {
    final trimmed = value.trim();
    if (trimmed.isEmpty) return null;
    return double.tryParse(trimmed);
  }

  Future<void> addLedgerEntry() async {
    setState(() => msg = 'Adding ledger entry...');
    try {
      await Api.addLedgerEntry({
        'accountId': accountCtrl.text.trim(),
        'entryType': entryType,
        'symbol': symbolCtrl.text.trim(),
        'quantity': _parseDouble(qtyCtrl.text),
        'price': _parseDouble(priceCtrl.text),
        'amount': _parseDouble(amountCtrl.text),
        'currency': currencyCtrl.text.trim(),
      });
      await loadLedger();
      setState(() => msg = 'Entry added.');
    } catch (e) {
      setState(() => msg = e.toString());
    }
  }

  Future<void> loadLedger() async {
    setState(() => msg = 'Loading ledger...');
    try {
      final data = await Api.listLedger(accountCtrl.text.trim());
      setState(() { ledger = data; msg = ''; });
    } catch (e) {
      setState(() => msg = e.toString());
    }
  }

  Future<void> loadSummary() async {
    setState(() => msg = 'Loading summary...');
    try {
      final data = await Api.statementSummary(
        accountCtrl.text.trim(),
        start: periodStartCtrl.text.trim(),
        end: periodEndCtrl.text.trim(),
      );
      setState(() { summary = data; msg = ''; });
    } catch (e) {
      setState(() => msg = e.toString());
    }
  }

  Future<void> generateStatement() async {
    setState(() => msg = 'Generating statement...');
    try {
      final data = await Api.generateStatement({
        'accountId': accountCtrl.text.trim(),
        'periodStart': periodStartCtrl.text.trim().isEmpty ? null : periodStartCtrl.text.trim(),
        'periodEnd': periodEndCtrl.text.trim().isEmpty ? null : periodEndCtrl.text.trim(),
        'baseCurrency': baseCurrencyCtrl.text.trim(),
        'startingBalance': _parseDouble(startingBalanceCtrl.text) ?? 0,
      });
      setState(() { statement = data; msg = 'Statement generated.'; });
    } catch (e) {
      setState(() => msg = e.toString());
    }
  }

  Future<void> addTaxLot() async {
    setState(() => msg = 'Saving tax lot...');
    try {
      await Api.upsertTaxLot({
        'accountId': accountCtrl.text.trim(),
        'symbol': taxSymbolCtrl.text.trim(),
        'quantity': _parseDouble(taxQtyCtrl.text),
        'costBasis': _parseDouble(taxBasisCtrl.text),
        'status': taxStatus,
      });
      await loadTaxLots();
      setState(() => msg = 'Tax lot saved.');
    } catch (e) {
      setState(() => msg = e.toString());
    }
  }

  Future<void> loadTaxLots() async {
    setState(() => msg = 'Loading tax lots...');
    try {
      final data = await Api.listTaxLots(accountCtrl.text.trim());
      setState(() { taxLots = data; msg = ''; });
    } catch (e) {
      setState(() => msg = e.toString());
    }
  }

  Future<void> addCorporateAction() async {
    setState(() => msg = 'Adding corporate action...');
    try {
      await Api.addCorporateAction({
        'accountId': accountCtrl.text.trim(),
        'actionType': actionType,
        'symbol': actionSymbolCtrl.text.trim(),
        'ratio': _parseDouble(actionRatioCtrl.text),
        'cashAmount': _parseDouble(actionCashCtrl.text),
        'effectiveDate': actionDateCtrl.text.trim().isEmpty ? null : actionDateCtrl.text.trim(),
      });
      await loadCorporateActions();
      setState(() => msg = 'Corporate action added.');
    } catch (e) {
      setState(() => msg = e.toString());
    }
  }

  Future<void> loadCorporateActions() async {
    setState(() => msg = 'Loading corporate actions...');
    try {
      final data = await Api.listCorporateActions(symbol: actionSymbolCtrl.text.trim());
      setState(() { actions = data; msg = ''; });
    } catch (e) {
      setState(() => msg = e.toString());
    }
  }

  Future<void> runReconcile() async {
    setState(() { reconcileMsg = 'Running reconciliation...'; reconcileResult = null; });
    try {
      final data = await Api.reconcileStatements({
        'accountId': accountCtrl.text.trim(),
        'applyPositions': reconcileApplyPositions,
        'rebuildTaxLots': reconcileRebuildTaxLots,
        'lotMethod': reconcileLotMethod,
      });
      setState(() { reconcileResult = data; reconcileMsg = 'Reconcile complete.'; });
    } catch (e) {
      setState(() => reconcileMsg = e.toString());
    }
  }

  Future<void> runImport() async {
    setState(() { importMsg = 'Importing statement...'; importResult = null; });
    try {
      final data = await Api.importStatement({
        'accountId': accountCtrl.text.trim(),
        'source': importSourceCtrl.text.trim().isEmpty ? 'statement' : importSourceCtrl.text.trim(),
        'csv': importCsvCtrl.text,
        'delimiter': importDelimiterCtrl.text.trim().isEmpty ? ',' : importDelimiterCtrl.text.trim(),
        'hasHeader': importHasHeader,
        'defaultCurrency': importCurrencyCtrl.text.trim().isEmpty ? 'USD' : importCurrencyCtrl.text.trim(),
        'applyPositions': importApplyPositions,
        'rebuildTaxLots': importRebuildTaxLots,
        'lotMethod': importLotMethod,
      });
      setState(() { importResult = data; importMsg = 'Import complete.'; });
    } catch (e) {
      setState(() => importMsg = e.toString());
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('xCAPITAL - Statements')),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: ListView(
          children: [
            const Text('Ledger ingestion, statements, tax lots, and corporate actions.',
                style: TextStyle(color: Colors.black54)),
            const SizedBox(height: 12),
            TextField(controller: accountCtrl, decoration: const InputDecoration(labelText: 'Account ID')),
            const SizedBox(height: 12),
            const Text('Ledger Entry', style: TextStyle(fontWeight: FontWeight.w600)),
            const SizedBox(height: 8),
            DropdownButtonFormField<String>(
              value: entryType,
              decoration: const InputDecoration(labelText: 'Entry Type'),
              items: const [
                DropdownMenuItem(value: 'DEPOSIT', child: Text('Deposit')),
                DropdownMenuItem(value: 'WITHDRAWAL', child: Text('Withdrawal')),
                DropdownMenuItem(value: 'BUY', child: Text('Buy')),
                DropdownMenuItem(value: 'SELL', child: Text('Sell')),
                DropdownMenuItem(value: 'DIVIDEND', child: Text('Dividend')),
                DropdownMenuItem(value: 'FEE', child: Text('Fee')),
                DropdownMenuItem(value: 'INTEREST', child: Text('Interest')),
                DropdownMenuItem(value: 'TRANSFER', child: Text('Transfer')),
                DropdownMenuItem(value: 'FX', child: Text('FX')),
                DropdownMenuItem(value: 'ADJUSTMENT', child: Text('Adjustment')),
              ],
              onChanged: (v) => setState(() => entryType = v ?? 'DEPOSIT'),
            ),
            const SizedBox(height: 8),
            TextField(controller: symbolCtrl, decoration: const InputDecoration(labelText: 'Symbol')),
            const SizedBox(height: 8),
            TextField(controller: qtyCtrl, decoration: const InputDecoration(labelText: 'Quantity'), keyboardType: TextInputType.number),
            const SizedBox(height: 8),
            TextField(controller: priceCtrl, decoration: const InputDecoration(labelText: 'Price'), keyboardType: TextInputType.number),
            const SizedBox(height: 8),
            TextField(controller: amountCtrl, decoration: const InputDecoration(labelText: 'Amount'), keyboardType: TextInputType.number),
            const SizedBox(height: 8),
            TextField(controller: currencyCtrl, decoration: const InputDecoration(labelText: 'Currency')),
            const SizedBox(height: 8),
            Row(
              children: [
                Expanded(child: ElevatedButton(onPressed: addLedgerEntry, child: const Text('Add Entry'))),
                const SizedBox(width: 8),
                Expanded(child: ElevatedButton(onPressed: loadLedger, child: const Text('Refresh Ledger'))),
              ],
            ),
            if (ledger.isNotEmpty) ...[
              const SizedBox(height: 8),
              Text(const JsonEncoder.withIndent('  ').convert(ledger)),
            ],
            const SizedBox(height: 16),
            const Text('Statement Summary', style: TextStyle(fontWeight: FontWeight.w600)),
            const SizedBox(height: 8),
            TextField(controller: periodStartCtrl, decoration: const InputDecoration(labelText: 'Period Start (ISO)')),
            const SizedBox(height: 8),
            TextField(controller: periodEndCtrl, decoration: const InputDecoration(labelText: 'Period End (ISO)')),
            const SizedBox(height: 8),
            TextField(controller: startingBalanceCtrl, decoration: const InputDecoration(labelText: 'Starting Balance'), keyboardType: TextInputType.number),
            const SizedBox(height: 8),
            TextField(controller: baseCurrencyCtrl, decoration: const InputDecoration(labelText: 'Base Currency')),
            const SizedBox(height: 8),
            Row(
              children: [
                Expanded(child: ElevatedButton(onPressed: loadSummary, child: const Text('Load Summary'))),
                const SizedBox(width: 8),
                Expanded(child: ElevatedButton(onPressed: generateStatement, child: const Text('Generate Statement'))),
              ],
            ),
            if (summary != null) ...[
              const SizedBox(height: 8),
              Text(const JsonEncoder.withIndent('  ').convert(summary)),
            ],
            if (statement != null) ...[
              const SizedBox(height: 8),
              Text(const JsonEncoder.withIndent('  ').convert(statement)),
            ],
            const SizedBox(height: 16),
            const Text('Tax Lots', style: TextStyle(fontWeight: FontWeight.w600)),
            const SizedBox(height: 8),
            TextField(controller: taxSymbolCtrl, decoration: const InputDecoration(labelText: 'Symbol')),
            const SizedBox(height: 8),
            TextField(controller: taxQtyCtrl, decoration: const InputDecoration(labelText: 'Quantity'), keyboardType: TextInputType.number),
            const SizedBox(height: 8),
            TextField(controller: taxBasisCtrl, decoration: const InputDecoration(labelText: 'Cost Basis'), keyboardType: TextInputType.number),
            const SizedBox(height: 8),
            DropdownButtonFormField<String>(
              value: taxStatus,
              decoration: const InputDecoration(labelText: 'Status'),
              items: const [
                DropdownMenuItem(value: 'OPEN', child: Text('Open')),
                DropdownMenuItem(value: 'CLOSED', child: Text('Closed')),
              ],
              onChanged: (v) => setState(() => taxStatus = v ?? 'OPEN'),
            ),
            const SizedBox(height: 8),
            Row(
              children: [
                Expanded(child: ElevatedButton(onPressed: addTaxLot, child: const Text('Add/Update Lot'))),
                const SizedBox(width: 8),
                Expanded(child: ElevatedButton(onPressed: loadTaxLots, child: const Text('Refresh Lots'))),
              ],
            ),
            if (taxLots.isNotEmpty) ...[
              const SizedBox(height: 8),
              Text(const JsonEncoder.withIndent('  ').convert(taxLots)),
            ],
            const SizedBox(height: 16),
            const Text('Corporate Actions', style: TextStyle(fontWeight: FontWeight.w600)),
            const SizedBox(height: 8),
            DropdownButtonFormField<String>(
              value: actionType,
              decoration: const InputDecoration(labelText: 'Action Type'),
              items: const [
                DropdownMenuItem(value: 'SPLIT', child: Text('Split')),
                DropdownMenuItem(value: 'MERGER', child: Text('Merger')),
                DropdownMenuItem(value: 'DIVIDEND', child: Text('Dividend')),
                DropdownMenuItem(value: 'SPINOFF', child: Text('Spinoff')),
                DropdownMenuItem(value: 'SYMBOL_CHANGE', child: Text('Symbol Change')),
              ],
              onChanged: (v) => setState(() => actionType = v ?? 'SPLIT'),
            ),
            const SizedBox(height: 8),
            TextField(controller: actionSymbolCtrl, decoration: const InputDecoration(labelText: 'Symbol')),
            const SizedBox(height: 8),
            TextField(controller: actionRatioCtrl, decoration: const InputDecoration(labelText: 'Ratio'), keyboardType: TextInputType.number),
            const SizedBox(height: 8),
            TextField(controller: actionCashCtrl, decoration: const InputDecoration(labelText: 'Cash Amount'), keyboardType: TextInputType.number),
            const SizedBox(height: 8),
            TextField(controller: actionDateCtrl, decoration: const InputDecoration(labelText: 'Effective Date (YYYY-MM-DD)')),
            const SizedBox(height: 8),
            Row(
              children: [
                Expanded(child: ElevatedButton(onPressed: addCorporateAction, child: const Text('Add Action'))),
                const SizedBox(width: 8),
                Expanded(child: ElevatedButton(onPressed: loadCorporateActions, child: const Text('Refresh Actions'))),
              ],
            ),
            if (actions.isNotEmpty) ...[
              const SizedBox(height: 8),
              Text(const JsonEncoder.withIndent('  ').convert(actions)),
            ],
            const SizedBox(height: 16),
            const Text('Statement Import', style: TextStyle(fontWeight: FontWeight.w600)),
            const SizedBox(height: 8),
            TextField(controller: importSourceCtrl, decoration: const InputDecoration(labelText: 'Source')),
            const SizedBox(height: 8),
            TextField(controller: importDelimiterCtrl, decoration: const InputDecoration(labelText: 'Delimiter')),
            const SizedBox(height: 8),
            TextField(controller: importCurrencyCtrl, decoration: const InputDecoration(labelText: 'Default Currency')),
            const SizedBox(height: 8),
            SwitchListTile(
              title: const Text('Has Header Row'),
              value: importHasHeader,
              onChanged: (v) => setState(() => importHasHeader = v),
            ),
            TextField(
              controller: importCsvCtrl,
              decoration: const InputDecoration(labelText: 'CSV Text'),
              maxLines: 6,
            ),
            SwitchListTile(
              title: const Text('Apply Positions'),
              value: importApplyPositions,
              onChanged: (v) => setState(() => importApplyPositions = v),
            ),
            SwitchListTile(
              title: const Text('Rebuild Tax Lots'),
              value: importRebuildTaxLots,
              onChanged: (v) => setState(() => importRebuildTaxLots = v),
            ),
            DropdownButtonFormField<String>(
              value: importLotMethod,
              decoration: const InputDecoration(labelText: 'Lot Method'),
              items: const [
                DropdownMenuItem(value: 'FIFO', child: Text('FIFO')),
              ],
              onChanged: (v) => setState(() => importLotMethod = v ?? 'FIFO'),
            ),
            const SizedBox(height: 8),
            ElevatedButton(onPressed: runImport, child: const Text('Import CSV')),
            const SizedBox(height: 6),
            Text(importMsg, style: const TextStyle(fontSize: 12, color: Colors.black54)),
            if (importResult != null) ...[
              const SizedBox(height: 8),
              Text(const JsonEncoder.withIndent('  ').convert(importResult)),
            ],
            const SizedBox(height: 16),
            const Text('Reconciliation', style: TextStyle(fontWeight: FontWeight.w600)),
            const SizedBox(height: 8),
            SwitchListTile(
              title: const Text('Apply Positions'),
              value: reconcileApplyPositions,
              onChanged: (v) => setState(() => reconcileApplyPositions = v),
            ),
            SwitchListTile(
              title: const Text('Rebuild Tax Lots'),
              value: reconcileRebuildTaxLots,
              onChanged: (v) => setState(() => reconcileRebuildTaxLots = v),
            ),
            DropdownButtonFormField<String>(
              value: reconcileLotMethod,
              decoration: const InputDecoration(labelText: 'Lot Method'),
              items: const [
                DropdownMenuItem(value: 'FIFO', child: Text('FIFO')),
              ],
              onChanged: (v) => setState(() => reconcileLotMethod = v ?? 'FIFO'),
            ),
            const SizedBox(height: 8),
            ElevatedButton(onPressed: runReconcile, child: const Text('Run Reconcile')),
            const SizedBox(height: 6),
            Text(reconcileMsg, style: const TextStyle(fontSize: 12, color: Colors.black54)),
            if (reconcileResult != null) ...[
              const SizedBox(height: 8),
              Text(const JsonEncoder.withIndent('  ').convert(reconcileResult)),
            ],
            const SizedBox(height: 12),
            ModuleFeedback(message: msg),
          ],
        ),
      ),
    );
  }
}
