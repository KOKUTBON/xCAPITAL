import { Component, DestroyRef, ElementRef, computed, inject, signal, viewChild } from "@angular/core";
import { ActivatedRoute, Router, RouterLink } from "@angular/router";
import { DemoStore } from "../../core/store";
import { entityById, toEur } from "../../core/demo-data";
import { FORMAT_PIPES, relTime } from "../../core/format";
import { ActionKind, AuditEvent, FinancialAction } from "../../core/models";
import { UI } from "../../shared/ui";

type Filter = "you" | "pending" | "blocked" | "history" | "all";
type StepState = "done" | "current" | "failed" | "todo";

interface TimelineStep {
  label: string;
  state: StepState;
  at?: string;
  note?: string;
}

const KIND_ICON: Record<ActionKind, string> = { Payment: "send", FX: "swap", Sweep: "liquidity", Rebalance: "portfolio", Investment: "markets" };
const CHECK_ICON = { pass: "check", warn: "alert", fail: "x" } as const;

@Component({
  selector: "x-approvals",
  standalone: true,
  imports: [RouterLink, ...UI, ...FORMAT_PIPES],
  template: `<div class="page">
    <x-page-head eyebrow="Control · Governed actions" title="Approvals" description="Every financial action is proposed, evaluated by Guard against active policy, then released by the right people. Approve or reject below — execution follows live.">
      <a class="btn btn-secondary" routerLink="/app/guard"><x-icon name="guard" [size]="16" />Guard policies</a>
      <a class="btn btn-ghost" routerLink="/app/audit"><x-icon name="audit" [size]="16" />Audit trail</a>
    </x-page-head>

    @if (store.killSwitch()) {
      <div class="callout neg rise" role="status">
        <x-icon name="power" [size]="18" />
        <div class="grow">
          <strong>Kill switch is engaged</strong>
          <div class="small muted">New approvals and submissions are frozen. Release it from <a routerLink="/app/guard">Guard</a> to resume execution.</div>
        </div>
      </div>
    }

    <section class="grid g-4">
      <div class="card kpi glow rise">
        <div class="label"><x-icon name="user" [size]="15" />Awaiting you</div>
        <div class="value">{{ store.awaitingYou().length }}</div>
        <div class="delta faint">{{ awaitingValue() | compact: "EUR" : 1 }} EUR equivalent</div>
      </div>
      <div class="card kpi rise" style="animation-delay: 40ms">
        <div class="label"><x-icon name="clock" [size]="15" />Pending in chain</div>
        <div class="value">{{ store.pendingActions().length }}</div>
        <div class="delta faint">{{ nextExpiry() }}</div>
      </div>
      <div class="card kpi rise" style="animation-delay: 80ms">
        <div class="label"><x-icon name="guard" [size]="15" />Blocked by Guard</div>
        <div class="value" [class.neg]="counts().blocked > 0">{{ counts().blocked }}</div>
        <div class="delta faint">Deterministic policy checks</div>
      </div>
      <div class="card kpi rise" style="animation-delay: 120ms">
        <div class="label"><x-icon name="check" [size]="15" />Executed</div>
        <div class="value">{{ executedCount() }}</div>
        <div class="delta faint">{{ executedValue() | compact: "EUR" : 1 }} released · last 7 days</div>
      </div>
    </section>

    <section class="grid g-12 inbox">
      <!-- ───────── list pane ───────── -->
      <div class="card flush span-5 list-pane" #listPane>
        <div class="card-head">
          <div>
            <h2>Inbox</h2>
            <div class="sub">{{ filtered().length }} of {{ store.actions().length }} actions</div>
          </div>
          <span class="badge"><span class="dot live pos"></span>Live</span>
        </div>
        <div class="filters">
          <div class="seg" role="tablist" aria-label="Filter actions">
            @for (f of filters; track f.id) {
              <button role="tab" [class.on]="filter() === f.id" [attr.aria-selected]="filter() === f.id" (click)="filter.set(f.id)">
                {{ f.label }} <span class="cnt">{{ counts()[f.id] }}</span>
              </button>
            }
          </div>
        </div>
        <div class="rows" role="listbox" aria-label="Actions">
          @for (a of filtered(); track a.id) {
            <button class="act-row" role="option" [attr.aria-selected]="selected()?.id === a.id" [class.sel]="selected()?.id === a.id" (click)="select(a.id)">
              <span class="icon-tile" [class.ai]="a.origin === 'AI'" [class.neg]="a.status === 'blocked'">
                <x-icon [name]="kindIcon(a.kind)" [size]="16" />
              </span>
              <span class="grow body">
                <span class="row t-row">
                  <strong class="truncate">{{ a.title }}</strong>
                </span>
                <span class="xs faint meta truncate">
                  <span class="mono">{{ a.id }}</span> · {{ a.requestedBy }} · {{ rel(a.createdAt) }}
                </span>
                @if (needsYou(a)) {
                  <span class="you-flag xs"><span class="dot"></span>Your approval</span>
                }
              </span>
              <span class="r-col">
                <span class="num small">{{ a.amount | compact: a.ccy : 2 }}</span>
                <x-status [value]="a.status" />
              </span>
            </button>
          } @empty {
            <div class="empty">
              <x-icon name="check" [size]="22" />
              <div style="margin-top: 8px">Nothing here. You're all caught up.</div>
            </div>
          }
        </div>
      </div>

      <!-- ───────── detail pane ───────── -->
      <div class="span-7 detail-pane" #detailPane>
        @if (selected(); as a) {
          <div class="card detail rise">
            <button class="btn-ghost btn-sm back" (click)="backToList()"><x-icon name="chevronLeft" [size]="14" />All actions</button>

            <div class="d-head">
              <span class="icon-tile big" [class.ai]="a.origin === 'AI'" [class.neg]="a.status === 'blocked'">
                <x-icon [name]="kindIcon(a.kind)" [size]="20" />
              </span>
              <div class="grow">
                <div class="row wrap" style="gap: 8px">
                  <span class="mono xs faint">{{ a.id }}</span>
                  <x-status [value]="a.status" />
                  @if (a.origin === "AI") {
                    <span class="badge ai"><x-icon name="copilot" [size]="12" />AI proposed</span>
                  } @else if (a.origin === "Rule") {
                    <span class="badge info"><x-icon name="settings" [size]="12" />Standing rule</span>
                  } @else {
                    <span class="badge"><x-icon name="user" [size]="12" />Human request</span>
                  }
                  <span class="badge">{{ a.kind }}</span>
                </div>
                <h2 class="d-title">{{ a.title }}</h2>
                <div class="small muted">{{ a.summary }}</div>
              </div>
            </div>

            <div class="amount-row">
              <div>
                <div class="eyebrow">Amount</div>
                <div class="amount num">{{ a.amount | money: a.ccy }}</div>
                @if (a.ccy !== "EUR") {
                  <div class="xs faint num">≈ {{ eur(a) | money: "EUR" }} at simulated FX</div>
                }
              </div>
              <div class="facts">
                <div><span class="faint xs">Entity</span><strong class="small">{{ entityName(a.entityId) }}</strong></div>
                <div><span class="faint xs">Requested by</span><strong class="small">{{ a.requestedBy }}</strong></div>
                <div><span class="faint xs">Created</span><strong class="small">{{ a.createdAt | dtime }}</strong></div>
              </div>
            </div>

            @if (a.origin === "AI") {
              <div class="model-ref xs">
                <x-icon name="cpu" [size]="14" />
                <span>Proposed by <strong>xCAPITAL Intelligence</strong> · {{ modelRef() }} · cannot approve or execute its own proposals</span>
              </div>
            }

            <!-- flow -->
            <div class="flow">
              <div class="node">
                <span class="faint xs eyebrow">From</span>
                <strong class="small">{{ a.from }}</strong>
              </div>
              <div class="flow-arrow" aria-hidden="true">
                <span class="line"></span>
                <span class="pill num xs">{{ a.amount | compact: a.ccy : 2 }}</span>
                <x-icon name="arrow" [size]="16" />
              </div>
              <div class="node">
                <span class="faint xs eyebrow">To</span>
                <strong class="small">{{ a.to }}</strong>
              </div>
            </div>

            @if (a.status === "blocked") {
              <div class="callout neg">
                <x-icon name="guard" [size]="18" />
                <div class="grow">
                  <strong>Blocked by Guard</strong>
                  <div class="small muted">This action cannot be approved until every failed control is resolved. Guard rules are deterministic and cannot be overridden from the inbox.</div>
                  <ul class="fail-list">
                    @for (c of failedChecks(); track c.label) {
                      <li><strong>{{ c.label }}</strong> — {{ c.detail }}</li>
                    }
                  </ul>
                </div>
              </div>
            }

            <div class="sec">
              <h3>Rationale</h3>
              <p class="small muted">{{ a.rationale }}</p>
              <div class="chips">
                @for (e of a.evidence; track e) {
                  <span class="chip"><x-icon name="file" [size]="12" />{{ e }}</span>
                }
              </div>
            </div>

            <div class="two">
              <div class="sec">
                <div class="row between">
                  <h3>Guard checks</h3>
                  <span class="xs" [class.pos]="checkSummary().fail === 0 && checkSummary().warn === 0" [class.warn]="checkSummary().warn > 0 && checkSummary().fail === 0" [class.neg]="checkSummary().fail > 0">{{ checkSummary().text }}</span>
                </div>
                <div class="checks">
                  @for (c of a.checks; track c.label) {
                    <div class="check" [class]="'check ' + c.result">
                      <span class="ck-ic"><x-icon [name]="checkIcon(c.result)" [size]="13" [stroke]="2.4" /></span>
                      <div class="grow">
                        <div class="small"><strong>{{ c.label }}</strong></div>
                        <div class="xs faint">{{ c.detail }}</div>
                      </div>
                    </div>
                  }
                </div>
              </div>

              <div class="sec risk">
                <h3>Risk score</h3>
                <div class="gauge">
                  <svg viewBox="0 0 120 70" role="img" [attr.aria-label]="'Risk score ' + a.riskScore + ' of 100'">
                    <path d="M10 62 A50 50 0 0 1 110 62" pathLength="100" class="g-track" />
                    <path d="M10 62 A50 50 0 0 1 110 62" pathLength="100" class="g-val" [style.stroke]="riskColor(a.riskScore)" [attr.stroke-dasharray]="a.riskScore + ' 100'" />
                  </svg>
                  <div class="g-num">
                    <strong class="num" [style.color]="riskColor(a.riskScore)">{{ a.riskScore }}</strong>
                    <span class="xs faint">/ 100 · {{ a.risk }}</span>
                  </div>
                </div>
                <div class="g-scale xs faint"><span>Low</span><span>Medium</span><span>High</span></div>
              </div>
            </div>

            <div class="two">
              <div class="sec">
                <h3>Approval chain</h3>
                <ol class="chain">
                  @for (s of a.chain; track $index) {
                    <li class="step" [class]="'step ' + s.status" [class.me]="s.you">
                      <span class="avatar round">{{ s.initials }}</span>
                      <div class="grow">
                        <div class="small"><strong>{{ s.name }}</strong></div>
                        <div class="xs faint">{{ s.role }}{{ s.at ? " · " + rel(s.at) : "" }}</div>
                      </div>
                      <x-status [value]="s.status === 'waiting' ? 'scheduled' : s.status" [label]="s.status" />
                    </li>
                  }
                </ol>
              </div>

              <div class="sec">
                <h3>Execution timeline</h3>
                <ol class="timeline">
                  @for (t of timeline(); track t.label) {
                    <li [class]="'tl ' + t.state">
                      <span class="tl-dot">
                        @if (t.state === "done") {
                          <x-icon name="check" [size]="11" [stroke]="2.6" />
                        } @else if (t.state === "failed") {
                          <x-icon name="x" [size]="11" [stroke]="2.6" />
                        }
                      </span>
                      <div class="grow">
                        <div class="small">{{ t.label }}</div>
                        @if (t.note || t.at) {
                          <div class="xs faint">{{ t.note }}{{ t.note && t.at ? " · " : "" }}{{ t.at ? rel(t.at) : "" }}</div>
                        }
                      </div>
                    </li>
                  }
                </ol>
              </div>
            </div>

            <div class="payload">
              <div class="row" style="gap: 8px">
                <x-icon name="lock" [size]="15" />
                <strong class="small">Immutable payload</strong>
                <span class="xs faint">Approvals bind to this exact version; any change invalidates them.</span>
              </div>
              <div class="pl-grid">
                <div><span class="xs faint">Version</span><span class="mono small">v{{ a.version }}</span></div>
                <div><span class="xs faint">Payload hash</span><span class="mono small" [attr.title]="a.payloadHash">{{ shortHash(a.payloadHash) }}</span></div>
                <div>
                  <span class="xs faint">Expires</span>
                  <span class="mono small" [class.warn]="expiry().soon" [class.neg]="expiry().expired">{{ expiry().label }}</span>
                </div>
              </div>
            </div>

            @if (store.killSwitch() && a.status === "pending") {
              <div class="callout warn">
                <x-icon name="power" [size]="18" />
                <div class="small">Kill switch is active — approval is disabled. You can still reject.</div>
              </div>
            }

            <div class="action-bar">
              @if (rejecting()) {
                <div class="reject-box">
                  <label class="field grow">
                    Reason for rejection
                    <input class="input" [value]="reason()" (input)="reason.set($any($event.target).value)" (keydown.enter)="confirmReject(a.id)" placeholder="e.g. Hold until disputed bill is resolved" autofocus />
                  </label>
                  <div class="row">
                    <button class="btn-ghost" (click)="rejecting.set(false)">Cancel</button>
                    <button class="btn-neg" (click)="confirmReject(a.id)"><x-icon name="x" [size]="15" />Confirm rejection</button>
                  </div>
                </div>
              } @else {
                <div class="xs faint grow">{{ actHint() }}</div>
                <div class="row">
                  <button class="btn-neg" [disabled]="!canAct()" (click)="rejecting.set(true)"><x-icon name="x" [size]="15" />Reject</button>
                  <button class="btn-pos" [disabled]="!canAct() || store.killSwitch()" (click)="approve(a.id)"><x-icon name="check" [size]="15" />Approve v{{ a.version }}</button>
                </div>
              }
            </div>
          </div>
        } @else {
          <div class="card empty">Select an action to review it.</div>
        }
      </div>
    </section>
  </div>`,
  styles: [
    `
      .callout.neg { background: var(--x-neg-soft); border-color: color-mix(in srgb, var(--x-neg) 30%, transparent); color: var(--x-text); }
      .callout.neg > x-icon { color: var(--x-neg); }
      .callout.warn > x-icon { color: var(--x-warn); }
      .inbox { align-items: start; }
      .list-pane { position: sticky; top: calc(var(--x-topbar) + 16px); }
      .filters { padding: 0 18px 12px; border-bottom: 1px solid var(--x-border); overflow-x: auto; }
      .cnt { font: 600 10px/1 var(--x-font-mono); color: var(--x-text-3); margin-left: 2px; }
      .seg button.on .cnt { color: var(--x-primary); }
      .rows { display: flex; flex-direction: column; padding: 6px; max-height: calc(100vh - 260px); overflow-y: auto; }
      .act-row {
        display: flex; align-items: center; gap: 12px; height: auto; width: 100%; text-align: left;
        padding: 11px 12px; border-radius: 12px; background: transparent; color: inherit; font-weight: 400;
        border: 1px solid transparent; white-space: normal;
      }
      .act-row:hover { background: var(--x-hover); color: inherit; }
      .act-row.sel { background: var(--x-primary-soft); border-color: color-mix(in srgb, var(--x-primary) 30%, transparent); }
      .act-row .body { display: flex; flex-direction: column; gap: 3px; min-width: 0; }
      .act-row strong { font-size: 13px; font-weight: 600; }
      .t-row { gap: 6px; min-width: 0; }
      .meta { display: block; }
      .you-flag { display: inline-flex; align-items: center; gap: 5px; color: var(--x-primary); font-weight: 600; }
      .you-flag .dot { width: 6px; height: 6px; }
      .r-col { display: flex; flex-direction: column; align-items: flex-end; gap: 5px; flex-shrink: 0; }

      .detail { display: flex; flex-direction: column; gap: 18px; }
      .back { display: none; align-self: flex-start; margin: -6px 0 -8px -6px; }
      .d-head { display: flex; gap: 14px; align-items: flex-start; }
      .icon-tile.big { width: 44px; height: 44px; border-radius: 13px; }
      .d-title { font-size: 20px; margin: 8px 0 4px; }
      .amount-row { display: flex; justify-content: space-between; align-items: flex-end; gap: 18px; flex-wrap: wrap; padding: 16px; border-radius: var(--x-radius); background: var(--x-surface-2); border: 1px solid var(--x-border); }
      .amount { font: 600 32px/1.1 var(--x-font-display); letter-spacing: -0.02em; margin-top: 6px; font-variant-numeric: tabular-nums; }
      .facts { display: flex; gap: 20px; flex-wrap: wrap; }
      .facts > div { display: flex; flex-direction: column; gap: 3px; }
      .model-ref { display: flex; align-items: center; gap: 8px; padding: 8px 12px; border-radius: 10px; background: var(--x-ai-soft); color: var(--x-ai); }
      .model-ref span { color: var(--x-text-2); }
      .model-ref strong { color: var(--x-ai); }

      .flow { display: grid; grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr); gap: 10px; align-items: stretch; }
      .node { display: flex; flex-direction: column; gap: 6px; padding: 12px 14px; border-radius: var(--x-radius); border: 1px solid var(--x-border); background: var(--x-surface-2); }
      .flow-arrow { position: relative; display: flex; align-items: center; gap: 6px; color: var(--x-primary); min-width: 110px; justify-content: center; }
      .flow-arrow .line { position: absolute; left: 0; right: 10px; top: 50%; height: 1px; background: linear-gradient(90deg, transparent, var(--x-primary)); }
      .flow-arrow .pill { position: relative; padding: 3px 8px; border-radius: 999px; background: var(--x-surface); border: 1px solid var(--x-border-strong); color: var(--x-text); }
      .flow-arrow x-icon { position: relative; }

      .sec { display: flex; flex-direction: column; gap: 10px; min-width: 0; }
      .sec h3 { font-size: 13px; font-family: var(--x-font); font-weight: 600; letter-spacing: 0; }
      .two { display: grid; grid-template-columns: minmax(0, 1.4fr) minmax(0, 1fr); gap: 18px; }
      .two + .two { grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); }
      .chips { display: flex; flex-wrap: wrap; gap: 6px; }
      .chip { display: inline-flex; align-items: center; gap: 6px; padding: 4px 9px; border-radius: 8px; font-size: 11.5px; color: var(--x-text-2); background: var(--x-surface-2); border: 1px solid var(--x-border); }
      .fail-list { margin: 8px 0 0; padding-left: 18px; font-size: 12px; color: var(--x-text-2); display: flex; flex-direction: column; gap: 4px; }

      .checks { display: flex; flex-direction: column; gap: 6px; }
      .check { display: flex; gap: 10px; align-items: flex-start; padding: 9px 10px; border-radius: 10px; background: var(--x-surface-2); border: 1px solid var(--x-border); }
      .ck-ic { display: grid; place-items: center; width: 20px; height: 20px; border-radius: 50%; flex-shrink: 0; margin-top: 1px; }
      .check.pass .ck-ic { background: var(--x-pos-soft); color: var(--x-pos); }
      .check.warn .ck-ic { background: var(--x-warn-soft); color: var(--x-warn); }
      .check.fail .ck-ic { background: var(--x-neg-soft); color: var(--x-neg); }
      .check.fail { border-color: color-mix(in srgb, var(--x-neg) 30%, transparent); }

      .risk { align-items: stretch; }
      .gauge { position: relative; max-width: 220px; width: 100%; margin: 0 auto; }
      .gauge svg { display: block; width: 100%; }
      .g-track { fill: none; stroke: var(--x-surface-3); stroke-width: 10; stroke-linecap: round; }
      .g-val { fill: none; stroke-width: 10; stroke-linecap: round; transition: stroke-dasharray .6s var(--x-ease); }
      .g-num { position: absolute; left: 0; right: 0; bottom: 2px; display: flex; flex-direction: column; align-items: center; }
      .g-num strong { font: 600 28px/1 var(--x-font-display); }
      .g-scale { display: flex; justify-content: space-between; max-width: 220px; width: 100%; margin: 0 auto; }

      .chain { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; }
      .step { position: relative; display: flex; align-items: center; gap: 10px; padding: 8px 10px; border-radius: 10px; }
      .step + .step::before { content: ""; position: absolute; left: 25px; top: -8px; height: 16px; width: 2px; background: var(--x-border-strong); }
      .step.approved .avatar { background: var(--x-pos-soft); color: var(--x-pos); }
      .step.rejected .avatar { background: var(--x-neg-soft); color: var(--x-neg); }
      .step.pending .avatar { background: var(--x-warn-soft); color: var(--x-warn); }
      .step.me { background: var(--x-primary-soft); box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--x-primary) 30%, transparent); }
      .step .avatar { width: 30px; height: 30px; font-size: 11px; position: relative; z-index: 1; }

      .timeline { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; }
      .tl { position: relative; display: flex; gap: 10px; padding: 0 0 12px; }
      .tl:last-child { padding-bottom: 0; }
      .tl::before { content: ""; position: absolute; left: 8px; top: 18px; bottom: -2px; width: 2px; background: var(--x-border); }
      .tl:last-child::before { display: none; }
      .tl.done::before { background: var(--x-pos); opacity: .5; }
      .tl-dot { display: grid; place-items: center; width: 18px; height: 18px; border-radius: 50%; flex-shrink: 0; border: 2px solid var(--x-border-strong); background: var(--x-surface); color: var(--x-surface); }
      .tl.done .tl-dot { background: var(--x-pos); border-color: var(--x-pos); color: var(--x-surface); }
      .tl.failed .tl-dot { background: var(--x-neg); border-color: var(--x-neg); color: var(--x-surface); }
      .tl.current .tl-dot { border-color: var(--x-primary); box-shadow: 0 0 0 4px var(--x-primary-soft); animation: x-pulse 1.6s infinite; color: var(--x-primary); }
      .tl.todo .small { color: var(--x-text-3); }
      .tl.current .small { color: var(--x-primary); font-weight: 600; }

      .payload { padding: 14px; border-radius: var(--x-radius); border: 1px dashed var(--x-border-strong); display: flex; flex-direction: column; gap: 12px; }
      .payload .row x-icon { color: var(--x-text-3); }
      .pl-grid { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; gap: 18px; }
      .pl-grid > div { display: flex; flex-direction: column; gap: 3px; min-width: 0; }

      .action-bar { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; padding-top: 14px; border-top: 1px solid var(--x-border); }
      .reject-box { display: flex; align-items: flex-end; gap: 10px; width: 100%; flex-wrap: wrap; }
      .reject-box .input { width: 100%; }

      @media (max-width: 1200px) {
        .list-pane { position: static; }
        .rows { max-height: none; }
        .back { display: inline-flex; }
      }
      @media (max-width: 760px) {
        .two, .two + .two { grid-template-columns: minmax(0, 1fr); }
        .flow { grid-template-columns: minmax(0, 1fr); }
        .flow-arrow { min-height: 34px; }
        .flow-arrow .line { left: 50%; right: auto; top: 0; bottom: 0; width: 1px; height: auto; background: linear-gradient(180deg, transparent, var(--x-primary)); }
        .flow-arrow x-icon { transform: rotate(90deg); }
        .pl-grid { grid-template-columns: minmax(0, 1fr); gap: 10px; }
        .amount { font-size: 26px; }
        .d-head { flex-direction: column; gap: 10px; }
        .facts { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; width: 100%; }
        .act-row .r-col x-status { display: none; }
      }
    `,
  ],
})
export class ApprovalsPage {
  readonly store = inject(DemoStore);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private listPane = viewChild<ElementRef<HTMLElement>>("listPane");
  private detailPane = viewChild<ElementRef<HTMLElement>>("detailPane");

