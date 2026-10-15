import { Component, computed, inject, signal } from "@angular/core";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { ActivatedRoute, RouterLink } from "@angular/router";
import { DemoStore } from "../../core/store";
import { DEFAULT_SCENARIO, FORECAST_BACKTEST, OBLIGATIONS, OPERATING_CASH, entityById, runForecast, toEur } from "../../core/demo-data";
import { ScenarioParams } from "../../core/models";
import { FORMAT_PIPES, compact } from "../../core/format";
import { BarStack, CHARTS, LineSeries } from "../../shared/charts";
import { UI } from "../../shared/ui";

type PresetId = "base" | "vela" | "recession" | "defensive";
interface Preset {
  id: PresetId;
  label: string;
  hint: string;
  params: Partial<ScenarioParams>;
}

const PRESETS: Preset[] = [
  { id: "base", label: "Base", hint: "Current plan", params: {} },
  { id: "vela", label: "Largest customer +30d", hint: "Vela Mobility pays a month late", params: { collectionDelay: 10 } },
  { id: "recession", label: "Recession −10%", hint: "Revenue down 10%, ramping over 3 weeks", params: { revenueShock: -10 } },
  { id: "defensive", label: "Defensive: defer capex", hint: "Push discretionary capex out 6 weeks", params: { deferCapex: true } },
];

const FLOW_COLORS = {
  collections: "var(--x-s2)",
  otherIn: "var(--x-s6)",
  payroll: "var(--x-s3)",
  suppliers: "var(--x-s1)",
  tax: "var(--x-s4)",
  capex: "var(--x-s5)",
  debt: "var(--x-s7)",
};

type ObFilter = "All" | "Receivables" | "Payables";

