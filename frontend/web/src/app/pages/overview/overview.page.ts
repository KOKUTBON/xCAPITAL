import { Component, computed, inject, signal } from "@angular/core";
import { RouterLink } from "@angular/router";
import { DemoStore } from "../../core/store";
import {
  DEFAULT_SCENARIO,
  ENTITIES,
  GROUP_LIQUIDITY_HISTORY,
  HOLDINGS,
  INSIGHTS,
  PERFORMANCE,
  PORTFOLIO_VALUE,
  TODAY,
  runForecast,
  toEur,
} from "../../core/demo-data";
import { FORMAT_PIPES, compact, pct } from "../../core/format";
import { CHARTS, DonutSegment } from "../../shared/charts";
import { UI } from "../../shared/ui";

const DAY = 86_400_000;
const CCY_COLORS: Record<string, string> = { EUR: "var(--x-s1)", USD: "var(--x-s2)", GBP: "var(--x-s3)", CHF: "var(--x-s4)" };

@Component({
  selector: "x-overview",
  standalone: true,
  imports: [RouterLink, ...UI, ...CHARTS, ...FORMAT_PIPES],
  template: `<div class="page">
    <x-page-head [eyebrow]="'Command Center · ' + dateLabel" [title]="greeting + ', ' + firstName" description="Group liquidity, obligations, approvals and capital in one place. Everything below is live simulated data for Northstar Dynamics Group.">
      <a class="btn btn-secondary" routerLink="/app/copilot"><x-icon name="copilot" [size]="16" />Ask Copilot</a>
      <button class="btn-glow" (click)="boardPack()"><x-icon name="download" [size]="16" />Board pack</button>
    </x-page-head>

    <section class="grid g-4">
      <div class="card kpi glow rise">
        <div class="label"><x-icon name="liquidity" [size]="15" />Group liquidity</div>
        <div class="value">{{ store.totalLiquidity() | compact: "EUR" : 2 }}</div>
        <div class="delta" [class.pos]="change7d() >= 0" [class.neg]="change7d() < 0">{{ fmtPct(change7d()) }} <span class="faint">vs 7 days</span></div>
        <x-spark [data]="liq30()" color="var(--x-accent)" [height]="34" style="margin-top: 10px" />
      </div>
      <div class="card kpi rise" style="animation-delay: 40ms">
        <div class="label"><x-icon name="forecast" [size]="15" />13-week low point (P10)</div>
        <div class="value">{{ lowPoint().value | compact: "EUR" : 1 }}</div>
        <div class="delta" [class.pos]="lowPoint().headroom >= 0" [class.neg]="lowPoint().headroom < 0">
          {{ lowPoint().headroom >= 0 ? "+" : "" }}{{ lowPoint().headroom | compact: "EUR" : 1 }} <span class="faint">above €18M buffer · {{ lowPoint().week }}</span>
        </div>
        <div class="meter grad" style="margin-top: 16px"><span [style.width.%]="lowPoint().fill"></span></div>
      </div>
      <a class="card kpi rise link-card" routerLink="/app/approvals" style="animation-delay: 80ms">
        <div class="label"><x-icon name="actions" [size]="15" />Awaiting your approval</div>
        <div class="value">{{ store.awaitingYou().length }} <span class="unit">actions</span></div>
        <div class="delta faint">{{ awaitingValue() | compact: "EUR" : 1 }} total · {{ blocked() }} blocked by Guard</div>
        <div class="avatars">
          @for (a of store.awaitingYou().slice(0, 4); track a.id) {
            <span class="badge" [class.ai]="a.origin === 'AI'">{{ a.kind }}</span>
          }
        </div>
      </a>
      <div class="card kpi rise" style="animation-delay: 120ms">
        <div class="label"><x-icon name="ledger" [size]="15" />Reconciled · last 30 days</div>
        <div class="value">{{ store.reconRate().toFixed(1) }}%</div>
        <div class="delta" [class.warn]="store.exceptions().length > 0">{{ store.exceptions().length }} open exceptions</div>
        <div class="meter pos" style="margin-top: 16px"><span [style.width.%]="store.reconRate()"></span></div>
      </div>
    </section>

    <section class="grid g-12">
      <div class="card span-8">
        <div class="card-head">
          <div>
            <h2>Liquidity trend</h2>
            <div class="sub">Group cash across {{ store.accounts().length }} accounts, converted to EUR</div>
          </div>
          <div class="seg" role="tablist" aria-label="Range">
            @for (r of ranges; track r) {
              <button [class.on]="range() === r" (click)="range.set(r)" role="tab" [attr.aria-selected]="range() === r">{{ r }}D</button>
            }
          </div>
        </div>
        <x-line-chart [series]="trendSeries()" [labels]="trendLabels()" [height]="260" [threshold]="{ value: 18_000_000, label: 'Operating buffer €18M' }" ariaLabel="Group liquidity trend" />
      </div>

      <div class="card span-4">
        <div class="card-head">
          <div>
            <h2>Cash by currency</h2>
            <div class="sub">EUR equivalent · FX simulated</div>
          </div>
          <a class="btn btn-ghost btn-sm" routerLink="/app/liquidity">Details <x-icon name="arrow" [size]="14" /></a>
        </div>
        <div class="donut-row">
          <x-donut [segments]="ccyMix()" [size]="168" [thickness]="18">
            <span class="xs faint">Total</span>
            <strong class="donut-total">{{ store.totalLiquidity() | compact: "EUR" : 1 }}</strong>
          </x-donut>
          <div class="ccy-list">
            @for (c of ccyMix(); track c.label) {
              <div class="ccy">
                <i [style.background]="c.color"></i>
                <span class="grow">{{ c.label }}</span>
                <span class="num">{{ c.value | compact: "EUR" : 1 }}</span>
              </div>
            }
          </div>
        </div>
      </div>
    </section>

    <section class="grid g-12">
      <div class="card span-7">
        <div class="card-head">
          <div>
            <h2>Approvals inbox</h2>
            <div class="sub">Every action is evaluated by Guard before it reaches you</div>
          </div>
          <a class="btn btn-ghost btn-sm" routerLink="/app/approvals">Open inbox <x-icon name="arrow" [size]="14" /></a>
        </div>
        <div class="list">
          @for (a of inbox(); track a.id) {
            <a class="list-item act" routerLink="/app/approvals" [queryParams]="{ id: a.id }">
              <span class="icon-tile" [class.ai]="a.origin === 'AI'" [class.neg]="a.status === 'blocked'">
                <x-icon [name]="a.kind === 'FX' ? 'swap' : a.kind === 'Sweep' ? 'liquidity' : a.kind === 'Rebalance' ? 'portfolio' : 'send'" [size]="16" />
              </span>
              <div class="grow" style="min-width: 0">
                <div class="row" style="gap: 8px">
                  <strong class="truncate">{{ a.title }}</strong>
                  @if (a.origin === "AI") {
                    <span class="badge ai">AI proposed</span>
                  }
                </div>
                <div class="xs faint">{{ a.id }} · {{ a.requestedBy }} · {{ a.createdAt | rel }}</div>
              </div>
              <div class="right">
                <div class="num">{{ a.amount | compact: a.ccy : 2 }}</div>
                <x-status [value]="a.status" />
              </div>
            </a>
          }
        </div>
      </div>

      <div class="card span-5">
        <div class="card-head">
          <div>
            <h2><span class="gradient-text">Intelligence</span> briefing</h2>
            <div class="sub">Grounded in your ledger, forecast and policies</div>
          </div>
          <span class="badge ai"><x-icon name="copilot" [size]="12" />{{ insights.length }} new</span>
        </div>
        <div class="list">
          @for (i of insights.slice(0, 4); track i.id) {
            <a class="list-item insight" [routerLink]="i.route">
              <span class="sev" [class]="'sev ' + i.severity"></span>
              <div class="grow" style="min-width: 0">
                <strong>{{ i.title }}</strong>
                <div class="xs muted clamp">{{ i.body }}</div>
              </div>
              <span class="metric num xs">{{ i.metric }}</span>
            </a>
          }
        </div>
      </div>
    </section>

    <section class="grid g-12">
      <div class="card span-8">
        <div class="card-head">
          <div>
            <h2>13-week cash outlook</h2>
            <div class="sub">Weekly inflows, outflows and projected closing balance · base case</div>
          </div>
          <div class="legend">
            <span><i style="background: var(--x-pos)"></i>Inflows</span>
            <span><i style="background: var(--x-neg)"></i>Outflows</span>
            <span><i style="background: var(--x-primary)"></i>Closing</span>
          </div>
        </div>
        <x-bar-chart [labels]="fcLabels" [stacks]="fcStacks" [line]="fcLine" [height]="250" ariaLabel="13-week forecast" />
      </div>

      <div class="card span-4">
        <div class="card-head">
          <div>
            <h2>Strategic reserve</h2>
            <div class="sub">Capital · Atlas Securities</div>
          </div>
          <a class="btn btn-ghost btn-sm" routerLink="/app/portfolio">Portfolio <x-icon name="arrow" [size]="14" /></a>
        </div>
        <div class="kpi">
          <div class="value">{{ portfolioValue | compact: "EUR" : 2 }}</div>
          <div class="delta" [class.pos]="ytd >= 0" [class.neg]="ytd < 0">{{ fmtPct(ytd) }} <span class="faint">1Y · vs benchmark {{ fmtPct(ytdBench) }}</span></div>
        </div>
        <x-line-chart
          [series]="[
            { name: 'Reserve', data: perf.portfolio, color: 'var(--x-s2)', area: true },
            { name: 'Benchmark', data: perf.benchmark, color: 'var(--x-text-3)', dashed: true, width: 1.4 }
          ]"
          [labels]="perfLabels" [height]="150" [xTicks]="3" [yFormat]="idx" [tipFormat]="idx2" ariaLabel="Reserve portfolio performance" />
        <div class="alloc">
          @for (a of allocation; track a.label) {
            <span class="seg-bar" [style.flex]="a.value" [style.background]="a.color" [attr.title]="a.label + ' ' + a.value.toFixed(1) + '%'"></span>
          }
        </div>
        <div class="legend" style="margin-top: 8px">
          @for (a of allocation; track a.label) {
            <span><i [style.background]="a.color"></i>{{ a.label }} {{ a.value.toFixed(0) }}%</span>
          }
        </div>
      </div>
    </section>

    <section class="grid g-12">
      <div class="card span-7">
        <div class="card-head">
          <div>
            <h2>Live activity</h2>
            <div class="sub">Hash-chained evidence of every material event</div>
          </div>
          <a class="btn btn-ghost btn-sm" routerLink="/app/audit">Audit trail <x-icon name="arrow" [size]="14" /></a>
        </div>
        <div class="feed">
          @for (e of recent(); track e.seq) {
            <div class="ev">
              <span class="avatar round" [class.ai-av]="e.actorType === 'ai'" [class.sys-av]="e.actorType === 'system'">{{ initials(e.actor) }}</span>
              <div class="grow" style="min-width: 0">
                <div class="small"><strong>{{ e.actor }}</strong> <code class="act-code">{{ e.action }}</code> <span class="muted">{{ e.target }}</span></div>
                <div class="xs faint truncate">{{ e.detail }}</div>
              </div>
              <span class="xs faint nowrap">{{ e.at | rel }}</span>
            </div>
          }
        </div>
      </div>

      <div class="card span-5">
        <div class="card-head">
          <div>
            <h2>Entities</h2>
            <div class="sub">Liquidity by legal entity</div>
          </div>
        </div>
        <div class="list">
          @for (e of entityRows(); track e.id) {
            <button class="list-item ent" (click)="store.entity.set(store.entity() === e.id ? 'all' : e.id)" [class.sel]="store.entity() === e.id">
              <span class="avatar">{{ e.country }}</span>
              <div class="grow" style="min-width: 0; text-align: left">
                <strong class="small">{{ e.short }}</strong>
                <div class="xs faint">{{ e.city }} · {{ e.accounts }} accounts · {{ e.ccy }}</div>
              </div>
              <div style="width: 96px">
                <div class="meter"><span [style.width.%]="e.share"></span></div>
              </div>
              <span class="num small" style="width: 70px; text-align: right">{{ e.total | compact: "EUR" : 1 }}</span>
            </button>
          }
        </div>
      </div>
    </section>
  </div>`,
  styles: [
    `
      .unit { font: 500 14px var(--x-font); color: var(--x-text-3); }
      .link-card { color: inherit; transition: border-color .15s, transform .15s; }
      .link-card:hover { color: inherit; border-color: var(--x-border-strong); transform: translateY(-1px); }
      .avatars { display: flex; gap: 6px; margin-top: 12px; flex-wrap: wrap; }
      .donut-row { display: flex; align-items: center; gap: 18px; flex-wrap: wrap; justify-content: center; }
      .donut-total { font: 600 19px var(--x-font-display); }
      .ccy-list { flex: 1; min-width: 140px; display: flex; flex-direction: column; gap: 10px; }
      .ccy { display: flex; align-items: center; gap: 8px; font-size: 13px; }
      .ccy i { width: 10px; height: 10px; border-radius: 3px; }
      .act, .insight { color: inherit; border-radius: 10px; margin: 0 -8px; padding-left: 8px; padding-right: 8px; }
      .act:hover, .insight:hover { background: var(--x-hover); color: inherit; }
      .act strong, .insight strong { font-size: 13px; font-weight: 600; }
      .act .right { display: flex; flex-direction: column; align-items: flex-end; gap: 4px; }
      .sev { align-self: stretch; width: 3px; border-radius: 3px; flex-shrink: 0; background: var(--x-info); }
      .sev.warning { background: var(--x-warn); }
      .sev.critical { background: var(--x-neg); }
      .sev.opportunity { background: var(--x-pos); }
      .clamp { display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; margin-top: 2px; }
      .metric { padding: 3px 7px; border-radius: 6px; background: var(--x-surface-3); color: var(--x-text-2); white-space: nowrap; }
      .alloc { display: flex; gap: 3px; height: 8px; margin-top: 14px; }
      .seg-bar { border-radius: 3px; }
      .feed { display: flex; flex-direction: column; }
      .ev { display: flex; align-items: center; gap: 11px; padding: 9px 0; border-bottom: 1px solid var(--x-border); }
      .ev:last-child { border-bottom: 0; }
      .ev .avatar { width: 28px; height: 28px; font-size: 10.5px; }
      .ai-av { background: var(--x-ai-soft); color: var(--x-ai); }
      .sys-av { background: var(--x-accent-soft); color: var(--x-accent); }
      .act-code { font-size: 11px; padding: 1px 5px; border-radius: 5px; background: var(--x-surface-3); color: var(--x-text-2); }
      .ent { width: 100%; height: auto; background: transparent; color: inherit; font-weight: 400; border-radius: 10px; margin: 0 -8px; padding-left: 8px; padding-right: 8px; width: calc(100% + 16px); }
      .ent:hover { background: var(--x-hover); color: inherit; }
      .ent.sel { background: var(--x-primary-soft); }
    `,
  ],
})
export class OverviewPage {
  readonly store = inject(DemoStore);
  readonly insights = INSIGHTS;
  readonly perf = PERFORMANCE;
  readonly portfolioValue = PORTFOLIO_VALUE;
  readonly ranges = [30, 90] as const;
  readonly range = signal<30 | 90>(90);
  readonly firstName = this.store.org.persona.name.split(" ")[0];
  readonly dateLabel = new Date(TODAY).toLocaleDateString("en-GB", { weekday: "long", day: "numeric", month: "long" });
  readonly greeting = (() => {
    const h = new Date().getHours();
    return h < 12 ? "Good morning" : h < 18 ? "Good afternoon" : "Good evening";
  })();

