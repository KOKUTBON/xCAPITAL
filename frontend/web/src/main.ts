import { bootstrapApplication } from "@angular/platform-browser";
import { provideRouter, Routes, withHashLocation, withInMemoryScrolling } from "@angular/router";
import { AppComponent } from "./app/app.component";
import { ShellComponent } from "./app/layout/shell.component";

/**
 * Hash routing keeps deep links working on GitHub Pages (static hosting with
 * no server-side rewrites). Pages are lazy-loaded so the landing page stays light.
 */
const routes: Routes = [
  { path: "", loadComponent: () => import("./app/pages/landing/landing.page").then((m) => m.LandingPage), title: "xCAPITAL — Financial operating system for modern companies" },
  {
    path: "app",
    component: ShellComponent,
    children: [
      { path: "", pathMatch: "full", redirectTo: "overview" },
      { path: "overview", loadComponent: () => import("./app/pages/overview/overview.page").then((m) => m.OverviewPage), title: "Command Center · xCAPITAL" },
      { path: "liquidity", loadComponent: () => import("./app/pages/liquidity/liquidity.page").then((m) => m.LiquidityPage), title: "Liquidity · xCAPITAL" },
      { path: "forecast", loadComponent: () => import("./app/pages/forecast/forecast.page").then((m) => m.ForecastPage), title: "Cash Forecast · xCAPITAL" },
      { path: "ledger", loadComponent: () => import("./app/pages/ledger/ledger.page").then((m) => m.LedgerPage), title: "Ledger & Reconciliation · xCAPITAL" },
      { path: "approvals", loadComponent: () => import("./app/pages/approvals/approvals.page").then((m) => m.ApprovalsPage), title: "Approvals · xCAPITAL" },
      { path: "guard", loadComponent: () => import("./app/pages/guard/guard.page").then((m) => m.GuardPage), title: "Guard · xCAPITAL" },
      { path: "audit", loadComponent: () => import("./app/pages/audit/audit.page").then((m) => m.AuditPage), title: "Audit Trail · xCAPITAL" },
      { path: "portfolio", loadComponent: () => import("./app/pages/portfolio/portfolio.page").then((m) => m.PortfolioPage), title: "Portfolio · xCAPITAL" },
      { path: "markets", loadComponent: () => import("./app/pages/markets/markets.page").then((m) => m.MarketsPage), title: "Markets · xCAPITAL" },
      { path: "risk", loadComponent: () => import("./app/pages/risk/risk.page").then((m) => m.RiskPage), title: "Risk · xCAPITAL" },
      { path: "copilot", loadComponent: () => import("./app/pages/copilot/copilot.page").then((m) => m.CopilotPage), title: "Copilot · xCAPITAL" },
      { path: "connect", loadComponent: () => import("./app/pages/connect/connect.page").then((m) => m.ConnectPage), title: "Connect · xCAPITAL" },
    ],
  },
  { path: "**", redirectTo: "" },
];

bootstrapApplication(AppComponent, {
  providers: [provideRouter(routes, withHashLocation(), withInMemoryScrolling({ scrollPositionRestoration: "top" }))],
}).catch((err) => console.error(err));
