import { Component, HostListener, computed, inject, signal } from "@angular/core";
import { RouterLink } from "@angular/router";
import { DemoStore } from "../../core/store";
import { FX, TODAY, entityById, toEur } from "../../core/demo-data";
import { Account, AccountType, Ccy } from "../../core/models";
import { FORMAT_PIPES, compact, pct } from "../../core/format";
import { CHARTS, LineSeries } from "../../shared/charts";
import { UI } from "../../shared/ui";

const DAY = 86_400_000;
const PALETTE = ["var(--x-s1)", "var(--x-s2)", "var(--x-s3)", "var(--x-s4)", "var(--x-s5)", "var(--x-s6)", "var(--x-s7)", "var(--x-primary)"];
/** Indicative annual yield by account type (%), simulated — used for the idle-cash estimate. */
const YIELD: Record<AccountType, number> = { Operating: 0.25, Payroll: 0, Collections: 0.1, Reserve: 1.4, "Money market": 3.41, Brokerage: 0.5 };
const CONCENTRATION_LIMIT = 35;
const OPERATING_TYPES: AccountType[] = ["Operating", "Payroll", "Collections"];

type GroupBy = "Entity" | "Currency" | "Bank";
type SortKey = "bank" | "entity" | "name" | "ccy" | "balance" | "eur" | "change7d" | "sync";

interface Row extends Account {
  entityShort: string;
  eur: number;
  spark: number[];
}