@Component({
  selector: "x-forecast",
  standalone: true,
  imports: [RouterLink, ...UI, ...CHARTS, ...FORMAT_PIPES],
  template: `<div class="page">
    <x-page-head eyebrow="Treasury · Forecast" title="13-week cash forecast" description="Direct cash forecast built from receivables, payables, payroll, tax, capex and debt schedules. Move the scenario levers to stress the group's operating cash against its minimum buffer.">
      <a class="btn btn-secondary" routerLink="/app/copilot"><x-icon name="copilot" [size]="16" />Explain with Copilot</a>
      <button class="btn-glow" (click)="saveScenario()"><x-icon name="flag" [size]="16" />Save scenario</button>
    </x-page-head>

    <section class="grid kpi5">
      <div class="card kpi glow rise">
        <div class="label"><x-icon name="forecast" [size]="15" />W13 closing balance</div>
        <div class="value">{{ last().closing | compact: "EUR" : 1 }}</div>
        <div class="delta" [class.pos]="last().closing >= opening" [class.neg]="last().closing < opening">
          {{ signed(last().closing - opening) }} <span class="faint">vs today</span>
        </div>
        @if (!isBase()) {
          <div class="xs" [class.pos]="last().closing - baseLast().closing >= 0" [class.neg]="last().closing - baseLast().closing < 0" style="margin-top: 4px">
            {{ signed(last().closing - baseLast().closing) }} <span class="faint">vs base case</span>
          </div>
        }
      </div>
      <div class="card kpi rise" style="animation-delay: 40ms">
        <div class="label"><x-icon name="down" [size]="15" />Lowest P10</div>
        <div class="value">{{ low().p10 | compact: "EUR" : 1 }}</div>
        <div class="delta faint">{{ low().label }} · week of {{ low().start | sdate }}</div>
      </div>
      <div class="card kpi rise" style="animation-delay: 80ms">
        <div class="label"><x-icon name="guard" [size]="15" />Headroom vs buffer</div>
        <div class="value" [class.pos]="headroom() >= 0" [class.neg]="headroom() < 0">{{ signed(headroom()) }}</div>
        <div class="delta faint">P10 low vs {{ buffer() | compact: "EUR" : 0 }} minimum</div>
        <div class="meter" [class.pos]="headroom() >= 2_000_000" [class.warn]="headroom() >= 0 && headroom() < 2_000_000" [class.neg]="headroom() < 0" style="margin-top: 12px">
          <span [style.width.%]="headroomFill()"></span>
        </div>
      </div>
      <div class="card kpi rise" style="animation-delay: 120ms">
        <div class="label"><x-icon name="swap" [size]="15" />13-week flows</div>
        <div class="flows">
          <div><span class="xs faint">In</span><strong class="num pos">{{ totals().inflow | compact: "EUR" : 1 }}</strong></div>
          <div><span class="xs faint">Out</span><strong class="num neg">{{ totals().outflow | compact: "EUR" : 1 }}</strong></div>
        </div>
        <div class="delta" [class.pos]="totals().net >= 0" [class.neg]="totals().net < 0">Net {{ signed(totals().net) }}</div>
      </div>
      <div class="card kpi rise" style="animation-delay: 160ms">
        <div class="label"><x-icon name="alert" [size]="15" />Weeks below buffer</div>
        <div class="value" [class.neg]="belowCount() > 0">{{ belowCount() }} <span class="unit">/ 13</span></div>
        <div class="delta" [class.warn]="p10BelowCount() > 0" [class.faint]="p10BelowCount() === 0">
          {{ p10BelowCount() }} {{ p10BelowCount() === 1 ? "week" : "weeks" }} at risk on P10
        </div>
        <div class="weeks-strip" aria-hidden="true">
          @for (w of weeks(); track w.week) {
            <i [class.below]="w.closing < buffer()" [class.risk]="w.closing >= buffer() && w.p10 < buffer()"></i>
          }
        </div>
      </div>
    </section>

    <section class="grid g-12">
      <div class="card span-4 scenario">
        <div class="card-head">
          <div>
            <h2>Scenario</h2>
            <div class="sub">Recomputes instantly · deterministic model</div>
          </div>
          <button class="btn-ghost btn-sm" (click)="reset()" [disabled]="isBase() && buffer() === defaults.minBuffer"><x-icon name="refresh" [size]="14" />Reset</button>
        </div>

        <div class="presets" role="group" aria-label="Scenario presets">
          @for (p of presets; track p.id) {
            <button class="preset" [class.on]="activePreset() === p.id" (click)="applyPreset(p)" [attr.aria-pressed]="activePreset() === p.id">
              <strong>{{ p.label }}</strong>
              <span class="xs faint">{{ p.hint }}</span>
            </button>
          }
        </div>

        <div class="levers">
          <label class="lever">
            <span class="row between"><span>Collection delay</span><strong class="num">{{ delay() }} days</strong></span>
            <input type="range" min="0" max="21" step="1" [value]="delay()" (input)="delay.set(+$any($event.target).value)" />
            <span class="row between xs faint"><span>On time</span><span>+21d</span></span>
          </label>
          <label class="lever">
            <span class="row between"><span>Revenue shock</span><strong class="num" [class.neg]="revenue() < 0" [class.pos]="revenue() > 0">{{ revenue() > 0 ? "+" : revenue() < 0 ? "−" : "" }}{{ abs(revenue()) }}%</strong></span>
            <input type="range" min="-20" max="10" step="1" [value]="revenue()" (input)="revenue.set(+$any($event.target).value)" />
            <span class="row between xs faint"><span>−20%</span><span>+10%</span></span>
          </label>
          <label class="lever">
            <span class="row between"><span>EUR/USD shock <span class="faint xs">(+ = USD stronger)</span></span><strong class="num" [class.neg]="fx() < 0" [class.pos]="fx() > 0">{{ fx() > 0 ? "+" : fx() < 0 ? "−" : "" }}{{ abs(fx()) }}%</strong></span>
            <input type="range" min="-10" max="10" step="1" [value]="fx()" (input)="fx.set(+$any($event.target).value)" />
            <span class="row between xs faint"><span>−10%</span><span>+10%</span></span>
          </label>
          <div class="lever row between">
            <div>
              <div>Defer discretionary capex</div>
              <div class="xs faint">Moves 4 capex payments out by 6 weeks</div>
            </div>
            <button class="switch" [class.on]="deferCapex()" (click)="deferCapex.set(!deferCapex())" role="switch" [attr.aria-checked]="deferCapex()" aria-label="Defer discretionary capex"></button>
          </div>
          <label class="lever">
            <span class="row between"><span>Minimum buffer</span><strong class="num">{{ buffer() | compact: "EUR" : 0 }}</strong></span>
            <input type="range" min="12000000" max="24000000" step="1000000" [value]="buffer()" (input)="buffer.set(+$any($event.target).value)" />
            <span class="row between xs faint"><span>€12M</span><span>€24M</span></span>
          </label>
        </div>

        <div class="callout" [class.warn]="headroom() < 0" [class.info]="headroom() >= 0" style="margin-top: 14px">
          <x-icon [name]="headroom() < 0 ? 'alert' : 'info'" [size]="16" />
          <div class="small">{{ verdict() }}</div>
        </div>
      </div>

      <div class="card span-8">
        <div class="card-head">
          <div>
            <h2>Closing balance outlook</h2>
            <div class="sub">Opening operating cash {{ opening | compact: "EUR" : 1 }} · P10–P90 confidence band</div>
          </div>
          <div class="legend">
            <span><i style="background: var(--x-primary)"></i>Scenario</span>
            <span><i style="background: var(--x-primary); opacity: 0.35"></i>P10–P90</span>
            @if (!isBase()) {
              <span><i class="dash"></i>Base case</span>
            }
            <span><i style="background: var(--x-neg)"></i>Buffer</span>
          </div>
        </div>
        <x-line-chart [series]="mainSeries()" [labels]="labels()" [band]="band()" [threshold]="threshold()" [height]="410" [xTicks]="13" ariaLabel="13-week closing balance forecast" />
        <div class="heat-head xs faint">P10 headroom vs buffer by week · €M</div>
        <div class="heat" role="list" aria-label="P10 headroom by week">
          @for (w of weeks(); track w.week) {
            <div class="cell" role="listitem" [class.ok]="w.p10 - buffer() >= 2_000_000" [class.thin]="w.p10 - buffer() >= 0 && w.p10 - buffer() < 2_000_000" [class.short]="w.p10 < buffer()" [attr.title]="w.label + ': ' + signed(w.p10 - buffer())">
              <span class="xs mono">{{ w.label }}</span>
              <strong class="num">{{ signedShort(w.p10 - buffer()) }}</strong>
            </div>
          }
        </div>
        <div class="scenario-chips">
          @for (c of chips(); track c) {
            <span class="badge" [class.primary]="!isBase()">{{ c }}</span>
          }
        </div>
      </div>
    </section>

    <section class="grid g-12">
      <div class="card span-8">
        <div class="card-head">
          <div>
            <h2>Weekly cash flows</h2>
            <div class="sub">Inflows above zero, outflows below, by category · line shows weekly net flow</div>
          </div>
        </div>
        <x-bar-chart [labels]="labels()" [stacks]="stacks()" [line]="netLine()" [height]="290" ariaLabel="Weekly inflows and outflows by category" />
        <div class="legend" style="margin-top: 10px">
          @for (s of stacks(); track s.name) {
            <span><i [style.background]="s.color"></i>{{ s.name }}</span>
          }
          <span><i style="background: var(--x-text); height: 3px; vertical-align: 3px"></i>Net flow</span>
        </div>
      </div>

      <div class="card span-4">
        <div class="card-head">
          <div>
            <h2>Forecast accuracy</h2>
            <div class="sub">Backtest · last 8 closed weeks (€M)</div>
          </div>
          <span class="badge" [class.pos]="mape < 2" [class.warn]="mape >= 2">MAPE {{ mape.toFixed(2) }}%</span>
        </div>
        <x-line-chart
          [series]="[
            { name: 'Forecast', data: backtestF, color: 'var(--x-primary)', dashed: true, width: 1.6 },
            { name: 'Actual', data: backtestA, color: 'var(--x-accent)', area: true }
          ]"
          [labels]="backtestLabels" [height]="170" [xTicks]="8" [yFormat]="mFmt" [tipFormat]="mFmt2" ariaLabel="Forecast versus actual backtest" />
        <div class="acc-stats">
          <div><span class="xs faint">MAPE</span><strong class="num">{{ mape.toFixed(2) }}%</strong></div>
          <div><span class="xs faint">Bias</span><strong class="num">{{ bias >= 0 ? "+" : "−" }}{{ abs(bias).toFixed(2) }}%</strong></div>
          <div><span class="xs faint">Within ±1.5%</span><strong class="num">{{ hits }}/{{ backtest.length }}</strong></div>
        </div>
        <div class="xs faint" style="margin-top: 10px">Mean absolute percentage error of week-ahead closing balance. Below 2% is treated as decision-grade.</div>
      </div>
    </section>

    <section class="card flush">
      <div class="card-head">
        <div>
          <h2>Weekly detail</h2>
          <div class="sub">All amounts EUR · rows where cash falls below the {{ buffer() | compact: "EUR" : 0 }} buffer are highlighted</div>
        </div>
        <div class="legend">
          <span><i style="background: var(--x-neg)"></i>Closing below buffer</span>
          <span><i style="background: var(--x-warn)"></i>P10 below buffer</span>
        </div>
      </div>
      <div class="table-wrap" style="margin-top: 12px">
        <table class="table">
          <thead>
            <tr>
              <th>Week</th>
              <th>Starts</th>
              <th class="right">Opening</th>
              <th class="right">Inflows</th>
              <th class="right">Outflows</th>
              <th class="right">Net</th>
              <th class="right">Closing</th>
              <th class="right">P10</th>
              <th class="right">vs buffer</th>
            </tr>
          </thead>
          <tbody>
            @for (w of weeks(); track w.week) {
              <tr [class.below]="w.closing < buffer()" [class.risk]="w.closing >= buffer() && w.p10 < buffer()">
                <td><strong class="mono small">{{ w.label }}</strong> <span class="xs faint">· {{ w.week }}</span></td>
                <td class="nowrap">{{ w.start | sdate }}</td>
                <td class="num nowrap">{{ w.opening | money }}</td>
                <td class="num nowrap pos">{{ w.inflow | money }}</td>
                <td class="num nowrap neg">−{{ w.outflow | money }}</td>
                <td class="num nowrap" [class.pos]="w.net >= 0" [class.neg]="w.net < 0">{{ w.net >= 0 ? "+" : "" }}{{ w.net | money }}</td>
                <td class="num nowrap"><strong>{{ w.closing | money }}</strong></td>
                <td class="num nowrap" [class.warn]="w.p10 < buffer()">{{ w.p10 | money }}</td>
                <td class="num nowrap" [class.pos]="w.p10 - buffer() >= 0" [class.neg]="w.p10 - buffer() < 0">{{ signed(w.p10 - buffer()) }}</td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    </section>

    <section class="card flush">
      <div class="card-head ob-head">
        <div>
          <h2>Obligations feeding the forecast</h2>
          <div class="sub">{{ obligations().length }} items · net {{ signed(obTotal()) }} EUR equivalent{{ store.entity() !== "all" ? " · filtered to " + entityShort(store.entity()) : "" }}</div>
        </div>
        <div class="seg" role="tablist" aria-label="Obligation type">
          @for (f of obFilters; track f) {
            <button [class.on]="obFilter() === f" (click)="obFilter.set(f)" role="tab" [attr.aria-selected]="obFilter() === f">{{ f }}</button>
          }
        </div>
      </div>
      <div class="table-wrap" style="margin-top: 12px">
        <table class="table">
          <thead>
            <tr>
              <th>Type</th>
              <th>Counterparty</th>
              <th>Entity</th>
              <th>Due</th>
              <th class="right">Amount</th>
              <th>Probability</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            @for (o of obligations(); track o.id) {
              <tr>
                <td>
                  <div class="row" style="gap: 8px">
                    <span class="icon-tile sm" [class.pos]="o.type === 'Receivable'" [class.warn]="o.type !== 'Receivable'"><x-icon [name]="o.type === 'Receivable' ? 'down' : 'up'" [size]="13" /></span>
                    <div>
                      <div class="small nowrap">{{ o.type }}</div>
                      <div class="xs faint mono">{{ o.id }}</div>
                    </div>
                  </div>
                </td>
                <td class="nowrap">{{ o.counterparty }}</td>
                <td class="nowrap">{{ entityShort(o.entityId) }}</td>
                <td class="nowrap">{{ o.due | sdate }} <span class="xs faint">· {{ o.due | rel }}</span></td>
                <td class="num nowrap" [class.pos]="o.type === 'Receivable'">{{ o.type === "Receivable" ? "+" : "−" }}{{ o.amount | money: o.ccy }}</td>
                <td>
                  <div class="prob">
                    <div class="meter" [class.pos]="o.probability >= 0.85" [class.warn]="o.probability < 0.85 && o.probability >= 0.7" [class.neg]="o.probability < 0.7"><span [style.width.%]="o.probability * 100"></span></div>
                    <span class="num xs">{{ (o.probability * 100).toFixed(0) }}%</span>
                  </div>
                </td>
                <td><x-status [value]="o.status" /></td>
              </tr>
            } @empty {
              <tr><td colspan="7"><div class="empty">No obligations for this filter.</div></td></tr>
            }
          </tbody>
        </table>
      </div>
    </section>
  </div>`,
  styles: [
    `
      .kpi5 { grid-template-columns: repeat(5, minmax(0, 1fr)); }
      @media (max-width: 1320px) { .kpi5 { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
      @media (max-width: 900px) { .kpi5 { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
      @media (max-width: 520px) { .kpi5 { grid-template-columns: minmax(0, 1fr); } }
      .unit { font: 500 14px var(--x-font); color: var(--x-text-3); }
      .flows { display: flex; gap: 18px; margin: 6px 0 2px; }
      .flows div { display: flex; flex-direction: column; }
      .flows strong { font: 600 20px var(--x-font-mono); }
      .weeks-strip { display: flex; gap: 3px; margin-top: 12px; }
      .weeks-strip i { flex: 1; height: 8px; border-radius: 2px; background: var(--x-pos-soft); border: 1px solid color-mix(in srgb, var(--x-pos) 35%, transparent); }
      .weeks-strip i.risk { background: var(--x-warn-soft); border-color: color-mix(in srgb, var(--x-warn) 50%, transparent); }
      .weeks-strip i.below { background: var(--x-neg); border-color: transparent; }

      .presets { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
      .preset {
        height: auto; display: flex; flex-direction: column; align-items: flex-start; gap: 2px; text-align: left; white-space: normal;
        padding: 9px 11px; border-radius: 10px; background: var(--x-surface-2); color: var(--x-text); border: 1px solid var(--x-border); font-weight: 400;
      }
      .preset strong { font-size: 12.5px; font-weight: 600; }
      .preset:hover { background: var(--x-hover); color: var(--x-text); border-color: var(--x-border-strong); }
      .preset.on { background: var(--x-primary-soft); border-color: color-mix(in srgb, var(--x-primary) 55%, transparent); }
      .levers { display: flex; flex-direction: column; gap: 4px; margin-top: 14px; }
      .lever { display: flex; flex-direction: column; gap: 6px; padding: 11px 0; border-bottom: 1px solid var(--x-border); font-size: 13px; color: var(--x-text); }
      .lever.row { flex-direction: row; }
      .lever:last-child { border-bottom: 0; }
      .lever strong { font-size: 13px; }

      .dash { background: repeating-linear-gradient(90deg, var(--x-text-2) 0 4px, transparent 4px 7px) !important; height: 2px !important; vertical-align: 3px !important; }
      .scenario-chips { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 12px; }
      .heat-head { margin: 14px 0 6px; }
      .heat { display: grid; grid-template-columns: repeat(13, minmax(0, 1fr)); gap: 4px; }
      .cell { display: flex; flex-direction: column; align-items: center; gap: 2px; padding: 7px 2px; border-radius: 8px; background: var(--x-surface-2); border: 1px solid var(--x-border); min-width: 0; }
      .cell span { color: var(--x-text-3); font-size: 10px; }
      .cell strong { font-size: 11.5px; white-space: nowrap; }
      .cell.ok { background: var(--x-pos-soft); border-color: transparent; }
      .cell.ok strong { color: var(--x-pos); }
      .cell.thin { background: var(--x-warn-soft); border-color: transparent; }
      .cell.thin strong { color: var(--x-warn); }
      .cell.short { background: var(--x-neg-soft); border-color: color-mix(in srgb, var(--x-neg) 40%, transparent); }
      .cell.short strong { color: var(--x-neg); }
      @media (max-width: 760px) { .heat { grid-template-columns: repeat(7, minmax(0, 1fr)); } }

      .acc-stats { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; margin-top: 12px; padding-top: 12px; border-top: 1px solid var(--x-border); }
      .acc-stats div { display: flex; flex-direction: column; gap: 2px; }
      .acc-stats strong { font-size: 15px; }

      tr.below td { background: var(--x-neg-soft); }
      tr.below td:first-child { box-shadow: inset 3px 0 0 var(--x-neg); }
      tr.risk td { background: var(--x-warn-soft); }
      tr.risk td:first-child { box-shadow: inset 3px 0 0 var(--x-warn); }

      .ob-head { flex-wrap: wrap; gap: 12px; }
      .icon-tile.sm { width: 26px; height: 26px; border-radius: 8px; }
      .prob { display: flex; align-items: center; gap: 8px; min-width: 110px; }
      .prob .meter { flex: 1; }
    `,
  ],
})
export class ForecastPage {
  readonly store = inject(DemoStore);
  private route = inject(ActivatedRoute);
  readonly presets = PRESETS;
  readonly defaults = DEFAULT_SCENARIO;
  readonly opening = OPERATING_CASH;
  readonly obFilters: ObFilter[] = ["All", "Receivables", "Payables"];