  readonly fmtPct = (v: number) => pct(v, 1);
  readonly idx = (v: number) => v.toFixed(0);
  readonly idx2 = (v: number) => v.toFixed(2);

  /** Liquidity history for the current entity filter (EUR). */
  private history = computed(() => {
    const accs = this.store.accounts();
    return Array.from({ length: 90 }, (_, d) => accs.reduce((s, a) => s + toEur(a.history[d], a.ccy), 0));
  });
  readonly liq30 = computed(() => this.history().slice(-30));
  readonly change7d = computed(() => {
    const h = this.history();
    return ((h[89] - h[82]) / h[82]) * 100;
  });
  readonly trendSeries = computed(() => [{ name: "Liquidity", data: this.history().slice(-this.range()), color: "var(--x-accent)", area: true }]);
  readonly trendLabels = computed(() =>
    Array.from({ length: this.range() }, (_, i) => new Date(TODAY - (this.range() - 1 - i) * DAY).toLocaleDateString("en-GB", { day: "2-digit", month: "short" })),
  );

  readonly ccyMix = computed<DonutSegment[]>(() => {
    const by: Record<string, number> = {};
    for (const a of this.store.accounts()) by[a.ccy] = (by[a.ccy] ?? 0) + toEur(a.balance, a.ccy);
    return Object.entries(by)
      .sort((a, b) => b[1] - a[1])
      .map(([k, v]) => ({ label: k, value: v, color: CCY_COLORS[k] }));
  });