@Component({
  selector: "x-liquidity",
  standalone: true,
  imports: [RouterLink, ...UI, ...CHARTS, ...FORMAT_PIPES],
  template: `<div class="page">
    <x-page-head eyebrow="Treasury · Liquidity" title="Liquidity" description="Every bank, money-market and brokerage account across the group in one position, converted to EUR. Balances are simulated and refresh from bank connectors.">
      <a class="btn btn-secondary" routerLink="/app/connect"><x-icon name="connect" [size]="16" />Connectors</a>
      <button class="btn-glow" (click)="exportPosition()"><x-icon name="download" [size]="16" />Export position</button>
    </x-page-head>

    <section class="grid kpi5">
      <div class="card kpi glow rise">
        <div class="label"><x-icon name="liquidity" [size]="15" />Total liquidity</div>
        <div class="value">{{ total() | compact: "EUR" : 2 }}</div>
        <div class="delta" [class.pos]="change7d() >= 0" [class.neg]="change7d() < 0">{{ fmtPct(change7d()) }} <span class="faint">vs 7 days</span></div>
        <x-spark [data]="history().slice(-30)" color="var(--x-accent)" [height]="30" style="margin-top: 10px" />
      </div>
      <div class="card kpi rise" style="animation-delay: 40ms">
        <div class="label"><x-icon name="wallet" [size]="15" />Available vs ledger</div>
        <div class="value">{{ available() | compact: "EUR" : 2 }}</div>
        <div class="delta faint">{{ availPct().toFixed(1) }}% of ledger · {{ total() - available() | compact: "EUR" : 1 }} held</div>
        <div class="meter pos" style="margin-top: 14px"><span [style.width.%]="availPct()"></span></div>
      </div>
      <div class="card kpi rise" style="animation-delay: 80ms">
        <div class="label"><x-icon name="bank" [size]="15" />Operating cash</div>
        <div class="value">{{ operating() | compact: "EUR" : 2 }}</div>
        <div class="delta faint">Operating, payroll &amp; collections · funds the forecast</div>
        <div class="meter" style="margin-top: 14px"><span [style.width.%]="total() ? (operating() / total()) * 100 : 0"></span></div>
      </div>
      <div class="card kpi rise" style="animation-delay: 120ms">
        <div class="label"><x-icon name="target" [size]="15" />Idle cash &lt; 1% yield</div>
        <div class="value warn">{{ idle().value | compact: "EUR" : 1 }}</div>
        <div class="delta faint">{{ idle().share.toFixed(0) }}% of liquidity · ≈{{ idle().uplift | compact: "EUR" : 0 }}/yr at 3.41% MMF</div>
        <div class="meter warn" style="margin-top: 14px"><span [style.width.%]="idle().share"></span></div>
      </div>
      <div class="card kpi rise" style="animation-delay: 160ms">
        <div class="label"><x-icon name="refresh" [size]="15" />Account feeds</div>
        <div class="value">{{ rows().length }} <span class="unit">accounts</span></div>
        <div class="sync-counts">
          <span class="badge pos"><span class="dot live"></span>{{ syncCounts().live }} live</span>
          <span class="badge warn"><span class="dot"></span>{{ syncCounts().delayed }} delayed</span>
          <span class="badge info"><span class="dot"></span>{{ syncCounts().manual }} manual</span>
        </div>
      </div>
    </section>

    <section class="grid g-12">
      <div class="card span-8">
        <div class="card-head">
          <div>
            <h2>Balances · 90 days</h2>
            <div class="sub">Daily closing balance by {{ groupBy().toLowerCase() }}, EUR equivalent</div>
          </div>
          <div class="seg" role="tablist" aria-label="Group balances by">
            @for (g of groupings; track g) {
              <button [class.on]="groupBy() === g" (click)="groupBy.set(g)" role="tab" [attr.aria-selected]="groupBy() === g">{{ g }}</button>
            }
          </div>
        </div>
        <x-line-chart [series]="groupSeries()" [labels]="dayLabels" [height]="330" [xTicks]="6" ariaLabel="Balances by group over 90 days" />
        <div class="legend" style="margin-top: 10px">
          @for (s of groupSeries(); track s.name) {
            <span><i [style.background]="s.color"></i>{{ s.name }} <span class="num faint">{{ s.data[s.data.length - 1] | compact: "EUR" : 1 }}</span></span>
          }
        </div>
      </div>

      <div class="card span-4">
        <div class="card-head">
          <div>
            <h2>Bank concentration</h2>
            <div class="sub">Share of group cash per bank · policy limit {{ limit }}%</div>
          </div>
          <a class="btn btn-ghost btn-sm" routerLink="/app/guard">Policy <x-icon name="arrow" [size]="14" /></a>
        </div>
        <div class="conc">
          @for (b of banks(); track b.bank) {
            <div class="conc-row">
              <div class="row between small">
                <span class="truncate">{{ b.bank }}</span>
                <span class="num" [class.warn]="b.tone === 'warn'" [class.neg]="b.tone === 'neg'">{{ b.share.toFixed(1) }}%</span>
              </div>
              <div class="conc-track">
                <div class="meter" [class.pos]="b.tone === 'ok'" [class.warn]="b.tone === 'warn'" [class.neg]="b.tone === 'neg'">
                  <span [style.width.%]="(b.share / 50) * 100"></span>
                </div>
                <i class="limit" [style.left.%]="(limit / 50) * 100" aria-hidden="true"></i>
              </div>
              <div class="xs faint">{{ b.value | compact: "EUR" : 1 }} · {{ b.accounts }} {{ b.accounts === 1 ? "account" : "accounts" }}</div>
            </div>
          }
        </div>
        @if (nearLimit().length) {
          <div class="callout warn" style="margin-top: 14px">
            <x-icon name="alert" [size]="16" />
            <div class="small">{{ nearLimit().join(", ") }} {{ nearLimit().length === 1 ? "is" : "are" }} within 7 pts of the {{ limit }}% limit. New deposits there will be flagged by Guard.</div>
          </div>
        }
      </div>
    </section>

    <section class="card flush">
      <div class="card-head acc-head">
        <div>
          <h2>Accounts</h2>
          <div class="sub">{{ filtered().length }} of {{ rows().length }} accounts · click a row for history and transactions</div>
        </div>
        <div class="row wrap tools">
          <label class="search">
            <x-icon name="search" [size]="15" />
            <input class="input" type="search" placeholder="Search bank, account, IBAN" [value]="query()" (input)="query.set($any($event.target).value)" aria-label="Search accounts" />
          </label>
          <select class="input" [value]="typeFilter()" (change)="typeFilter.set($any($event.target).value)" aria-label="Filter by account type">
            <option value="all">All types</option>
            @for (t of types; track t) {
              <option [value]="t">{{ t }}</option>
            }
          </select>
        </div>
      </div>
      <div class="table-wrap" style="margin-top: 12px">
        <table class="table">
          <thead>
            <tr>
              @for (c of columns; track c.key) {
                <th [class.right]="c.right" [attr.aria-sort]="sortKey() === c.key ? (sortDir() === 1 ? 'ascending' : 'descending') : null">
                  <button class="sort" [class.on]="sortKey() === c.key" (click)="sort(c.key)">
                    {{ c.label }}
                    @if (sortKey() === c.key) {
                      <x-icon name="chevronDown" [size]="12" [class.flip]="sortDir() === 1" />
                    }
                  </button>
                </th>
              }
              <th class="right">7d trend</th>
            </tr>
          </thead>
          <tbody>
            @for (a of filtered(); track a.id) {
              <tr class="clickable" [class.selected]="selectedId() === a.id" (click)="open(a.id)" (keydown.enter)="open(a.id)" tabindex="0" [attr.aria-label]="'Open ' + a.bank + ' ' + a.name">
                <td>
                  <div class="row" style="gap: 10px">
                    <span class="avatar bank-av">{{ initials(a.bank) }}</span>
                    <div style="min-width: 0">
                      <div class="nowrap"><strong class="small">{{ a.bank }}</strong></div>
                      <div class="xs faint mono nowrap">{{ a.iban }}</div>
                    </div>
                  </div>
                </td>
                <td class="nowrap">{{ a.entityShort }}</td>
                <td>
                  <div class="nowrap">{{ a.name }}</div>
                  <div class="xs faint">{{ a.type }}</div>
                </td>
                <td><span class="badge">{{ a.ccy }}</span></td>
                <td class="num nowrap">{{ a.balance | money: a.ccy }}</td>
                <td class="num nowrap">{{ a.eur | money: "EUR" }}</td>
                <td class="num nowrap" [class.pos]="a.change7d >= 0" [class.neg]="a.change7d < 0">{{ fmtPct(a.change7d) }}</td>
                <td class="nowrap">
                  <x-status [value]="a.status" />
                  <div class="xs faint" style="margin-top: 3px">{{ a.lastSync | rel }}</div>
                </td>
                <td class="right"><div class="spark-cell"><x-spark [data]="a.spark" [color]="a.change7d >= 0 ? 'var(--x-pos)' : 'var(--x-neg)'" [height]="26" /></div></td>
              </tr>
            } @empty {
              <tr><td colspan="9"><div class="empty">No accounts match “{{ query() }}”.</div></td></tr>
            }
          </tbody>
          @if (filtered().length) {
            <tfoot>
              <tr>
                <td colspan="5" class="small faint">Total · {{ filtered().length }} accounts</td>
                <td class="num nowrap"><strong>{{ filteredTotal() | money: "EUR" }}</strong></td>
                <td colspan="3"></td>
              </tr>
            </tfoot>
          }
        </table>
      </div>
    </section>

    <section class="card fx-strip">
      <div class="row" style="gap: 10px">
        <span class="icon-tile accent"><x-icon name="globe" [size]="16" /></span>
        <div>
          <strong class="small">FX rates</strong>
          <div class="xs faint">EUR base · simulated reference</div>
        </div>
      </div>
      @for (f of fxRows; track f.ccy) {
        <div class="fx">
          <span class="xs faint mono">EUR/{{ f.ccy }}</span>
          <span class="num fx-rate">{{ f.rate.toFixed(4) }}</span>
          <span class="xs faint">1 {{ f.ccy }} = €{{ (1 / f.rate).toFixed(4) }}</span>
          <span class="xs muted">Exposure {{ exposure()[f.ccy] | compact: "EUR" : 1 }}</span>
        </div>
      }
    </section>

    @if (selected(); as acc) {
      <div class="scrim" (click)="close()" aria-hidden="true"></div>
      <aside class="drawer" role="dialog" aria-modal="true" [attr.aria-label]="acc.bank + ' ' + acc.name">
        <div class="drawer-head">
          <div class="row" style="gap: 12px; min-width: 0">
            <span class="avatar bank-av lg">{{ initials(acc.bank) }}</span>
            <div style="min-width: 0">
              <div class="eyebrow">{{ acc.entityShort }} · {{ acc.type }}</div>
              <h2 class="truncate">{{ acc.bank }} · {{ acc.name }}</h2>
              <div class="xs faint mono">{{ acc.iban }} · {{ acc.ccy }}</div>
            </div>
          </div>
          <button id="drawer-close" class="btn-ghost btn-icon" (click)="close()" aria-label="Close account details"><x-icon name="x" [size]="18" /></button>
        </div>

        <div class="drawer-body">
          <div class="row wrap" style="gap: 8px">
            <x-status [value]="acc.status" [label]="acc.status + ' · ' + (acc.lastSync | rel)" />
            <span class="badge">Yield ≈ {{ yieldOf(acc).toFixed(2) }}%</span>
            <span class="badge" [class.pos]="acc.change7d >= 0" [class.neg]="acc.change7d < 0">{{ fmtPct(acc.change7d) }} 7d</span>
          </div>

          <div class="d-kpis">
            <div>
              <div class="xs faint">Ledger balance</div>
              <div class="num d-val">{{ acc.balance | money: acc.ccy }}</div>
              @if (acc.ccy !== "EUR") {
                <div class="xs faint num">{{ acc.eur | money: "EUR" }}</div>
              }
            </div>
            <div>
              <div class="xs faint">Available</div>
              <div class="num d-val">{{ acc.available | money: acc.ccy }}</div>
              <div class="xs faint num">{{ acc.balance - acc.available | money: acc.ccy }} held / in float</div>
            </div>
          </div>
          <div class="meter pos"><span [style.width.%]="(acc.available / acc.balance) * 100"></span></div>
          <div class="xs faint" style="margin-top: 6px">{{ ((acc.available / acc.balance) * 100).toFixed(1) }}% of ledger balance is available for same-day use</div>

          <h3 class="d-title">Balance history · 90 days</h3>
          <x-line-chart [series]="[{ name: acc.name, data: acc.history, color: 'var(--x-accent)', area: true }]" [labels]="dayLabels" [height]="190" [xTicks]="4" [yFormat]="localFmt(acc.ccy, 1)" [tipFormat]="localFmt(acc.ccy, 2)" ariaLabel="Account balance history" />

          <h3 class="d-title">Recent transactions</h3>
          <div class="list">
            @for (t of accountTxns(); track t.id) {
              <div class="list-item txn">
                <span class="icon-tile" [class.pos]="t.amount > 0" [class.neg]="t.amount < 0"><x-icon [name]="t.amount > 0 ? 'down' : 'up'" [size]="15" /></span>
                <div class="grow">
                  <div class="small truncate"><strong>{{ t.counterparty }}</strong></div>
                  <div class="xs faint truncate">{{ t.date | sdate }} · {{ t.memo }}</div>
                </div>
                <div class="t-right">
                  <span class="num small" [class.pos]="t.amount > 0">{{ t.amount > 0 ? "+" : "" }}{{ t.amount | money: t.ccy }}</span>
                  <x-status [value]="t.status" />
                </div>
              </div>
            } @empty {
              <div class="empty small">No transactions in the last 30 days. {{ acc.type === "Money market" || acc.type === "Reserve" ? "Reserve and fund balances move only on sweeps." : "" }}</div>
            }
          </div>
          @if (accountTxns().length) {
            <a class="btn btn-ghost btn-sm" routerLink="/app/ledger" style="margin-top: 8px">Open in ledger <x-icon name="arrow" [size]="14" /></a>
          }
        </div>

        <div class="drawer-foot">
          <div class="xs faint grow">Sweeps are proposed, evaluated by Guard and approved before execution.</div>
          <button class="btn-secondary" (click)="close()">Close</button>
          <button class="btn-glow" (click)="requestSweep(acc)"><x-icon name="swap" [size]="16" />Request sweep</button>
        </div>
      </aside>
    }
  </div>`,
  styles: [
    `
      .kpi5 { grid-template-columns: repeat(5, minmax(0, 1fr)); }
      @media (max-width: 1320px) { .kpi5 { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
      @media (max-width: 900px) { .kpi5 { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
      @media (max-width: 520px) { .kpi5 { grid-template-columns: minmax(0, 1fr); } }
      .unit { font: 500 14px var(--x-font); color: var(--x-text-3); }
      .sync-counts { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 10px; }

      .conc { display: flex; flex-direction: column; gap: 11px; }
      .conc-row { display: flex; flex-direction: column; gap: 6px; }
      .conc-track { position: relative; }
      .limit { position: absolute; top: -3px; bottom: -3px; width: 2px; border-radius: 2px; background: var(--x-neg); opacity: .7; }

      .acc-head { flex-wrap: wrap; gap: 12px; }
      .tools { gap: 8px; }
      .search { position: relative; display: flex; align-items: center; }
      .search x-icon { position: absolute; left: 10px; color: var(--x-text-3); pointer-events: none; }
      .search .input { padding-left: 32px; width: 240px; max-width: 100%; }
      @media (max-width: 520px) { .tools, .search, .search .input, .tools select { width: 100%; } }

      .sort { height: auto; padding: 0; background: none; border: 0; color: inherit; font: inherit; letter-spacing: inherit; text-transform: inherit; display: inline-flex; align-items: center; gap: 4px; cursor: pointer; }
      .sort:hover, .sort.on { background: none; color: var(--x-text); }
      .sort:active { transform: none; }
      .flip { transform: rotate(180deg); }
      x-icon.flip { display: inline-flex; }
      .bank-av { width: 30px; height: 30px; font-size: 10.5px; background: var(--x-primary-soft); color: var(--x-primary); }
      .bank-av.lg { width: 40px; height: 40px; font-size: 13px; }
      .spark-cell { width: 84px; margin-left: auto; }
      tfoot td { padding: 12px 14px; border-top: 1px solid var(--x-border); background: var(--x-surface-2); }

      .fx-strip { display: flex; align-items: center; gap: 18px; flex-wrap: wrap; justify-content: space-between; }
      .fx { display: grid; gap: 2px; padding-left: 18px; border-left: 1px solid var(--x-border); min-width: 140px; }
      .fx-rate { font: 600 18px var(--x-font-mono); }
      @media (max-width: 760px) { .fx { border-left: 0; padding-left: 0; } }

      .scrim { position: fixed; inset: 0; z-index: 90; background: rgba(3, 6, 14, 0.45); backdrop-filter: blur(2px); animation: fade .2s ease both; }
      .drawer {
        position: fixed; top: 0; right: 0; bottom: 0; z-index: 91; width: min(520px, 100vw);
        display: flex; flex-direction: column; background: var(--x-surface); border-left: 1px solid var(--x-border-strong);
        box-shadow: var(--x-shadow-pop); animation: slide .28s var(--x-ease) both;
      }
      .drawer-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; padding: 18px 18px 14px; border-bottom: 1px solid var(--x-border); }
      .drawer-head h2 { font-size: 17px; margin-top: 4px; }
      .drawer-body { flex: 1; overflow-y: auto; padding: 16px 18px 20px; }
      .drawer-foot { display: flex; align-items: center; gap: 8px; padding: 14px 18px; border-top: 1px solid var(--x-border); background: var(--x-surface-2); flex-wrap: wrap; }
      .d-kpis { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; margin: 16px 0 12px; }
      .d-val { font: 600 19px var(--x-font-mono); margin-top: 2px; }
      .d-title { margin: 22px 0 10px; font-size: 13px; color: var(--x-text-2); }
      .txn .t-right { display: flex; flex-direction: column; align-items: flex-end; gap: 4px; }
      .txn .icon-tile { width: 30px; height: 30px; }
      @keyframes slide { from { transform: translateX(24px); opacity: 0; } to { transform: none; opacity: 1; } }
      @keyframes fade { from { opacity: 0; } to { opacity: 1; } }
      @media (prefers-reduced-motion: reduce) { .drawer, .scrim { animation: none; } }
    `,
  ],
})
export class LiquidityPage {
  readonly store = inject(DemoStore);
  readonly limit = CONCENTRATION_LIMIT;
  readonly groupings: GroupBy[] = ["Entity", "Currency", "Bank"];
  readonly types: AccountType[] = ["Operating", "Payroll", "Collections", "Reserve", "Money market", "Brokerage"];
  readonly columns: { key: SortKey; label: string; right?: boolean }[] = [
    { key: "bank", label: "Bank" },
    { key: "entity", label: "Entity" },
    { key: "name", label: "Account" },
    { key: "ccy", label: "Ccy" },
    { key: "balance", label: "Balance", right: true },
    { key: "eur", label: "EUR equiv.", right: true },
    { key: "change7d", label: "7d", right: true },
    { key: "sync", label: "Sync" },
  ];
  readonly fxRows = (["USD", "GBP", "CHF"] as Ccy[]).map((ccy) => ({ ccy, rate: FX[ccy] }));
  readonly dayLabels = Array.from({ length: 90 }, (_, i) => new Date(TODAY - (89 - i) * DAY).toLocaleDateString("en-GB", { day: "2-digit", month: "short" }));

