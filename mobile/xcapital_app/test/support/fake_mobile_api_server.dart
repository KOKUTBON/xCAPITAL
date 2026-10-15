import 'dart:convert';
import 'dart:io';

import '../fixtures/mobile_integration_fixtures.dart';

class FakeMobileApiServer {
  HttpServer? _server;

  String get baseUrl {
    final server = _server;
    if (server == null) {
      throw StateError('Fake API server has not started yet.');
    }
    return 'http://127.0.0.1:${server.port}';
  }

  Future<void> start() async {
    _server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    _server!.listen(_handleRequest);
  }

  Future<void> stop() async {
    await _server?.close(force: true);
    _server = null;
  }

  Future<void> _handleRequest(HttpRequest request) async {
    final method = request.method;
    final path = request.uri.path;

    if (method == 'POST' && path == '/api/v1/auth/login') {
      await _handleLogin(request);
      return;
    }

    if (!_isAuthorized(request)) {
      await _writeJson(request, HttpStatus.unauthorized, invalidTokenFixture);
      return;
    }

    switch ('$method $path') {
      case 'GET /api/v1/banking/account':
        await _writeJson(request, HttpStatus.ok, bankingAccountFixture);
        return;
      case 'GET /api/v1/banking/transfers':
        await _writeJson(request, HttpStatus.ok, bankingTransfersFixture);
        return;
      case 'GET /api/v1/wealth/plan':
        await _writeJson(request, HttpStatus.ok, wealthPlansFixture);
        return;
      case 'GET /api/v1/rewards/offers':
        await _writeJson(request, HttpStatus.ok, rewardOffersFixture);
        return;
      case 'GET /api/v1/rewards/enrollments':
        await _writeJson(request, HttpStatus.ok, rewardEnrollmentsFixture);
        return;
      case 'GET /api/v1/admin/org/summary':
        await _writeJson(request, HttpStatus.ok, orgAdminSummaryFixture);
        return;
      case 'GET /api/v1/admin/org/audit/events':
        await _writeJson(request, HttpStatus.ok, orgAdminAuditEventsFixture);
        return;
      default:
        await _writeJson(
          request,
          HttpStatus.notFound,
          {'error': 'NOT_FOUND', 'path': path},
        );
    }
  }

  Future<void> _handleLogin(HttpRequest request) async {
    final payload = await _readJsonMap(request);
    final email = payload['email']?.toString() ?? '';
    final password = payload['password']?.toString() ?? '';
    if (email == testLoginEmail && password == testLoginPassword) {
      await _writeJson(request, HttpStatus.ok, loginSuccessFixture);
      return;
    }
    await _writeJson(request, HttpStatus.unauthorized, invalidTokenFixture);
  }

  bool _isAuthorized(HttpRequest request) {
    final auth = request.headers.value(HttpHeaders.authorizationHeader) ?? '';
    return auth == 'Bearer $validAuthToken';
  }

  Future<Map<String, dynamic>> _readJsonMap(HttpRequest request) async {
    final raw = await utf8.decoder.bind(request).join();
    if (raw.trim().isEmpty) {
      return <String, dynamic>{};
    }
    return Map<String, dynamic>.from(jsonDecode(raw) as Map);
  }

  Future<void> _writeJson(
    HttpRequest request,
    int statusCode,
    Object payload,
  ) async {
    request.response.statusCode = statusCode;
    request.response.headers.contentType = ContentType.json;
    request.response.write(jsonEncode(payload));
    await request.response.close();
  }
}