  readonly awaitingValue = computed(() => this.store.awaitingYou().reduce((s, a) => s + toEur(a.amount, a.ccy), 0));
  readonly blocked = computed(() => this.store.actions().filter((a) => a.status === "blocked").length);
  readonly inbox = computed(() => this.store.actions().filter((a) => a.status === "pending" || a.status === "blocked").slice(0, 5));
  readonly recent = computed(() => [...this.store.audit()].reverse().slice(0, 7));

  private forecast = runForecast(DEFAULT_SCENARIO);
  readonly lowPoint = computed(() => {
    const low = this.forecast.reduce((m, w) => (w.p10 < m.p10 ? w : m), this.forecast[0]);
    const headroom = low.p10 - DEFAULT_SCENARIO.minBuffer;
    return { value: low.p10, week: low.label, headroom, fill: Math.min(100, (low.p10 / (DEFAULT_SCENARIO.minBuffer * 2)) * 100) };
  });
  readonly fcLabels = this.forecast.map((w) => w.label);
  readonly fcStacks = [
    { name: "Inflows", data: this.forecast.map((w) => w.inflow), color: "var(--x-pos)" },
    { name: "Outflows", data: this.forecast.map((w) => -w.outflow), color: "var(--x-neg)" },
  ];
  readonly fcLine = { name: "Closing balance", data: this.forecast.map((w) => w.closing), color: "var(--x-primary)" };

