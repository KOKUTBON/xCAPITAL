import { Component, computed, inject, signal } from "@angular/core";
import { RouterLink } from "@angular/router";
import { DemoStore } from "../../core/store";
import { JOURNALS, TODAY, accountById, entityById, rng, toEur } from "../../core/demo-data";
import { Journal, Txn, TxnStatus } from "../../core/models";
import { FORMAT_PIPES } from "../../core/format";
import { CHARTS, DonutSegment } from "../../shared/charts";
import { UI } from "../../shared/ui";

const DAY = 86_400_000;
const PAGE = 40;
const CAT_COLORS: Record<string, string> = {
  "Customer receipt": "var(--x-s2)",
  "Supplier payment": "var(--x-s1)",
  Payroll: "var(--x-s3)",
  Tax: "var(--x-s4)",
  Intercompany: "var(--x-s5)",
  "Interest income": "var(--x-s6)",
  "Operating expense": "var(--x-s7)",
  Fees: "var(--x-primary)",
};

type Tab = "Exceptions" | "Transactions" | "Journals";
type StatusFilter = "all" | TxnStatus;

interface Factor {
  label: string;
  score: number;
  detail: string;
}
interface Suggestion {
  ref: string;
  doc: string;
  amount: number;
  date: string;
  score: number;
  factors: Factor[];
  recommend: "match" | "adjust";
  rationale: string;
}