  readonly filters: { id: Filter; label: string }[] = [
    { id: "you", label: "Needs you" },
    { id: "pending", label: "Pending" },
    { id: "blocked", label: "Blocked" },
    { id: "history", label: "History" },
    { id: "all", label: "All" },
  ];
  readonly filter = signal<Filter>(this.store.awaitingYou().length ? "you" : "all");
  readonly selectedId = signal<string | null>(null);
  readonly rejecting = signal(false);
  readonly reason = signal("");
  /** Ticking clock so relative times, countdowns and the timeline stay live. */
  readonly now = signal(Date.now());

  constructor() {
    const timer = setInterval(() => this.now.set(Date.now()), 1000);
    const sub = this.route.queryParamMap.subscribe((q) => {
      const id = q.get("id");
      if (!id || !this.store.actions().some((a) => a.id === id)) return;
      if (!this.matches(this.store.actions().find((a) => a.id === id)!, this.filter())) this.filter.set("all");
      if (this.selectedId() !== id) {
        this.selectedId.set(id);
        this.rejecting.set(false);
      }
    });
    inject(DestroyRef).onDestroy(() => {
      clearInterval(timer);
      sub.unsubscribe();
    });
  }

  private matches(a: FinancialAction, f: Filter) {
    switch (f) {
      case "you":
        return this.needsYou(a);
      case "pending":
        return a.status === "pending";
      case "blocked":
        return a.status === "blocked";
      case "history":
        return a.status === "approved" || a.status === "executed" || a.status === "rejected";
      default:
        return true;
    }
  }

