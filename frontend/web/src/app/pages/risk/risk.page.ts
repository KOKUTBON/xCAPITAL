import { Component, computed, inject, signal } from "@angular/core";
import { RouterLink } from "@angular/router";
import { DemoStore } from "../../core/store";
import { HOLDINGS, PORTFOLIO_VALUE, RISK, STRESS, TODAY } from "../../core/demo-data";
import { FORMAT_PIPES, compact, pct } from "../../core/format";
import { CHARTS, LineSeries } from "../../shared/charts";
import { UI } from "../../shared/ui";

const DAY = 86_400_000;
const VAR_LIMIT = 350_000;
const SPREAD_DURATION = 2.5;
/** Issuer / manager behind each reserve line (simulated). */
const ISSUER: Record<string, string> = {
  "LMN-EUR": "Lumen Asset Management",
  "UST-0-1": "United States Treasury",
  "BTP-29": "Republic of Italy",
  "BUND-31": "Federal Republic of Germany",
  "EUIG-15": "Diversified IG issuers",
  MSFT: "Microsoft",
  NVDA: "NVIDIA",
  ASML: "ASML Holding",
  SAP: "SAP",
  AAPL: "Apple",
  XAU: "Gold ETC trustee",
  CASH: "Atlas Securities (custody)",
};
const sum = (xs: number[]) => xs.reduce((s, v) => s + v, 0);
const classValue = (c: string) => sum(HOLDINGS.filter((h) => h.assetClass === c).map((h) => h.value));

const PRESETS = [
  { name: "Mild risk-off", eq: -10, rates: -25, spread: 40 },
  { name: "2008-style", eq: -40, rates: -100, spread: 250 },
  { name: "Inflation shock", eq: -15, rates: 150, spread: 60 },
  { name: "Soft landing", eq: 8, rates: -50, spread: -20 },
];