  readonly groupBy = signal<GroupBy>("Entity");
  readonly query = signal("");
  readonly typeFilter = signal<string>("all");
  readonly sortKey = signal<SortKey>("eur");
  readonly sortDir = signal<1 | -1>(-1);
  readonly selectedId = signal<string | null>(null);

  readonly fmtPct = (v: number) => pct(v, 1);
  localFmt(ccy: Ccy, d: number) {
    return (v: number) => compact(v, ccy, d);
  }

  readonly rows = computed<Row[]>(() =>
    this.store.accounts().map((a) => ({ ...a, entityShort: entityById(a.entityId).short, eur: toEur(a.balance, a.ccy), spark: a.history.slice(-8) })),
  );
  readonly total = computed(() => this.rows().reduce((s, a) => s + a.eur, 0));
  readonly available = computed(() => this.rows().reduce((s, a) => s + toEur(a.available, a.ccy), 0));
  readonly availPct = computed(() => (this.total() ? (this.available() / this.total()) * 100 : 0));
  readonly operating = computed(() => this.rows().filter((a) => OPERATING_TYPES.includes(a.type)).reduce((s, a) => s + a.eur, 0));
  readonly idle = computed(() => {
    const idle = this.rows().filter((a) => YIELD[a.type] < 1);
    const value = idle.reduce((s, a) => s + a.eur, 0);
    const uplift = idle.reduce((s, a) => s + (a.eur * (3.41 - YIELD[a.type])) / 100, 0);
    return { value, uplift, share: this.total() ? (value / this.total()) * 100 : 0 };
  });
  readonly syncCounts = computed(() => {
    const c = { live: 0, delayed: 0, manual: 0 };
    for (const a of this.rows()) c[a.status]++;
    return c;
  });