  readonly filtered = computed(() => {
    const f = this.filter();
    return this.store.actions().filter((a) => this.matches(a, f));
  });
  readonly counts = computed(() => {
    const all = this.store.actions();
    const c = {} as Record<Filter, number>;
    for (const f of this.filters) c[f.id] = all.filter((a) => this.matches(a, f.id)).length;
    return c;
  });
  readonly selected = computed(() => {
    const id = this.selectedId();
    const all = this.store.actions();
    return (id && all.find((a) => a.id === id)) || this.filtered()[0] || null;
  });

  readonly awaitingValue = computed(() => this.store.awaitingYou().reduce((s, a) => s + toEur(a.amount, a.ccy), 0));
  readonly executedCount = computed(() => this.store.actions().filter((a) => a.status === "executed").length);
  readonly executedValue = computed(() => this.store.actions().filter((a) => a.status === "executed").reduce((s, a) => s + toEur(a.amount, a.ccy), 0));
  readonly nextExpiry = computed(() => {
    const now = this.now();
    const next = this.store
      .pendingActions()
      .map((a) => new Date(a.expiresAt).getTime())
      .filter((t) => t > now)
      .sort((a, b) => a - b)[0];
    return next ? `Next expiry in ${this.duration(next - now)}` : "No upcoming expiries";
  });