  readonly delay = signal(DEFAULT_SCENARIO.collectionDelay);
  readonly revenue = signal(DEFAULT_SCENARIO.revenueShock);
  readonly fx = signal(DEFAULT_SCENARIO.fxShock);
  readonly deferCapex = signal(DEFAULT_SCENARIO.deferCapex);
  readonly buffer = signal(DEFAULT_SCENARIO.minBuffer);
  readonly obFilter = signal<ObFilter>("All");

  readonly abs = Math.abs;
  readonly signed = (v: number) => (v >= 0 ? "+" : "−") + compact(Math.abs(v), "EUR", 1);
  readonly signedShort = (v: number) => (v >= 0 ? "+" : "−") + (Math.abs(v) / 1e6).toFixed(1);
  readonly mFmt = (v: number) => "€" + v.toFixed(0) + "M";
  readonly mFmt2 = (v: number) => "€" + v.toFixed(1) + "M";

  constructor() {
    this.route.queryParamMap.pipe(takeUntilDestroyed()).subscribe((q) => {
      const p = PRESETS.find((x) => x.id === q.get("scenario"));
      if (p) this.applyPreset(p);
    });
  }

  readonly params = computed<ScenarioParams>(() => ({
    collectionDelay: this.delay(),
    revenueShock: this.revenue(),
    fxShock: this.fx(),
    deferCapex: this.deferCapex(),
    minBuffer: this.buffer(),
  }));
  readonly weeks = computed(() => runForecast(this.params()));
  private readonly baseWeeks = runForecast(DEFAULT_SCENARIO);
  readonly isBase = computed(() => this.delay() === 0 && this.revenue() === 0 && this.fx() === 0 && !this.deferCapex());

