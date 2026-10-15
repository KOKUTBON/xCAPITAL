import { Component, computed, inject, signal } from "@angular/core";
import { RouterLink } from "@angular/router";
import { DemoStore } from "../../core/store";
import { AUDIT_EVENTS, POLICIES, TODAY } from "../../core/demo-data";
import { FORMAT_PIPES, compact, relTime } from "../../core/format";
import { Policy } from "../../core/models";
import { UI } from "../../shared/ui";

interface Usage {
  kind: "meter" | "headroom" | "text";
  text: string;
  sub?: string;
  fill: number;
  tone: "pos" | "warn" | "neg" | "";
}

interface Decision {
  key: string;
  at: string;
  target: string;
  title: string;
  outcome: "pass" | "warn" | "fail" | "info";
  label: string;
  detail: string;
  link?: string;
}

type PolicyType = "All" | "Limit" | "Mandate" | "Control";
const BASE_POLICY_SET = 41;
const BASE_VERSION_SUM = POLICIES.reduce((s, p) => s + p.version, 0);

@Component({
  selector: "x-guard",
  standalone: true,
  imports: [RouterLink, ...UI, ...FORMAT_PIPES],
  template: `<div class="page">
    <x-page-head eyebrow="Control · Policy engine" title="Guard" description="Deterministic controls that sit between every proposal and every payment. AI and people can propose; only actions that pass active policy can reach an approver — and nothing executes while the kill switch is engaged.">
      <a class="btn btn-secondary" routerLink="/app/approvals"><x-icon name="actions" [size]="16" />Approvals</a>
      <a class="btn btn-ghost" routerLink="/app/audit"><x-icon name="audit" [size]="16" />Evidence</a>
    </x-page-head>

    <section class="grid g-12">
      <div class="card span-5 kill rise" [class.armed]="store.killSwitch()">
        <div class="card-head">
          <div>
            <h2>Execution kill switch</h2>
            <div class="sub">Group-wide · applies to every connector</div>
          </div>
          <span class="badge" [class.neg]="store.killSwitch()" [class.pos]="!store.killSwitch()">
            <span class="dot live"></span>{{ store.killSwitch() ? "Frozen" : "Execution live" }}
          </span>
        </div>
        <div class="ks-body">
          <span class="ks-icon"><x-icon name="power" [size]="26" /></span>
          <div class="grow">
            <div class="ks-state">{{ store.killSwitch() ? "All new submissions are frozen" : "Submissions are flowing normally" }}</div>
            <p class="small muted">
              Engaging the switch blocks approvals and stops any new instruction from leaving xCAPITAL. Actions already submitted to a bank or broker are not recalled — they need recovery with the provider.
            </p>
          </div>
          <button class="switch danger big-switch" role="switch" [class.on]="store.killSwitch()" [attr.aria-checked]="store.killSwitch()" aria-label="Execution kill switch" (click)="store.toggleKillSwitch()"></button>
        </div>
        <div class="ks-foot xs faint">
          @if (lastKill(); as k) {
            Last change by {{ k.actor }} · {{ rel(k.at) }}
          } @else {
            Not engaged in the last 90 days · dual-control release in production
          }
        </div>
      </div>

      <div class="card span-7 rise" style="animation-delay: 40ms">
        <div class="card-head">
          <div>
            <h2>Engine</h2>
            <div class="sub">Policy set v{{ policySet() }} · evaluated on every proposal, approval and submission</div>
          </div>
          <span class="badge pos"><span class="dot"></span>Healthy</span>
        </div>
        <div class="stats">
          <div class="stat">
            <span class="xs faint">Policies active</span>
            <strong class="num">{{ activeCount() }}<span class="faint">/{{ store.policies().length }}</span></strong>
            <div class="meter pos"><span [style.width.%]="(activeCount() / store.policies().length) * 100"></span></div>
          </div>
          <div class="stat">
            <span class="xs faint">Evaluations today</span>
            <strong class="num">{{ evaluationsToday().toLocaleString("en-US") }}</strong>
            <span class="xs faint">proposals · approvals · re-checks</span>
          </div>
          <div class="stat">
            <span class="xs faint">Blocks today</span>
            <strong class="num" [class.neg]="blocksToday() > 0">{{ blocksToday() }}</strong>
            <span class="xs faint">0 overrides</span>
          </div>
          <div class="stat">
            <span class="xs faint">Avg evaluation</span>
            <strong class="num">38 ms</strong>
            <span class="xs faint">p99 112 ms</span>
          </div>
          <div class="stat">
            <span class="xs faint">Policy set</span>
            <strong class="num">v{{ policySet() }}</strong>
            <span class="xs faint">signed · versioned</span>
          </div>
        </div>
      </div>
    </section>

    <section class="card flow-card rise" style="animation-delay: 80ms">
      <div class="card-head">
        <div>
          <h2>Decision flow</h2>
          <div class="sub">AI proposes. Guard evaluates. People approve. Everything is evidenced.</div>
        </div>
      </div>
      <div class="flow">
        @for (s of flow(); track s.title; let last = $last) {
          <div class="f-node" [class]="'f-node ' + s.tone">
            <span class="icon-tile" [class]="'icon-tile ' + s.tone"><x-icon [name]="s.icon" [size]="16" /></span>
            <div class="grow">
              <strong class="small">{{ s.title }}</strong>
              <div class="xs faint">{{ s.sub }}</div>
            </div>
          </div>
          @if (!last) {
            <span class="f-arrow" aria-hidden="true"><x-icon name="arrow" [size]="16" /></span>
          }
        }
      </div>
    </section>

    <section class="grid g-12">
      <div class="card flush span-8">
        <div class="card-head">
          <div>
            <h2>Policies</h2>
            <div class="sub">Select a policy to inspect it as code · toggling publishes a new version</div>
          </div>
          <div class="seg" role="tablist" aria-label="Policy type">
            @for (t of types; track t) {
              <button role="tab" [class.on]="type() === t" [attr.aria-selected]="type() === t" (click)="type.set(t)">{{ t }}</button>
            }
          </div>
        </div>
        <div class="table-wrap">
          <table class="table">
            <thead>
              <tr>
                <th>Policy</th>
                <th>Type</th>
                <th>Owner</th>
                <th>Utilisation</th>
                <th class="right">Ver.</th>
                <th class="right">Active</th>
              </tr>
            </thead>
            <tbody>
              @for (p of rows(); track p.id) {
                <tr class="clickable" [class.selected]="selected()?.id === p.id" [class.off]="!p.active" (click)="selectedId.set(p.id)">
                  <td class="name-cell">
                    <button class="name-btn" (click)="selectedId.set(p.id); $event.stopPropagation()">
                      <span class="mono xs faint">{{ p.id }}</span>
                      <strong class="small">{{ p.name }}</strong>
                    </button>
                    <div class="xs faint">{{ p.scope }}</div>
                  </td>
                  <td><span class="badge" [class.primary]="p.type === 'Limit'" [class.ai]="p.type === 'Mandate'" [class.info]="p.type === 'Control'">{{ p.type }}</span></td>
                  <td class="small muted nowrap">{{ p.owner }}</td>
                  <td class="use-cell">
                    @if (usage(p); as u) {
                      @if (u.kind !== "text") {
                        <div class="meter" [class]="'meter ' + u.tone"><span [style.width.%]="u.fill"></span></div>
                      }
                      <div class="xs nowrap" [class.pos]="u.kind === 'headroom'"><span class="num">{{ u.text }}</span>
                        @if (u.sub) {
                          <span class="faint"> · {{ u.sub }}</span>
                        }
                      </div>
                    }
                  </td>
                  <td class="right mono small">v{{ p.version }}</td>
                  <td class="right">
                    <button class="switch" role="switch" [class.on]="p.active" [attr.aria-checked]="p.active" [attr.aria-label]="(p.active ? 'Suspend ' : 'Activate ') + p.name" (click)="toggle(p.id); $event.stopPropagation()"></button>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      </div>

      <div class="card span-4 detail">
        @if (selected(); as p) {
          <div class="card-head">
            <div>
              <div class="row" style="gap: 8px">
                <span class="mono xs faint">{{ p.id }}</span>
                <x-status [value]="p.active ? 'active' : 'warn'" [label]="p.active ? 'Active' : 'Suspended'" />
              </div>
              <h2 style="margin-top: 6px">{{ p.name }}</h2>
            </div>
          </div>
          <p class="small muted">{{ p.description }}</p>
          <dl class="facts">
            <div><dt>Type</dt><dd>{{ p.type }}</dd></div>
            <div><dt>Owner</dt><dd>{{ p.owner }}</dd></div>
            <div><dt>Scope</dt><dd>{{ p.scope }}</dd></div>
            <div><dt>Version</dt><dd class="mono">v{{ p.version }}</dd></div>
          </dl>
          @if (usage(p); as u) {
            @if (u.kind !== "text") {
              <div class="u-box">
                <div class="row between xs"><span class="faint">{{ u.kind === "headroom" ? "Headroom above floor" : "Utilisation" }}</span><span class="num" [class.pos]="u.kind === 'headroom'">{{ u.text }}</span></div>
                <div class="meter" [class]="'meter ' + u.tone"><span [style.width.%]="u.fill"></span></div>
                @if (u.sub) {
                  <div class="xs faint">{{ u.sub }}</div>
                }
              </div>
            }
          }
          <div class="code-head xs faint"><x-icon name="file" [size]="12" />policy.yaml · read-only</div>
          <pre class="code" [attr.aria-label]="'Policy ' + p.id + ' as code'"><code>{{ yaml(p) }}</code></pre>
          @if (related().length) {
            <div class="xs faint" style="margin-top: 12px">Referenced by</div>
            <div class="refs">
              @for (a of related(); track a.id) {
                <a class="chip" routerLink="/app/approvals" [queryParams]="{ id: a.id }"><span class="mono">{{ a.id }}</span> <x-status [value]="a.status" /></a>
              }
            </div>
          }
          <div class="row" style="margin-top: 14px">
            <button class="btn-secondary btn-sm" (click)="toggle(p.id)"><x-icon [name]="p.active ? 'pause' : 'play'" [size]="14" />{{ p.active ? "Suspend policy" : "Activate policy" }}</button>
          </div>
        }
      </div>
    </section>

    <section class="card rise">
      <div class="card-head">
        <div>
          <h2>Recent Guard decisions</h2>
          <div class="sub">Outcomes from proposal-time evaluation and pre-submission re-checks</div>
        </div>
        <div class="legend">
          <span><i style="background: var(--x-pos)"></i>Passed</span>
          <span><i style="background: var(--x-warn)"></i>Passed with warnings</span>
          <span><i style="background: var(--x-neg)"></i>Blocked</span>
        </div>
      </div>
      <div class="decisions">
        @for (d of decisions(); track d.key) {
          <div class="dec" [class]="'dec ' + d.outcome">
            <span class="d-bar"></span>
            <div class="grow" style="min-width: 0">
              <div class="row wrap" style="gap: 8px">
                @if (d.link) {
                  <a class="mono small" routerLink="/app/approvals" [queryParams]="{ id: d.link }">{{ d.target }}</a>
                } @else {
                  <span class="mono small">{{ d.target }}</span>
                }
                <span class="badge" [class.pos]="d.outcome === 'pass'" [class.warn]="d.outcome === 'warn'" [class.neg]="d.outcome === 'fail'" [class.info]="d.outcome === 'info'">{{ d.label }}</span>
              </div>
              <div class="small truncate" style="margin-top: 4px">{{ d.title }}</div>
              <div class="xs faint truncate">{{ d.detail }}</div>
            </div>
            <span class="xs faint nowrap">{{ rel(d.at) }}</span>
          </div>
        }
      </div>
    </section>
  </div>`,
  styles: [
    `
      .kill { display: flex; flex-direction: column; transition: border-color .2s, background .2s; }
      .kill.armed { border-color: color-mix(in srgb, var(--x-neg) 55%, transparent); background: linear-gradient(160deg, var(--x-neg-soft), var(--x-surface) 60%); }
      .ks-body { display: flex; gap: 16px; align-items: center; }
      .ks-icon { display: grid; place-items: center; width: 56px; height: 56px; border-radius: 16px; flex-shrink: 0; background: var(--x-pos-soft); color: var(--x-pos); transition: background .2s, color .2s; }
      .kill.armed .ks-icon { background: var(--x-neg-soft); color: var(--x-neg); }
      .ks-state { font: 600 16px/1.3 var(--x-font-display); margin-bottom: 6px; }
      .big-switch { width: 58px; height: 32px; flex-shrink: 0; }
      .big-switch::after { width: 24px; height: 24px; top: 3px; left: 3px; }
      .big-switch.on::after { transform: translateX(26px); }
      .ks-foot { margin-top: auto; padding-top: 14px; border-top: 1px solid var(--x-border); margin-top: 16px; }

      .stats { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 12px; }
      .stat { display: flex; flex-direction: column; gap: 6px; padding: 12px; border-radius: var(--x-radius); background: var(--x-surface-2); border: 1px solid var(--x-border); min-width: 0; }
      .stat strong { font: 600 22px/1.1 var(--x-font-display); font-variant-numeric: tabular-nums; }
      .stat strong .faint { font-size: 15px; }

      .flow { display: flex; align-items: stretch; gap: 6px; }
      .f-node { flex: 1; min-width: 0; display: flex; align-items: center; gap: 10px; padding: 12px; border-radius: var(--x-radius); border: 1px solid var(--x-border); background: var(--x-surface-2); transition: border-color .2s; }
      .f-node.ai { border-color: color-mix(in srgb, var(--x-ai) 35%, transparent); }
      .f-node.primary { border-color: color-mix(in srgb, var(--x-primary) 40%, transparent); box-shadow: 0 0 0 3px var(--x-primary-soft); }
      .f-node.neg { border-color: color-mix(in srgb, var(--x-neg) 50%, transparent); background: var(--x-neg-soft); }
      .f-node .icon-tile.primary { background: var(--x-primary-soft); color: var(--x-primary); }
      .f-arrow { display: grid; place-items: center; color: var(--x-text-3); flex-shrink: 0; }

      .name-btn { height: auto; padding: 0; background: transparent; color: inherit; font-weight: 400; display: flex; flex-direction: column; gap: 2px; align-items: flex-start; text-align: left; white-space: normal; }
      .name-cell { min-width: 210px; }
      .name-cell .mono { white-space: nowrap; }
      .name-btn:hover { background: transparent; color: var(--x-primary); }
      tr.off td { opacity: .6; }
      tr.off td:last-child { opacity: 1; }
      .use-cell { min-width: 170px; }
      .use-cell .meter { margin-bottom: 5px; max-width: 200px; }

      .detail { position: sticky; top: calc(var(--x-topbar) + 16px); align-self: start; }
      .facts { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; margin: 14px 0; }
      .facts dt { font-size: 11px; color: var(--x-text-3); }
      .facts dd { margin: 2px 0 0; font-size: 12.5px; }
      .u-box { display: flex; flex-direction: column; gap: 6px; padding: 12px; border-radius: var(--x-radius); background: var(--x-surface-2); border: 1px solid var(--x-border); margin-bottom: 14px; }
      .code-head { display: flex; align-items: center; gap: 6px; margin-bottom: 6px; }
      .code { margin: 0; padding: 14px; border-radius: var(--x-radius); background: var(--x-canvas); border: 1px solid var(--x-border); font: 12px/1.6 var(--x-font-mono); color: var(--x-text-2); overflow-x: auto; white-space: pre; }
      .refs { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 6px; }
      .chip { display: inline-flex; align-items: center; gap: 6px; padding: 3px 4px 3px 8px; border-radius: 8px; font-size: 11.5px; color: var(--x-text); background: var(--x-surface-2); border: 1px solid var(--x-border); }
      .chip:hover { border-color: var(--x-border-strong); color: var(--x-text); }

      .decisions { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); column-gap: 24px; }
      .dec { display: flex; gap: 12px; align-items: center; padding: 10px 0; border-bottom: 1px solid var(--x-border); min-width: 0; }
      .d-bar { align-self: stretch; width: 3px; border-radius: 3px; flex-shrink: 0; background: var(--x-info); }
      .dec.pass .d-bar { background: var(--x-pos); }
      .dec.warn .d-bar { background: var(--x-warn); }
      .dec.fail .d-bar { background: var(--x-neg); }

      @media (max-width: 1200px) {
        .detail { position: static; }
        .stats { grid-template-columns: repeat(3, minmax(0, 1fr)); }
        .flow { flex-wrap: wrap; }
        .f-node { flex: 1 1 200px; }
      }
      @media (max-width: 760px) {
        .stats { grid-template-columns: repeat(2, minmax(0, 1fr)); }
        .flow { flex-direction: column; flex-wrap: nowrap; }
        .f-node { flex: none; }
        .f-arrow { transform: rotate(90deg); height: 18px; }
        .decisions { grid-template-columns: minmax(0, 1fr); }
        .ks-icon { display: none; }
      }
    `,
  ],
})
export class GuardPage {
  readonly store = inject(DemoStore);
  readonly types: PolicyType[] = ["All", "Limit", "Mandate", "Control"];
  readonly type = signal<PolicyType>("All");
  readonly selectedId = signal<string>("LIM-01");