  /* ───────── detail derivations ───────── */

  private events = computed<AuditEvent[]>(() => {
    const a = this.selected();
    return a ? this.store.audit().filter((e) => e.target === a.id) : [];
  });

  readonly modelRef = computed(() => {
    const ev = this.events().find((e) => e.action === "action.proposed");
    if (!ev) return "model reference on file";
    const part = ev.detail.split("·").pop()?.trim() ?? "";
    return part.startsWith("model") ? part : `signal: ${part}`;
  });

  readonly failedChecks = computed(() => this.selected()?.checks.filter((c) => c.result === "fail") ?? []);
  readonly checkSummary = computed(() => {
    const cs = this.selected()?.checks ?? [];
    const pass = cs.filter((c) => c.result === "pass").length;
    const warn = cs.filter((c) => c.result === "warn").length;
    const fail = cs.filter((c) => c.result === "fail").length;
    const parts = [`${pass} passed`];
    if (warn) parts.push(`${warn} warning${warn > 1 ? "s" : ""}`);
    if (fail) parts.push(`${fail} failed`);
    return { pass, warn, fail, text: parts.join(" · ") };
  });

  readonly canAct = computed(() => {
    const a = this.selected();
    return !!a && this.needsYou(a);
  });

  readonly actHint = computed(() => {
    const a = this.selected();
    if (!a) return "";
    if (this.canAct()) return this.store.killSwitch() ? "Execution is frozen by the kill switch." : `You are approving payload v${a.version} · ${this.shortHash(a.payloadHash)}.`;
    switch (a.status) {
      case "blocked":
        return "Blocked by Guard — no approver can release this action.";
      case "approved":
        return "Fully approved · submitting to the partner.";
      case "executed":
        return "Executed and confirmed by the partner.";
      case "rejected":
        return "Rejected · the proposer has been notified.";
      default: {
        const p = a.chain.find((s) => s.status === "pending");
        return p ? `Waiting for ${p.name} (${p.role}).` : "Not awaiting your approval.";
      }
    }
  });

