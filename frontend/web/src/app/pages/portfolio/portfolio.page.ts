import { Component, computed, inject, signal } from "@angular/core";
import { RouterLink } from "@angular/router";
import { DemoStore, MarketFeed } from "../../core/store";
import { HOLDINGS, PERFORMANCE, PORTFOLIO_VALUE, RISK, instrumentBySymbol } from "../../core/demo-data";
import { AssetClass, Holding } from "../../core/models";
import { FORMAT_PIPES, pct } from "../../core/format";
import { CHARTS, DonutSegment, LineSeries } from "../../shared/charts";
import { UI } from "../../shared/ui";

const CLASS_COLORS: Record<string, string> = {
  "Money market": "var(--x-s2)",
  "Fixed income": "var(--x-s1)",
  Equity: "var(--x-s3)",
  Commodity: "var(--x-s4)",
  Alternatives: "var(--x-s5)",
  Cash: "var(--x-s7)",
};
/** Estimated round-trip execution cost by asset class, in basis points (simulated). */
const COST_BP: Record<AssetClass, number> = { Equity: 6, "Fixed income": 9, "Money market": 1, Commodity: 12, Alternatives: 25, Cash: 0 };
const LIVE_SYMBOLS = new Set(["MSFT", "NVDA", "AAPL", "ASML", "SAP", "XAU"]);
const RANGES = [
  { key: "1M", n: 21 },
  { key: "3M", n: 63 },
  { key: "6M", n: 126 },
  { key: "1Y", n: 252 },
] as const;
type RangeKey = (typeof RANGES)[number]["key"];
const CLASS_FILTERS = ["All", "Fixed income", "Money market", "Equity", "Other"] as const;
type ClassFilter = (typeof CLASS_FILTERS)[number];
type SortKey = "symbol" | "assetClass" | "region" | "liveValue" | "weight" | "drift" | "dayPct" | "upl" | "yieldPct";

interface Row extends Holding {
  live: boolean;
  tick: "up" | "down" | "flat";
  livePrice: number;
  liveValue: number;
  liveDelta: number;
  drift: number;
  upl: number;
}

const sum = (xs: number[]) => xs.reduce((s, v) => s + v, 0);
const groupWeights = (key: "assetClass" | "region" | "sector") => {
  const by: Record<string, number> = {};
  for (const h of HOLDINGS) by[h[key]] = (by[h[key]] ?? 0) + h.weight;
  return Object.entries(by)
    .sort((a, b) => b[1] - a[1])
    .map(([label, value]) => ({ label, value }));
};