@Component({
  selector: "x-ledger",
  standalone: true,
  imports: [RouterLink, ...UI, ...CHARTS, ...FORMAT_PIPES],
  template: `<div class="page">
    <x-page-head eyebrow="Treasury · Ledger" title="Ledger &amp; reconciliation" description="Bank, ERP and file transactions normalised into one ledger, auto-matched to invoices and bills, and posted as balanced double-entry journals. Exceptions wait for a human decision.">
      <a class="btn btn-secondary" routerLink="/app/audit"><x-icon name="audit" [size]="16" />Evidence</a>
      <button class="btn-glow" (click)="runMatching()"><x-icon name="bolt" [size]="16" />Run auto-match</button>
    </x-page-head>

    <section class="grid kpi5">
      <div class="card kpi glow rise">
        <div class="label"><x-icon name="ledger" [size]="15" />Reconciliation rate</div>
        <div class="value">{{ store.reconRate().toFixed(1) }}%</div>
        <div class="delta faint">Reconciled or matched · all entities</div>
        <div class="meter pos" style="margin-top: 12px"><span [style.width.%]="store.reconRate()"></span></div>
      </div>
      <div class="card kpi rise" style="animation-delay: 40ms">
        <div class="label"><x-icon name="bolt" [size]="15" />Auto-match rate</div>
        <div class="value">{{ autoRate().toFixed(1) }}%</div>
        <div class="delta faint">Of normalised items, no human touch · {{ manualCount() }} manual this session</div>
        <div class="meter grad" style="margin-top: 12px"><span [style.width.%]="autoRate()"></span></div>
      </div>
      <div class="card kpi rise" style="animation-delay: 80ms">
        <div class="label"><x-icon name="alert" [size]="15" />Open exceptions</div>
        <div class="value" [class.warn]="exceptions().length > 0" [class.pos]="exceptions().length === 0">{{ exceptions().length }}</div>
        <div class="delta faint">{{ exceptionValue() | compact: "EUR" : 2 }} gross value</div>
      </div>
      <div class="card kpi rise" style="animation-delay: 120ms">
        <div class="label"><x-icon name="layers" [size]="15" />Transactions · 30 days</div>
        <div class="value">{{ txns30() }}</div>
        <div class="delta faint">{{ sourceMix() }}</div>
      </div>
      <div class="card kpi rise" style="animation-delay: 160ms">
        <div class="label"><x-icon name="file" [size]="15" />Journals posted</div>
        <div class="value">{{ journals().length + manualCount() }}</div>
        <div class="delta pos">100% balanced <span class="faint">· debits = credits</span></div>
      </div>
    </section>

    <section class="grid g-12">
      <div class="card span-7">
        <div class="card-head">
          <div>
            <h2>Reconciliation funnel</h2>
            <div class="sub">From raw feed to reconciled ledger · {{ store.entity() === "all" ? "all entities" : entityShort(store.entity()) }}</div>
          </div>
        </div>
        <div class="funnel">
          @for (f of funnel(); track f.label; let i = $index) {
            <div class="stage">
              <div class="stage-head">
                <span class="step mono">{{ i + 1 }}</span>
                <strong class="small">{{ f.label }}</strong>
                <span class="xs faint grow truncate">{{ f.hint }}</span>
                <span class="num">{{ f.count }}</span>
                @if (i > 0) {
                  <span class="badge conv">{{ f.conv.toFixed(0) }}%</span>
                }
              </div>
              <div class="bar"><span [style.width.%]="f.pct" [style.background]="f.color"></span></div>
            </div>
          }
        </div>
      </div>

      <div class="card span-5">
        <div class="card-head">
          <div>
            <h2>By category</h2>
            <div class="sub">Gross volume, EUR equivalent</div>
          </div>
        </div>
        <div class="donut-row">
          <x-donut [segments]="categories()" [size]="164" [thickness]="18" ariaLabel="Transactions by category">
            <span class="xs faint">Transactions</span>
            <strong class="donut-total">{{ scoped().length }}</strong>
          </x-donut>
          <div class="cat-list">
            @for (c of categories(); track c.label) {
              <div class="cat">
                <i [style.background]="c.color"></i>
                <span class="grow truncate">{{ c.label }}</span>
                <span class="xs faint">{{ c.count }}</span>
                <span class="num small">{{ c.value | compact: "EUR" : 1 }}</span>
              </div>
            }
          </div>
        </div>
      </div>
    </section>

    <section class="card">
      <div class="card-head tabs-head">
        <div class="seg" role="tablist" aria-label="Ledger views">
          @for (t of tabs; track t) {
            <button [class.on]="tab() === t" (click)="tab.set(t)" role="tab" [attr.aria-selected]="tab() === t">
              {{ t }}
              <span class="count">{{ t === "Exceptions" ? exceptions().length : t === "Transactions" ? scoped().length : journals().length }}</span>
            </button>
          }
        </div>
        <span class="xs faint">{{ store.entity() === "all" ? "All entities" : entityShort(store.entity()) }} · simulated feeds</span>
      </div>

      @switch (tab()) {
        @case ("Exceptions") {
          @if (exceptions().length) {
            <div class="ex-grid">
              <div class="queue" role="listbox" aria-label="Exception queue">
                @for (t of exceptions(); track t.id) {
                  <button class="ex" [class.sel]="selectedEx()?.id === t.id" (click)="exId.set(t.id)" role="option" [attr.aria-selected]="selectedEx()?.id === t.id">
                    <div class="row between" style="gap: 8px">
                      <span class="mono xs faint">{{ t.id }} · {{ t.date | sdate }}</span>
                      <span class="num small" [class.pos]="t.amount > 0">{{ t.amount > 0 ? "+" : "" }}{{ t.amount | money: t.ccy }}</span>
                    </div>
                    <strong class="small truncate">{{ t.counterparty }}</strong>
                    <span class="xs warn reason">{{ t.exceptionReason }}</span>
                    <div class="conf">
                      <div class="meter" [class.warn]="t.confidence >= 0.5" [class.neg]="t.confidence < 0.5"><span [style.width.%]="t.confidence * 100"></span></div>
                      <span class="num xs faint">{{ (t.confidence * 100).toFixed(0) }}%</span>
                    </div>
                  </button>
                }
              </div>

              @if (selectedEx(); as t) {
                <div class="detail">
                  <div class="row between wrap" style="gap: 10px">
                    <div style="min-width: 0">
                      <div class="eyebrow">{{ t.id }} · {{ t.source }} · {{ accLabel(t.accountId) }}</div>
                      <h3 style="margin-top: 6px">{{ t.counterparty }}</h3>
                      <div class="xs faint">{{ t.memo }} · {{ t.date | dtime }}</div>
                    </div>
                    <div class="d-amt num" [class.pos]="t.amount > 0">{{ t.amount > 0 ? "+" : "" }}{{ t.amount | money: t.ccy : 2 }}</div>
                  </div>

                  <div class="callout warn" style="margin-top: 14px">
                    <x-icon name="alert" [size]="16" />
                    <div class="small"><strong>Why it stopped:</strong> {{ t.exceptionReason }}</div>
                  </div>

                  @if (suggestion(); as s) {
                    <div class="suggest">
                      <div class="row between wrap" style="gap: 8px">
                        <div class="row" style="gap: 8px">
                          <span class="icon-tile ai"><x-icon name="copilot" [size]="15" /></span>
                          <div>
                            <div class="xs ai">Suggested match</div>
                            <strong class="small">{{ s.ref }} · {{ s.doc }}</strong>
                          </div>
                        </div>
                        <span class="badge ai">Score {{ (s.score * 100).toFixed(0) }}/100</span>
                      </div>
                      <div class="cand">
                        <div><span class="xs faint">Candidate amount</span><span class="num">{{ s.amount | money: t.ccy : 2 }}</span></div>
                        <div><span class="xs faint">Difference</span><span class="num" [class.warn]="diff(t, s) !== 0">{{ diff(t, s) | money: t.ccy : 2 }}</span></div>
                        <div><span class="xs faint">Document date</span><span class="num">{{ s.date | sdate }}</span></div>
                      </div>
                      <div class="factors">
                        @for (f of s.factors; track f.label) {
                          <div class="factor">
                            <span class="small f-label">{{ f.label }}</span>
                            <div class="meter" [class.pos]="f.score >= 0.85" [class.warn]="f.score < 0.85 && f.score >= 0.55" [class.neg]="f.score < 0.55"><span [style.width.%]="f.score * 100"></span></div>
                            <span class="num xs f-score">{{ (f.score * 100).toFixed(0) }}</span>
                            <span class="xs faint f-detail">{{ f.detail }}</span>
                          </div>
                        }
                      </div>
                      <p class="small muted" style="margin-top: 10px">{{ s.rationale }}</p>
                    </div>
                  }

                  <div class="row wrap actions-row">
                    <button [class.btn-pos]="suggestion()?.recommend === 'match'" [class.btn-secondary]="suggestion()?.recommend !== 'match'" (click)="resolve(t, 'match')"><x-icon name="check" [size]="15" />Match{{ suggestion()?.recommend === "match" ? " (recommended)" : "" }}</button>
                    <button [class.btn-secondary]="suggestion()?.recommend !== 'adjust'" (click)="resolve(t, 'adjust')"><x-icon name="file" [size]="15" />Adjust{{ suggestion()?.recommend === "adjust" ? " (recommended)" : "" }}</button>
                    <span class="xs faint grow">Both actions post a journal and are written to the audit chain.</span>
                  </div>
                </div>
              }
            </div>
          } @else {
            <div class="empty">
              <span class="icon-tile pos" style="margin: 0 auto 10px"><x-icon name="check" [size]="18" /></span>
              <strong>Queue is clear.</strong>
              <div class="small">Every transaction in scope is matched or reconciled.</div>
            </div>
          }
        }

        @case ("Transactions") {
          <div class="filters">
            <div class="seg chips" role="group" aria-label="Status filter">
              @for (s of statuses; track s) {
                <button [class.on]="status() === s" (click)="setStatus(s)" [attr.aria-pressed]="status() === s">{{ s === "all" ? "All" : s }} <span class="count">{{ statusCounts()[s] }}</span></button>
              }
            </div>
            <div class="row wrap" style="gap: 8px">
              <label class="search">
                <x-icon name="search" [size]="15" />
                <input class="input" type="search" placeholder="Search counterparty, memo, ID" [value]="query()" (input)="setQuery($any($event.target).value)" aria-label="Search transactions" />
              </label>
              <select class="input" [value]="source()" (change)="setSource($any($event.target).value)" aria-label="Filter by source">
                <option value="all">All sources</option>
                <option value="Bank API">Bank API</option>
                <option value="ERP">ERP</option>
                <option value="CSV import">CSV import</option>
              </select>
            </div>
          </div>
          <div class="table-wrap" style="margin: 12px -18px 0">
            <table class="table">
              <thead>
                <tr>
                  <th>Date</th>
                  <th>ID</th>
                  <th>Counterparty</th>
                  <th>Account</th>
                  <th>Category</th>
                  <th>Source</th>
                  <th class="right">Amount</th>
                  <th>Status</th>
                  <th>Journal</th>
                </tr>
              </thead>
              <tbody>
                @for (t of visibleTx(); track t.id) {
                  <tr>
                    <td class="nowrap">{{ t.date | sdate }}</td>
                    <td class="mono xs nowrap">{{ t.id }}</td>
                    <td>
                      <div class="nowrap">{{ t.counterparty }}</div>
                      <div class="xs faint nowrap">{{ t.memo }}</div>
                    </td>
                    <td class="nowrap small">{{ accLabel(t.accountId) }}</td>
                    <td class="nowrap"><span class="cat-dot" [style.background]="catColor(t.category)"></span>{{ t.category }}</td>
                    <td class="nowrap xs muted">{{ t.source }}</td>
                    <td class="num nowrap" [class.pos]="t.amount > 0">{{ t.amount > 0 ? "+" : "" }}{{ t.amount | money: t.ccy }}</td>
                    <td>
                      <x-status [value]="t.status" />
                      @if (t.status === "matched") {
                        <div class="xs faint" style="margin-top: 3px">{{ (t.confidence * 100).toFixed(0) }}% confidence</div>
                      }
                    </td>
                    <td class="mono xs nowrap">
                      @if (t.journalRef) {
                        <button class="link-btn" (click)="openJournal(t.journalRef)">{{ t.journalRef }}</button>
                      } @else if (t.status === "exception") {
                        <button class="link-btn warn" (click)="openException(t.id)">Resolve</button>
                      } @else {
                        <span class="faint">—</span>
                      }
                    </td>
                  </tr>
                } @empty {
                  <tr><td colspan="9"><div class="empty">No transactions match these filters.</div></td></tr>
                }
              </tbody>
            </table>
          </div>
          <div class="row between wrap more">
            <span class="xs faint">Showing {{ visibleTx().length }} of {{ filteredTx().length }}</span>
            @if (visibleTx().length < filteredTx().length) {
              <button class="btn-secondary btn-sm" (click)="limit.set(limit() + pageSize)">Show {{ min(pageSize, filteredTx().length - visibleTx().length) }} more</button>
            }
          </div>
        }

        @case ("Journals") {
          <div class="journals">
            @for (j of journals(); track j.id) {
              <article class="journal" [class.hl]="highlight() === j.id" [attr.id]="'j-' + j.id">
                <div class="row between" style="gap: 8px">
                  <div class="row" style="gap: 8px; min-width: 0">
                    <strong class="mono small">{{ j.id }}</strong>
                    <x-status [value]="j.status" />
                  </div>
                  @if (balanced(j)) {
                    <span class="badge pos"><x-icon name="check" [size]="11" />Balanced</span>
                  } @else {
                    <span class="badge neg">Out of balance</span>
                  }
                </div>
                <div class="small truncate j-memo" [attr.title]="j.memo">{{ j.memo }}</div>
                <div class="xs faint">{{ j.date | sdate }} · {{ entityShort(j.entityId) }} · {{ j.ccy }}</div>
                <table class="lines">
                  <thead>
                    <tr><th>Account</th><th class="right">Debit</th><th class="right">Credit</th></tr>
                  </thead>
                  <tbody>
                    @for (l of j.lines; track $index) {
                      <tr>
                        <td class="truncate" [class.indent]="l.credit > 0">{{ l.account }}</td>
                        <td class="num right">{{ l.debit ? (l.debit | num: 2) : "" }}</td>
                        <td class="num right">{{ l.credit ? (l.credit | num: 2) : "" }}</td>
                      </tr>
                    }
                  </tbody>
                  <tfoot>
                    <tr>
                      <td>Total</td>
                      <td class="num right">{{ sum(j, "debit") | num: 2 }}</td>
                      <td class="num right">{{ sum(j, "credit") | num: 2 }}</td>
                    </tr>
                  </tfoot>
                </table>
                <div class="row between xs">
                  <span class="faint">Source <button class="link-btn mono" (click)="openTxn(j.source)">{{ j.source }}</button></span>
                  <span class="faint">Posted · ERP sync</span>
                </div>
              </article>
            } @empty {
              <div class="empty">No journals for this entity yet.</div>
            }
          </div>
        }
      }
    </section>
  </div>`,
  styles: [
    `
      .kpi5 { grid-template-columns: repeat(5, minmax(0, 1fr)); }
      @media (max-width: 1320px) { .kpi5 { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
      @media (max-width: 900px) { .kpi5 { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
      @media (max-width: 520px) { .kpi5 { grid-template-columns: minmax(0, 1fr); } }

      .funnel { display: flex; flex-direction: column; gap: 14px; }
      .stage-head { display: flex; align-items: center; gap: 10px; margin-bottom: 7px; }
      .step { display: grid; place-items: center; width: 20px; height: 20px; border-radius: 6px; background: var(--x-surface-3); color: var(--x-text-2); font-size: 10.5px; flex-shrink: 0; }
      .conv { font-family: var(--x-font-mono); }
      .bar { height: 12px; border-radius: 6px; background: var(--x-surface-3); overflow: hidden; }
      .bar span { display: block; height: 100%; border-radius: inherit; transition: width .5s var(--x-ease); }

      .donut-row { display: flex; align-items: center; gap: 18px; flex-wrap: wrap; justify-content: center; }
      .donut-total { font: 600 20px var(--x-font-display); }
      .cat-list { flex: 1; min-width: 180px; display: flex; flex-direction: column; gap: 8px; }
      .cat { display: flex; align-items: center; gap: 8px; font-size: 12.5px; }
      .cat i { width: 10px; height: 10px; border-radius: 3px; flex-shrink: 0; }
      .cat .num { width: 64px; text-align: right; }
      .cat-dot { display: inline-block; width: 8px; height: 8px; border-radius: 2px; margin-right: 7px; }

      .tabs-head { flex-wrap: wrap; gap: 10px; }
      .seg .count { margin-left: 5px; padding: 1px 6px; border-radius: 6px; background: var(--x-surface-3); font: 500 10.5px var(--x-font-mono); color: var(--x-text-3); }
      .seg button.on .count { background: var(--x-primary-soft); color: var(--x-primary); }
      .chips, .tabs-head .seg { flex-wrap: wrap; max-width: 100%; }
      .chips button { text-transform: capitalize; }

      .ex-grid { display: grid; grid-template-columns: minmax(0, 5fr) minmax(0, 7fr); gap: 16px; margin-top: 14px; }
      @media (max-width: 900px) { .ex-grid { grid-template-columns: minmax(0, 1fr); } }
      .queue { display: flex; flex-direction: column; gap: 8px; max-height: 620px; overflow-y: auto; padding-right: 2px; }
      .ex {
        height: auto; display: flex; flex-direction: column; align-items: stretch; gap: 4px; text-align: left; white-space: normal;
        padding: 11px 12px; border-radius: 12px; border: 1px solid var(--x-border); background: var(--x-surface-2); color: var(--x-text); font-weight: 400;
      }
      .ex:hover { background: var(--x-hover); color: var(--x-text); border-color: var(--x-border-strong); }
      .ex.sel { background: var(--x-primary-soft); border-color: color-mix(in srgb, var(--x-primary) 55%, transparent); }
      .ex .reason { line-height: 1.4; }
      .conf { display: flex; align-items: center; gap: 8px; margin-top: 4px; }
      .conf .meter { flex: 1; }
      .detail { padding: 16px; border-radius: 14px; border: 1px solid var(--x-border); background: var(--x-surface-2); min-width: 0; }
      .d-amt { font: 600 20px var(--x-font-mono); }
      .suggest { margin-top: 14px; padding: 14px; border-radius: 12px; background: var(--x-surface); border: 1px solid color-mix(in srgb, var(--x-ai) 35%, transparent); }
      .cand { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; margin: 14px 0 12px; }
      .cand div { display: flex; flex-direction: column; gap: 2px; font-size: 13px; }
      .factors { display: flex; flex-direction: column; gap: 9px; }
      .factor { display: grid; grid-template-columns: 110px minmax(60px, 1fr) 30px; align-items: center; gap: 4px 10px; }
      .f-score { text-align: right; }
      .f-detail { grid-column: 1 / -1; margin-top: -3px; }
      @media (max-width: 520px) { .cand { grid-template-columns: 1fr 1fr; } .factor { grid-template-columns: 90px 1fr 30px; } }
      .actions-row { margin-top: 14px; gap: 8px; }

      .filters { display: flex; justify-content: space-between; gap: 10px; flex-wrap: wrap; margin-top: 14px; }
      .search { position: relative; display: flex; align-items: center; }
      .search x-icon { position: absolute; left: 10px; color: var(--x-text-3); pointer-events: none; }
      .search .input { padding-left: 32px; width: 250px; max-width: 100%; }
      @media (max-width: 520px) { .filters > .row, .search, .search .input, .filters select { width: 100%; } }
      .more { margin-top: 12px; padding-top: 4px; }
      .link-btn { height: auto; padding: 0; background: none; border: 0; color: var(--x-primary); font: inherit; cursor: pointer; }
      .link-btn:hover { background: none; color: var(--x-primary-strong); text-decoration: underline; }
      .link-btn.warn { color: var(--x-warn); }
      .link-btn:active { transform: none; }

      .journals { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 12px; margin-top: 14px; }
      @media (max-width: 400px) { .journals { grid-template-columns: minmax(0, 1fr); } }
      .journal { display: flex; flex-direction: column; gap: 6px; padding: 14px; border-radius: 14px; border: 1px solid var(--x-border); background: var(--x-surface-2); min-width: 0; transition: border-color .2s, box-shadow .2s; }
      .journal.hl { border-color: var(--x-primary); box-shadow: 0 0 0 3px var(--x-primary-soft); }
      .j-memo { margin-top: 2px; }
      .lines { width: 100%; border-collapse: collapse; font-size: 12px; margin: 6px 0 4px; table-layout: fixed; }
      .lines th { font: 600 10px var(--x-font-mono); letter-spacing: .1em; text-transform: uppercase; color: var(--x-text-3); text-align: left; padding: 6px 0; border-bottom: 1px solid var(--x-border); }
      .lines th:first-child { width: 56%; }
      .lines td { padding: 6px 0; border-bottom: 1px dashed var(--x-border); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
      .lines .right { text-align: right; }
      .lines .indent { padding-left: 14px; color: var(--x-text-2); }
      .lines tfoot td { border-bottom: 0; border-top: 1px solid var(--x-border-strong); font-weight: 600; }
    `,
  ],
})
export class LedgerPage {
  readonly store = inject(DemoStore);
  readonly tabs: Tab[] = ["Exceptions", "Transactions", "Journals"];
  readonly statuses: StatusFilter[] = ["all", "reconciled", "matched", "exception", "pending"];
  readonly pageSize = PAGE;
  readonly min = Math.min;