  readonly rows = computed(() => {
    const t = this.type();
    return this.store.policies().filter((p) => t === "All" || p.type === t);
  });
  readonly selected = computed(() => this.store.policies().find((p) => p.id === this.selectedId()) ?? this.rows()[0] ?? null);
  readonly activeCount = computed(() => this.store.policies().filter((p) => p.active).length);
  readonly policySet = computed(() => BASE_POLICY_SET + this.store.policies().reduce((s, p) => s + p.version, 0) - BASE_VERSION_SUM);

  private guardEvents = computed(() => this.store.audit().filter((e) => e.action.startsWith("guard.")));
  private todayEvents = computed(() => this.guardEvents().filter((e) => new Date(e.at).getTime() >= TODAY));
  /** Fictional daily baseline plus every evaluation-relevant event in this session. */
  readonly evaluationsToday = computed(() => 1_284 + (this.store.audit().length - AUDIT_EVENTS.length));
  readonly blocksToday = computed(() => {
    const blocked = new Set(this.store.actions().filter((a) => a.status === "blocked").map((a) => a.id));
    for (const e of this.todayEvents()) if (e.action === "guard.blocked") blocked.add(e.target);
    return blocked.size;
  });
  readonly lastKill = computed(() => [...this.guardEvents()].reverse().find((e) => e.action.startsWith("guard.kill_switch")));