@Component({
  selector: "x-portfolio",
  standalone: true,
  imports: [RouterLink, ...UI, ...CHARTS, ...FORMAT_PIPES],
  template: `<div class="page">
    <x-page-head eyebrow="Capital · Portfolio" title="Strategic reserve portfolio" description="Corporate reserves held at Atlas Securities under the board investment mandate: investment grade only, duration up to 4 years, equities capped at 25%. Prices and performance are simulated.">
      <span class="badge" [class.pos]="!feed.paused()" [class.warn]="feed.paused()"><span class="dot" [class.live]="!feed.paused()"></span>{{ feed.paused() ? "Feed paused" : "Live prices" }}</span>
      <a class="btn btn-secondary" routerLink="/app/risk"><x-icon name="risk" [size]="16" />Risk view</a>
      <a class="btn-glow btn" routerLink="/app/approvals" [queryParams]="{ id: 'ACT-2045' }"><x-icon name="actions" [size]="16" />Review rebalance</a>
    </x-page-head>

    <section class="kpis">
      <div class="card kpi glow rise">
        <div class="label"><x-icon name="wallet" [size]="15" />Market value</div>
        <div class="value">{{ liveTotal() | compact: "EUR" : 2 }}</div>
        <div class="delta faint">{{ holdings.length }} holdings · 1 custodian</div>
      </div>
      <div class="card kpi rise" style="animation-delay: 40ms">
        <div class="label"><x-icon name="forecast" [size]="15" />1Y return</div>
        <div class="value" [class.pos]="ret1y >= 0" [class.neg]="ret1y < 0">{{ fmtPct(ret1y) }}</div>
        <div class="delta" [class.pos]="ret1y - bench1y >= 0" [class.neg]="ret1y - bench1y < 0">{{ fmtBp(ret1y - bench1y) }} <span class="faint">vs bench {{ fmtPct(bench1y) }}</span></div>
      </div>
      <div class="card kpi rise" style="animation-delay: 80ms">
        <div class="label"><x-icon name="clock" [size]="15" />Today's P&amp;L</div>
        <div class="value" [class.pos]="dayPnl >= 0" [class.neg]="dayPnl < 0">{{ dayPnl >= 0 ? "+" : "" }}{{ dayPnl | compact: "EUR" : 1 }}</div>
        <div class="delta" [class.pos]="liveDelta() >= 0" [class.neg]="liveDelta() < 0">{{ liveDelta() >= 0 ? "+" : "" }}{{ liveDelta() | compact: "EUR" : 1 }} <span class="faint">intraday live</span></div>
      </div>
      <div class="card kpi rise" style="animation-delay: 120ms">
        <div class="label"><x-icon name="target" [size]="15" />Weighted yield</div>
        <div class="value">{{ wYield.toFixed(2) }}%</div>
        <div class="delta faint">≈ {{ (wYield / 100) * portfolioValue | compact: "EUR" : 0 }} / year income</div>
      </div>
      <div class="card kpi rise" style="animation-delay: 160ms">
        <div class="label"><x-icon name="layers" [size]="15" />Duration</div>
        <div class="value">{{ risk.duration.toFixed(1) }}<span class="unit"> yrs</span></div>
        <div class="meter grad" style="margin-top: 14px" [attr.aria-label]="'Duration ' + risk.duration + ' of 4 year limit'"><span [style.width.%]="(risk.duration / 4) * 100"></span></div>
        <div class="xs faint" style="margin-top: 6px">Mandate limit 4.0 yrs</div>
      </div>
    </section>

    <section class="grid g-12">
      <div class="card span-8">
        <div class="card-head">
          <div>
            <h2>Performance</h2>
            <div class="sub">Reserve portfolio vs policy benchmark · indexed to 100 at range start</div>
          </div>
          <div class="seg" role="tablist" aria-label="Performance range">
            @for (r of ranges; track r.key) {
              <button [class.on]="range() === r.key" (click)="range.set(r.key)" role="tab" [attr.aria-selected]="range() === r.key">{{ r.key }}</button>
            }
          </div>
        </div>
        <div class="perf-stats">
          <div>
            <span class="xs faint">Portfolio</span>
            <strong class="num" [class.pos]="perfStats().p >= 0" [class.neg]="perfStats().p < 0">{{ fmtPct(perfStats().p) }}</strong>
          </div>
          <div>
            <span class="xs faint">Benchmark</span>
            <strong class="num muted">{{ fmtPct(perfStats().b) }}</strong>
          </div>
          <div>
            <span class="xs faint">Excess return</span>
            <strong class="num" [class.pos]="perfStats().x >= 0" [class.neg]="perfStats().x < 0">{{ fmtBp(perfStats().x) }}</strong>
          </div>
          <div class="legend" style="margin-left: auto">
            <span><i style="background: var(--x-s2)"></i>Reserve</span>
            <span><i style="background: var(--x-text-3)"></i>Benchmark</span>
          </div>
        </div>
        <x-line-chart [series]="perfSeries()" [labels]="perfLabels()" [height]="300" [xTicks]="6" [yFormat]="idx" [tipFormat]="idx2" ariaLabel="Reserve portfolio vs benchmark" />
      </div>

      <div class="card span-4">
        <div class="card-head">
          <div>
            <h2>Allocation</h2>
            <div class="sub">By asset class · current vs target</div>
          </div>
          @if (driftAlerts > 0) {
            <span class="badge warn"><x-icon name="alert" [size]="12" />{{ driftAlerts }} drifting</span>
          } @else {
            <span class="badge pos">On target</span>
          }
        </div>
        <div class="donut-row">
          <x-donut [segments]="classDonut" [size]="140" [thickness]="16" ariaLabel="Allocation by asset class">
            <span class="xs faint">Classes</span>
            <strong class="donut-total">{{ classDonut.length }}</strong>
          </x-donut>
          <div class="legend col" style="gap: 7px">
            @for (c of classDonut; track c.label) {
              <span><i [style.background]="c.color"></i>{{ c.label }}</span>
            }
          </div>
        </div>
        <div class="cvt">
          @for (c of classRows; track c.label) {
            <div class="cvt-row">
              <div class="row between small">
                <span><i class="sw" [style.background]="c.color"></i>{{ c.label }}</span>
                <span class="num">
                  {{ c.weight.toFixed(1) }}% <span class="faint">/ {{ c.target.toFixed(0) }}%</span>
                  <span class="drift" [class.warn]="c.flag" [class.faint]="!c.flag">{{ c.drift >= 0 ? "+" : "−" }}{{ abs(c.drift).toFixed(1) }}</span>
                </span>
              </div>
              <div class="track">
                <span class="fill" [style.width.%]="(c.weight / maxClass) * 100" [style.background]="c.color"></span>
                <span class="tgt" [style.left.%]="(c.target / maxClass) * 100" title="Target"></span>
              </div>
            </div>
          }
        </div>
        <div class="xs faint" style="margin-top: 10px">Marker shows target weight. Drift above 1.5 pts is flagged.</div>
      </div>
    </section>

    <section class="grid g-12">
      <div class="card flush span-12">
        <div class="card-head">
          <div>
            <h2>Holdings</h2>
            <div class="sub">Values in EUR · equity and gold lines reprice from the simulated market feed</div>
          </div>
          <div class="row wrap filters" style="gap: 8px">
            <div class="seg" role="tablist" aria-label="Filter by asset class">
              @for (f of classFilters; track f) {
                <button [class.on]="classFilter() === f" (click)="classFilter.set(f)" role="tab" [attr.aria-selected]="classFilter() === f">{{ f }}</button>
              }
            </div>
          </div>
        </div>
        <div class="table-wrap">
          <table class="table">
            <thead>
              <tr>
                @for (c of cols; track c.key) {
                  <th [class.right]="c.right" [attr.aria-sort]="sortKey() === c.key ? (sortDir() === 1 ? 'ascending' : 'descending') : null">
                    @if (c.sort) {
                      <button class="th-btn" [class.on]="sortKey() === c.key" (click)="sortBy(c.sort)">
                        {{ c.label }}
                        @if (sortKey() === c.key) {
                          <x-icon [name]="sortDir() === 1 ? 'up' : 'down'" [size]="11" />
                        }
                      </button>
                    } @else {
                      {{ c.label }}
                    }
                  </th>
                }
              </tr>
            </thead>
            <tbody>
              @for (h of sortedRows(); track h.symbol) {
                <tr>
                  <td>
                    <div class="row" style="gap: 8px">
                      <span class="sym">{{ h.symbol }}</span>
                      @if (h.live) {
                        <span class="dot live" title="Live price"></span>
                      }
                    </div>
                  </td>
                  <td class="name"><span class="truncate">{{ h.name }}</span></td>
                  <td><span class="row" style="gap: 6px"><i class="sw" [style.background]="classColor(h.assetClass)"></i>{{ h.assetClass }}</span></td>
                  <td class="muted">{{ h.region }}</td>
                  <td class="num">{{ h.symbol === "CASH" ? "—" : (h.qty | num: 0) }}</td>
                  <td class="num">
                    @for (p of [h.livePrice]; track p) {
                      <span class="px" [class.up]="h.tick === 'up'" [class.down]="h.tick === 'down'">{{ p | num: 2 }}</span>
                    }
                    <span class="faint xs"> {{ h.ccy }}</span>
                  </td>
                  <td class="num">
                    {{ h.liveValue | money }}
                    @if (h.live && abs(h.liveDelta) >= 1) {
                      <div class="xs" [class.pos]="h.liveDelta > 0" [class.neg]="h.liveDelta < 0">{{ h.liveDelta > 0 ? "+" : "" }}{{ h.liveDelta | compact: "EUR" : 1 }}</div>
                    }
                  </td>
                  <td class="num">{{ h.weight.toFixed(1) }}%</td>
                  <td class="num faint">{{ h.target.toFixed(1) }}%</td>
                  <td class="num" [class.pos]="h.drift < -0.5" [class.warn]="h.drift > 0.5" [class.faint]="abs(h.drift) <= 0.5">{{ h.drift >= 0 ? "+" : "−" }}{{ abs(h.drift).toFixed(2) }}</td>
                  <td class="num" [class.pos]="h.dayPct > 0" [class.neg]="h.dayPct < 0">{{ h.dayPct | pct: 2 }}</td>
                  <td class="num" [class.pos]="h.upl > 0" [class.neg]="h.upl < 0">{{ h.symbol === "CASH" ? "—" : (h.upl | pct: 1) }}</td>
                  <td class="num">{{ h.yieldPct.toFixed(2) }}%</td>
                </tr>
              }
            </tbody>
            <tfoot>
              <tr>
                <td colspan="6"><strong>Total</strong> <span class="faint xs">{{ sortedRows().length }} lines</span></td>
                <td class="num"><strong>{{ filteredTotal() | money }}</strong></td>
                <td class="num">{{ filteredWeight().toFixed(1) }}%</td>
                <td colspan="5"></td>
              </tr>
            </tfoot>
          </table>
        </div>
      </div>
    </section>

    <section class="grid g-12">
      <div class="card span-7">
        <div class="card-head">
          <div>
            <h2>Rebalance proposal</h2>
            <div class="sub">Trades that restore target weights for lines drifting more than 0.5 pts</div>
          </div>
          <span class="badge ai"><x-icon name="copilot" [size]="12" />Computed</span>
        </div>
        <div class="trades">
          @for (t of trades; track t.symbol) {
            <div class="trade">
              <span class="side" [class.buy]="t.side === 'Buy'" [class.sell]="t.side === 'Sell'">{{ t.side }}</span>
              <div class="grow" style="min-width: 0">
                <strong class="small">{{ t.symbol }}</strong>
                <div class="xs faint truncate">{{ t.name }} · {{ t.from.toFixed(1) }}% → {{ t.to.toFixed(1) }}%</div>
              </div>
              <div class="bar-wrap" aria-hidden="true">
                <span class="tbar" [class.buy]="t.side === 'Buy'" [style.width.%]="(abs(t.amount) / maxTrade) * 100"></span>
              </div>
              <span class="num small amt">{{ t.amount | compact: "EUR" : 0 }}</span>
            </div>
          } @empty {
            <div class="empty">Portfolio is within 0.5 pts of target on every line.</div>
          }
        </div>
        <div class="divider"></div>
        <div class="totals">
          <div><span class="xs faint">Sells</span><strong class="num neg">{{ tradeTotals.sells | compact: "EUR" : 2 }}</strong></div>
          <div><span class="xs faint">Buys</span><strong class="num pos">{{ tradeTotals.buys | compact: "EUR" : 2 }}</strong></div>
          <div><span class="xs faint">Net to settlement cash</span><strong class="num">{{ tradeTotals.sells - tradeTotals.buys | compact: "EUR" : 0 }}</strong></div>
          <div><span class="xs faint">Est. cost</span><strong class="num">{{ tradeTotals.cost | money }}</strong></div>
        </div>
        <div class="callout info" style="margin-top: 14px">
          <x-icon name="guard" [size]="18" />
          <div class="grow small">
            Intelligence has already drafted this rebalance as <strong>ACT-2045</strong>
            @if (rebalanceAction(); as a) {
              (<x-status [value]="a.status" />).
            }
            Guard checks mandate limits before it reaches the approval chain.
          </div>
        </div>
        <div class="row" style="justify-content: flex-end; margin-top: 14px; gap: 8px">
          <a class="btn btn-ghost" routerLink="/app/guard">Mandate policies</a>
          <a class="btn btn-glow" routerLink="/app/approvals" [queryParams]="{ id: 'ACT-2045' }"><x-icon name="send" [size]="15" />Send to approvals</a>
        </div>
      </div>

      <div class="card span-5">
        <div class="card-head">
          <div>
            <h2>Mandate compliance</h2>
            <div class="sub">Board investment mandate v3 · checked on every holding change</div>
          </div>
          <span class="badge" [class.pos]="allPass" [class.warn]="!allPass">{{ passCount }}/{{ checks.length }} pass</span>
        </div>
        <div class="list">
          @for (c of checks; track c.label) {
            <div class="list-item check">
              <span class="icon-tile" [class.pos]="c.ok" [class.neg]="!c.ok"><x-icon [name]="c.ok ? 'check' : 'x'" [size]="15" /></span>
              <div class="grow" style="min-width: 0">
                <strong class="small">{{ c.label }}</strong>
                <div class="xs faint">{{ c.detail }}</div>
              </div>
              <span class="num small">{{ c.value }}</span>
            </div>
          }
        </div>
        <div class="divider"></div>
        <div class="row between" style="margin-bottom: 10px">
          <h3 class="small">Exposure</h3>
          <div class="seg" role="tablist" aria-label="Exposure dimension">
            <button [class.on]="exposureBy() === 'region'" (click)="exposureBy.set('region')" role="tab" [attr.aria-selected]="exposureBy() === 'region'">Region</button>
            <button [class.on]="exposureBy() === 'sector'" (click)="exposureBy.set('sector')" role="tab" [attr.aria-selected]="exposureBy() === 'sector'">Sector</button>
          </div>
        </div>
        <div class="expo">
          @for (e of exposure(); track e.label) {
            <div class="expo-row">
              <span class="small truncate">{{ e.label }}</span>
              <div class="meter"><span [style.width.%]="(e.value / exposure()[0].value) * 100"></span></div>
              <span class="num small">{{ e.value.toFixed(1) }}%</span>
            </div>
          }
        </div>
      </div>
    </section>
  </div>`,
  styles: [
    `
      .kpis { display: grid; gap: 16px; grid-template-columns: repeat(5, minmax(0, 1fr)); }
      @media (max-width: 1200px) { .kpis { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
      @media (max-width: 760px) { .kpis { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
      @media (max-width: 420px) { .kpis { grid-template-columns: minmax(0, 1fr); } }
      .unit { font: 500 14px var(--x-font); color: var(--x-text-3); }
      .perf-stats { display: flex; align-items: center; gap: 24px; flex-wrap: wrap; margin-bottom: 10px; }
      .perf-stats > div:not(.legend) { display: flex; flex-direction: column; gap: 3px; }
      .perf-stats strong { font-size: 17px; font-weight: 600; }
      .donut-row { display: flex; align-items: center; gap: 18px; flex-wrap: wrap; justify-content: center; margin-bottom: 14px; }
      .donut-total { font: 600 19px var(--x-font-display); }
      .sw { display: inline-block; width: 9px; height: 9px; border-radius: 3px; margin-right: 6px; flex-shrink: 0; }
      .cvt { display: flex; flex-direction: column; gap: 10px; }
      .cvt-row { display: flex; flex-direction: column; gap: 5px; }
      .drift { display: inline-block; min-width: 38px; text-align: right; font-size: 11px; }
      .track { position: relative; height: 6px; border-radius: 999px; background: var(--x-surface-3); }
      .track .fill { position: absolute; inset: 0 auto 0 0; border-radius: inherit; transition: width .5s var(--x-ease); }
      .track .tgt { position: absolute; top: -3px; width: 2px; height: 12px; border-radius: 1px; background: var(--x-text); transform: translateX(-1px); }
      .th-btn { height: auto; padding: 0; background: none; border: 0; font: inherit; letter-spacing: inherit; text-transform: inherit; color: inherit; gap: 4px; }
      .th-btn:hover, .th-btn.on { background: none; color: var(--x-text); }
      .card-head:has(.filters) { flex-wrap: wrap; }
      .filters { max-width: 100%; }
      .filters .seg { max-width: 100%; overflow-x: auto; scrollbar-width: none; }
      .filters .seg button { flex-shrink: 0; }
      .sym { font: 600 12.5px var(--x-font-mono); white-space: nowrap; }
      .table td { white-space: nowrap; }
      td.name { max-width: 220px; }
      td.name .truncate { display: block; }
      td .warn { color: var(--x-warn); }
      tfoot td { padding: 11px 14px; border-top: 1px solid var(--x-border); background: var(--x-surface-2); font-size: 13px; }
      .px { padding: 1px 3px; border-radius: 4px; }
      .px.up { animation: flash-up 1.1s var(--x-ease); }
      .px.down { animation: flash-down 1.1s var(--x-ease); }
      @keyframes flash-up { from { background: var(--x-pos-soft); color: var(--x-pos); } }
      @keyframes flash-down { from { background: var(--x-neg-soft); color: var(--x-neg); } }
      .trades { display: flex; flex-direction: column; }
      .trade { display: flex; align-items: center; gap: 12px; padding: 9px 0; border-bottom: 1px solid var(--x-border); }
      .trade:last-child { border-bottom: 0; }
      .side { width: 42px; text-align: center; padding: 3px 0; border-radius: 6px; font: 600 11px var(--x-font-mono); text-transform: uppercase; letter-spacing: .06em; }
      .side.buy { background: var(--x-pos-soft); color: var(--x-pos); }
      .side.sell { background: var(--x-neg-soft); color: var(--x-neg); }
      .bar-wrap { width: 120px; height: 6px; border-radius: 999px; background: var(--x-surface-3); overflow: hidden; flex-shrink: 0; }
      .tbar { display: block; height: 100%; border-radius: inherit; background: var(--x-neg); }
      .tbar.buy { background: var(--x-pos); }
      .amt { width: 64px; text-align: right; }
      @media (max-width: 520px) { .bar-wrap { display: none; } }
      .totals { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; }
      .totals > div { display: flex; flex-direction: column; gap: 3px; }
      .totals strong { font-size: 15px; font-weight: 600; }
      @media (max-width: 520px) { .totals { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
      .check { padding: 9px 0; }
      .check .icon-tile { width: 28px; height: 28px; border-radius: 8px; }
      .expo { display: flex; flex-direction: column; gap: 9px; }
      .expo-row { display: grid; grid-template-columns: 120px 1fr 52px; align-items: center; gap: 10px; }
      .expo-row .num { text-align: right; }
    `,
  ],
})
export class PortfolioPage {
  readonly store = inject(DemoStore);
  readonly feed = inject(MarketFeed);
  readonly holdings = HOLDINGS;
  readonly risk = RISK;
  readonly portfolioValue = PORTFOLIO_VALUE;
  readonly ranges = RANGES;
  readonly range = signal<RangeKey>("1Y");
  readonly abs = Math.abs;