  readonly perfLabels = this.perf.dates.map((d) => new Date(d).toLocaleDateString("en-GB", { month: "short", year: "2-digit" }));
  readonly ytd = (this.perf.portfolio[this.perf.portfolio.length - 1] / this.perf.portfolio[0] - 1) * 100;
  readonly ytdBench = (this.perf.benchmark[this.perf.benchmark.length - 1] / this.perf.benchmark[0] - 1) * 100;
  readonly allocation = (() => {
    const colors: Record<string, string> = { "Money market": "var(--x-s2)", "Fixed income": "var(--x-s1)", Equity: "var(--x-s3)", Commodity: "var(--x-s4)", Cash: "var(--x-s7)" };
    const by: Record<string, number> = {};
    for (const h of HOLDINGS) by[h.assetClass] = (by[h.assetClass] ?? 0) + h.weight;
    return Object.entries(by)
      .sort((a, b) => b[1] - a[1])
      .map(([label, value]) => ({ label, value, color: colors[label] ?? "var(--x-s7)" }));
  })();

  readonly entityRows = computed(() => {
    const all = ENTITIES.map((e) => {
      const accs = this.store.accounts().filter((a) => a.entityId === e.id);
      return { ...e, accounts: accs.length, total: accs.reduce((s, a) => s + toEur(a.balance, a.ccy), 0) };
    }).filter((e) => e.accounts > 0);
    const max = Math.max(...all.map((e) => e.total));
    return all.map((e) => ({ ...e, share: (e.total / max) * 100 }));
  });

  initials(name: string) {
    return name
      .split(/\s+/)
      .map((p) => p[0])
      .join("")
      .slice(0, 2)
      .toUpperCase();
  }

  boardPack() {
    this.store.toast("pos", "Board pack generated", `Liquidity ${compact(this.store.totalLiquidity(), "EUR", 1)}, 13-week outlook, approvals and risk — demo export only.`);
  }
}
