const String testLoginEmail = 'mobile.integration@xcapital.test';
const String testLoginPassword = 'Password123!';
const String validAuthToken = 'integration-valid-token';
const String expiredAuthToken = 'integration-expired-token';

const Map<String, dynamic> loginSuccessFixture = {
  'token': validAuthToken,
  'mfaRequired': false,
};

const Map<String, dynamic> bankingAccountFixture = {
  'cash': 15234.22,
  'currency': 'USD',
  'status': 'ACTIVE',
};

const List<Map<String, dynamic>> bankingTransfersFixture = [
  {
    'id': 'tx-1001',
    'direction': 'TO_INVESTING',
    'amount': 250.00,
    'status': 'SETTLED',
  },
  {
    'id': 'tx-1002',
    'direction': 'TO_BANKING',
    'amount': 95.50,
    'status': 'PENDING',
  },
];

const List<Map<String, dynamic>> wealthPlansFixture = [
  {
    'id': 'plan-42',
    'name': 'Retirement Plan',
    'planType': 'RETIREMENT',
    'targetBalance': 1000000.0,
  },
];

const List<Map<String, dynamic>> rewardOffersFixture = [
  {
    'id': 'offer-10',
    'name': 'Cash Bonus Challenge',
  },
];

const List<Map<String, dynamic>> rewardEnrollmentsFixture = [
  {
    'id': 'enr-33',
    'offerId': 'offer-10',
    'status': 'ENROLLED',
  },
];

const Map<String, dynamic> orgAdminSummaryFixture = {
  'orgId': 'org-123',
  'users': 24,
  'activeApprovals': 3,
  'pendingApprovals': 1,
};

const List<Map<String, dynamic>> orgAdminAuditEventsFixture = [
  {
    'id': 'audit-1',
    'type': 'TRADE_APPROVED',
    'actor': 'ops-admin',
  },
  {
    'id': 'audit-2',
    'type': 'TRANSFER_SUBMITTED',
    'actor': 'advisor-7',
  },
];

const Map<String, dynamic> invalidTokenFixture = {
  'error': 'TOKEN_INVALID',
  'message': 'Token expired or invalid.',
};