  readonly fmtPct = (v: number) => pct(v, 2);
  readonly fmtBp = (v: number) => (v >= 0 ? "+" : "−") + Math.abs(v * 100).toFixed(0) + " bp";
  readonly idx = (v: number) => v.toFixed(1);
  readonly idx2 = (v: number) => v.toFixed(2);
  classColor = (c: string) => CLASS_COLORS[c] ?? "var(--x-s7)";

  /* ───── KPIs ───── */
  readonly ret1y = (PERFORMANCE.portfolio[PERFORMANCE.portfolio.length - 1] / PERFORMANCE.portfolio[0] - 1) * 100;
  readonly bench1y = (PERFORMANCE.benchmark[PERFORMANCE.benchmark.length - 1] / PERFORMANCE.benchmark[0] - 1) * 100;
  readonly dayPnl = sum(HOLDINGS.map((h) => (h.value * h.dayPct) / 100));
  readonly wYield = sum(HOLDINGS.map((h) => h.weight * h.yieldPct)) / 100;

  /* ───── live repricing ───── */
  readonly rows = computed<Row[]>(() => {
    const q = this.feed.quotes();
    return HOLDINGS.map((h) => {
      const ins = LIVE_SYMBOLS.has(h.symbol) ? instrumentBySymbol(h.symbol) : undefined;
      const quote = ins ? q[h.symbol] : undefined;
      const factor = ins && quote ? quote.price / ins.candles[ins.candles.length - 1].c : 1;
      const livePrice = h.price * factor;
      const liveValue = h.value * factor;
      return {
        ...h,
        live: !!quote,
        tick: quote?.tick ?? "flat",
        livePrice,
        liveValue,
        liveDelta: liveValue - h.value,
        drift: h.weight - h.target,
        upl: (livePrice / h.avgCost - 1) * 100,
      };
    });
  });
  readonly liveDelta = computed(() => sum(this.rows().map((r) => r.liveDelta)));
  readonly liveTotal = computed(() => PORTFOLIO_VALUE + this.liveDelta());