@Component({
  selector: "x-risk",
  standalone: true,
  imports: [RouterLink, ...UI, ...CHARTS, ...FORMAT_PIPES],
  template: `<div class="page">
    <x-page-head eyebrow="Capital · Risk" title="Risk" description="Market, liquidity and concentration risk on the strategic reserve portfolio. Historical-simulation VaR on 500 days, refreshed overnight from simulated data.">
      <a class="btn btn-secondary" routerLink="/app/portfolio"><x-icon name="portfolio" [size]="16" />Portfolio</a>
      <button class="btn-glow" (click)="exportReport()"><x-icon name="download" [size]="16" />Risk report</button>
    </x-page-head>

    <section class="grid g-4">
      <x-kpi class="rise" icon="risk" label="1-day VaR · 95%" [value]="c(risk.var95)" [delta]="pctOfPv(risk.var95) + ' of portfolio · ' + util().toFixed(0) + '% of limit'" />
      <x-kpi class="rise" style="animation-delay: 30ms" icon="risk" label="1-day VaR · 99%" [value]="c(risk.var99)" [delta]="pctOfPv(risk.var99) + ' of portfolio'" />
      <x-kpi class="rise" style="animation-delay: 60ms" icon="alert" label="Expected shortfall · 97.5%" [value]="c(risk.es975)" [delta]="'Tail loss · ' + pctOfPv(risk.es975)" />
      <x-kpi class="rise" style="animation-delay: 90ms" icon="markets" label="Annual volatility" [value]="risk.volAnn.toFixed(1) + '%'" delta="Ex-ante, 1Y window" />
      <x-kpi class="rise" style="animation-delay: 120ms" icon="globe" label="Beta to MSCI World" [value]="risk.beta.toFixed(2)" delta="Low equity sensitivity" />
      <x-kpi class="rise" style="animation-delay: 150ms" icon="target" label="Sharpe ratio" [value]="risk.sharpe.toFixed(2)" delta="1Y, vs €STR" tone="pos" />
      <x-kpi class="rise" style="animation-delay: 180ms" icon="down" label="Max drawdown · 1Y" [value]="fmtPct(risk.maxDrawdown, 1)" delta="Within −5% tolerance" />
      <x-kpi class="rise" style="animation-delay: 210ms" icon="liquidity" label="Days to liquidate 90%" [value]="risk.liquidityDays.toFixed(1) + ' days'" delta="At 20% of ADV" />
    </section>

    <section class="grid g-12">
      <div class="card span-8">
        <div class="card-head">
          <div>
            <h2>Value at Risk · 60 days</h2>
            <div class="sub">1-day 95% historical VaR against the board limit of {{ limit | compact: "EUR" : 0 }}</div>
          </div>
          <div class="row" style="gap: 8px">
            <span class="badge" [class.pos]="util() < 75" [class.warn]="util() >= 75">{{ util().toFixed(0) }}% utilised</span>
          </div>
        </div>
        <div class="var-stats">
          <div><span class="xs faint">Current</span><strong class="num">{{ varNow | compact: "EUR" : 0 }}</strong></div>
          <div><span class="xs faint">60D average</span><strong class="num">{{ varAvg | compact: "EUR" : 0 }}</strong></div>
          <div><span class="xs faint">60D peak</span><strong class="num">{{ varPeak | compact: "EUR" : 0 }}</strong></div>
          <div><span class="xs faint">Headroom</span><strong class="num pos">{{ limit - varNow | compact: "EUR" : 0 }}</strong></div>
        </div>
        <x-line-chart [series]="varSeries" [labels]="varLabels" [height]="240" [threshold]="{ value: limit, label: 'Board limit €350K' }" [zeroBased]="false" ariaLabel="VaR history" />
      </div>

      <div class="card span-4">
        <div class="card-head">
          <div>
            <h2>Factor exposures</h2>
            <div class="sub">Standardised betas · negative left, positive right</div>
          </div>
        </div>
        <div class="factors">
          @for (f of risk.factors; track f.name) {
            <div class="factor" [attr.title]="f.name + ' ' + f.exposure.toFixed(2)">
              <span class="small truncate">{{ f.name }}</span>
              <div class="div-track">
                <span class="axis-0"></span>
                @if (f.exposure >= 0) {
                  <span class="dbar pos" [style.width.%]="(f.exposure / maxFactor) * 50"></span>
                } @else {
                  <span class="dbar neg" [style.width.%]="(-f.exposure / maxFactor) * 50"></span>
                }
              </div>
              <span class="num small" [class.pos]="f.exposure > 0" [class.neg]="f.exposure < 0">{{ f.exposure > 0 ? "+" : f.exposure < 0 ? "−" : "" }}{{ abs(f.exposure).toFixed(2) }}</span>
            </div>
          }
        </div>
        <div class="xs faint" style="margin-top: 12px">Rates duration dominates: a 1 bp parallel move ≈ {{ dv01 | compact: "EUR" : 1 }} (DV01).</div>
      </div>
    </section>

    <section class="grid g-12">
      <div class="card span-7">
        <div class="card-head">
          <div>
            <h2>Correlation matrix</h2>
            <div class="sub">Daily returns, 1Y · hover to trace a row and column</div>
          </div>
          <div class="scale" aria-hidden="true">
            <span class="xs faint">−1</span><span class="scale-bar"></span><span class="xs faint">+1</span>
          </div>
        </div>
        <div class="heat-wrap">
          <div class="heat" [style.grid-template-columns]="'minmax(76px, auto) repeat(' + corr.labels.length + ', minmax(52px, 1fr))'" (mouseleave)="hover.set(null)" role="table" aria-label="Correlation matrix">
            <span></span>
            @for (l of corr.labels; track l; let j = $index) {
              <span class="hl col-l" [class.on]="hover()?.c === j" role="columnheader">{{ l }}</span>
            }
            @for (row of corr.matrix; track row; let i = $index) {
              <span class="hl row-l" [class.on]="hover()?.r === i" role="rowheader">{{ corr.labels[i] }}</span>
              @for (v of row; track $index) {
                <span
                  class="cell num"
                  role="cell"
                  [style.background]="cellColor(v)"
                  [class.diag]="i === $index"
                  [class.dim]="hover() !== null && hover()?.r !== i && hover()?.c !== $index"
                  [class.focus]="hover()?.r === i && hover()?.c === $index"
                  (mouseenter)="hover.set({ r: i, c: $index })"
                  [attr.title]="corr.labels[i] + ' / ' + corr.labels[$index] + ': ' + v.toFixed(2)"
                >{{ v.toFixed(2) }}</span>
              }
            }
          </div>
        </div>
        @if (hover(); as h) {
          <div class="small muted hover-note">
            <strong>{{ corr.labels[h.r] }}</strong> vs <strong>{{ corr.labels[h.c] }}</strong>: {{ corr.matrix[h.r][h.c].toFixed(2) }} — {{ corrWord(corr.matrix[h.r][h.c]) }}
          </div>
        } @else {
          <div class="small faint hover-note">Gold and USD (−0.37) and equities vs government bonds (−0.28) provide the main diversification.</div>
        }
      </div>

      <div class="card span-5">
        <div class="card-head">
          <div>
            <h2>Concentration</h2>
            <div class="sub">Single-line and issuer exposure</div>
          </div>
        </div>
        <div class="conc-kpis">
          <div class="kpi">
            <div class="label">Top-5 holdings</div>
            <div class="value">{{ top5Weight.toFixed(1) }}%</div>
          </div>
          <div class="kpi">
            <div class="label">Effective # of lines</div>
            <div class="value">{{ effectiveN.toFixed(1) }}</div>
          </div>
        </div>
        <div class="list">
          @for (h of top5; track h.symbol; let i = $index) {
            <div class="list-item conc">
              <span class="rank num xs">{{ i + 1 }}</span>
              <div class="grow" style="min-width: 0">
                <div class="row between small"><strong class="truncate">{{ h.name }}</strong><span class="num">{{ h.weight.toFixed(1) }}%</span></div>
                <div class="meter" style="margin-top: 6px"><span [style.width.%]="(h.weight / top5[0].weight) * 100"></span></div>
              </div>
            </div>
          }
        </div>
        <div class="callout info" style="margin-top: 12px">
          <x-icon name="building" [size]="18" />
          <div class="small">
            Largest issuer: <strong>{{ largestIssuer.name }}</strong> at {{ largestIssuer.weight.toFixed(1) }}% ({{ largestIssuer.value | compact: "EUR" : 2 }}).
            Daily-dealing money market fund within the 30% single-line limit.
          </div>
        </div>
      </div>
    </section>

    <section class="grid g-12">
      <div class="card flush span-7">
        <div class="card-head">
          <div>
            <h2>Stress testing</h2>
            <div class="sub">Historical and hypothetical scenarios · select a row for detail</div>
          </div>
        </div>
        <div class="table-wrap">
          <table class="table">
            <thead>
              <tr>
                <th>Scenario</th>
                <th class="right">Portfolio %</th>
                <th class="right">Portfolio €</th>
                <th class="right">Liquidity €</th>
              </tr>
            </thead>
            <tbody>
              @for (s of stress; track s.name; let i = $index) {
                <tr class="clickable" [class.selected]="sel() === i" (click)="sel.set(i)" (keydown.enter)="sel.set(i)" tabindex="0" [attr.aria-selected]="sel() === i">
                  <td>
                    <strong class="small">{{ s.name }}</strong>
                    <div class="xs faint">{{ s.description }}</div>
                  </td>
                  <td class="num" [class.pos]="s.impactPct > 0" [class.neg]="s.impactPct < 0">{{ s.impactPct === 0 ? "—" : (s.impactPct | pct: 1) }}</td>
                  <td class="num" [class.pos]="s.impact > 0" [class.neg]="s.impact < 0">{{ s.impact === 0 ? "—" : signed(s.impact) }}</td>
                  <td class="num" [class.pos]="s.liquidityImpact > 0" [class.neg]="s.liquidityImpact < 0">{{ s.liquidityImpact === 0 ? "—" : signed(s.liquidityImpact) }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      </div>

      <div class="card span-5">
        <div class="card-head">
          <div>
            <h2>{{ scenario().name }}</h2>
            <div class="sub">{{ scenario().description }}</div>
          </div>
          <span class="badge" [class.neg]="scenario().impact < 0" [class.pos]="scenario().impact > 0">{{ severity() }}</span>
        </div>
        <div class="sc-grid">
          <div><span class="xs faint">Portfolio impact</span><strong class="num" [class.neg]="scenario().impact < 0" [class.pos]="scenario().impact > 0">{{ scenario().impact === 0 ? "€0" : signed(scenario().impact) }}</strong></div>
          <div><span class="xs faint">Post-stress value</span><strong class="num">{{ pv + scenario().impact | compact: "EUR" : 2 }}</strong></div>
          <div><span class="xs faint">Group liquidity impact</span><strong class="num" [class.neg]="scenario().liquidityImpact < 0" [class.pos]="scenario().liquidityImpact > 0">{{ scenario().liquidityImpact === 0 ? "€0" : signed(scenario().liquidityImpact) }}</strong></div>
          <div><span class="xs faint">Multiple of 1-day VaR 99%</span><strong class="num">{{ abs(scenario().impact) / risk.var99 | num: 1 }}×</strong></div>
        </div>
        <div class="divider"></div>
        <div class="xs faint" style="margin-bottom: 8px">Portfolio impact across scenarios</div>
        <div class="cmp">
          @for (s of stress; track s.name; let i = $index) {
            <button class="cmp-row" [class.on]="sel() === i" (click)="sel.set(i)" [attr.aria-label]="'Select ' + s.name">
              <span class="xs truncate">{{ s.name }}</span>
              <div class="div-track">
                <span class="axis-0"></span>
                @if (s.impact >= 0) {
                  <span class="dbar pos" [style.width.%]="(s.impact / maxStress) * 50"></span>
                } @else {
                  <span class="dbar neg" [style.width.%]="(-s.impact / maxStress) * 50"></span>
                }
              </div>
              <span class="num xs">{{ s.impact === 0 ? "—" : c(s.impact) }}</span>
            </button>
          }
        </div>
      </div>
    </section>

    <section class="grid g-12">
      <div class="card span-12">
        <div class="card-head wrap-head">
          <div>
            <h2>Run custom shock</h2>
            <div class="sub">Instant first-order estimate from equity value, rates duration {{ risk.duration }}y and spread duration {{ spreadDur }}y</div>
          </div>
          <div class="row wrap" style="gap: 6px">
            @for (p of presets; track p.name) {
              <button class="btn-ghost btn-sm" (click)="applyPreset(p)">{{ p.name }}</button>
            }
            <button class="btn-ghost btn-sm" (click)="applyPreset({ name: 'Reset', eq: 0, rates: 0, spread: 0 })" aria-label="Reset shock"><x-icon name="refresh" [size]="14" /></button>
          </div>
        </div>
        <div class="shock">
          <div class="shock-form">
            <label class="field">
              <span class="row between">Equity shock <span class="num" [class.neg]="eq() < 0" [class.pos]="eq() > 0">{{ eq() > 0 ? "+" : "" }}{{ eq() }}%</span></span>
              <input type="range" min="-50" max="30" step="1" [value]="eq()" (input)="eq.set(+$any($event.target).value)" aria-label="Equity shock percent" />
            </label>
            <label class="field">
              <span class="row between">Rates shift <span class="num">{{ rates() > 0 ? "+" : "" }}{{ rates() }} bp</span></span>
              <input type="range" min="-200" max="300" step="5" [value]="rates()" (input)="rates.set(+$any($event.target).value)" aria-label="Rates shift in basis points" />
            </label>
            <label class="field">
              <span class="row between">Credit spread shift <span class="num">{{ spread() > 0 ? "+" : "" }}{{ spread() }} bp</span></span>
              <input type="range" min="-100" max="400" step="5" [value]="spread()" (input)="spread.set(+$any($event.target).value)" aria-label="Spread shift in basis points" />
            </label>
            <div class="xs faint">Exposures: equities {{ eqValue | compact: "EUR" : 2 }} · fixed income {{ fiValue | compact: "EUR" : 2 }} · IG credit {{ igValue | compact: "EUR" : 2 }}</div>
          </div>
          <div class="shock-out">
            <div class="kpi">
              <div class="label">Estimated P&amp;L</div>
              <div class="value" [class.pos]="shock().total > 0" [class.neg]="shock().total < 0">{{ shock().total === 0 ? "€0" : signed(shock().total) }}</div>
              <div class="delta" [class.pos]="shock().total > 0" [class.neg]="shock().total < 0">{{ fmtPct((shock().total / pv) * 100, 2) }} <span class="faint">of portfolio · {{ abs(shock().total) / risk.var99 | num: 1 }}× VaR 99%</span></div>
            </div>
            <div class="parts">
              @for (p of shock().parts; track p.label) {
                <div class="part">
                  <span class="small">{{ p.label }}</span>
                  <div class="div-track">
                    <span class="axis-0"></span>
                    @if (p.value >= 0) {
                      <span class="dbar pos" [style.width.%]="(p.value / shock().max) * 50"></span>
                    } @else {
                      <span class="dbar neg" [style.width.%]="(-p.value / shock().max) * 50"></span>
                    }
                  </div>
                  <span class="num small" [class.pos]="p.value > 0" [class.neg]="p.value < 0">{{ p.value === 0 ? "—" : signed(p.value) }}</span>
                </div>
              }
            </div>
            @if (shock().total < -limit) {
              <div class="callout warn small"><x-icon name="alert" [size]="16" />Loss exceeds the {{ limit | compact: "EUR" : 0 }} VaR limit — Guard would require CFO sign-off on new risk-adding actions.</div>
            }
          </div>
        </div>
      </div>
    </section>
  </div>`,
  styles: [
    `
      .wrap-head { flex-wrap: wrap; }
      .var-stats { display: flex; gap: 28px; flex-wrap: wrap; margin-bottom: 10px; }
      .var-stats > div, .sc-grid > div { display: flex; flex-direction: column; gap: 3px; }
      .var-stats strong { font-size: 17px; font-weight: 600; }
      .factors { display: flex; flex-direction: column; gap: 11px; }
      .factor, .part { display: grid; grid-template-columns: 112px 1fr 48px; align-items: center; gap: 10px; }
      .factor .num, .part .num { text-align: right; }
      .part { grid-template-columns: 110px 1fr 86px; }
      .div-track { position: relative; height: 10px; border-radius: 4px; background: var(--x-surface-2); }
      .axis-0 { position: absolute; left: 50%; top: -3px; bottom: -3px; width: 1px; background: var(--x-border-strong); }
      .dbar { position: absolute; top: 1px; bottom: 1px; border-radius: 3px; transition: width .4s var(--x-ease); }
      .dbar.pos { left: 50%; background: var(--x-pos); }
      .dbar.neg { right: 50%; background: var(--x-neg); }
      .scale { display: flex; align-items: center; gap: 6px; }
      .scale-bar { width: 90px; height: 8px; border-radius: 4px; background: linear-gradient(90deg, var(--x-neg), var(--x-surface-3), var(--x-pos)); }
      .heat-wrap { overflow-x: auto; }
      .heat { display: grid; gap: 3px; min-width: 420px; }
      .hl { font: 500 11px var(--x-font-mono); color: var(--x-text-3); display: flex; align-items: center; transition: color .12s; }
      .hl.on { color: var(--x-text); }
      .col-l { justify-content: center; align-items: flex-end; text-align: center; padding-bottom: 4px; font-size: 10.5px; line-height: 1.25; }
      .row-l { padding-right: 8px; white-space: nowrap; }
      .cell { display: grid; place-items: center; height: 42px; border-radius: 6px; font-size: 11.5px; color: var(--x-text); transition: opacity .12s, transform .12s, box-shadow .12s; cursor: default; }
      .cell.diag { color: var(--x-text-3); background: var(--x-surface-3) !important; }
      .cell.dim { opacity: .38; }
      .cell.focus { transform: scale(1.06); box-shadow: 0 0 0 2px var(--x-text); position: relative; z-index: 1; }
      .hover-note { margin-top: 12px; min-height: 18px; }
      .conc-kpis { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-bottom: 6px; }
      .conc-kpis .kpi { padding: 12px; border-radius: 12px; background: var(--x-surface-2); border: 1px solid var(--x-border); }
      .conc { padding: 8px 0; }
      .rank { width: 22px; height: 22px; display: grid; place-items: center; border-radius: 6px; background: var(--x-surface-3); color: var(--x-text-2); flex-shrink: 0; }
      .sc-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }
      .sc-grid strong { font-size: 17px; font-weight: 600; }
      .cmp { display: flex; flex-direction: column; gap: 2px; }
      .cmp-row { display: grid; grid-template-columns: 130px 1fr 58px; align-items: center; gap: 10px; height: auto; padding: 6px 8px; margin: 0 -8px; border-radius: 8px; background: transparent; color: var(--x-text-2); font-weight: 400; text-align: left; }
      .cmp-row:hover { background: var(--x-hover); color: var(--x-text); }
      .cmp-row.on { background: var(--x-primary-soft); color: var(--x-text); }
      .cmp-row .num { text-align: right; }
      .shock { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1.2fr); gap: 28px; }
      .shock-form { display: flex; flex-direction: column; gap: 16px; }
      .shock-out { display: flex; flex-direction: column; gap: 16px; padding: 16px; border-radius: 14px; background: var(--x-surface-2); border: 1px solid var(--x-border); }
      .parts { display: flex; flex-direction: column; gap: 10px; }
      .callout x-icon { flex-shrink: 0; color: var(--x-warn); }
      @media (max-width: 900px) { .shock { grid-template-columns: minmax(0, 1fr); } }
      @media (max-width: 520px) {
        .factor { grid-template-columns: 96px 1fr 44px; }
        .part { grid-template-columns: 84px 1fr 76px; }
        .cmp-row { grid-template-columns: 100px 1fr 52px; }
        .sc-grid { grid-template-columns: minmax(0, 1fr); }
      }
    `,
  ],
})
export class RiskPage {
  readonly store = inject(DemoStore);
  readonly risk = RISK;
  readonly corr = RISK.correlation;
  readonly stress = STRESS;
  readonly pv = PORTFOLIO_VALUE;
  readonly limit = VAR_LIMIT;
  readonly spreadDur = SPREAD_DURATION;
  readonly presets = PRESETS;
  readonly abs = Math.abs;

