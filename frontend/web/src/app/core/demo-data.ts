// Entirely synthetic UI fixtures; no real accounts, balances or transactions.
/**
 * Deterministic simulated dataset for the xCAPITAL demo.
 *
 * Every number is generated from a fixed seed and anchored to "today", so the
 * workspace always looks current yet renders identically for every visitor.
 * Company, banks, counterparties and people are fictional. Market tickers are
 * real symbols with SIMULATED prices — this is not market data.
 */
import {
  Account,
  AuditEvent,
  Candle,
  Ccy,
  Connector,
  CopilotAnswer,
  Entity,
  FinancialAction,
  ForecastWeek,
  Holding,
  Insight,
  Instrument,
  Journal,
  Obligation,
  Policy,
  ScenarioParams,
  StressScenario,
  Txn,
} from "./models";

/* ───────────── deterministic helpers ───────────── */

function mulberry32(seed: number) {
  return () => {
    seed |= 0;
    seed = (seed + 0x6d2b79f5) | 0;
    let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
export function rng(seed: number) {
  const r = mulberry32(seed);
  const api = {
    next: r,
    range: (a: number, b: number) => a + (b - a) * r(),
    int: (a: number, b: number) => Math.floor(a + (b - a + 1) * r()),
    pick: <T>(arr: readonly T[]) => arr[Math.floor(r() * arr.length)],
    normal: () => {
      const u = Math.max(r(), 1e-9);
      const v = r();
      return Math.sqrt(-2 * Math.log(u)) * Math.cos(2 * Math.PI * v);
    },
  };
  return api;
}

/** 256-bit hex digest (non-cryptographic, deterministic) used for the simulated audit chain. */
export function digest(input: string): string {
  let out = "";
  for (let k = 0; k < 4; k++) {
    let h1 = 0xdeadbeef ^ (k * 0x9e3779b1);
    let h2 = 0x41c6ce57 ^ k;
    for (let i = 0; i < input.length; i++) {
      const ch = input.charCodeAt(i);
      h1 = Math.imul(h1 ^ ch, 2654435761);
      h2 = Math.imul(h2 ^ ch, 1597334677);
    }
    h1 = Math.imul(h1 ^ (h1 >>> 16), 2246822507) ^ Math.imul(h2 ^ (h2 >>> 13), 3266489909);
    h2 = Math.imul(h2 ^ (h2 >>> 16), 2246822507) ^ Math.imul(h1 ^ (h1 >>> 13), 3266489909);
    out += (h2 >>> 0).toString(16).padStart(8, "0") + (h1 >>> 0).toString(16).padStart(8, "0");
  }
  return out;
}

const DAY = 86_400_000;
const today = new Date();
today.setHours(0, 0, 0, 0);
export const TODAY = today.getTime();
const iso = (t: number) => new Date(t).toISOString();
const daysAgo = (d: number, hours = 0) => iso(TODAY - d * DAY + hours * 3_600_000);
const daysAhead = (d: number) => iso(TODAY + d * DAY);
const minutesAgo = (m: number) => iso(Date.now() - m * 60_000);

/* ───────────── organisation ───────────── */

export const ORG = {
  name: "Northstar Dynamics Group",
  short: "Northstar",
  baseCcy: "EUR" as Ccy,
  employees: 420,
  persona: { name: "Alex Morgan", role: "Group Treasurer", initials: "AM" },
};

export const FX: Record<Ccy, number> = { EUR: 1, USD: 1.0874, GBP: 0.8462, CHF: 0.9412 };
/** Convert an amount in `ccy` to the EUR base currency. */
export const toEur = (v: number, ccy: Ccy) => v / FX[ccy];

export const ENTITIES: Entity[] = [
  { id: "it", name: "Northstar Dynamics S.p.A.", short: "NSD Italia", country: "IT", city: "Milan", ccy: "EUR" },
  { id: "de", name: "Northstar Dynamics GmbH", short: "NSD Germany", country: "DE", city: "Munich", ccy: "EUR" },
  { id: "us", name: "Northstar Dynamics Inc.", short: "NSD US", country: "US", city: "Austin", ccy: "USD" },
  { id: "uk", name: "Northstar Dynamics Ltd", short: "NSD UK", country: "GB", city: "London", ccy: "GBP" },
  { id: "ch", name: "Northstar Treasury AG", short: "NSD Treasury", country: "CH", city: "Zug", ccy: "CHF" },
];
export const entityById = (id: string) => ENTITIES.find((e) => e.id === id)!;

/* ───────────── accounts ───────────── */

const ACCOUNT_SEED: Array<Omit<Account, "history" | "change7d" | "available" | "lastSync" | "status"> & { vol: number; drift: number }> = [
  { id: "acc-it-op", entityId: "it", bank: "Alpina Commercial Bank", name: "Main operating", type: "Operating", ccy: "EUR", iban: "SYNTHETIC-ACCOUNT", balance: 9_840_000, vol: 0.022, drift: 0.0006 },
  { id: "acc-it-pay", entityId: "it", bank: "Meridian Trust", name: "Payroll", type: "Payroll", ccy: "EUR", iban: "SYNTHETIC-ACCOUNT", balance: 2_310_000, vol: 0.05, drift: 0 },
  { id: "acc-it-col", entityId: "it", bank: "Alpina Commercial Bank", name: "Customer collections", type: "Collections", ccy: "EUR", iban: "SYNTHETIC-ACCOUNT", balance: 4_120_000, vol: 0.035, drift: 0.001 },
  { id: "acc-de-op", entityId: "de", bank: "Nordbank Clearing", name: "Operating", type: "Operating", ccy: "EUR", iban: "SYNTHETIC-ACCOUNT", balance: 6_450_000, vol: 0.025, drift: 0.0012 },
  { id: "acc-de-res", entityId: "de", bank: "Nordbank Clearing", name: "Liquidity reserve", type: "Reserve", ccy: "EUR", iban: "SYNTHETIC-ACCOUNT", balance: 3_800_000, vol: 0.004, drift: 0.0002 },
  { id: "acc-us-op", entityId: "us", bank: "Harborline Bank", name: "Operating", type: "Operating", ccy: "USD", iban: "SYNTHETIC-ACCOUNT", balance: 7_920_000, vol: 0.028, drift: 0.0004 },
  { id: "acc-us-col", entityId: "us", bank: "Harborline Bank", name: "Lockbox collections", type: "Collections", ccy: "USD", iban: "SYNTHETIC-ACCOUNT", balance: 2_640_000, vol: 0.04, drift: 0.0008 },
  { id: "acc-uk-op", entityId: "uk", bank: "Thames & Union Bank", name: "Operating", type: "Operating", ccy: "GBP", iban: "SYNTHETIC-ACCOUNT", balance: 2_180_000, vol: 0.03, drift: 0.0003 },
  { id: "acc-ch-res", entityId: "ch", bank: "Helvetia Private Bank", name: "Strategic reserve", type: "Reserve", ccy: "CHF", iban: "SYNTHETIC-ACCOUNT", balance: 3_250_000, vol: 0.003, drift: 0.0001 },
  { id: "acc-ch-mmf", entityId: "ch", bank: "Lumen Funds", name: "EUR liquidity fund", type: "Money market", ccy: "EUR", iban: "SYNTHETIC-ACCOUNT", balance: 6_100_000, vol: 0.001, drift: 0.00011 },
  { id: "acc-ch-brk", entityId: "ch", bank: "Atlas Securities", name: "Reserve portfolio cash", type: "Brokerage", ccy: "EUR", iban: "SYNTHETIC-ACCOUNT", balance: 1_050_000, vol: 0.02, drift: 0 },
];

export const ACCOUNTS: Account[] = ACCOUNT_SEED.map((a, i) => {
  const r = rng(101 + i * 17);
  const history: number[] = new Array(90);
  let v = a.balance;
  history[89] = v;
  for (let d = 88; d >= 0; d--) {
    const shock = r.normal() * a.vol * 0.3;
    v = v / (1 + a.drift + shock);
    history[d] = v;
  }
  // Shape a gentle group-wide uptrend: +6% over the quarter, +4% in the last week.
  for (let d = 0; d < 89; d++) {
    const lastWeek = Math.min(1, (89 - d) / 7);
    const earlier = Math.max(0, 82 - d) / 82;
    history[d] = Math.max(a.balance * 0.3, history[d] / ((1 + 0.04 * lastWeek) * (1 + 0.06 * earlier)));
  }
  const { vol, drift, ...rest } = a;
  const status = a.id === "acc-uk-op" ? "delayed" : a.id === "acc-ch-res" ? "manual" : "live";
  return {
    ...rest,
    history,
    available: Math.round(a.balance * (a.type === "Money market" ? 1 : r.range(0.93, 0.995))),
    change7d: ((history[89] - history[82]) / history[82]) * 100,
    lastSync: status === "live" ? minutesAgo(r.int(1, 9)) : status === "delayed" ? minutesAgo(r.int(95, 140)) : daysAgo(1, 18),
    status,
  };
});
export const accountById = (id: string) => ACCOUNTS.find((a) => a.id === id)!;

/** Group liquidity per day (EUR), 90 points. */
export const GROUP_LIQUIDITY_HISTORY: number[] = Array.from({ length: 90 }, (_, d) =>
  ACCOUNTS.reduce((s, a) => s + toEur(a.history[d], a.ccy), 0),
);
export const TOTAL_LIQUIDITY = GROUP_LIQUIDITY_HISTORY[89];

/* ───────────── transactions ───────────── */

const CUSTOMERS = ["Vela Mobility", "Orion Health Systems", "Kestrel Logistics", "Brightwater Energy", "Atlas Retail Group", "Solenne Pharma", "Corvid Aerospace", "Halcyon Foods", "Tessera Robotics"];
const SUPPLIERS = ["Polaris Semiconductors", "Ferro Components", "Quanta Cloud Services", "Delta Freight", "Lumio Facilities", "Granite Insurance", "Vector Machining", "Northwind Energy Supply"];

export const TRANSACTIONS: Txn[] = (() => {
  const r = rng(2026);
  const out: Txn[] = [];
  const opAccounts = ACCOUNTS.filter((a) => a.type !== "Money market" && a.type !== "Reserve");
  for (let i = 0; i < 160; i++) {
    const acc = r.pick(opAccounts);
    const inflow = acc.type === "Collections" ? r.next() < 0.92 : r.next() < 0.38;
    let counterparty: string;
    let category: string;
    let amount: number;
    if (acc.type === "Payroll") {
      counterparty = r.next() < 0.7 ? "Payroll run — " + entityById(acc.entityId).short : "Social security contributions";
      category = "Payroll";
      amount = -r.range(40_000, 380_000);
    } else if (inflow) {
      counterparty = r.pick(CUSTOMERS);
      category = r.next() < 0.85 ? "Customer receipt" : "Interest income";
      amount = category === "Interest income" ? r.range(2_000, 18_000) : r.range(35_000, 640_000);
    } else {
      const roll = r.next();
      if (roll < 0.62) {
        counterparty = r.pick(SUPPLIERS);
        category = "Supplier payment";
        amount = -r.range(12_000, 420_000);
      } else if (roll < 0.74) {
        counterparty = "Tax authority (" + entityById(acc.entityId).country + ")";
        category = "Tax";
        amount = -r.range(60_000, 520_000);
      } else if (roll < 0.86) {
        counterparty = "Intercompany — " + r.pick(ENTITIES.filter((e) => e.id !== acc.entityId)).short;
        category = "Intercompany";
        amount = -r.range(100_000, 900_000);
      } else {
        counterparty = r.pick(["Bank fees", "Card settlement", "Lease — HQ campus"]);
        category = counterparty === "Bank fees" ? "Fees" : "Operating expense";
        amount = -r.range(800, 95_000);
      }
    }
    const s = r.next();
    const status = i < 6 ? "pending" : s < 0.74 ? "reconciled" : s < 0.89 ? "matched" : s < 0.96 ? "exception" : "pending";
    const id = "TX-" + (48210 + i);
    const t: Txn = {
      id,
      date: daysAgo(Math.floor(i / 5.5), r.int(7, 18)),
      accountId: acc.id,
      counterparty,
      category,
      memo:
        category === "Customer receipt"
          ? `INV-${r.int(24000, 26999)} settlement`
          : category === "Supplier payment"
            ? `PO-${r.int(7000, 8999)} / bill ${r.int(100000, 999999)}`
            : category,
      amount: Math.round(amount * (FX[acc.ccy] ?? 1)),
      ccy: acc.ccy,
      status,
      source: r.next() < 0.78 ? "Bank API" : r.next() < 0.5 ? "ERP" : "CSV import",
      confidence: status === "reconciled" ? 1 : status === "matched" ? r.range(0.86, 0.99) : status === "exception" ? r.range(0.31, 0.72) : 0,
    };
    if (status === "reconciled") t.journalRef = "JE-" + (91000 + i);
    if (status === "exception")
      t.exceptionReason = r.pick([
        `Amount differs from invoice by ${(r.range(0.4, 3.2)).toFixed(1)}%`,
        "No matching invoice or bill found",
        `Possible duplicate of TX-${48210 + Math.max(0, i - r.int(1, 9))}`,
        "FX rate variance above 0.5% tolerance",
        "Counterparty name mismatch (fuzzy score 0.61)",
      ]);
    out.push(t);
  }
  return out;
})();

export const JOURNALS: Journal[] = TRANSACTIONS.filter((t) => t.journalRef)
  .slice(0, 18)
  .map((t) => {
    const acc = accountById(t.accountId);
    const amt = Math.abs(t.amount);
    const cash = `1010 Cash — ${acc.bank.split(" ")[0]} ${acc.ccy}`;
    const contra =
      t.category === "Customer receipt"
        ? "1200 Accounts receivable"
        : t.category === "Supplier payment"
          ? "2000 Accounts payable"
          : t.category === "Payroll"
            ? "2100 Payroll liabilities"
            : t.category === "Tax"
              ? "2200 Tax payable"
              : t.category === "Intercompany"
                ? "1900 Intercompany clearing"
                : t.category === "Interest income"
                  ? "7100 Interest income"
                  : "6100 Operating expenses";
    const inflow = t.amount > 0;
    return {
      id: t.journalRef!,
      date: t.date,
      memo: `${t.counterparty} · ${t.memo}`,
      entityId: acc.entityId,
      ccy: t.ccy,
      status: "posted" as const,
      source: t.id,
      lines: inflow
        ? [
            { account: cash, debit: amt, credit: 0 },
            { account: contra, debit: 0, credit: amt },
          ]
        : [
            { account: contra, debit: amt, credit: 0 },
            { account: cash, debit: 0, credit: amt },
          ],
    };
  });

/* ───────────── 13-week forecast ───────────── */

export const DEFAULT_SCENARIO: ScenarioParams = {
  collectionDelay: 0,
  revenueShock: 0,
  fxShock: 0,
  deferCapex: false,
  minBuffer: 18_000_000,
};

/** Base-case weekly cash schedule (EUR). Q4 billing ramps collections; payroll, VAT, capex and a loan instalment land on fixed weeks. */
const BASE_WEEKS = Array.from({ length: 13 }, (_, w) => ({
  collections: 2_550_000 + 200_000 * w + 160_000 * Math.sin(w * 1.7),
  otherIn: 230_000 + 60_000 * Math.cos(w * 2.3),
  payroll: [3, 7, 12].includes(w) ? 3_240_000 : 160_000,
  suppliers: 2_700_000 + 200_000 * Math.cos(w * 1.3),
  tax: w === 1 ? 2_150_000 : w === 10 ? 1_760_000 : 90_000,
  capex: [2, 5, 8, 11].includes(w) ? 1_150_000 : 120_000,
  debt: w === 6 ? 1_480_000 : 0,
}));

/** Operating cash that funds the forecast: operating, payroll and collection accounts (EUR). */
export const OPERATING_CASH = ACCOUNTS.filter((a) => ["Operating", "Payroll", "Collections"].includes(a.type)).reduce((s, a) => s + toEur(a.balance, a.ccy), 0);

/** Deterministic 13-week direct cash forecast under a scenario. */
export function runForecast(p: ScenarioParams, opening = OPERATING_CASH): ForecastWeek[] {
  const shift = p.collectionDelay / 7;
  const baseCol = (x: number) => {
    if (x <= -1) return 0;
    if (x < 0) return BASE_WEEKS[0].collections * (1 + x);
    const i = Math.floor(x);
    const f = x - i;
    const a = BASE_WEEKS[Math.min(i, 12)].collections;
    const b = BASE_WEEKS[Math.min(i + 1, 12)].collections;
    return a + (b - a) * f;
  };
  const usdShare = 0.31;
  const weeks: ForecastWeek[] = [];
  let open = opening * (1 + (usdShare * 0.45 * p.fxShock) / 100);
  for (let w = 0; w < 13; w++) {
    const b = BASE_WEEKS[w];
    const ramp = Math.min(1, w / 3);
    let collections = baseCol(w - shift) * (1 + (p.revenueShock / 100) * ramp);
    collections *= 1 + (usdShare * p.fxShock) / 100;
    let capex = b.capex;
    if (p.deferCapex) {
      if (w < 6 && b.capex > 500_000) capex = 120_000;
      const src = w - 6;
      if (src >= 0 && BASE_WEEKS[src].capex > 500_000) capex += BASE_WEEKS[src].capex - 120_000;
    }
    const inflow = collections + b.otherIn;
    const outflow = b.payroll + b.suppliers + b.tax + capex + b.debt;
    const net = inflow - outflow;
    const closing = open + net;
    const sigma = 650_000 * Math.sqrt(w + 1) * (1 + p.collectionDelay / 40 + Math.abs(p.revenueShock) / 50);
    const start = new Date(TODAY + w * 7 * DAY);
    const wk = isoWeek(start);
    weeks.push({
      week: w + 1,
      label: `W${wk}`,
      start: start.toISOString(),
      opening: open,
      collections,
      otherIn: b.otherIn,
      payroll: b.payroll,
      suppliers: b.suppliers,
      tax: b.tax,
      capex,
      debt: b.debt,
      inflow,
      outflow,
      net,
      closing,
      p10: closing - 1.28 * sigma,
      p90: closing + 1.28 * sigma,
    });
    open = closing;
  }
  return weeks;
}

function isoWeek(d: Date) {
  const t = new Date(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()));
  const day = t.getUTCDay() || 7;
  t.setUTCDate(t.getUTCDate() + 4 - day);
  const y0 = new Date(Date.UTC(t.getUTCFullYear(), 0, 1));
  return Math.ceil(((t.getTime() - y0.getTime()) / DAY + 1) / 7);
}

/** Forecast accuracy (MAPE %) for the last 8 closed weeks — simulated backtest. */
export const FORECAST_BACKTEST = [
  { week: "−8", forecast: 41.2, actual: 40.6 },
  { week: "−7", forecast: 42.0, actual: 42.4 },
  { week: "−6", forecast: 43.1, actual: 42.7 },
  { week: "−5", forecast: 41.8, actual: 41.1 },
  { week: "−4", forecast: 44.0, actual: 44.6 },
  { week: "−3", forecast: 45.2, actual: 45.9 },
  { week: "−2", forecast: 46.4, actual: 46.1 },
  { week: "−1", forecast: 47.9, actual: 48.3 },
];

/* ───────────── obligations ───────────── */

export const OBLIGATIONS: Obligation[] = (() => {
  const r = rng(77);
  const out: Obligation[] = [];
  for (let i = 0; i < 26; i++) {
    const recv = i % 2 === 0;
    const ent = r.pick(ENTITIES.slice(0, 4));
    const due = r.int(-6, 70);
    const type = recv ? "Receivable" : r.pick(["Payable", "Payable", "Payable", "Tax", "Payroll", "Debt service"] as const);
    const amount = type === "Payroll" ? r.range(1.6e6, 3.3e6) : type === "Tax" ? r.range(4e5, 2.2e6) : type === "Debt service" ? 1_480_000 : r.range(80_000, 1_400_000);
    out.push({
      id: (recv ? "AR-" : "AP-") + (3100 + i),
      type,
      counterparty: recv ? r.pick(CUSTOMERS) : type === "Payable" ? r.pick(SUPPLIERS) : type === "Tax" ? `Tax authority (${ent.country})` : type === "Payroll" ? `Payroll — ${ent.short}` : "Term loan — Meridian Trust",
      entityId: ent.id,
      due: daysAhead(due),
      amount: Math.round(amount * FX[ent.ccy]),
      ccy: ent.ccy,
      probability: recv ? r.range(0.55, 0.99) : 1,
      status: due < 0 ? (recv ? "overdue" : "scheduled") : recv && r.next() < 0.22 ? "at risk" : r.next() < 0.5 ? "confirmed" : "scheduled",
    });
  }
  return out.sort((a, b) => a.due.localeCompare(b.due));
})();

/* ───────────── governed financial actions ───────────── */

const you = (status: "pending" | "waiting" | "approved" | "rejected" = "pending", at?: string) => ({
  role: "Group Treasurer",
  name: ORG.persona.name + " (you)",
  initials: ORG.persona.initials,
  status,
  at,
  you: true,
});

export const ACTIONS: FinancialAction[] = [
  {
    id: "ACT-2048",
    kind: "Sweep",
    title: "Sweep idle cash into EUR liquidity fund",
    summary: "Move surplus above the 13-week peak need from NSD Germany operating into the Lumen EUR liquidity fund.",
    amount: 4_500_000,
    ccy: "EUR",
    from: "Nordbank Clearing · Operating (DE89 •••• 3104)",
    to: "Lumen Funds · EUR liquidity fund",
    entityId: "de",
    origin: "AI",
    requestedBy: "xCAPITAL Intelligence",
    createdAt: minutesAgo(38),
    expiresAt: daysAhead(1),
    status: "pending",
    riskScore: 12,
    risk: "low",
    rationale:
      "Operating balance exceeds the forecast 13-week peak requirement by €5.2M. The approved liquidity fund yields 3.41% vs 0.25% on the operating account — about €3,550 of additional interest per week with same-day redemption.",
    evidence: ["13-week forecast v41 · P10 low €24.1M (W8)", "Lumen EUR fund · T+0 liquidity, AAAm", "Policy MAN-04 AI sweep mandate"],
    checks: [
      { label: "Within AI sweep mandate (≤ €5M/day)", result: "pass", detail: "€4.5M of €5.0M daily mandate." },
      { label: "Destination is pre-approved", result: "pass", detail: "Lumen EUR fund is on the approved instrument list." },
      { label: "Post-sweep buffer ≥ minimum", result: "pass", detail: "P10 low point stays €1.6M above the €18M buffer." },
      { label: "Counterparty concentration", result: "pass", detail: "Lumen Funds rises to 21% of group cash (limit 35%)." },
    ],
    chain: [you()],
    version: 1,
    payloadHash: digest("ACT-2048v1"),
  },
  {
    id: "ACT-2047",
    kind: "Payment",
    title: "Supplier payment run — Polaris Semiconductors",
    summary: "Batch of 14 approved bills for wafers and packaging, due within 5 days.",
    amount: 1_284_500,
    ccy: "EUR",
    from: "Alpina Commercial Bank · Main operating (IT60 •••• 4821)",
    to: "Polaris Semiconductors B.V. · NL91 •••• 2231",
    entityId: "it",
    origin: "Human",
    requestedBy: "Giulia Conti · AP Lead",
    createdAt: minutesAgo(124),
    expiresAt: daysAhead(2),
    status: "pending",
    riskScore: 46,
    risk: "medium",
    rationale: "Bills are three-way matched against purchase orders and goods receipts. Paying by Thursday captures a 1.5% early-payment discount (€19,268).",
    evidence: ["14 bills · 3-way matched", "Early-payment discount 1.5% / 10 days", "Vendor master change log"],
    checks: [
      { label: "Sanctions & watchlist screening", result: "pass", detail: "No matches across 4 lists." },
      { label: "Beneficiary bank details", result: "warn", detail: "Changed 9 days ago — verified by call-back on file." },
      { label: "Dual approval threshold (≥ €1M)", result: "pass", detail: "Two approvers required and configured." },
      { label: "Daily outbound payment limit", result: "pass", detail: "€6.5M of €8.0M after this payment." },
    ],
    chain: [
      { role: "AP Lead", name: "Giulia Conti", initials: "GC", status: "approved", at: minutesAgo(120) },
      you(),
      { role: "CFO", name: "Daniel Brandt", initials: "DB", status: "waiting" },
    ],
    version: 2,
    payloadHash: digest("ACT-2047v2"),
  },
  {
    id: "ACT-2046",
    kind: "FX",
    title: "Hedge forecast USD receipts — 3M forward",
    summary: "Sell USD 6.0M forward against EUR to cover forecast US collections for weeks 6–13.",
    amount: 6_000_000,
    ccy: "USD",
    from: "NSD US · forecast collections",
    to: "EUR/USD forward @ 1.0912 · 3M",
    entityId: "us",
    origin: "AI",
    requestedBy: "xCAPITAL Intelligence",
    createdAt: minutesAgo(312),
    expiresAt: iso(Date.now() + 5 * 3_600_000),
    status: "pending",
    riskScore: 34,
    risk: "medium",
    rationale: "Unhedged USD exposure is 52% of forecast receipts against a 60–80% policy band. This forward brings the hedge ratio to 74%. The quote expires in 5 hours.",
    evidence: ["USD exposure report · W41", "Policy CTL-05 hedge band 60–80%", "3 bank quotes · best 1.0912"],
    checks: [
      { label: "Hedge ratio within band after trade", result: "pass", detail: "52% → 74% (band 60–80%)." },
      { label: "Counterparty credit limit", result: "pass", detail: "Harborline Bank: $14.2M headroom." },
      { label: "Quote freshness", result: "warn", detail: "Indicative quote; firm price at execution." },
    ],
    chain: [
      { role: "Treasury Analyst", name: "Priya Nair", initials: "PN", status: "approved", at: minutesAgo(290) },
      you(),
    ],
    version: 1,
    payloadHash: digest("ACT-2046v1"),
  },
  {
    id: "ACT-2045",
    kind: "Rebalance",
    title: "Rebalance strategic reserve portfolio",
    summary: "Trim equities by 2.1 pts and add short-dated investment-grade credit to restore target weights.",
    amount: 842_000,
    ccy: "EUR",
    from: "Atlas Securities · Reserve portfolio",
    to: "6 orders · 3 sells / 3 buys",
    entityId: "ch",
    origin: "AI",
    requestedBy: "xCAPITAL Intelligence",
    createdAt: minutesAgo(540),
    expiresAt: daysAhead(3),
    status: "pending",
    riskScore: 22,
    risk: "low",
    rationale: "Equity drift reached +2.1 pts above target after a 6-week rally. The rebalance lowers 1-day VaR by €38K and keeps duration within the 4-year mandate.",
    evidence: ["Drift monitor · 3 sleeves outside band", "Risk: VaR 99% −€38K", "Investment mandate MAN-08"],
    checks: [
      { label: "Investment mandate (IG only, duration ≤ 4y)", result: "pass", detail: "Post-trade duration 2.7y." },
      { label: "Equity ceiling ≤ 25%", result: "pass", detail: "23.5% → 21.4%." },
      { label: "Best-execution venue routing", result: "pass", detail: "Smart routing across 3 venues." },
    ],
    chain: [you(), { role: "Investment Committee", name: "IC quorum (2 of 3)", initials: "IC", status: "waiting" }],
    version: 1,
    payloadHash: digest("ACT-2045v1"),
  },
  {
    id: "ACT-2044",
    kind: "Payment",
    title: "Advisory fee — Kappa Consulting Ltd",
    summary: "One-off invoice for the market-entry study.",
    amount: 96_400,
    ccy: "GBP",
    from: "Thames & Union Bank · Operating (GB29 •••• 6640)",
    to: "Kappa Consulting Ltd · GB82 •••• 9910 (new)",
    entityId: "uk",
    origin: "Human",
    requestedBy: "Tom Hughes · Finance Manager UK",
    createdAt: minutesAgo(95),
    expiresAt: daysAhead(5),
    status: "blocked",
    riskScore: 81,
    risk: "high",
    rationale: "Guard blocked this action: the beneficiary was created 2 hours ago and is inside the 24-hour cooling-off window.",
    evidence: ["Vendor created 2h ago by T. Hughes", "Policy CTL-07 cooling-off 24h"],
    checks: [
      { label: "New beneficiary cooling-off (24h)", result: "fail", detail: "Beneficiary added 2h ago. Eligible in 22h." },
      { label: "Requester ≠ beneficiary creator", result: "fail", detail: "Same user created the vendor and requested the payment." },
      { label: "Sanctions & watchlist screening", result: "pass", detail: "No matches." },
    ],
    chain: [
      { role: "Finance Manager UK", name: "Tom Hughes", initials: "TH", status: "approved", at: minutesAgo(94) },
      { ...you("waiting") },
    ],
    version: 1,
    payloadHash: digest("ACT-2044v1"),
  },
  {
    id: "ACT-2043",
    kind: "Payment",
    title: "Payroll — October · NSD Italia",
    summary: "Net salaries for 212 employees.",
    amount: 2_184_000,
    ccy: "EUR",
    from: "Meridian Trust · Payroll (IT12 •••• 0937)",
    to: "212 beneficiaries (SEPA batch)",
    entityId: "it",
    origin: "Rule",
    requestedBy: "Payroll schedule",
    createdAt: daysAgo(2, 9),
    expiresAt: daysAgo(1),
    status: "executed",
    riskScore: 8,
    risk: "low",
    rationale: "Recurring payroll under standing mandate. Totals match the payroll register within €0.",
    evidence: ["Payroll register Oct", "Variance vs Sep +1.2%"],
    checks: [
      { label: "Matches payroll register", result: "pass", detail: "Exact match." },
      { label: "Funding available", result: "pass", detail: "Pre-funded from main operating." },
    ],
    chain: [
      { role: "HR Payroll", name: "Sara Gallo", initials: "SG", status: "approved", at: daysAgo(2, 10) },
      { ...you("approved", daysAgo(2, 11)) },
    ],
    version: 1,
    payloadHash: digest("ACT-2043v1"),
  },
  {
    id: "ACT-2042",
    kind: "Investment",
    title: "Buy Italy BTP 3.35% 2034",
    summary: "Extend reserve duration to lock in yield.",
    amount: 1_000_000,
    ccy: "EUR",
    from: "Atlas Securities · Reserve portfolio",
    to: "IT0005-BTP-2034",
    entityId: "ch",
    origin: "Human",
    requestedBy: "Priya Nair · Treasury Analyst",
    createdAt: daysAgo(3, 14),
    expiresAt: daysAgo(1),
    status: "rejected",
    riskScore: 57,
    risk: "medium",
    rationale: "Rejected by the CFO: the bond's 8-year duration exceeds the 4-year reserve mandate.",
    evidence: ["Mandate MAN-08 duration ≤ 4y"],
    checks: [{ label: "Investment mandate duration ≤ 4y", result: "fail", detail: "Instrument duration 7.6y." }],
    chain: [
      { ...you("approved", daysAgo(3, 15)) },
      { role: "CFO", name: "Daniel Brandt", initials: "DB", status: "rejected", at: daysAgo(3, 17) },
    ],
    version: 1,
    payloadHash: digest("ACT-2042v1"),
  },
  {
    id: "ACT-2041",
    kind: "Sweep",
    title: "Concentrate US lockbox into operating",
    summary: "Daily zero-balance sweep of the Harborline lockbox.",
    amount: 1_120_000,
    ccy: "USD",
    from: "Harborline Bank · Lockbox collections",
    to: "Harborline Bank · Operating",
    entityId: "us",
    origin: "Rule",
    requestedBy: "Cash pooling rule CP-02",
    createdAt: daysAgo(1, 16),
    expiresAt: daysAgo(0),
    status: "executed",
    riskScore: 4,
    risk: "low",
    rationale: "Internal transfer between accounts the group owns, under a standing cash-pooling rule.",
    evidence: ["Rule CP-02"],
    checks: [{ label: "Internal transfer, same owner", result: "pass", detail: "No external beneficiary." }],
    chain: [{ role: "Mandate", name: "Standing rule CP-02", initials: "CP", status: "approved", at: daysAgo(1, 16) }],
    version: 1,
    payloadHash: digest("ACT-2041v1"),
  },
];

/* ───────────── Guard policies ───────────── */

export const POLICIES: Policy[] = [
  { id: "LIM-01", name: "Daily outbound payment limit", type: "Limit", scope: "Group · all entities", description: "Total external payments released per calendar day.", owner: "CFO", limit: 8_000_000, used: 5_210_000, ccy: "EUR", active: true, version: 6 },
  { id: "CTL-02", name: "Dual approval ≥ €1M", type: "Control", scope: "Payments, FX, investments", description: "Two independent approvers required at or above €1,000,000 equivalent.", owner: "CFO", limit: 1_000_000, used: 0, ccy: "EUR", active: true, version: 3 },
  { id: "LIM-03", name: "Minimum operating buffer", type: "Limit", scope: "Group liquidity", description: "Forecast P10 closing balance must remain above the buffer for 13 weeks.", owner: "Group Treasurer", limit: 18_000_000, used: 24_090_000, ccy: "EUR", active: true, version: 4 },
  { id: "MAN-04", name: "AI sweep mandate", type: "Mandate", scope: "Excess cash → approved MMFs", description: "Intelligence may prepare sweeps into approved money-market funds up to €5M per day; one approver releases.", owner: "Group Treasurer", limit: 5_000_000, used: 0, ccy: "EUR", active: true, version: 2 },
  { id: "CTL-05", name: "FX hedge ratio band", type: "Control", scope: "Forecast USD & GBP receipts", description: "Keep hedged share of 6-month forecast foreign receipts between 60% and 80%.", owner: "Treasury Committee", limit: 80, used: 52, unit: "%", ccy: "EUR", active: true, version: 5 },
  { id: "LIM-06", name: "Bank counterparty concentration", type: "Limit", scope: "Per banking group", description: "No single bank holds more than 35% of group liquidity.", owner: "Treasury Committee", limit: 35, used: 28.3, unit: "%", ccy: "EUR", active: true, version: 2 },
  { id: "CTL-07", name: "New beneficiary cooling-off", type: "Control", scope: "External payments", description: "Payments to beneficiaries created in the last 24h are blocked; creator cannot request payment.", owner: "Head of Controls", limit: 24, used: 0, unit: "h", ccy: "EUR", active: true, version: 1 },
  { id: "MAN-08", name: "Reserve investment mandate", type: "Mandate", scope: "Strategic reserve portfolio", description: "Investment-grade only, portfolio duration ≤ 4 years, equities ≤ 25%.", owner: "Investment Committee", limit: 25, used: 23.5, unit: "% equities", ccy: "EUR", active: true, version: 7 },
  { id: "CTL-09", name: "Sanctions & PEP screening", type: "Control", scope: "All counterparties", description: "Screen every new and changed counterparty against 4 consolidated lists before release.", owner: "Compliance", limit: 0, used: 0, ccy: "EUR", active: true, version: 12 },
];

/* ───────────── audit chain ───────────── */

export const AUDIT_EVENTS: AuditEvent[] = (() => {
  const seed: Array<Omit<AuditEvent, "seq" | "hash" | "prevHash">> = [
    { at: daysAgo(2, 10), actor: "Sara Gallo", actorType: "human", action: "action.approved", target: "ACT-2043", detail: "Step 1/2 approved" },
    { at: daysAgo(2, 11), actor: ORG.persona.name, actorType: "human", action: "action.approved", target: "ACT-2043", detail: "Step 2/2 approved · payload sha matched" },
    { at: daysAgo(2, 11.1), actor: "Execution", actorType: "system", action: "execution.submitted", target: "ACT-2043", detail: "SEPA batch submitted · idem-key a91f…" },
    { at: daysAgo(1, 9), actor: "Execution", actorType: "system", action: "execution.settled", target: "ACT-2043", detail: "Bank confirmed 212/212 credits" },
    { at: daysAgo(1, 9.2), actor: "Ledger", actorType: "system", action: "journal.posted", target: "JE-91004", detail: "Payroll settlement · balanced 2 lines" },
    { at: daysAgo(3, 15), actor: ORG.persona.name, actorType: "human", action: "action.approved", target: "ACT-2042", detail: "Step 1/2 approved" },
    { at: daysAgo(3, 17), actor: "Daniel Brandt", actorType: "human", action: "action.rejected", target: "ACT-2042", detail: "Duration exceeds mandate MAN-08" },
    { at: daysAgo(1, 16), actor: "Rule CP-02", actorType: "system", action: "action.executed", target: "ACT-2041", detail: "Lockbox sweep $1.12M" },
    { at: daysAgo(1, 18), actor: "Connect", actorType: "system", action: "sync.completed", target: "Helvetia Private Bank", detail: "MT940 statement imported · 31 rows" },
    { at: daysAgo(0, 7), actor: "Connect", actorType: "system", action: "sync.completed", target: "Alpina Commercial Bank", detail: "PSD2 AIS · 48 new transactions" },
    { at: daysAgo(0, 7.1), actor: "Reconciliation", actorType: "system", action: "recon.matched", target: "Batch R-1182", detail: "41 auto-matched · 3 exceptions raised" },
    { at: iso(Date.now() - 312 * 60_000), actor: "xCAPITAL Intelligence", actorType: "ai", action: "action.proposed", target: "ACT-2046", detail: "FX forward proposal · model fx-hedge v3.2" },
    { at: iso(Date.now() - 290 * 60_000), actor: "Priya Nair", actorType: "human", action: "action.approved", target: "ACT-2046", detail: "Step 1/2 approved" },
    { at: iso(Date.now() - 540 * 60_000), actor: "xCAPITAL Intelligence", actorType: "ai", action: "action.proposed", target: "ACT-2045", detail: "Rebalance proposal · drift monitor" },
    { at: iso(Date.now() - 124 * 60_000), actor: "Giulia Conti", actorType: "human", action: "action.created", target: "ACT-2047", detail: "Payment run v1 · 14 bills" },
    { at: iso(Date.now() - 122 * 60_000), actor: "Giulia Conti", actorType: "human", action: "action.amended", target: "ACT-2047", detail: "v2 · removed 1 disputed bill · prior approvals invalidated" },
    { at: iso(Date.now() - 120 * 60_000), actor: "Giulia Conti", actorType: "human", action: "action.approved", target: "ACT-2047", detail: "Step 1/3 approved on v2" },
    { at: iso(Date.now() - 96 * 60_000), actor: "Tom Hughes", actorType: "human", action: "beneficiary.created", target: "Kappa Consulting Ltd", detail: "New vendor GB82 •••• 9910" },
    { at: iso(Date.now() - 95 * 60_000), actor: "Guard", actorType: "system", action: "guard.blocked", target: "ACT-2044", detail: "CTL-07 cooling-off · requester created beneficiary" },
    { at: iso(Date.now() - 38 * 60_000), actor: "xCAPITAL Intelligence", actorType: "ai", action: "action.proposed", target: "ACT-2048", detail: "Sweep proposal · model liquidity-opt v2.4" },
    { at: iso(Date.now() - 37 * 60_000), actor: "Guard", actorType: "system", action: "guard.evaluated", target: "ACT-2048", detail: "4/4 checks passed · policy set v41" },
  ];
  seed.sort((a, b) => a.at.localeCompare(b.at));
  let prev = "0".repeat(64);
  return seed.map((e, i) => {
    const seq = 18_420 + i;
    const hash = digest(`${seq}|${e.at}|${e.actor}|${e.action}|${e.target}|${prev}`);
    const ev = { ...e, seq, hash, prevHash: prev };
    prev = hash;
    return ev;
  });
})();

/* ───────────── connectors ───────────── */

export const CONNECTORS: Connector[] = [
  { id: "c1", name: "Alpina Commercial Bank", kind: "Bank", protocol: "PSD2 AIS / PIS", status: "healthy", lastSync: minutesAgo(3), latencyMs: 412, records24h: 1_284, coverage: 100, region: "EU", scopes: ["balances", "transactions", "payments:initiate"] },
  { id: "c2", name: "Nordbank Clearing", kind: "Bank", protocol: "EBICS 3.0", status: "healthy", lastSync: minutesAgo(6), latencyMs: 980, records24h: 642, coverage: 100, region: "EU", scopes: ["statements", "payments:initiate"] },
  { id: "c3", name: "Harborline Bank", kind: "Bank", protocol: "REST API + webhooks", status: "healthy", lastSync: minutesAgo(1), latencyMs: 188, records24h: 2_031, coverage: 100, region: "US", scopes: ["balances", "transactions", "ach", "wires"] },
  { id: "c4", name: "Thames & Union Bank", kind: "Bank", protocol: "Open Banking UK", status: "degraded", lastSync: minutesAgo(118), latencyMs: 4_820, records24h: 96, coverage: 82, region: "UK", scopes: ["balances", "transactions"] },
  { id: "c5", name: "Helvetia Private Bank", kind: "Bank", protocol: "SWIFT MT940 (SFTP)", status: "healthy", lastSync: daysAgo(1, 18), latencyMs: 0, records24h: 31, coverage: 100, region: "CH", scopes: ["statements"] },
  { id: "c6", name: "Meridian Trust", kind: "Bank", protocol: "PSD2 AIS", status: "healthy", lastSync: minutesAgo(0.4), latencyMs: 620, records24h: 388, coverage: 100, region: "EU", scopes: ["balances", "transactions"] },
  { id: "c7", name: "Ledgerline ERP", kind: "ERP", protocol: "OData v4", status: "healthy", lastSync: minutesAgo(11), latencyMs: 730, records24h: 5_412, coverage: 97, region: "EU", scopes: ["ar", "ap", "gl:read", "vendors"] },
  { id: "c8", name: "Paystream Payroll", kind: "Payroll", protocol: "SFTP · CSV", status: "healthy", lastSync: daysAgo(2, 8), latencyMs: 0, records24h: 0, coverage: 100, region: "EU", scopes: ["register"] },
  { id: "c9", name: "Atlas Securities", kind: "Broker", protocol: "FIX 4.4", status: "healthy", lastSync: minutesAgo(0.2), latencyMs: 24, records24h: 214, coverage: 100, region: "EU", scopes: ["positions", "orders", "executions"] },
  { id: "c10", name: "Quantfeed Market Data", kind: "Market data", protocol: "WebSocket", status: "healthy", lastSync: minutesAgo(0.05), latencyMs: 9, records24h: 1_920_400, coverage: 100, region: "Global", scopes: ["quotes", "fx", "rates"] },
  { id: "c11", name: "Spendwise Corporate Cards", kind: "Cards", protocol: "REST API", status: "offline", lastSync: daysAgo(4, 9), latencyMs: 0, records24h: 0, coverage: 0, region: "EU", scopes: ["transactions"] },
];

/* ───────────── capital: reserve portfolio ───────────── */

const HOLDING_SEED: Array<Omit<Holding, "value" | "weight" | "qty" | "avgCost" | "dayPct"> & { valueEur: number; costDelta: number }> = [
  { symbol: "LMN-EUR", name: "Lumen EUR Liquidity Fund", assetClass: "Money market", sector: "Cash equivalents", region: "Europe", price: 102.84, target: 26, yieldPct: 3.41, ccy: "EUR", valueEur: 5_720_000, costDelta: -0.02 },
  { symbol: "UST-0-1", name: "US Treasury 0–1Y", assetClass: "Fixed income", sector: "Government", region: "US", price: 100.42, target: 14, yieldPct: 4.06, ccy: "USD", valueEur: 3_060_000, costDelta: 0.004 },
  { symbol: "BTP-29", name: "Italy BTP 3.35% 2029", assetClass: "Fixed income", sector: "Government", region: "Europe", price: 101.18, target: 10, yieldPct: 3.02, ccy: "EUR", valueEur: 2_240_000, costDelta: -0.011 },
  { symbol: "BUND-31", name: "Germany Bund 2.2% 2031", assetClass: "Fixed income", sector: "Government", region: "Europe", price: 98.71, target: 8, yieldPct: 2.48, ccy: "EUR", valueEur: 1_710_000, costDelta: 0.008 },
  { symbol: "EUIG-15", name: "EUR Corporate IG 1–5Y", assetClass: "Fixed income", sector: "Credit", region: "Europe", price: 52.36, target: 13, yieldPct: 3.27, ccy: "EUR", valueEur: 2_390_000, costDelta: -0.018 },
  { symbol: "MSFT", name: "Microsoft", assetClass: "Equity", sector: "Technology", region: "US", price: 431.2, target: 5, yieldPct: 0.7, ccy: "USD", valueEur: 1_290_000, costDelta: -0.16 },
  { symbol: "NVDA", name: "NVIDIA", assetClass: "Equity", sector: "Semiconductors", region: "US", price: 182.4, target: 4, yieldPct: 0.03, ccy: "USD", valueEur: 1_160_000, costDelta: -0.34 },
  { symbol: "ASML", name: "ASML Holding", assetClass: "Equity", sector: "Semiconductors", region: "Europe", price: 742.1, target: 4, yieldPct: 0.9, ccy: "EUR", valueEur: 940_000, costDelta: -0.08 },
  { symbol: "SAP", name: "SAP", assetClass: "Equity", sector: "Software", region: "Europe", price: 238.6, target: 4, yieldPct: 1.0, ccy: "EUR", valueEur: 1_010_000, costDelta: -0.21 },
  { symbol: "AAPL", name: "Apple", assetClass: "Equity", sector: "Technology", region: "US", price: 227.3, target: 4, yieldPct: 0.45, ccy: "USD", valueEur: 830_000, costDelta: -0.07 },
  { symbol: "XAU", name: "Physical gold ETC", assetClass: "Commodity", sector: "Precious metals", region: "Global", price: 248.9, target: 4, yieldPct: 0, ccy: "USD", valueEur: 980_000, costDelta: -0.19 },
  { symbol: "CASH", name: "Settlement cash", assetClass: "Cash", sector: "Cash", region: "Europe", price: 1, target: 4, yieldPct: 2.1, ccy: "EUR", valueEur: 1_050_000, costDelta: 0 },
];

export const HOLDINGS: Holding[] = (() => {
  const r = rng(909);
  const total = HOLDING_SEED.reduce((s, h) => s + h.valueEur, 0);
  return HOLDING_SEED.map(({ valueEur, costDelta, ...h }) => {
    const local = valueEur * FX[h.ccy];
    const qty = h.symbol === "CASH" ? local : Math.round(local / h.price);
    const dayPct = h.assetClass === "Equity" ? r.normal() * 1.3 : h.assetClass === "Commodity" ? r.normal() * 0.8 : h.assetClass === "Cash" ? 0 : r.normal() * 0.08;
    return {
      ...h,
      qty,
      avgCost: h.price * (1 + costDelta),
      value: valueEur,
      weight: (valueEur / total) * 100,
      dayPct,
    };
  });
})();
export const PORTFOLIO_VALUE = HOLDINGS.reduce((s, h) => s + h.value, 0);

/** 1-year daily index (start = 100) for the reserve portfolio vs its policy benchmark. */
export const PERFORMANCE = (() => {
  const r = rng(4242);
  const n = 252;
  const p: number[] = [100];
  const b: number[] = [100];
  for (let i = 1; i < n; i++) {
    const m = r.normal();
    p.push(p[i - 1] * (1 + 0.00027 + m * 0.0029 + r.normal() * 0.0009));
    b.push(b[i - 1] * (1 + 0.00021 + m * 0.0027 + r.normal() * 0.0006));
  }
  const dates = Array.from({ length: n }, (_, i) => iso(TODAY - (n - 1 - i) * 1.45 * DAY));
  return { dates, portfolio: p, benchmark: b };
})();

/* ───────────── markets (simulated) ───────────── */

const INSTRUMENT_SEED: Array<Omit<Instrument, "candles"> & { vol: number }> = [
  { symbol: "SPX", name: "S&P 500", venue: "Index", kind: "Index", ccy: "USD", decimals: 2, base: 6512.4, vol: 0.009 },
  { symbol: "SX5E", name: "Euro Stoxx 50", venue: "Index", kind: "Index", ccy: "EUR", decimals: 2, base: 5410.8, vol: 0.01 },
  { symbol: "FTSEMIB", name: "FTSE MIB", venue: "Index", kind: "Index", ccy: "EUR", decimals: 0, base: 42310, vol: 0.011 },
  { symbol: "EURUSD", name: "Euro / US Dollar", venue: "FX", kind: "FX", ccy: "USD", decimals: 4, base: 1.0874, vol: 0.0035 },
  { symbol: "EURGBP", name: "Euro / Pound", venue: "FX", kind: "FX", ccy: "GBP", decimals: 4, base: 0.8462, vol: 0.003 },
  { symbol: "EURCHF", name: "Euro / Swiss Franc", venue: "FX", kind: "FX", ccy: "CHF", decimals: 4, base: 0.9412, vol: 0.0028 },
  { symbol: "DE10Y", name: "Bund 10Y yield", venue: "Rates", kind: "Rates", ccy: "%", decimals: 3, base: 2.584, vol: 0.012 },
  { symbol: "US10Y", name: "Treasury 10Y yield", venue: "Rates", kind: "Rates", ccy: "%", decimals: 3, base: 4.118, vol: 0.011 },
  { symbol: "MSFT", name: "Microsoft", venue: "NASDAQ", kind: "Equity", ccy: "USD", decimals: 2, base: 431.2, vol: 0.014 },
  { symbol: "NVDA", name: "NVIDIA", venue: "NASDAQ", kind: "Equity", ccy: "USD", decimals: 2, base: 182.4, vol: 0.026 },
  { symbol: "AAPL", name: "Apple", venue: "NASDAQ", kind: "Equity", ccy: "USD", decimals: 2, base: 227.3, vol: 0.015 },
  { symbol: "ASML", name: "ASML Holding", venue: "Euronext", kind: "Equity", ccy: "EUR", decimals: 2, base: 742.1, vol: 0.019 },
  { symbol: "SAP", name: "SAP", venue: "XETRA", kind: "Equity", ccy: "EUR", decimals: 2, base: 238.6, vol: 0.015 },
  { symbol: "XAU", name: "Gold spot", venue: "Commodity", kind: "Commodity", ccy: "USD", decimals: 2, base: 2648.3, vol: 0.009 },
  { symbol: "BRENT", name: "Brent crude", venue: "Commodity", kind: "Commodity", ccy: "USD", decimals: 2, base: 74.31, vol: 0.017 },
  { symbol: "BTC", name: "Bitcoin", venue: "Crypto", kind: "Crypto", ccy: "USD", decimals: 0, base: 98420, vol: 0.028 },
];

export const INSTRUMENTS: Instrument[] = INSTRUMENT_SEED.map(({ vol, ...ins }, k) => {
  const r = rng(700 + k * 31);
  const n = 140;
  const closes: number[] = new Array(n);
  closes[n - 1] = ins.base;
  for (let i = n - 2; i >= 0; i--) closes[i] = closes[i + 1] / (1 + r.normal() * vol + vol * 0.06);
  const candles: Candle[] = closes.map((c, i) => {
    const o = i === 0 ? c * (1 + r.normal() * vol * 0.4) : closes[i - 1] * (1 + r.normal() * vol * 0.15);
    const h = Math.max(o, c) * (1 + Math.abs(r.normal()) * vol * 0.5);
    const l = Math.min(o, c) * (1 - Math.abs(r.normal()) * vol * 0.5);
    return { t: iso(TODAY - (n - 1 - i) * DAY), o, h, l, c, v: Math.round(r.range(0.5, 1.6) * 1e6 * (1 + Math.abs(c - o) / (c * vol))) };
  });
  return { ...ins, candles };
});
export const instrumentBySymbol = (s: string) => INSTRUMENTS.find((i) => i.symbol === s);

/* ───────────── risk ───────────── */

export const RISK = {
  var95: 182_400,
  var99: 291_800,
  es975: 336_200,
  volAnn: 4.8,
  beta: 0.21,
  sharpe: 1.42,
  maxDrawdown: -3.1,
  duration: 2.9,
  liquidityDays: 1.6,
  factors: [
    { name: "Rates duration", exposure: 0.62 },
    { name: "Credit spread", exposure: 0.34 },
    { name: "Equity beta", exposure: 0.21 },
    { name: "USD", exposure: 0.27 },
    { name: "Momentum", exposure: 0.08 },
    { name: "Value", exposure: -0.05 },
    { name: "Size", exposure: -0.11 },
    { name: "Commodities", exposure: 0.04 },
  ],
  correlation: {
    labels: ["Equity", "Gov bonds", "IG credit", "Money mkt", "Gold", "USD"],
    matrix: [
      [1, -0.28, 0.41, 0.02, 0.12, -0.18],
      [-0.28, 1, 0.66, 0.31, 0.24, 0.09],
      [0.41, 0.66, 1, 0.22, 0.15, -0.04],
      [0.02, 0.31, 0.22, 1, 0.01, 0.03],
      [0.12, 0.24, 0.15, 0.01, 1, -0.37],
      [-0.18, 0.09, -0.04, 0.03, -0.37, 1],
    ],
  },
  // 60 daily VaR(95%) observations ending at today's figure.
  varHistory: (() => {
    const raw = Array.from({ length: 60 }, (_, i) => 160_000 + 30_000 * Math.sin(i / 7) + i * 420 + ((i * 7919) % 13) * 1_200);
    const k = 182_400 / raw[59];
    return raw.map((v) => v * k);
  })(),
};

export const STRESS: StressScenario[] = [
  { name: "Rates +200 bp", description: "Parallel shift up in EUR and USD curves", impactPct: -5.6, impact: -1_250_000, liquidityImpact: 0 },
  { name: "2008 credit crunch", description: "IG spreads +250 bp, equities −40%", impactPct: -9.8, impact: -2_190_000, liquidityImpact: -4_200_000 },
  { name: "2020 liquidity shock", description: "Equities −30%, MMF gating 1 week", impactPct: -6.4, impact: -1_430_000, liquidityImpact: -6_100_000 },
  { name: "EUR/USD −10%", description: "USD strengthens 10% against EUR", impactPct: 1.6, impact: 360_000, liquidityImpact: 980_000 },
  { name: "Largest customer default", description: "Vela Mobility receivables written off", impactPct: 0, impact: 0, liquidityImpact: -3_420_000 },
  { name: "Italian sovereign stress", description: "BTP–Bund spread +180 bp", impactPct: -1.9, impact: -420_000, liquidityImpact: 0 },
];

/* ───────────── intelligence ───────────── */

export const INSIGHTS: Insight[] = [
  { id: "in1", severity: "opportunity", title: "€5.2M idle in NSD Germany operating", body: "Balance has stayed above the 13-week peak requirement for 19 days. A sweep to the approved liquidity fund earns about €185K a year.", metric: "+€185K / yr", route: "/app/approvals", confidence: 0.93 },
  { id: "in2", severity: "warning", title: "Vela Mobility paying 11 days later", body: "Average days-to-pay rose from 34 to 45 over 3 invoices. €2.9M is due in the next 4 weeks.", metric: "€2.9M at risk", route: "/app/forecast", confidence: 0.81 },
  { id: "in3", severity: "warning", title: "USD hedge ratio below policy band", body: "52% of 6-month USD receipts are hedged against a 60–80% band. A proposal is waiting for approval.", metric: "52% vs 60–80%", route: "/app/approvals", confidence: 0.97 },
  { id: "in4", severity: "critical", title: "Thames & Union feed delayed 2h", body: "UK balances are stale. The forecast is using the last confirmed balance for NSD UK.", metric: "118 min stale", route: "/app/connect", confidence: 1 },
  { id: "in5", severity: "info", title: "Reserve portfolio drifted +2.1 pts to equity", body: "After a 6-week rally the equity sleeve sits at 23.5% against a 21.4% target.", metric: "3 sleeves outside band", route: "/app/portfolio", confidence: 0.99 },
];

export const COPILOT_PROMPTS: CopilotAnswer[] = [
  {
    prompt: "Why did group liquidity move last week?",
    answer: [
      "Group liquidity rose by **€2.7M (+5.8%)** week-over-week to **€49.4M**.",
      "Three drivers explain most of the move: customer collections in NSD Italia (+€3.4M, led by Orion Health Systems and Atlas Retail Group), the Q3 VAT payment in NSD Germany (−€2.1M), and USD appreciation that revalued NSD US balances (+€0.4M). Money-market accruals and smaller receipts add the remaining €1.0M.",
      "Nothing unusual was detected. All 3 reconciliation exceptions are under €45K.",
    ],
    table: {
      head: ["Driver", "Entity", "Impact"],
      rows: [
        ["Customer collections", "NSD Italia", "+€3.4M"],
        ["Q3 VAT settlement", "NSD Germany", "−€2.1M"],
        ["FX revaluation (USD)", "NSD US", "+€0.4M"],
        ["Fund accruals & other, net", "Group", "+€1.0M"],
      ],
    },
    sources: [
      { label: "Liquidity · 7-day bridge", route: "/app/liquidity" },
      { label: "Ledger · 214 postings", route: "/app/ledger" },
    ],
  },
  {
    prompt: "Can we cover payroll if Vela Mobility pays 30 days late?",
    answer: [
      "**Yes, with headroom.** If Vela Mobility's €2.9M slips 30 days, the week-8 low point falls from €24.1M to €21.2M. That is still €3.2M above the €18M operating buffer, at P10 confidence.",
      "Payroll in weeks 4, 8 and 13 (€3.24M each) stays fully funded. The tightest week is week 8, where payroll lands one week after the €1.48M term-loan instalment.",
      "Optional mitigation: defer €1.15M of discretionary capex in week 6. This restores €1.1M of buffer.",
    ],
    sources: [
      { label: "Forecast · scenario 'Vela +30d'", route: "/app/forecast" },
      { label: "Obligations · AR-3100…3124", route: "/app/forecast" },
    ],
    proposal: "Open this scenario in Forecast",
  },
  {
    prompt: "Where is idle cash earning below 1%?",
    answer: [
      "**€11.6M** across 3 accounts earns below 1%. That is roughly **€318K a year** of yield left on the table, compared with the approved liquidity fund at 3.41%.",
      "The largest pocket is NSD Germany operating, at €5.2M above its 13-week need. A sweep proposal (ACT-2048) is already prepared under the AI sweep mandate and only needs your approval.",
    ],
    table: {
      head: ["Account", "Excess", "Yield now", "Opportunity"],
      rows: [
        ["NSD Germany · Operating", "€5.2M", "0.25%", "€164K / yr"],
        ["NSD Italia · Collections", "€3.1M", "0.40%", "€93K / yr"],
        ["NSD US · Operating", "$3.6M", "0.85%", "€61K / yr"],
      ],
    },
    sources: [
      { label: "Liquidity · yield analysis", route: "/app/liquidity" },
      { label: "Approvals · ACT-2048", route: "/app/approvals" },
    ],
    proposal: "Review sweep ACT-2048",
  },
  {
    prompt: "Summarize our FX exposure for the next quarter",
    answer: [
      "Net forecast foreign-currency receipts over the next 13 weeks are **$18.4M** and **£3.1M**.",
      "USD is **52% hedged**, below the 60–80% policy band. GBP is 71% hedged and inside its band. A 5% EUR appreciation would reduce EUR-equivalent receipts by about €0.42M after existing hedges.",
      "The pending forward (ACT-2046, USD 6.0M at 1.0912) would bring USD coverage to 74%.",
    ],
    table: {
      head: ["Currency", "Exposure", "Hedged", "Band", "Status"],
      rows: [
        ["USD", "$18.4M", "52%", "60–80%", "Below band"],
        ["GBP", "£3.1M", "71%", "60–80%", "Within"],
        ["CHF", "CHF 0.4M", "0%", "n/a", "Natural hedge"],
      ],
    },
    sources: [
      { label: "Guard · CTL-05 hedge band", route: "/app/guard" },
      { label: "Approvals · ACT-2046", route: "/app/approvals" },
    ],
  },
  {
    prompt: "What would a 10% revenue shock do to our runway?",
    answer: [
      "A sustained **−10% collections shock**, phased in over three weeks, lowers week-13 closing operating cash by **€4.4M**, to €24.1M.",
      "The P10 path stays above the €18M buffer in every week, so no funding action is needed. Minimum headroom is €2.5M, in week 13.",
      "Combined with a 30-day slip from Vela Mobility, headroom would narrow to about €0.6M around week 8. Deferring week-6 capex restores roughly €1.1M.",
    ],
    sources: [{ label: "Forecast · stress grid", route: "/app/forecast" }],
    proposal: "Run in Forecast",
  },
];