  /* ───── performance ───── */
  private rangeN = computed(() => RANGES.find((r) => r.key === this.range())!.n);
  readonly perfSeries = computed<LineSeries[]>(() => {
    const n = this.rangeN();
    const p = PERFORMANCE.portfolio.slice(-n);
    const b = PERFORMANCE.benchmark.slice(-n);
    return [
      { name: "Reserve", data: p.map((v) => (v / p[0]) * 100), color: "var(--x-s2)", area: true },
      { name: "Benchmark", data: b.map((v) => (v / b[0]) * 100), color: "var(--x-text-3)", dashed: true, width: 1.4 },
    ];
  });
  readonly perfLabels = computed(() =>
    PERFORMANCE.dates.slice(-this.rangeN()).map((d) => new Date(d).toLocaleDateString("en-GB", { day: "2-digit", month: "short" })),
  );
  readonly perfStats = computed(() => {
    const [p, b] = this.perfSeries();
    const pr = p.data[p.data.length - 1] - 100;
    const br = b.data[b.data.length - 1] - 100;
    return { p: pr, b: br, x: pr - br };
  });

  /* ───── allocation ───── */
  readonly classRows = (() => {
    const by: Record<string, { weight: number; target: number }> = {};
    for (const h of HOLDINGS) {
      const c = (by[h.assetClass] ??= { weight: 0, target: 0 });
      c.weight += h.weight;
      c.target += h.target;
    }
    return Object.entries(by)
      .sort((a, b) => b[1].weight - a[1].weight)
      .map(([label, v]) => ({ label, ...v, drift: v.weight - v.target, flag: Math.abs(v.weight - v.target) > 1.5, color: CLASS_COLORS[label] ?? "var(--x-s7)" }));
  })();
  readonly maxClass = Math.max(...this.classRows.map((c) => Math.max(c.weight, c.target))) * 1.08;
  readonly classDonut: DonutSegment[] = this.classRows.map((c) => ({ label: c.label, value: c.weight, color: c.color }));
  readonly driftAlerts = this.classRows.filter((c) => c.flag).length;