  readonly expiry = computed(() => {
    const a = this.selected();
    if (!a) return { label: "—", soon: false, expired: false };
    const ms = new Date(a.expiresAt).getTime() - this.now();
    if (ms <= 0) return { label: `Expired ${relTime(a.expiresAt, this.now())}`, soon: false, expired: a.status === "pending" };
    return { label: `in ${this.duration(ms)}`, soon: ms < 6 * 3_600_000, expired: false };
  });

  readonly timeline = computed<TimelineStep[]>(() => {
    const a = this.selected();
    if (!a) return [];
    const ev = this.events();
    const find = (...names: string[]) => ev.filter((e) => names.includes(e.action)).pop();
    const now = this.now();

    const proposed = find("action.proposed", "action.created");
    const guardEv = find("guard.evaluated", "guard.blocked");
    const lastApproval = [...a.chain].reverse().find((s) => s.status === "approved" && s.at);
    const submitted = find("execution.submitted");
    const confirmed = find("execution.confirmed", "execution.settled", "action.executed");
    const killed = ev.find((e) => e.action === "guard.blocked" && e.detail.includes("Kill switch"));
    const done = a.status === "executed";

    const steps: TimelineStep[] = [
      { label: a.origin === "AI" ? "Proposed by Intelligence" : a.origin === "Rule" ? "Generated by standing rule" : "Requested", state: "done", at: proposed?.at ?? a.createdAt, note: a.requestedBy },
    ];

    const guardFailed = a.status === "blocked" && !killed;
    steps.push({ label: "Guard evaluated", state: guardFailed ? "failed" : "done", at: guardEv && !killed ? guardEv.at : undefined, note: this.checkSummary().text });

    const approvedCount = a.chain.filter((s) => s.status === "approved").length;
    const approvedState: StepState =
      a.status === "rejected" ? "failed" : a.status === "approved" || done || killed ? "done" : a.status === "pending" ? "current" : "todo";
    steps.push({
      label: a.status === "rejected" ? "Rejected" : "Approved",
      state: approvedState,
      at: approvedState === "done" || approvedState === "failed" ? (a.chain.find((s) => s.status === "rejected")?.at ?? lastApproval?.at) : undefined,
      note: `${approvedCount}/${a.chain.length} approvers`,
    });

    const submittedState: StepState = killed ? "failed" : submitted || done ? "done" : a.status === "approved" ? "current" : "todo";
    steps.push({ label: killed ? "Submission frozen" : "Submitted", state: submittedState, at: killed?.at ?? submitted?.at, note: killed ? "Kill switch" : submitted ? "Idempotent submission" : undefined });

    const confirmedState: StepState = confirmed || done ? "done" : submitted && !killed ? "current" : "todo";
    steps.push({ label: "Confirmed by partner", state: confirmedState, at: confirmed?.at });

    let reconState: StepState = "todo";
    let reconAt: string | undefined;
    if (done) {
      const confAt = confirmed ? new Date(confirmed.at).getTime() : 0;
      if (!confirmed || now - confAt > 6000) {
        reconState = "done";
        reconAt = confirmed ? new Date(confAt + 6000).toISOString() : undefined;
      } else reconState = "current";
    }
    steps.push({ label: "Reconciled to ledger", state: reconState, at: reconAt, note: reconState === "current" ? "Matching bank confirmation" : reconState === "done" ? "Auto-matched" : undefined });
    return steps;
  });