  readonly c = (v: number) => compact(v, "EUR", 1);
  readonly signed = (v: number) => (v > 0 ? "+" : "") + compact(v, "EUR", 2);
  readonly fmtPct = (v: number, d = 2) => pct(v, d);
  readonly pctOfPv = (v: number) => ((v / PORTFOLIO_VALUE) * 100).toFixed(2) + "%";

  /* ───── VaR history ───── */
  /** Seeded history rescaled so its last point matches today's reported VaR. */
  private readonly varHist = RISK.varHistory.map((v) => (v * RISK.var95) / RISK.varHistory[RISK.varHistory.length - 1]);
  readonly varNow = RISK.var95;
  readonly varAvg = sum(this.varHist) / this.varHist.length;
  readonly varPeak = Math.max(...this.varHist);
  readonly util = () => (this.varNow / VAR_LIMIT) * 100;
  readonly varSeries: LineSeries[] = [{ name: "VaR 95%", data: this.varHist, color: "var(--x-primary)", area: true }];
  readonly varLabels = RISK.varHistory.map((_, i) =>
    new Date(TODAY - (RISK.varHistory.length - 1 - i) * DAY).toLocaleDateString("en-GB", { day: "2-digit", month: "short" }),
  );

  /* ───── factors ───── */
  readonly maxFactor = Math.max(...RISK.factors.map((f) => Math.abs(f.exposure)));