  /* ───── holdings table ───── */
  readonly classFilters = CLASS_FILTERS;
  readonly classFilter = signal<ClassFilter>("All");
  readonly sortKey = signal<SortKey>("liveValue");
  readonly sortDir = signal<1 | -1>(-1);
  readonly cols: { key: string; label: string; sort?: SortKey; right?: boolean }[] = [
    { key: "symbol", label: "Symbol", sort: "symbol" },
    { key: "name", label: "Name" },
    { key: "assetClass", label: "Class", sort: "assetClass" },
    { key: "region", label: "Region", sort: "region" },
    { key: "qty", label: "Qty", right: true },
    { key: "price", label: "Price", right: true },
    { key: "liveValue", label: "Value EUR", sort: "liveValue", right: true },
    { key: "weight", label: "Weight", sort: "weight", right: true },
    { key: "target", label: "Target", right: true },
    { key: "drift", label: "Drift", sort: "drift", right: true },
    { key: "dayPct", label: "Day", sort: "dayPct", right: true },
    { key: "upl", label: "Unreal. P&L", sort: "upl", right: true },
    { key: "yieldPct", label: "Yield", sort: "yieldPct", right: true },
  ];
  private filteredRows = computed(() => {
    const f = this.classFilter();
    const rows = this.rows();
    if (f === "All") return rows;
    if (f === "Other") return rows.filter((r) => !["Fixed income", "Money market", "Equity"].includes(r.assetClass));
    return rows.filter((r) => r.assetClass === f);
  });
  readonly sortedRows = computed(() => {
    const k = this.sortKey();
    const d = this.sortDir();
    return [...this.filteredRows()].sort((a, b) => {
      const av = a[k];
      const bv = b[k];
      return (typeof av === "number" && typeof bv === "number" ? av - bv : String(av).localeCompare(String(bv))) * d;
    });
  });
  readonly filteredTotal = computed(() => sum(this.filteredRows().map((r) => r.liveValue)));
  readonly filteredWeight = computed(() => sum(this.filteredRows().map((r) => r.weight)));