  readonly activePreset = computed<PresetId | null>(() => {
    const cur = this.params();
    for (const p of PRESETS) {
      const target = { ...DEFAULT_SCENARIO, ...p.params };
      if (target.collectionDelay === cur.collectionDelay && target.revenueShock === cur.revenueShock && target.fxShock === cur.fxShock && target.deferCapex === cur.deferCapex) return p.id;
    }
    return null;
  });

  readonly labels = computed(() => this.weeks().map((w) => w.label));
  readonly last = computed(() => this.weeks()[12]);
  readonly baseLast = () => this.baseWeeks[12];
  readonly low = computed(() => this.weeks().reduce((m, w) => (w.p10 < m.p10 ? w : m), this.weeks()[0]));
  readonly headroom = computed(() => this.low().p10 - this.buffer());
  readonly headroomFill = computed(() => Math.max(4, Math.min(100, (this.low().p10 / (this.buffer() * 1.6)) * 100)));
  readonly totals = computed(() => {
    const inflow = this.weeks().reduce((s, w) => s + w.inflow, 0);
    const outflow = this.weeks().reduce((s, w) => s + w.outflow, 0);
    return { inflow, outflow, net: inflow - outflow };
  });
  readonly belowCount = computed(() => this.weeks().filter((w) => w.closing < this.buffer()).length);
  readonly p10BelowCount = computed(() => this.weeks().filter((w) => w.p10 < this.buffer()).length);