  readonly tab = signal<Tab>("Exceptions");
  readonly exId = signal<string | null>(null);
  readonly status = signal<StatusFilter>("all");
  readonly source = signal<string>("all");
  readonly query = signal("");
  readonly limit = signal(PAGE);
  readonly highlight = signal<string | null>(null);

  private inScope = (t: { accountId: string }) => {
    const e = this.store.entity();
    return e === "all" || accountById(t.accountId).entityId === e;
  };

  readonly scoped = computed(() => this.store.transactions().filter(this.inScope));
  readonly exceptions = computed(() => this.store.exceptions().filter(this.inScope));
  readonly exceptionValue = computed(() => this.exceptions().reduce((s, t) => s + toEur(Math.abs(t.amount), t.ccy), 0));
  readonly manualCount = computed(() => this.store.transactions().filter((t) => t.journalRef?.startsWith("JE-92")).length);
  readonly autoRate = computed(() => {
    const tx = this.store.transactions();
    const processed = tx.filter((t) => t.status !== "pending").length;
    const auto = tx.filter((t) => (t.status === "reconciled" || t.status === "matched") && !t.journalRef?.startsWith("JE-92")).length;
    return processed ? (auto / processed) * 100 : 0;
  });
  readonly txns30 = computed(() => this.scoped().filter((t) => new Date(t.date).getTime() >= TODAY - 30 * DAY).length);
  readonly sourceMix = computed(() => {
    const tx = this.scoped();
    const n = (s: string) => tx.filter((t) => t.source === s).length;
    return `${n("Bank API")} bank API · ${n("ERP")} ERP · ${n("CSV import")} CSV`;
  });
  readonly journals = computed(() => {
    const e = this.store.entity();
    return JOURNALS.filter((j) => e === "all" || j.entityId === e);
  });