  sortBy(k: SortKey) {
    if (this.sortKey() === k) this.sortDir.update((d) => (d === 1 ? -1 : 1));
    else {
      this.sortKey.set(k);
      this.sortDir.set(["symbol", "assetClass", "region"].includes(k) ? 1 : -1);
    }
  }

  /* ───── rebalance ───── */
  readonly trades = HOLDINGS.filter((h) => Math.abs(h.weight - h.target) > 0.5)
    .map((h) => {
      const amount = ((h.target - h.weight) / 100) * PORTFOLIO_VALUE;
      return {
        symbol: h.symbol,
        name: h.name,
        side: amount > 0 ? ("Buy" as const) : ("Sell" as const),
        amount,
        from: h.weight,
        to: h.target,
        cost: (Math.abs(amount) * COST_BP[h.assetClass]) / 10_000,
      };
    })
    .sort((a, b) => a.amount - b.amount);
  readonly maxTrade = Math.max(1, ...this.trades.map((t) => Math.abs(t.amount)));
  readonly tradeTotals = {
    sells: sum(this.trades.filter((t) => t.amount < 0).map((t) => -t.amount)),
    buys: sum(this.trades.filter((t) => t.amount > 0).map((t) => t.amount)),
    gross: sum(this.trades.map((t) => Math.abs(t.amount))),
    cost: sum(this.trades.map((t) => t.cost)),
  };
  readonly rebalanceAction = computed(() => this.store.actions().find((a) => a.id === "ACT-2045"));