  readonly history = computed(() => {
    const accs = this.rows();
    return Array.from({ length: 90 }, (_, d) => accs.reduce((s, a) => s + toEur(a.history[d], a.ccy), 0));
  });
  readonly change7d = computed(() => {
    const h = this.history();
    return h[82] ? ((h[89] - h[82]) / h[82]) * 100 : 0;
  });

  readonly groupSeries = computed<LineSeries[]>(() => {
    const mode = this.groupBy();
    const groups = new Map<string, Row[]>();
    for (const a of this.rows()) {
      const k = mode === "Entity" ? a.entityShort : mode === "Currency" ? a.ccy : a.bank;
      groups.set(k, [...(groups.get(k) ?? []), a]);
    }
    return [...groups.entries()]
      .map(([name, accs]) => ({ name, data: Array.from({ length: 90 }, (_, d) => accs.reduce((s, a) => s + toEur(a.history[d], a.ccy), 0)) }))
      .sort((a, b) => b.data[89] - a.data[89])
      .map((s, i) => ({ ...s, color: PALETTE[i % PALETTE.length], width: 1.8 }));
  });

  readonly banks = computed(() => {
    const total = this.total() || 1;
    const by = new Map<string, { value: number; accounts: number }>();
    for (const a of this.rows()) {
      const cur = by.get(a.bank) ?? { value: 0, accounts: 0 };
      by.set(a.bank, { value: cur.value + a.eur, accounts: cur.accounts + 1 });
    }
    return [...by.entries()]
      .map(([bank, v]) => {
        const share = (v.value / total) * 100;
        const tone = share > CONCENTRATION_LIMIT ? "neg" : share >= CONCENTRATION_LIMIT - 7 ? "warn" : "ok";
        return { bank, ...v, share, tone };
      })
      .sort((a, b) => b.value - a.value);
  });
  readonly nearLimit = computed(() => this.banks().filter((b) => b.tone !== "ok").map((b) => b.bank));