  /* ───────── helpers ───────── */

  needsYou(a: FinancialAction) {
    return a.status === "pending" && a.chain.some((s) => s.you && s.status === "pending");
  }
  kindIcon(k: ActionKind) {
    return KIND_ICON[k] ?? "send";
  }
  checkIcon(r: "pass" | "warn" | "fail") {
    return CHECK_ICON[r];
  }
  entityName(id: string) {
    return entityById(id)?.name ?? id;
  }
  eur(a: FinancialAction) {
    return toEur(a.amount, a.ccy);
  }
  rel(iso: string) {
    return relTime(iso, this.now());
  }
  shortHash(h: string) {
    return `${h.slice(0, 10)}…${h.slice(-6)}`;
  }
  riskColor(score: number) {
    return score < 30 ? "var(--x-pos)" : score < 60 ? "var(--x-warn)" : "var(--x-neg)";
  }
  private duration(ms: number) {
    const s = Math.floor(ms / 1000);
    const d = Math.floor(s / 86400);
    const h = Math.floor((s % 86400) / 3600);
    const m = Math.floor((s % 3600) / 60);
    const sec = s % 60;
    if (d > 0) return `${d}d ${h}h ${m}m`;
    if (h > 0) return `${h}h ${String(m).padStart(2, "0")}m ${String(sec).padStart(2, "0")}s`;
    return `${m}m ${String(sec).padStart(2, "0")}s`;
  }

  /* ───────── interactions ───────── */

  select(id: string) {
    this.selectedId.set(id);
    this.rejecting.set(false);
    this.reason.set("");
    this.router.navigate([], { relativeTo: this.route, queryParams: { id }, replaceUrl: true });
    if (window.innerWidth <= 1200) {
      setTimeout(() => this.detailPane()?.nativeElement.scrollIntoView({ behavior: "smooth", block: "start" }));
    }
  }

  backToList() {
    this.listPane()?.nativeElement.scrollIntoView({ behavior: "smooth", block: "start" });
  }

  approve(id: string) {
    this.store.approve(id);
  }

  confirmReject(id: string) {
    const r = this.reason().trim() || "Rejected by approver";
    this.store.reject(id, r);
    this.rejecting.set(false);
    this.reason.set("");
  }
}