  /* ───── mandate ───── */
  private readonly classW = (c: AssetClass) => sum(HOLDINGS.filter((h) => h.assetClass === c).map((h) => h.weight));
  private readonly equityW = this.classW("Equity");
  private readonly liquidW = this.classW("Money market") + this.classW("Cash") + sum(HOLDINGS.filter((h) => h.symbol === "UST-0-1").map((h) => h.weight));
  private readonly maxLine = HOLDINGS.reduce((m, h) => (h.weight > m.weight ? h : m), HOLDINGS[0]);
  private readonly fiLines = HOLDINGS.filter((h) => h.assetClass === "Fixed income");
  readonly checks = [
    { label: "Investment grade only", detail: `${this.fiLines.length} of ${this.fiLines.length} fixed-income lines rated BBB− or better`, value: "IG", ok: true },
    { label: "Duration ≤ 4.0 years", detail: "Portfolio modified duration", value: `${RISK.duration.toFixed(1)}y`, ok: RISK.duration <= 4 },
    { label: "Equities ≤ 25%", detail: `${HOLDINGS.filter((h) => h.assetClass === "Equity").length} listed equity lines`, value: `${this.equityW.toFixed(1)}%`, ok: this.equityW <= 25 },
    { label: "Same-day liquidity ≥ 30%", detail: "Money market, cash and T-bills", value: `${this.liquidW.toFixed(1)}%`, ok: this.liquidW >= 30 },
    { label: "Single line ≤ 30%", detail: `Largest: ${this.maxLine.name}`, value: `${this.maxLine.weight.toFixed(1)}%`, ok: this.maxLine.weight <= 30 },
    { label: "Commodities ≤ 5%", detail: "Physical gold ETC only", value: `${this.classW("Commodity").toFixed(1)}%`, ok: this.classW("Commodity") <= 5 },
  ];
  readonly passCount = this.checks.filter((c) => c.ok).length;
  readonly allPass = this.passCount === this.checks.length;

  readonly exposureBy = signal<"region" | "sector">("region");
  private readonly regionW = groupWeights("region");
  private readonly sectorW = groupWeights("sector");
  readonly exposure = computed(() => (this.exposureBy() === "region" ? this.regionW : this.sectorW));
}