  readonly funnel = computed(() => {
    const tx = this.scoped();
    const ingested = tx.length;
    const normalized = tx.filter((t) => t.status !== "pending").length;
    const matched = tx.filter((t) => t.status === "matched" || t.status === "reconciled").length;
    const reconciled = tx.filter((t) => t.status === "reconciled").length;
    const stages = [
      { label: "Ingested", hint: "Bank API, ERP and file feeds", count: ingested, color: "var(--x-s7)" },
      { label: "Normalized", hint: "Enriched, categorised, de-duplicated", count: normalized, color: "var(--x-s1)" },
      { label: "Matched", hint: "Linked to invoice, bill or payroll run", count: matched, color: "var(--x-s2)" },
      { label: "Reconciled", hint: "Journal posted and cleared", count: reconciled, color: "var(--x-pos)" },
    ];
    return stages.map((s, i) => ({
      ...s,
      pct: ingested ? (s.count / ingested) * 100 : 0,
      conv: i > 0 && stages[i - 1].count ? (s.count / stages[i - 1].count) * 100 : 100,
    }));
  });

  readonly categories = computed<(DonutSegment & { count: number })[]>(() => {
    const by = new Map<string, { value: number; count: number }>();
    for (const t of this.scoped()) {
      const cur = by.get(t.category) ?? { value: 0, count: 0 };
      by.set(t.category, { value: cur.value + toEur(Math.abs(t.amount), t.ccy), count: cur.count + 1 });
    }
    return [...by.entries()].map(([label, v]) => ({ label, ...v, color: this.catColor(label) })).sort((a, b) => b.value - a.value);
  });