  readonly exposure = computed(() => {
    const by: Record<string, number> = { USD: 0, GBP: 0, CHF: 0 };
    for (const a of this.rows()) if (a.ccy !== "EUR") by[a.ccy] += a.eur;
    return by;
  });

  readonly filtered = computed(() => {
    const q = this.query().trim().toLowerCase();
    const t = this.typeFilter();
    const key = this.sortKey();
    const dir = this.sortDir();
    const list = this.rows().filter(
      (a) => (t === "all" || a.type === t) && (!q || `${a.bank} ${a.name} ${a.iban} ${a.entityShort} ${a.ccy} ${a.type}`.toLowerCase().includes(q)),
    );
    const val = (a: Row): string | number =>
      key === "bank" ? a.bank : key === "entity" ? a.entityShort : key === "name" ? a.name : key === "ccy" ? a.ccy : key === "balance" ? a.balance : key === "eur" ? a.eur : key === "change7d" ? a.change7d : a.status;
    return [...list].sort((a, b) => {
      const x = val(a);
      const y = val(b);
      return (typeof x === "number" ? x - (y as number) : x.localeCompare(y as string)) * dir;
    });
  });
  readonly filteredTotal = computed(() => this.filtered().reduce((s, a) => s + a.eur, 0));

  readonly selected = computed(() => this.rows().find((a) => a.id === this.selectedId()) ?? null);
  readonly accountTxns = computed(() => {
    const id = this.selectedId();
    return id ? this.store.transactions().filter((t) => t.accountId === id).slice(0, 8) : [];
  });