  readonly related = computed(() => {
    const p = this.selected();
    if (!p) return [];
    return this.store.actions().filter((a) => a.evidence.some((e) => e.includes(p.id)) || a.rationale.includes(p.id) || a.checks.some((c) => c.detail.includes(p.id)));
  });

  readonly flow = computed(() => {
    const ks = this.store.killSwitch();
    const pending = this.store.pendingActions().length;
    const blocked = this.store.actions().filter((a) => a.status === "blocked").length;
    return [
      { title: "Proposal", sub: "AI, people or standing rules", icon: "copilot", tone: "ai" },
      { title: "Guard", sub: `${this.activeCount()} policies · ${blocked} blocked`, icon: "guard", tone: "primary" },
      { title: "Authorization", sub: `Maker-checker · ${pending} pending`, icon: "users", tone: "" },
      { title: "Execution", sub: ks ? "Frozen by kill switch" : "Idempotent submission", icon: ks ? "power" : "send", tone: ks ? "neg" : "" },
      { title: "Reconciliation", sub: "Matched to bank & ledger", icon: "ledger", tone: "" },
      { title: "Evidence", sub: "Hash-chained audit trail", icon: "audit", tone: "accent" },
    ];
  });

  readonly decisions = computed<Decision[]>(() => {
    const out: Decision[] = [];
    const actions = this.store.actions();
    for (const a of actions) {
      const fails = a.checks.filter((c) => c.result === "fail");
      const warns = a.checks.filter((c) => c.result === "warn");
      const outcome = fails.length ? "fail" : warns.length ? "warn" : "pass";
      const first = fails[0] ?? warns[0];
      out.push({
        key: "eval-" + a.id,
        at: new Date(new Date(a.createdAt).getTime() + 60_000).toISOString(),
        target: a.id,
        link: a.id,
        title: a.title,
        outcome,
        label: outcome === "fail" ? (a.status === "blocked" ? "Blocked" : "Failed check") : outcome === "warn" ? `Passed · ${warns.length} warning${warns.length > 1 ? "s" : ""}` : `Passed ${a.checks.length}/${a.checks.length}`,
        detail: first ? `${first.label} — ${first.detail}` : a.checks.map((c) => c.label).join(" · "),
      });
    }
    for (const e of this.guardEvents()) {
      // Proposal-time evaluations are already represented by the action rows above.
      const a = actions.find((x) => x.id === e.target);
      if (a && (e.action === "guard.evaluated" || e.action === "guard.blocked") && Math.abs(new Date(e.at).getTime() - new Date(a.createdAt).getTime()) < 10 * 60_000) continue;
      const fail = e.action === "guard.blocked" || e.action === "guard.kill_switch.on";
      const info = e.action === "guard.kill_switch.off";
      out.push({
        key: "ev-" + e.seq,
        at: e.at,
        target: e.target,
        link: a ? a.id : undefined,
        title: e.action.replace("guard.", "").replace(/[._]/g, " "),
        outcome: fail ? "fail" : info ? "info" : "pass",
        label: e.action === "guard.revalidated" ? "Re-check passed" : e.action.startsWith("guard.kill_switch") ? (fail ? "Kill switch on" : "Kill switch off") : fail ? "Blocked" : "Passed",
        detail: `${e.detail} · ${e.actor}`,
      });
    }
    return out.sort((a, b) => b.at.localeCompare(a.at)).slice(0, 12);
  });

