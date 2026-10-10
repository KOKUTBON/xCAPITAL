import 'package:flutter_test/flutter_test.dart';
import 'package:xcapital_app/config.dart';
import 'package:xcapital_app/services/api.dart';
import 'package:xcapital_app/services/token_store.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../fixtures/mobile_integration_fixtures.dart';
import '../support/fake_mobile_api_server.dart';

Future<void> _expectInvalidTokenFailure(Future<Object?> Function() action) async {
  try {
    await action();
    fail('Expected invalid token failure.');
  } catch (error) {
    final message = error.toString();
    expect(message, contains('TOKEN_INVALID'));
    expect(message, contains('Token expired or invalid.'));
  }
}

void main() {
  late FakeMobileApiServer server;

  setUpAll(() async {
    server = FakeMobileApiServer();
    await server.start();
    Config.overrideApiBaseForTests(server.baseUrl);
  });

  tearDownAll(() async {
    Config.overrideApiBaseForTests(null);
    await server.stop();
  });

  setUp(() async {
    SharedPreferences.setMockInitialValues(<String, Object>{});
  });

  test('login persists token and unlocks protected wealth/banking/rewards/admin fetches', () async {
    final auth = await Api.login(testLoginEmail, testLoginPassword);
    expect(auth.mfaRequired, isFalse);
    expect(auth.token, validAuthToken);

    final persistedToken = await TokenStore.getToken();
    expect(persistedToken, validAuthToken);

    final bankingAccount = await Api.bankingAccount();
    expect(bankingAccount, bankingAccountFixture);

    final bankingTransfers = await Api.bankingTransfers(limit: 50);
    expect(bankingTransfers, bankingTransfersFixture);

    final plans = await Api.wealthPlans();
    expect(plans, wealthPlansFixture);

    final offers = await Api.rewardOffers();
    expect(offers, rewardOffersFixture);

    final enrollments = await Api.rewardEnrollments();
    expect(enrollments, rewardEnrollmentsFixture);

    final orgSummary = await Api.orgAdminSummary();
    expect(orgSummary, orgAdminSummaryFixture);

    final orgEvents = await Api.orgAdminAuditEvents(limit: 50);
    expect(orgEvents, orgAdminAuditEventsFixture);
  });

  test('expired/invalid token failures are surfaced for protected critical workflows', () async {
    await TokenStore.setToken(expiredAuthToken);

    await _expectInvalidTokenFailure(() => Api.bankingAccount());
    await _expectInvalidTokenFailure(() => Api.bankingTransfers(limit: 50));
    await _expectInvalidTokenFailure(() => Api.wealthPlans());
    await _expectInvalidTokenFailure(() => Api.rewardOffers());
    await _expectInvalidTokenFailure(() => Api.rewardEnrollments());
    await _expectInvalidTokenFailure(() => Api.orgAdminSummary());
    await _expectInvalidTokenFailure(() => Api.orgAdminAuditEvents(limit: 50));
  });
}