  /* ───── correlation ───── */
  readonly hover = signal<{ r: number; c: number } | null>(null);
  cellColor(v: number): string {
    const k = Math.round(Math.min(1, Math.abs(v)) * 72);
    return `color-mix(in srgb, ${v >= 0 ? "var(--x-pos)" : "var(--x-neg)"} ${k}%, var(--x-surface-2))`;
  }
  corrWord(v: number): string {
    const a = Math.abs(v);
    if (a >= 0.999) return "identity";
    const strength = a >= 0.6 ? "strong" : a >= 0.3 ? "moderate" : a >= 0.1 ? "weak" : "negligible";
    return strength === "negligible" ? "effectively uncorrelated" : `${strength} ${v > 0 ? "positive" : "negative"} correlation`;
  }

  /* ───── concentration ───── */
  readonly top5 = [...HOLDINGS].sort((a, b) => b.weight - a.weight).slice(0, 5);
  readonly top5Weight = sum(this.top5.map((h) => h.weight));
  readonly effectiveN = 1 / sum(HOLDINGS.map((h) => (h.weight / 100) ** 2));
  readonly largestIssuer = (() => {
    const by: Record<string, number> = {};
    for (const h of HOLDINGS) {
      const k = ISSUER[h.symbol] ?? h.name;
      by[k] = (by[k] ?? 0) + h.value;
    }
    const [name, value] = Object.entries(by).sort((a, b) => b[1] - a[1])[0];
    return { name, value, weight: (value / PORTFOLIO_VALUE) * 100 };
  })();