  sort(k: SortKey) {
    if (this.sortKey() === k) this.sortDir.set(this.sortDir() === 1 ? -1 : 1);
    else {
      this.sortKey.set(k);
      this.sortDir.set(["bank", "entity", "name", "ccy", "sync"].includes(k) ? 1 : -1);
    }
  }

  open(id: string) {
    this.selectedId.set(id);
    setTimeout(() => document.getElementById("drawer-close")?.focus(), 30);
  }
  @HostListener("document:keydown.escape")
  close() {
    this.selectedId.set(null);
  }

  yieldOf(a: Account) {
    return YIELD[a.type];
  }

  initials(bank: string) {
    return bank
      .split(/[\s&]+/)
      .filter(Boolean)
      .map((p) => p[0])
      .join("")
      .slice(0, 2)
      .toUpperCase();
  }

  requestSweep(a: Row) {
    const surplus = Math.max(0, a.available - a.balance * 0.4);
    const target = a.type === "Money market" ? "group operating" : "Lumen EUR liquidity fund";
    this.store.log(this.store.org.persona.name, "human", "sweep.requested", a.id, `Sweep of ${compact(surplus, a.ccy, 1)} to ${target} requested`);
    this.store.toast("info", "Sweep request drafted", `${compact(surplus, a.ccy, 1)} from ${a.bank} · ${a.name} to ${target}. Guard will evaluate it before it reaches approvers.`);
  }

  exportPosition() {
    this.store.toast("pos", "Cash position exported", `${this.rows().length} accounts · ${compact(this.total(), "EUR", 2)} total — demo export only.`);
  }
}