  usage(p: Policy): Usage {
    const fmt = (v: number, full = true) => {
      if (!p.unit) return compact(v, p.ccy, 1);
      const u = full ? p.unit : p.unit.split(" ")[0];
      return `${+v.toFixed(1)}${u.startsWith("%") ? "" : " "}${u}`;
    };
    if (p.type === "Control") {
      if (p.id === "CTL-05") {
        const inBand = p.used >= 60 && p.used <= p.limit;
        return { kind: "meter", text: `${p.used}% hedged`, sub: "band 60–80%", fill: (p.used / 100) * 100, tone: inBand ? "pos" : "warn" };
      }
      if (p.limit === 0) return { kind: "text", text: "Always on", fill: 0, tone: "" };
      return { kind: "text", text: p.unit ? `${fmt(p.limit)} window` : `≥ ${compact(p.limit, p.ccy, 0)}`, fill: 0, tone: "" };
    }
    if (p.used > p.limit && /buffer|floor|minimum/i.test(p.name)) {
      const head = p.used - p.limit;
      return { kind: "headroom", text: `${compact(head, p.ccy, 1)} above floor`, sub: `P10 ${compact(p.used, p.ccy, 1)} vs ${compact(p.limit, p.ccy, 0)}`, fill: Math.min(100, (head / p.limit) * 100 * 2), tone: "pos" };
    }
    const ratio = p.limit ? (p.used / p.limit) * 100 : 0;
    return { kind: "meter", text: `${fmt(p.used, false)} / ${fmt(p.limit)}`, sub: `${ratio.toFixed(0)}%`, fill: Math.min(100, ratio), tone: ratio < 70 ? "pos" : ratio < 90 ? "warn" : "neg" };
  }