  /* ── exceptions ── */
  readonly selectedEx = computed(() => {
    const list = this.exceptions();
    return list.find((t) => t.id === this.exId()) ?? list[0] ?? null;
  });
  readonly suggestion = computed<Suggestion | null>(() => {
    const t = this.selectedEx();
    return t ? this.suggest(t) : null;
  });

  private suggest(t: Txn): Suggestion {
    const r = rng(Number(t.id.slice(3)) * 7 + 11);
    const inflow = t.amount > 0;
    const abs = Math.abs(t.amount);
    const reason = t.exceptionReason ?? "";
    const pctM = reason.match(/by ([\d.]+)%/);
    const dateOffset = r.int(1, 6);
    const date = new Date(new Date(t.date).getTime() - dateOffset * DAY).toISOString();
    const docNo = inflow ? `INV-${r.int(24000, 26999)}` : `BILL-${r.int(100000, 999999)}`;
    const doc = inflow ? "open customer invoice" : "approved supplier bill";
    let amount = abs;
    let factors: Factor[];
    let recommend: "match" | "adjust" = "match";
    let rationale: string;
    let ref = docNo;
    let docLabel = doc;

    if (pctM) {
      const p = +pctM[1];
      amount = abs / (1 - p / 100);
      recommend = p > 2 ? "adjust" : "match";
      factors = [
        { label: "Amount", score: Math.max(0.3, 1 - p / 5), detail: `${p.toFixed(1)}% short of the document total — typical of a deducted fee or partial payment` },
        { label: "Counterparty", score: 0.97, detail: `Exact name match on ${t.counterparty}` },
        { label: "Reference", score: 0.92, detail: `${docNo} found in remittance text` },
        { label: "Value date", score: 0.88, detail: `${dateOffset} days after document due date` },
      ];
      rationale =
        recommend === "adjust"
          ? `Reference and counterparty agree, but the shortfall exceeds the 2% auto-tolerance. Adjust posts the difference to bank charges / short-payment and clears ${docNo}.`
          : `Reference and counterparty agree and the shortfall is within the manual tolerance. Matching clears ${docNo} and books the difference to bank charges.`;
    } else if (reason.startsWith("Possible duplicate")) {
      ref = reason.split("of ")[1] ?? "TX-?";
      docLabel = "earlier bank transaction";
      recommend = "adjust";
      factors = [
        { label: "Amount", score: 0.99, detail: "Identical amount and currency" },
        { label: "Counterparty", score: 0.99, detail: "Same counterparty and account" },
        { label: "Reference", score: 0.74, detail: "Remittance text differs only in batch number" },
        { label: "Value date", score: 0.81, detail: `${dateOffset} days apart` },
      ];
      rationale = `This looks like a re-sent file line rather than a second payment. Adjust posts a reversing entry and links both transactions for the audit trail.`;
    } else if (reason.startsWith("FX rate")) {
      amount = abs * (1 + r.range(0.005, 0.009));
      factors = [
        { label: "Amount", score: 0.7, detail: "Within 0.9% after converting at the booking-date rate" },
        { label: "Counterparty", score: 0.95, detail: `Exact name match on ${t.counterparty}` },
        { label: "Reference", score: 0.9, detail: `${docNo} cited in payment details` },
        { label: "Value date", score: 0.86, detail: `${dateOffset} days after booking date` },
      ];
      rationale = `The document is in a different currency from the account. Matching books the variance to realised FX gains/losses (account 7900).`;
    } else if (reason.startsWith("Counterparty name")) {
      factors = [
        { label: "Amount", score: 1, detail: "Exact amount match" },
        { label: "Counterparty", score: 0.61, detail: `Bank shows a trading name; ERP holds the legal entity name` },
        { label: "Reference", score: 0.84, detail: `${docNo} partially present in remittance` },
        { label: "Value date", score: 0.9, detail: `${dateOffset} days after due date` },
      ];
      rationale = `Amount and reference line up; only the name differs. Matching also saves the alias so future payments from this payer match automatically.`;
    } else {
      amount = abs * (1 + r.range(-0.03, 0.03));
      recommend = "adjust";
      factors = [
        { label: "Amount", score: 0.58, detail: "Closest open item by amount" },
        { label: "Counterparty", score: 0.66, detail: "Payer not linked to an ERP master record" },
        { label: "Reference", score: 0.3, detail: "No document number in remittance text" },
        { label: "Value date", score: 0.72, detail: `${dateOffset} days from the candidate's due date` },
      ];
      rationale = `No reliable candidate exists. Adjust posts the amount to the suspense account (1990) and opens a task for ${inflow ? "Accounts Receivable" : "Accounts Payable"} to identify it.`;
    }
    return { ref, doc: docLabel, amount: Math.round(amount * 100) / 100, date, score: t.confidence, factors, recommend, rationale };
  }