  readonly mainSeries = computed<LineSeries[]>(() => {
    const s: LineSeries[] = [{ name: this.isBase() ? "Base case" : "Scenario", data: this.weeks().map((w) => w.closing), color: "var(--x-primary)", area: true, width: 2.4 }];
    if (!this.isBase()) s.push({ name: "Base case", data: this.baseWeeks.map((w) => w.closing), color: "var(--x-text-2)", dashed: true, width: 1.5 });
    return s;
  });
  readonly band = computed(() => ({ lo: this.weeks().map((w) => w.p10), hi: this.weeks().map((w) => w.p90), color: "var(--x-primary)" }));
  readonly threshold = computed(() => ({ value: this.buffer(), label: "Minimum buffer " + compact(this.buffer(), "EUR", 0), color: "var(--x-neg)" }));

  readonly stacks = computed<BarStack[]>(() => {
    const w = this.weeks();
    return [
      { name: "Collections", data: w.map((x) => x.collections), color: FLOW_COLORS.collections },
      { name: "Other inflows", data: w.map((x) => x.otherIn), color: FLOW_COLORS.otherIn },
      { name: "Payroll", data: w.map((x) => -x.payroll), color: FLOW_COLORS.payroll },
      { name: "Suppliers", data: w.map((x) => -x.suppliers), color: FLOW_COLORS.suppliers },
      { name: "Tax", data: w.map((x) => -x.tax), color: FLOW_COLORS.tax },
      { name: "Capex", data: w.map((x) => -x.capex), color: FLOW_COLORS.capex },
      { name: "Debt service", data: w.map((x) => -x.debt), color: FLOW_COLORS.debt },
    ];
  });
  readonly netLine = computed<BarStack>(() => ({ name: "Net flow", data: this.weeks().map((w) => w.net), color: "var(--x-text)" }));

