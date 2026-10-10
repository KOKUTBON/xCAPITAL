import 'dart:convert';
import 'package:http/http.dart' as http;
import '../config.dart';
import 'token_store.dart';

class AuthResult {
  final String? token;
  final bool mfaRequired;
  final String? mfaToken;
  final String? mfaTokenExpiresAt;

  AuthResult({
    this.token,
    required this.mfaRequired,
    this.mfaToken,
    this.mfaTokenExpiresAt,
  });

  factory AuthResult.fromJson(Map<String, dynamic> json) {
    return AuthResult(
      token: json['token'] as String?,
      mfaRequired: json['mfaRequired'] == true,
      mfaToken: json['mfaToken'] as String?,
      mfaTokenExpiresAt: json['mfaTokenExpiresAt'] as String?,
    );
  }
}

class Api {
  static Uri _u(String path) => Uri.parse('${Config.apiBase}$path');

  static Future<AuthResult> register(String email, String password) async {
    final res = await http.post(_u('/api/v1/auth/register'),
      headers: {'Content-Type':'application/json'},
      body: jsonEncode({'email': email, 'password': password}),
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      final j = jsonDecode(res.body);
      final result = AuthResult.fromJson(j as Map<String, dynamic>);
      if (result.token != null) {
        await TokenStore.setToken(result.token!);
      }
      return result;
    }
    throw Exception(res.body);
  }

  static Future<AuthResult> login(String email, String password) async {
    final res = await http.post(_u('/api/v1/auth/login'),
      headers: {'Content-Type':'application/json'},
      body: jsonEncode({'email': email, 'password': password}),
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      final j = jsonDecode(res.body);
      final result = AuthResult.fromJson(j as Map<String, dynamic>);
      if (result.token != null) {
        await TokenStore.setToken(result.token!);
      }
      return result;
    }
    throw Exception(res.body);
  }