  diff(t: Txn, s: Suggestion) {
    return Math.round((Math.abs(t.amount) - s.amount) * 100) / 100;
  }

  resolve(t: Txn, how: "match" | "adjust") {
    const list = this.exceptions();
    const idx = list.findIndex((x) => x.id === t.id);
    const next = list[idx + 1] ?? list[idx - 1] ?? null;
    this.store.resolveException(t.id, how);
    this.exId.set(next?.id ?? null);
  }

  /* ── transactions ── */
  readonly statusCounts = computed(() => {
    const c: Record<StatusFilter, number> = { all: 0, reconciled: 0, matched: 0, exception: 0, pending: 0 };
    for (const t of this.scoped()) {
      c.all++;
      c[t.status]++;
    }
    return c;
  });
  readonly filteredTx = computed(() => {
    const s = this.status();
    const src = this.source();
    const q = this.query().trim().toLowerCase();
    return this.scoped().filter(
      (t) =>
        (s === "all" || t.status === s) &&
        (src === "all" || t.source === src) &&
        (!q || `${t.id} ${t.counterparty} ${t.memo} ${t.category} ${t.journalRef ?? ""}`.toLowerCase().includes(q)),
    );
  });
  readonly visibleTx = computed(() => this.filteredTx().slice(0, this.limit()));