  readonly chips = computed(() => {
    const out: string[] = [];
    if (this.delay()) out.push(`Collections +${this.delay()}d`);
    if (this.revenue()) out.push(`Revenue ${this.revenue() > 0 ? "+" : "−"}${Math.abs(this.revenue())}%`);
    if (this.fx()) out.push(`USD ${this.fx() > 0 ? "+" : "−"}${Math.abs(this.fx())}% vs EUR`);
    if (this.deferCapex()) out.push("Capex deferred 6w");
    out.push(`Buffer ${compact(this.buffer(), "EUR", 0)}`);
    if (out.length === 1) out.unshift("Base case");
    return out;
  });

  readonly verdict = computed(() => {
    const low = this.low();
    const h = this.headroom();
    if (h < 0)
      return `P10 falls ${compact(-h, "EUR", 1)} below the buffer in ${low.label}. Consider drawing the revolving facility, deferring capex or accelerating collections before ${new Date(low.start).toLocaleDateString("en-GB", { day: "2-digit", month: "short" })}.`;
    if (h < 2_000_000) return `Headroom is thin: ${compact(h, "EUR", 1)} above the buffer at the ${low.label} low point. Hold surplus sweeps until the week clears.`;
    return `Cash stays ${compact(h, "EUR", 1)} above the buffer even at P10. Surplus above the 13-week peak need can be swept to the liquidity fund.`;
  });