  static Future<Map<String, dynamic>> profile() async {
    final token = await TokenStore.getToken();
    final res = await http.get(_u('/api/v1/auth/profile'),
      headers: {'Authorization':'Bearer ${token ?? ''}'},
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as Map<String, dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<Map<String, dynamic>> enrollMfa({String method = 'TOTP'}) async {
    final token = await TokenStore.getToken();
    final res = await http.post(_u('/api/v1/auth/mfa/enroll'),
      headers: {'Content-Type':'application/json', 'Authorization':'Bearer ${token ?? ''}'},
      body: jsonEncode({'method': method}),
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as Map<String, dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<AuthResult> verifyMfa(String code, {String? mfaToken}) async {
    final token = await TokenStore.getToken();
    final headers = <String, String>{'Content-Type':'application/json'};
    if (mfaToken == null || mfaToken.isEmpty) {
      headers['Authorization'] = 'Bearer ${token ?? ''}';
    }
    final body = <String, dynamic>{'code': code};
    if (mfaToken != null && mfaToken.isNotEmpty) {
      body['mfaToken'] = mfaToken;
    }
    final res = await http.post(_u('/api/v1/auth/mfa/verify'),
      headers: headers,
      body: jsonEncode(body),
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      final result = AuthResult.fromJson(jsonDecode(res.body) as Map<String, dynamic>);
      if (result.token != null) {
        await TokenStore.setToken(result.token!);
      }
      return result;
    }
    throw Exception(res.body);
  }

  static Future<Map<String, dynamic>> disableMfa() async {
    final token = await TokenStore.getToken();
    final res = await http.post(_u('/api/v1/auth/mfa/disable'),
      headers: {'Authorization':'Bearer ${token ?? ''}'},
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as Map<String, dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<Map<String, dynamic>> optimize(List<double> mu, List<List<double>> cov) async {
    final token = await TokenStore.getToken();
    final res = await http.post(_u('/api/v1/portfolio/optimize'),
      headers: {'Content-Type':'application/json', 'Authorization':'Bearer ${token ?? ''}'},
      body: jsonEncode({'mu': mu, 'cov': cov, 'riskAversion': 6, 'maxWeight': 0.6, 'minWeight': 0.0}),
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body);
    }
    throw Exception(res.body);
  }

  static Future<Map<String, dynamic>> riskMetrics(List<double> returns) async {
    final token = await TokenStore.getToken();
    final res = await http.post(_u('/api/v1/risk/metrics'),
      headers: {'Content-Type':'application/json', 'Authorization':'Bearer ${token ?? ''}'},
      body: jsonEncode({'returns': returns, 'confidence': 0.95, 'riskFree': 0.0}),
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body);
    }
    throw Exception(res.body);
  }

  static Future<List<dynamic>> auditEvents({int limit = 50}) async {
    final token = await TokenStore.getToken();
    final res = await http.get(_u('/api/v1/audit/events?limit=$limit'),
      headers: {'Authorization':'Bearer ${token ?? ''}'},
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as List<dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<Map<String, dynamic>> latestQuotes(String symbols) async {
    final token = await TokenStore.getToken();
    final uri = _u('/api/v1/market-data/quotes/latest')
        .replace(queryParameters: {'symbols': symbols});
    final res = await http.get(uri, headers: {'Authorization':'Bearer ${token ?? ''}'});
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as Map<String, dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<Map<String, dynamic>> marketHistory({
    required String symbol,
    String? start,
    String? end,
    String granularity = 'DAY',
    int limit = 0,
  }) async {
    final token = await TokenStore.getToken();
    final params = <String, String>{
      'symbol': symbol,
      'granularity': granularity,
    };
    if (start != null && start.isNotEmpty) params['start'] = start;
    if (end != null && end.isNotEmpty) params['end'] = end;
    if (limit > 0) params['limit'] = limit.toString();
    final uri = _u('/api/v1/market-data/history').replace(queryParameters: params);
    final res = await http.get(uri, headers: {'Authorization':'Bearer ${token ?? ''}'});
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as Map<String, dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<Map<String, dynamic>> marketBackfill({
    required List<String> symbols,
    String? start,
    String? end,
    String granularity = 'DAY',
    int limit = 0,
    String source = 'csv',
  }) async {
    final token = await TokenStore.getToken();
    final body = <String, dynamic>{
      'symbols': symbols,
      'granularity': granularity,
      'limit': limit,
      'source': source,
    };
    if (start != null && start.isNotEmpty) body['start'] = start;
    if (end != null && end.isNotEmpty) body['end'] = end;
    final res = await http.post(_u('/api/v1/market-data/backfill'),
      headers: {'Content-Type':'application/json', 'Authorization':'Bearer ${token ?? ''}'},
      body: jsonEncode(body),
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as Map<String, dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<List<dynamic>> marketDataLicenses({String? status}) async {
    final token = await TokenStore.getToken();
    final params = <String, String>{};
    if (status != null && status.isNotEmpty) params['status'] = status;
    final uri = _u('/api/v1/market-data/licenses').replace(queryParameters: params.isEmpty ? null : params);
    final res = await http.get(uri, headers: {'Authorization':'Bearer ${token ?? ''}'});
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as List<dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<Map<String, dynamic>> upsertMarketDataLicense(Map<String, dynamic> body) async {
    final token = await TokenStore.getToken();
    final res = await http.post(_u('/api/v1/market-data/licenses'),
      headers: {'Content-Type':'application/json', 'Authorization':'Bearer ${token ?? ''}'},
      body: jsonEncode(body),
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as Map<String, dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<List<dynamic>> marketDataEntitlements({String? status}) async {
    final token = await TokenStore.getToken();
    final params = <String, String>{};
    if (status != null && status.isNotEmpty) params['status'] = status;
    final uri = _u('/api/v1/market-data/entitlements').replace(queryParameters: params.isEmpty ? null : params);
    final res = await http.get(uri, headers: {'Authorization':'Bearer ${token ?? ''}'});
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as List<dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<List<dynamic>> marketDataProviders() async {
    final token = await TokenStore.getToken();
    final res = await http.get(_u('/api/v1/market-data/providers'),
      headers: {'Authorization':'Bearer ${token ?? ''}'},
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as List<dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<Map<String, dynamic>> upsertMarketDataEntitlement(Map<String, dynamic> body) async {
    final token = await TokenStore.getToken();
    final res = await http.post(_u('/api/v1/market-data/entitlements'),
      headers: {'Content-Type':'application/json', 'Authorization':'Bearer ${token ?? ''}'},
      body: jsonEncode(body),
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as Map<String, dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<List<dynamic>> fundingSources() async {
    final token = await TokenStore.getToken();
    final res = await http.get(_u('/api/v1/funding/sources'),
      headers: {'Authorization':'Bearer ${token ?? ''}'},
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as List<dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<Map<String, dynamic>> linkFundingSource(Map<String, dynamic> body) async {
    final token = await TokenStore.getToken();
    final res = await http.post(_u('/api/v1/funding/sources'),
      headers: {'Content-Type':'application/json', 'Authorization':'Bearer ${token ?? ''}'},
      body: jsonEncode(body),
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as Map<String, dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<Map<String, dynamic>> verifyFundingSource(String id, Map<String, dynamic> body) async {
    final token = await TokenStore.getToken();
    final res = await http.post(_u('/api/v1/funding/sources/$id/verify'),
      headers: {'Content-Type':'application/json', 'Authorization':'Bearer ${token ?? ''}'},
      body: jsonEncode(body),
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as Map<String, dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<Map<String, dynamic>> createDeposit(Map<String, dynamic> body) async {
    final token = await TokenStore.getToken();
    final res = await http.post(_u('/api/v1/funding/deposits'),
      headers: {'Content-Type':'application/json', 'Authorization':'Bearer ${token ?? ''}'},
      body: jsonEncode(body),
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as Map<String, dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<List<dynamic>> fundingDeposits() async {
    final token = await TokenStore.getToken();
    final res = await http.get(_u('/api/v1/funding/deposits'),
      headers: {'Authorization':'Bearer ${token ?? ''}'},
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as List<dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<Map<String, dynamic>> createWithdrawal(Map<String, dynamic> body) async {
    final token = await TokenStore.getToken();
    final res = await http.post(_u('/api/v1/funding/withdrawals'),
      headers: {'Content-Type':'application/json', 'Authorization':'Bearer ${token ?? ''}'},
      body: jsonEncode(body),
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as Map<String, dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<List<dynamic>> fundingWithdrawals() async {
    final token = await TokenStore.getToken();
    final res = await http.get(_u('/api/v1/funding/withdrawals'),
      headers: {'Authorization':'Bearer ${token ?? ''}'},
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as List<dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<Map<String, dynamic>> createTransfer(Map<String, dynamic> body) async {
    final token = await TokenStore.getToken();
    final res = await http.post(_u('/api/v1/funding/transfers'),
      headers: {'Content-Type':'application/json', 'Authorization':'Bearer ${token ?? ''}'},
      body: jsonEncode(body),
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as Map<String, dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<List<dynamic>> fundingTransfers() async {
    final token = await TokenStore.getToken();
    final res = await http.get(_u('/api/v1/funding/transfers'),
      headers: {'Authorization':'Bearer ${token ?? ''}'},
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as List<dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<List<dynamic>> brokerAccounts() async {
    final token = await TokenStore.getToken();
    final res = await http.get(_u('/api/v1/brokers/accounts'),
      headers: {'Authorization':'Bearer ${token ?? ''}'},
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as List<dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<List<dynamic>> brokerOrders(String accountId) async {
    final token = await TokenStore.getToken();
    final res = await http.get(_u('/api/v1/brokers/accounts/$accountId/orders'),
      headers: {'Authorization':'Bearer ${token ?? ''}'},
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as List<dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<Map<String, dynamic>> brokerOrderPreview(String accountId, Map<String, dynamic> body) async {
    final token = await TokenStore.getToken();
    final res = await http.post(_u('/api/v1/brokers/accounts/$accountId/orders/preview'),
      headers: {'Content-Type':'application/json', 'Authorization':'Bearer ${token ?? ''}'},
      body: jsonEncode(body),
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as Map<String, dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<Map<String, dynamic>> brokerPlaceOrder(String accountId, Map<String, dynamic> body) async {
    final token = await TokenStore.getToken();
    final res = await http.post(_u('/api/v1/brokers/accounts/$accountId/orders'),
      headers: {'Content-Type':'application/json', 'Authorization':'Bearer ${token ?? ''}'},
      body: jsonEncode(body),
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as Map<String, dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<Map<String, dynamic>> brokerOrderReview(String accountId, Map<String, dynamic> order,
      {int aiHorizon = 1, int lookback = 120, bool includeCompliance = true}) async {
    final token = await TokenStore.getToken();
    final res = await http.post(_u('/api/v1/brokers/accounts/$accountId/orders/review'),
      headers: {'Content-Type':'application/json', 'Authorization':'Bearer ${token ?? ''}'},
      body: jsonEncode({
        'order': order,
        'aiHorizon': aiHorizon,
        'lookback': lookback,
        'includeCompliance': includeCompliance,
      }),
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as Map<String, dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<Map<String, dynamic>> brokerOrderCancel(String accountId, String orderId) async {
    final token = await TokenStore.getToken();
    final res = await http.post(_u('/api/v1/brokers/accounts/$accountId/orders/$orderId/cancel'),
      headers: {'Authorization':'Bearer ${token ?? ''}'},
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as Map<String, dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<Map<String, dynamic>> brokerOrderRefresh(String accountId, String orderId) async {
    final token = await TokenStore.getToken();
    final res = await http.post(_u('/api/v1/brokers/accounts/$accountId/orders/$orderId/refresh'),
      headers: {'Authorization':'Bearer ${token ?? ''}'},
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as Map<String, dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<List<dynamic>> autoInvestPlans() async {
    final token = await TokenStore.getToken();
    final res = await http.get(_u('/api/v1/auto-invest/plans'),
      headers: {'Authorization':'Bearer ${token ?? ''}'},
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as List<dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<Map<String, dynamic>> createAutoInvestPlan(Map<String, dynamic> body) async {
    final token = await TokenStore.getToken();
    final res = await http.post(_u('/api/v1/auto-invest/plans'),
      headers: {'Content-Type':'application/json', 'Authorization':'Bearer ${token ?? ''}'},
      body: jsonEncode(body),
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as Map<String, dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<Map<String, dynamic>> setAutoInvestStatus(String id, String status) async {
    final token = await TokenStore.getToken();
    final res = await http.post(_u('/api/v1/auto-invest/plans/$id/status'),
      headers: {'Content-Type':'application/json', 'Authorization':'Bearer ${token ?? ''}'},
      body: jsonEncode({'status': status}),
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as Map<String, dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<Map<String, dynamic>> runAutoInvestPlan(String id) async {
    final token = await TokenStore.getToken();
    final res = await http.post(_u('/api/v1/auto-invest/plans/$id/run'),
      headers: {'Authorization':'Bearer ${token ?? ''}'},
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as Map<String, dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<List<dynamic>> autoInvestRuns(String planId) async {
    final token = await TokenStore.getToken();
    final res = await http.get(_u('/api/v1/auto-invest/plans/$planId/runs'),
      headers: {'Authorization':'Bearer ${token ?? ''}'},
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as List<dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<List<dynamic>> notifications({int limit = 50}) async {
    final token = await TokenStore.getToken();
    final res = await http.get(_u('/api/v1/notifications?limit=$limit'),
      headers: {'Authorization':'Bearer ${token ?? ''}'},
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as List<dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<Map<String, dynamic>> markNotificationRead(String id) async {
    final token = await TokenStore.getToken();
    final res = await http.post(_u('/api/v1/notifications/$id/read'),
      headers: {'Authorization':'Bearer ${token ?? ''}'},
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as Map<String, dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<List<dynamic>> notificationPreferences() async {
    final token = await TokenStore.getToken();
    final res = await http.get(_u('/api/v1/notifications/preferences'),
      headers: {'Authorization':'Bearer ${token ?? ''}'},
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as List<dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<Map<String, dynamic>> saveNotificationPreference(Map<String, dynamic> body) async {
    final token = await TokenStore.getToken();
    final res = await http.post(_u('/api/v1/notifications/preferences'),
      headers: {'Content-Type':'application/json', 'Authorization':'Bearer ${token ?? ''}'},
      body: jsonEncode(body),
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as Map<String, dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<List<dynamic>> notificationDestinations({String? channel}) async {
    final token = await TokenStore.getToken();
    Uri uri = _u('/api/v1/notifications/destinations');
    if (channel != null && channel.isNotEmpty) {
      uri = uri.replace(queryParameters: {'channel': channel});
    }
    final res = await http.get(uri, headers: {'Authorization':'Bearer ${token ?? ''}'});
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as List<dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<Map<String, dynamic>> createNotificationDestination(Map<String, dynamic> body) async {
    final token = await TokenStore.getToken();
    final res = await http.post(_u('/api/v1/notifications/destinations'),
      headers: {'Content-Type':'application/json', 'Authorization':'Bearer ${token ?? ''}'},
      body: jsonEncode(body),
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as Map<String, dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<Map<String, dynamic>> verifyNotificationDestination(String id) async {
    final token = await TokenStore.getToken();
    final res = await http.post(_u('/api/v1/notifications/destinations/$id/verify'),
      headers: {'Authorization':'Bearer ${token ?? ''}'},
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as Map<String, dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<Map<String, dynamic>> disableNotificationDestination(String id) async {
    final token = await TokenStore.getToken();
    final res = await http.post(_u('/api/v1/notifications/destinations/$id/disable'),
      headers: {'Authorization':'Bearer ${token ?? ''}'},
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as Map<String, dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<List<dynamic>> notificationDeliveries({String? status, int limit = 50}) async {
    final token = await TokenStore.getToken();
    final params = <String, String>{'limit': limit.toString()};
    if (status != null && status.isNotEmpty) params['status'] = status;
    final uri = _u('/api/v1/notifications/deliveries').replace(queryParameters: params);
    final res = await http.get(uri, headers: {'Authorization':'Bearer ${token ?? ''}'});
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as List<dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<List<dynamic>> watchlists() async {
    final token = await TokenStore.getToken();
    final res = await http.get(_u('/api/v1/watchlists'),
      headers: {'Authorization':'Bearer ${token ?? ''}'},
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as List<dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<Map<String, dynamic>> createWatchlist(String name, String description) async {
    final token = await TokenStore.getToken();
    final res = await http.post(_u('/api/v1/watchlists'),
      headers: {'Content-Type':'application/json', 'Authorization':'Bearer ${token ?? ''}'},
      body: jsonEncode({'name': name, 'description': description}),
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as Map<String, dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<void> deleteWatchlist(String id) async {
    final token = await TokenStore.getToken();
    final res = await http.delete(_u('/api/v1/watchlists/$id'),
      headers: {'Authorization':'Bearer ${token ?? ''}'},
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return;
    }
    throw Exception(res.body);
  }

  static Future<List<dynamic>> watchlistItems(String id) async {
    final token = await TokenStore.getToken();
    final res = await http.get(_u('/api/v1/watchlists/$id/items'),
      headers: {'Authorization':'Bearer ${token ?? ''}'},
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as List<dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<Map<String, dynamic>> addWatchlistItem(String id, Map<String, dynamic> body) async {
    final token = await TokenStore.getToken();
    final res = await http.post(_u('/api/v1/watchlists/$id/items'),
      headers: {'Content-Type':'application/json', 'Authorization':'Bearer ${token ?? ''}'},
      body: jsonEncode(body),
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as Map<String, dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<void> removeWatchlistItem(String id, String itemId) async {
    final token = await TokenStore.getToken();
    final res = await http.delete(_u('/api/v1/watchlists/$id/items/$itemId'),
      headers: {'Authorization':'Bearer ${token ?? ''}'},
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return;
    }
    throw Exception(res.body);
  }

  static Future<List<dynamic>> refreshWatchlistInsights(String id, {int horizon = 1, int lookback = 120}) async {
    final token = await TokenStore.getToken();
    final res = await http.post(_u('/api/v1/watchlists/$id/insights'),
      headers: {'Content-Type':'application/json', 'Authorization':'Bearer ${token ?? ''}'},
      body: jsonEncode({'horizon': horizon, 'lookback': lookback}),
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as List<dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<List<dynamic>> alerts({String? status, int limit = 50}) async {
    final token = await TokenStore.getToken();
    final params = <String, String>{'limit': limit.toString()};
    if (status != null && status.isNotEmpty) params['status'] = status;
    final uri = _u('/api/v1/alerts').replace(queryParameters: params);
    final res = await http.get(uri, headers: {'Authorization':'Bearer ${token ?? ''}'});
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as List<dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<Map<String, dynamic>> createAlert(Map<String, dynamic> body) async {
    final token = await TokenStore.getToken();
    final res = await http.post(_u('/api/v1/alerts'),
      headers: {'Content-Type':'application/json', 'Authorization':'Bearer ${token ?? ''}'},
      body: jsonEncode(body),
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as Map<String, dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<Map<String, dynamic>> updateAlertStatus(String id, String status) async {
    final token = await TokenStore.getToken();
    final res = await http.post(_u('/api/v1/alerts/$id/status'),
      headers: {'Content-Type':'application/json', 'Authorization':'Bearer ${token ?? ''}'},
      body: jsonEncode({'status': status}),
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as Map<String, dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<Map<String, dynamic>> triggerAlert(String id) async {
    final token = await TokenStore.getToken();
    final res = await http.post(_u('/api/v1/alerts/$id/trigger'),
      headers: {'Content-Type':'application/json', 'Authorization':'Bearer ${token ?? ''}'},
      body: jsonEncode({}),
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as Map<String, dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<List<dynamic>> addLedgerEntry(Map<String, dynamic> entry) async {
    final token = await TokenStore.getToken();
    final res = await http.post(_u('/api/v1/statements/ledger'),
      headers: {'Content-Type':'application/json', 'Authorization':'Bearer ${token ?? ''}'},
      body: jsonEncode([entry]),
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as List<dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<List<dynamic>> listLedger(String accountId, {String? start, String? end, int limit = 0}) async {
    final token = await TokenStore.getToken();
    final params = <String, String>{'accountId': accountId};
    if (start != null && start.isNotEmpty) params['start'] = start;
    if (end != null && end.isNotEmpty) params['end'] = end;
    if (limit > 0) params['limit'] = limit.toString();
    final uri = _u('/api/v1/statements/ledger').replace(queryParameters: params);
    final res = await http.get(uri, headers: {'Authorization':'Bearer ${token ?? ''}'});
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as List<dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<Map<String, dynamic>> statementSummary(String accountId, {String? start, String? end}) async {
    final token = await TokenStore.getToken();
    final params = <String, String>{'accountId': accountId};
    if (start != null && start.isNotEmpty) params['start'] = start;
    if (end != null && end.isNotEmpty) params['end'] = end;
    final uri = _u('/api/v1/statements/summary').replace(queryParameters: params);
    final res = await http.get(uri, headers: {'Authorization':'Bearer ${token ?? ''}'});
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as Map<String, dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<Map<String, dynamic>> generateStatement(Map<String, dynamic> body) async {
    final token = await TokenStore.getToken();
    final res = await http.post(_u('/api/v1/statements'),
      headers: {'Content-Type':'application/json', 'Authorization':'Bearer ${token ?? ''}'},
      body: jsonEncode(body),
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as Map<String, dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<List<dynamic>> listStatements(String accountId, {int limit = 0}) async {
    final token = await TokenStore.getToken();
    final params = <String, String>{'accountId': accountId};
    if (limit > 0) params['limit'] = limit.toString();
    final uri = _u('/api/v1/statements').replace(queryParameters: params);
    final res = await http.get(uri, headers: {'Authorization':'Bearer ${token ?? ''}'});
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as List<dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<Map<String, dynamic>> upsertTaxLot(Map<String, dynamic> body) async {
    final token = await TokenStore.getToken();
    final res = await http.post(_u('/api/v1/statements/tax-lots'),
      headers: {'Content-Type':'application/json', 'Authorization':'Bearer ${token ?? ''}'},
      body: jsonEncode(body),
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as Map<String, dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<List<dynamic>> listTaxLots(String accountId, {String? symbol, String? status, int limit = 0}) async {
    final token = await TokenStore.getToken();
    final params = <String, String>{'accountId': accountId};
    if (symbol != null && symbol.isNotEmpty) params['symbol'] = symbol;
    if (status != null && status.isNotEmpty) params['status'] = status;
    if (limit > 0) params['limit'] = limit.toString();
    final uri = _u('/api/v1/statements/tax-lots').replace(queryParameters: params);
    final res = await http.get(uri, headers: {'Authorization':'Bearer ${token ?? ''}'});
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as List<dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<Map<String, dynamic>> addCorporateAction(Map<String, dynamic> body) async {
    final token = await TokenStore.getToken();
    final res = await http.post(_u('/api/v1/statements/corporate-actions'),
      headers: {'Content-Type':'application/json', 'Authorization':'Bearer ${token ?? ''}'},
      body: jsonEncode(body),
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as Map<String, dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<List<dynamic>> listCorporateActions({String? symbol, int limit = 0}) async {
    final token = await TokenStore.getToken();
    final params = <String, String>{};
    if (symbol != null && symbol.isNotEmpty) params['symbol'] = symbol;
    if (limit > 0) params['limit'] = limit.toString();
    final uri = _u('/api/v1/statements/corporate-actions').replace(queryParameters: params);
    final res = await http.get(uri, headers: {'Authorization':'Bearer ${token ?? ''}'});
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as List<dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<Map<String, dynamic>> reconcileStatements(Map<String, dynamic> body) async {
    final token = await TokenStore.getToken();
    final res = await http.post(_u('/api/v1/statements/reconcile'),
      headers: {'Content-Type':'application/json', 'Authorization':'Bearer ${token ?? ''}'},
      body: jsonEncode(body),
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as Map<String, dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<Map<String, dynamic>> importStatement(Map<String, dynamic> body) async {
    final token = await TokenStore.getToken();
    final res = await http.post(_u('/api/v1/statements/import'),
      headers: {'Content-Type':'application/json', 'Authorization':'Bearer ${token ?? ''}'},
      body: jsonEncode(body),
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as Map<String, dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<Map<String, dynamic>> createResearchNote(Map<String, dynamic> body) async {
    final token = await TokenStore.getToken();
    final res = await http.post(_u('/api/v1/research/notes'),
      headers: {'Content-Type':'application/json', 'Authorization':'Bearer ${token ?? ''}'},
      body: jsonEncode(body),
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as Map<String, dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<List<dynamic>> listResearchNotes({String? source, int limit = 0}) async {
    final token = await TokenStore.getToken();
    final params = <String, String>{};
    if (source != null && source.isNotEmpty) params['source'] = source;
    if (limit > 0) params['limit'] = limit.toString();
    final uri = _u('/api/v1/research/notes').replace(queryParameters: params);
    final res = await http.get(uri, headers: {'Authorization':'Bearer ${token ?? ''}'});
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as List<dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<Map<String, dynamic>> refreshResearchNote(String id, {int lookback = 120, int horizon = 1}) async {
    final token = await TokenStore.getToken();
    final res = await http.post(_u('/api/v1/research/notes/$id/ai'),
      headers: {'Content-Type':'application/json', 'Authorization':'Bearer ${token ?? ''}'},
      body: jsonEncode({'lookback': lookback, 'horizon': horizon}),
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as Map<String, dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<List<dynamic>> refreshAllResearchNotes({int lookback = 120, int horizon = 1}) async {
    final token = await TokenStore.getToken();
    final res = await http.post(_u('/api/v1/research/notes/ai'),
      headers: {'Content-Type':'application/json', 'Authorization':'Bearer ${token ?? ''}'},
      body: jsonEncode({'lookback': lookback, 'horizon': horizon}),
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as List<dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<Map<String, dynamic>> simulationQuota() async {
    final token = await TokenStore.getToken();
    final res = await http.get(_u('/api/v1/simulation/quota'),
      headers: {'Authorization':'Bearer ${token ?? ''}'},
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as Map<String, dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<Map<String, dynamic>> submitBacktest({
    required List<double> returns,
    String strategy = 'BUY_AND_HOLD',
    double initialCash = 10000.0,
    double contribution = 0.0,
    int contributionEvery = 1,
  }) async {
    final token = await TokenStore.getToken();
    final res = await http.post(_u('/api/v1/simulation/backtest'),
      headers: {'Content-Type':'application/json', 'Authorization':'Bearer ${token ?? ''}'},
      body: jsonEncode({
        'returns': returns,
        'strategy': strategy,
        'initialCash': initialCash,
        'contribution': contribution,
        'contributionEvery': contributionEvery,
      }),
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as Map<String, dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<Map<String, dynamic>> getBacktest(String id) async {
    final token = await TokenStore.getToken();
    final res = await http.get(_u('/api/v1/simulation/backtest/$id'),
      headers: {'Authorization':'Bearer ${token ?? ''}'},
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as Map<String, dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<Map<String, dynamic>> aiPredict(List<double> returns, int horizon) async {
    final token = await TokenStore.getToken();
    final res = await http.post(_u('/api/v1/ai/predict'),
      headers: {'Content-Type':'application/json', 'Authorization':'Bearer ${token ?? ''}'},
      body: jsonEncode({'returns': returns, 'horizon': horizon}),
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as Map<String, dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<Map<String, dynamic>> aiRisk(List<double> returns, int horizon) async {
    final token = await TokenStore.getToken();
    final res = await http.post(_u('/api/v1/ai/risk'),
      headers: {'Content-Type':'application/json', 'Authorization':'Bearer ${token ?? ''}'},
      body: jsonEncode({'returns': returns, 'horizon': horizon}),
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as Map<String, dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<Map<String, dynamic>> aiEvaluate(List<double> returns, int horizon, int window) async {
    final token = await TokenStore.getToken();
    final res = await http.post(_u('/api/v1/ai/evaluate'),
      headers: {'Content-Type':'application/json', 'Authorization':'Bearer ${token ?? ''}'},
      body: jsonEncode({'returns': returns, 'horizon': horizon, 'window': window}),
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as Map<String, dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<List<dynamic>> aiModels() async {
    final token = await TokenStore.getToken();
    final res = await http.get(_u('/api/v1/ai/models'),
      headers: {'Authorization':'Bearer ${token ?? ''}'},
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as List<dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<Map<String, dynamic>> aiRegisterModel(Map<String, dynamic> body) async {
    final token = await TokenStore.getToken();
    final res = await http.post(_u('/api/v1/ai/models'),
      headers: {'Content-Type':'application/json', 'Authorization':'Bearer ${token ?? ''}'},
      body: jsonEncode(body),
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as Map<String, dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<Map<String, dynamic>> bankingAccount() async {
    final token = await TokenStore.getToken();
    final res = await http.get(_u('/api/v1/banking/account'),
      headers: {'Authorization':'Bearer ${token ?? ''}'},
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as Map<String, dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<List<dynamic>> bankingTransfers({int limit = 50}) async {
    final token = await TokenStore.getToken();
    final uri = _u('/api/v1/banking/transfers').replace(queryParameters: {'limit': '$limit'});
    final res = await http.get(uri, headers: {'Authorization':'Bearer ${token ?? ''}'});
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as List<dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<Map<String, dynamic>> bankingTransfer(Map<String, dynamic> body) async {
    final token = await TokenStore.getToken();
    final res = await http.post(_u('/api/v1/banking/transfer'),
      headers: {'Content-Type':'application/json', 'Authorization':'Bearer ${token ?? ''}'},
      body: jsonEncode(body),
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as Map<String, dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<Map<String, dynamic>> screenerQuery(Map<String, dynamic> body) async {
    final token = await TokenStore.getToken();
    final res = await http.post(_u('/api/v1/screeners/query'),
      headers: {'Content-Type':'application/json', 'Authorization':'Bearer ${token ?? ''}'},
      body: jsonEncode(body),
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as Map<String, dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<List<dynamic>> wealthPlans() async {
    final token = await TokenStore.getToken();
    final res = await http.get(_u('/api/v1/wealth/plan'),
      headers: {'Authorization':'Bearer ${token ?? ''}'},
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as List<dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<Map<String, dynamic>> createWealthPlan(Map<String, dynamic> body) async {
    final token = await TokenStore.getToken();
    final res = await http.post(_u('/api/v1/wealth/plan'),
      headers: {'Content-Type':'application/json', 'Authorization':'Bearer ${token ?? ''}'},
      body: jsonEncode(body),
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as Map<String, dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<Map<String, dynamic>> simulateWealthPlan(String id, {Map<String, dynamic>? body}) async {
    final token = await TokenStore.getToken();
    final res = await http.post(_u('/api/v1/wealth/plan/$id/simulate'),
      headers: {'Content-Type':'application/json', 'Authorization':'Bearer ${token ?? ''}'},
      body: jsonEncode(body ?? <String, dynamic>{}),
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as Map<String, dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<List<dynamic>> rewardOffers() async {
    final token = await TokenStore.getToken();
    final res = await http.get(_u('/api/v1/rewards/offers'),
      headers: {'Authorization':'Bearer ${token ?? ''}'},
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as List<dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<List<dynamic>> rewardEnrollments() async {
    final token = await TokenStore.getToken();
    final res = await http.get(_u('/api/v1/rewards/enrollments'),
      headers: {'Authorization':'Bearer ${token ?? ''}'},
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as List<dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<Map<String, dynamic>> rewardEnroll(String offerId) async {
    final token = await TokenStore.getToken();
    final res = await http.post(_u('/api/v1/rewards/enroll'),
      headers: {'Content-Type':'application/json', 'Authorization':'Bearer ${token ?? ''}'},
      body: jsonEncode({'offerId': offerId}),
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as Map<String, dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<List<dynamic>> rewardEvaluate({String? enrollmentId}) async {
    final token = await TokenStore.getToken();
    final body = enrollmentId == null || enrollmentId.isEmpty ? <String, dynamic>{} : {'enrollmentId': enrollmentId};
    final res = await http.post(_u('/api/v1/rewards/evaluate'),
      headers: {'Content-Type':'application/json', 'Authorization':'Bearer ${token ?? ''}'},
      body: jsonEncode(body),
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as List<dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<List<dynamic>> surveillanceAlerts({int limit = 50}) async {
    final token = await TokenStore.getToken();
    final uri = _u('/api/v1/surveillance/alerts').replace(queryParameters: {'limit': '$limit'});
    final res = await http.get(uri, headers: {'Authorization':'Bearer ${token ?? ''}'});
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as List<dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<List<dynamic>> bestExecution({String? symbol, int limit = 50}) async {
    final token = await TokenStore.getToken();
    final params = <String, String>{'limit': '$limit'};
    if (symbol != null && symbol.isNotEmpty) params['symbol'] = symbol;
    final uri = _u('/api/v1/best-execution').replace(queryParameters: params);
    final res = await http.get(uri, headers: {'Authorization':'Bearer ${token ?? ''}'});
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as List<dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<Map<String, dynamic>> portfolioBuilderAnalyze(List<Map<String, dynamic>> holdings) async {
    final token = await TokenStore.getToken();
    final res = await http.post(_u('/api/v1/portfolio/builder/analyze'),
      headers: {'Content-Type':'application/json', 'Authorization':'Bearer ${token ?? ''}'},
      body: jsonEncode({'holdings': holdings}),
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as Map<String, dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<Map<String, dynamic>> orgAdminSummary() async {
    final token = await TokenStore.getToken();
    final res = await http.get(_u('/api/v1/admin/org/summary'),
      headers: {'Authorization':'Bearer ${token ?? ''}'},
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as Map<String, dynamic>;
    }
    throw Exception(res.body);
  }

  static Future<List<dynamic>> orgAdminAuditEvents({int limit = 50}) async {
    final token = await TokenStore.getToken();
    final uri = _u('/api/v1/admin/org/audit/events').replace(queryParameters: {'limit': '$limit'});
    final res = await http.get(uri, headers: {'Authorization':'Bearer ${token ?? ''}'});
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(res.body) as List<dynamic>;
    }
    throw Exception(res.body);
  }
}