  yaml(p: Policy): string {
    const slug = p.name.toLowerCase().replace(/[^a-z0-9]+/g, "_").replace(/^_|_$/g, "");
    const floor = /buffer|floor|minimum/i.test(p.name);
    const op = p.type === "Control" ? (p.limit ? (p.unit === "h" ? "cooling_off" : p.unit === "%" ? "within_band" : ">=") : "require") : floor ? ">=" : "<=";
    const lines = [
      `policy:`,
      `  id: ${p.id}`,
      `  name: "${p.name}"`,
      `  type: ${p.type.toLowerCase()}`,
      `  version: ${p.version}`,
      `  status: ${p.active ? "active" : "suspended"}`,
      `  owner: "${p.owner}"`,
      `  scope: "${p.scope}"`,
      `  rule:`,
      `    metric: ${slug}`,
      `    operator: "${op}"`,
    ];
    if (p.id === "CTL-05") lines.push(`    range: [60, 80]`, `    unit: "%"`);
    else if (p.limit) lines.push(`    threshold: ${p.limit}`, `    unit: ${p.unit ? `"${p.unit}"` : p.ccy}`);
    else lines.push(`    lists: [EU, OFAC, UN, UK_HMT]`);
    lines.push(
      `  evaluate_on: [proposal, approval, pre_submission]`,
      `  on_breach: ${p.type === "Mandate" ? "escalate" : "block"}`,
      `  override: none`,
      `  evidence: audit_chain`,
    );
    return lines.join("\n");
  }

  rel(iso: string) {
    return relTime(iso);
  }

  toggle(id: string) {
    this.selectedId.set(id);
    this.store.togglePolicy(id);
  }
}