  /* ── backtest ── */
  readonly backtest = FORECAST_BACKTEST;
  readonly backtestF = FORECAST_BACKTEST.map((b) => b.forecast);
  readonly backtestA = FORECAST_BACKTEST.map((b) => b.actual);
  readonly backtestLabels = FORECAST_BACKTEST.map((b) => "W" + b.week);
  readonly mape = (FORECAST_BACKTEST.reduce((s, b) => s + Math.abs(b.forecast - b.actual) / b.actual, 0) / FORECAST_BACKTEST.length) * 100;
  readonly bias = (FORECAST_BACKTEST.reduce((s, b) => s + (b.forecast - b.actual) / b.actual, 0) / FORECAST_BACKTEST.length) * 100;
  readonly hits = FORECAST_BACKTEST.filter((b) => Math.abs(b.forecast - b.actual) / b.actual <= 0.015).length;

  /* ── obligations ── */
  readonly obligations = computed(() => {
    const f = this.obFilter();
    const e = this.store.entity();
    return OBLIGATIONS.filter((o) => (e === "all" || o.entityId === e) && (f === "All" || (f === "Receivables" ? o.type === "Receivable" : o.type !== "Receivable")));
  });
  readonly obTotal = computed(() => this.obligations().reduce((s, o) => s + toEur(o.amount, o.ccy) * (o.type === "Receivable" ? 1 : -1), 0));

  entityShort(id: string) {
    return entityById(id)?.short ?? id;
  }

  applyPreset(p: Preset) {
    const t = { ...DEFAULT_SCENARIO, ...p.params };
    this.delay.set(t.collectionDelay);
    this.revenue.set(t.revenueShock);
    this.fx.set(t.fxShock);
    this.deferCapex.set(t.deferCapex);
  }

  reset() {
    this.applyPreset(PRESETS[0]);
    this.buffer.set(DEFAULT_SCENARIO.minBuffer);
  }

  saveScenario() {
    const name = this.chips().join(" · ");
    this.store.log(this.store.org.persona.name, "human", "forecast.scenario_saved", "13-week forecast", name);
    this.store.toast("pos", "Scenario saved", `${name}. Low point ${compact(this.low().p10, "EUR", 1)} in ${this.low().label}.`);
  }
}