  setStatus(s: StatusFilter) {
    this.status.set(s);
    this.limit.set(PAGE);
  }
  setSource(s: string) {
    this.source.set(s);
    this.limit.set(PAGE);
  }
  setQuery(q: string) {
    this.query.set(q);
    this.limit.set(PAGE);
  }

  openJournal(ref: string) {
    if (!this.journals().some((j) => j.id === ref)) {
      this.store.toast("info", `${ref} posted`, "Journal is in the ERP archive; only the latest 18 journals are shown here.");
      return;
    }
    this.tab.set("Journals");
    this.highlight.set(ref);
    setTimeout(() => document.getElementById("j-" + ref)?.scrollIntoView({ block: "center" }), 40);
    setTimeout(() => this.highlight.set(null), 2600);
  }
  openTxn(id: string) {
    this.status.set("all");
    this.source.set("all");
    this.query.set(id);
    this.limit.set(PAGE);
    this.tab.set("Transactions");
  }
  openException(id: string) {
    this.exId.set(id);
    this.tab.set("Exceptions");
  }

  /* ── journals ── */
  sum(j: Journal, side: "debit" | "credit") {
    return j.lines.reduce((s, l) => s + l[side], 0);
  }
  balanced(j: Journal) {
    return Math.abs(this.sum(j, "debit") - this.sum(j, "credit")) < 0.005;
  }

  /* ── helpers ── */
  catColor(c: string) {
    return CAT_COLORS[c] ?? "var(--x-s7)";
  }
  entityShort(id: string) {
    return entityById(id)?.short ?? id;
  }
  accLabel(id: string) {
    const a = accountById(id);
    return `${a.bank.split(" ")[0]} · ${entityById(a.entityId).short}`;
  }

  runMatching() {
    const pending = this.scoped().filter((t) => t.status === "pending").length;
    this.store.log("Reconciliation engine", "system", "recon.run", "Ledger", `Auto-match pass · ${this.scoped().length} transactions scanned`);
    this.store.toast("info", "Auto-match pass complete", `${this.scoped().length} transactions scanned. ${pending} pending items await bank confirmation; ${this.exceptions().length} exceptions need review.`);
  }
}
