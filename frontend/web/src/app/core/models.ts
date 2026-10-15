/** Domain contracts for the xCAPITAL demo workspace. All data is simulated. */

export type Ccy = "EUR" | "USD" | "GBP" | "CHF";

export interface Entity {
  id: string;
  name: string;
  short: string;
  country: string;
  city: string;
  ccy: Ccy;
}

export type AccountType = "Operating" | "Payroll" | "Reserve" | "Collections" | "Money market" | "Brokerage";
export type SyncStatus = "live" | "delayed" | "manual";

export interface Account {
  id: string;
  entityId: string;
  bank: string;
  name: string;
  type: AccountType;
  ccy: Ccy;
  /** Masked identifier, e.g. "IT60 •••• 4821". */
  iban: string;
  balance: number;
  available: number;
  /** 90 daily closing balances, oldest first, last == balance. */
  history: number[];
  change7d: number;
  lastSync: string;
  status: SyncStatus;
}

export type TxnStatus = "reconciled" | "matched" | "exception" | "pending";

export interface Txn {
  id: string;
  date: string;
  accountId: string;
  counterparty: string;
  category: string;
  memo: string;
  amount: number;
  ccy: Ccy;
  status: TxnStatus;
  source: "Bank API" | "CSV import" | "ERP";
  /** Match confidence 0–1 for matched/exception rows. */
  confidence: number;
  journalRef?: string;
  exceptionReason?: string;
}

export interface JournalLine {
  account: string;
  debit: number;
  credit: number;
}
export interface Journal {
  id: string;
  date: string;
  memo: string;
  entityId: string;
  ccy: Ccy;
  lines: JournalLine[];
  status: "posted" | "draft" | "reversed";
  source: string;
}

export interface ForecastWeek {
  week: number;
  label: string;
  start: string;
  opening: number;
  collections: number;
  otherIn: number;
  payroll: number;
  suppliers: number;
  tax: number;
  capex: number;
  debt: number;
  inflow: number;
  outflow: number;
  net: number;
  closing: number;
  p10: number;
  p90: number;
}

export interface ScenarioParams {
  /** Average days customer collections slip. */
  collectionDelay: number;
  /** Revenue shock in percent, e.g. -10. */
  revenueShock: number;
  /** EUR/USD move in percent (positive = USD strengthens). */
  fxShock: number;
  /** Defer discretionary capex by 6 weeks. */
  deferCapex: boolean;
  /** Minimum operating buffer in EUR. */
  minBuffer: number;
}

export type ObligationType = "Receivable" | "Payable" | "Tax" | "Payroll" | "Debt service";
export interface Obligation {
  id: string;
  type: ObligationType;
  counterparty: string;
  entityId: string;
  due: string;
  amount: number;
  ccy: Ccy;
  probability: number;
  status: "scheduled" | "overdue" | "at risk" | "confirmed";
}

export type ActionKind = "Payment" | "FX" | "Sweep" | "Rebalance" | "Investment";
export type ActionStatus = "pending" | "approved" | "executed" | "rejected" | "blocked";
export type CheckResult = "pass" | "warn" | "fail";

export interface GuardCheck {
  label: string;
  result: CheckResult;
  detail: string;
}

export interface Approver {
  role: string;
  name: string;
  initials: string;
  status: "approved" | "pending" | "waiting" | "rejected";
  at?: string;
  /** True for the demo persona — the viewer can act on this step. */
  you?: boolean;
}

export interface FinancialAction {
  id: string;
  kind: ActionKind;
  title: string;
  summary: string;
  amount: number;
  ccy: Ccy;
  from: string;
  to: string;
  entityId: string;
  origin: "AI" | "Human" | "Rule";
  requestedBy: string;
  createdAt: string;
  expiresAt: string;
  status: ActionStatus;
  riskScore: number;
  risk: "low" | "medium" | "high";
  rationale: string;
  evidence: string[];
  checks: GuardCheck[];
  chain: Approver[];
  version: number;
  payloadHash: string;
}

export interface Policy {
  id: string;
  name: string;
  type: "Limit" | "Mandate" | "Control";
  scope: string;
  description: string;
  owner: string;
  limit: number;
  used: number;
  ccy: Ccy;
  /** Unit label for non-money limits. */
  unit?: string;
  active: boolean;
  version: number;
}

export interface AuditEvent {
  seq: number;
  at: string;
  actor: string;
  actorType: "human" | "ai" | "system";
  action: string;
  target: string;
  detail: string;
  hash: string;
  prevHash: string;
}

export type ConnectorKind = "Bank" | "ERP" | "Broker" | "Market data" | "Payroll" | "Cards";
export interface Connector {
  id: string;
  name: string;
  kind: ConnectorKind;
  protocol: string;
  status: "healthy" | "degraded" | "syncing" | "offline";
  lastSync: string;
  latencyMs: number;
  records24h: number;
  coverage: number;
  region: string;
  scopes: string[];
}

export type AssetClass = "Equity" | "Fixed income" | "Money market" | "Commodity" | "Alternatives" | "Cash";
export interface Holding {
  symbol: string;
  name: string;
  assetClass: AssetClass;
  sector: string;
  region: string;
  qty: number;
  price: number;
  avgCost: number;
  value: number;
  weight: number;
  target: number;
  dayPct: number;
  yieldPct: number;
  ccy: Ccy;
}

export interface Candle {
  t: string;
  o: number;
  h: number;
  l: number;
  c: number;
  v: number;
}

export interface Instrument {
  symbol: string;
  name: string;
  venue: string;
  kind: "Equity" | "Index" | "FX" | "Rates" | "Commodity" | "Crypto";
  ccy: string;
  decimals: number;
  base: number;
  candles: Candle[];
}

export interface Quote {
  symbol: string;
  price: number;
  prev: number;
  change: number;
  changePct: number;
  tick: "up" | "down" | "flat";
}

export interface StressScenario {
  name: string;
  description: string;
  impactPct: number;
  impact: number;
  liquidityImpact: number;
}

export interface Insight {
  id: string;
  severity: "info" | "opportunity" | "warning" | "critical";
  title: string;
  body: string;
  metric: string;
  route: string;
  confidence: number;
}

export interface CopilotSource {
  label: string;
  route: string;
}
export interface CopilotAnswer {
  prompt: string;
  answer: string[];
  table?: { head: string[]; rows: string[][] };
  sources: CopilotSource[];
  proposal?: string;
}

export interface Toast {
  id: number;
  tone: "pos" | "neg" | "info" | "warn";
  title: string;
  body?: string;
}
