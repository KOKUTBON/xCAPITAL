export interface NavItem {
  path: string;
  label: string;
  icon: string;
  group: string;
  keywords: string;
}

export const NAV_GROUPS = [
  { code: "TREASURY", label: "Treasury" },
  { code: "CONTROL", label: "Control" },
  { code: "CAPITAL", label: "Capital" },
  { code: "INTELLIGENCE", label: "Intelligence" },
  { code: "PLATFORM", label: "Platform" },
] as const;

export const NAV: NavItem[] = [
  { path: "/app/overview", label: "Command Center", icon: "overview", group: "TREASURY", keywords: "dashboard home overview kpi" },
  { path: "/app/liquidity", label: "Liquidity", icon: "liquidity", group: "TREASURY", keywords: "cash accounts banks balances currency" },
  { path: "/app/forecast", label: "Cash Forecast", icon: "forecast", group: "TREASURY", keywords: "13-week scenario projection runway obligations" },
  { path: "/app/ledger", label: "Ledger & Recon", icon: "ledger", group: "TREASURY", keywords: "journal reconciliation transactions exceptions" },
  { path: "/app/approvals", label: "Approvals", icon: "actions", group: "CONTROL", keywords: "actions payments approve maker checker inbox" },
  { path: "/app/guard", label: "Guard", icon: "guard", group: "CONTROL", keywords: "policy limits mandates kill switch controls" },
  { path: "/app/audit", label: "Audit Trail", icon: "audit", group: "CONTROL", keywords: "evidence hash chain log events" },
  { path: "/app/portfolio", label: "Portfolio", icon: "portfolio", group: "CAPITAL", keywords: "holdings allocation performance reserve investments" },
  { path: "/app/markets", label: "Markets", icon: "markets", group: "CAPITAL", keywords: "quotes watchlist fx rates equities chart" },
  { path: "/app/risk", label: "Risk", icon: "risk", group: "CAPITAL", keywords: "var stress factors correlation exposure" },
  { path: "/app/copilot", label: "Copilot", icon: "copilot", group: "INTELLIGENCE", keywords: "ai assistant ask chat explain" },
  { path: "/app/connect", label: "Connect", icon: "connect", group: "PLATFORM", keywords: "integrations banks erp connectors api sync" },
];