  /* ───── stress ───── */
  readonly sel = signal(1);
  readonly scenario = computed(() => STRESS[this.sel()]);
  readonly maxStress = Math.max(...STRESS.map((s) => Math.abs(s.impact))) || 1;
  readonly severity = computed(() => {
    const p = this.scenario().impactPct;
    if (p > 0) return "Gain";
    if (p === 0) return "Liquidity only";
    return p <= -6 ? "Severe" : p <= -3 ? "Material" : "Moderate";
  });

  /* ───── custom shock ───── */
  readonly eqValue = classValue("Equity");
  readonly fiValue = classValue("Fixed income");
  readonly igValue = sum(HOLDINGS.filter((h) => h.assetClass === "Fixed income" && h.sector === "Credit").map((h) => h.value));
  readonly dv01 = (RISK.duration * this.fiValue) / 10_000;
  readonly eq = signal(-20);
  readonly rates = signal(100);
  readonly spread = signal(150);
  readonly shock = computed(() => {
    const equity = (this.eqValue * this.eq()) / 100;
    const ratesPnl = (-RISK.duration * this.fiValue * this.rates()) / 10_000;
    const spreadPnl = (-SPREAD_DURATION * this.igValue * this.spread()) / 10_000;
    const parts = [
      { label: "Equities", value: equity },
      { label: "Rates", value: ratesPnl },
      { label: "Credit spread", value: spreadPnl },
    ];
    return { parts, total: equity + ratesPnl + spreadPnl, max: Math.max(1, ...parts.map((p) => Math.abs(p.value))) };
  });

  applyPreset(p: { eq: number; rates: number; spread: number }) {
    this.eq.set(p.eq);
    this.rates.set(p.rates);
    this.spread.set(p.spread);
  }

  exportReport() {
    this.store.toast("pos", "Risk report generated", `VaR 95% ${compact(RISK.var95, "EUR", 1)}, ${STRESS.length} stress scenarios and concentration — demo export only.`);
  }
}
