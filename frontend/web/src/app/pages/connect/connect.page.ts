import { Component, OnDestroy, computed, inject, signal } from "@angular/core";
import { DemoStore } from "../../core/store";
import { ACCOUNTS, JOURNALS, entityById } from "../../core/demo-data";
import { Connector, ConnectorKind } from "../../core/models";
import { FORMAT_PIPES, compact, relTime } from "../../core/format";
import { UI } from "../../shared/ui";

type KindFilter = "All" | ConnectorKind;

const KIND_ICON: Record<ConnectorKind, string> = {
  Bank: "bank",
  ERP: "building",
  Broker: "portfolio",
  "Market data": "markets",
  Payroll: "users",
  Cards: "wallet",
};

const CATALOG = [
  { icon: "bank", title: "Banks", protocols: ["PSD2", "EBICS", "SWIFT"], body: "Balances, statements and payment initiation via open banking or host-to-host." },
  { icon: "building", title: "ERP", protocols: ["OData", "REST"], body: "AR, AP, vendors and GL read access for forecast and reconciliation." },
  { icon: "users", title: "Payroll", protocols: ["SFTP"], body: "Payroll registers for exact salary obligations by entity." },
  { icon: "portfolio", title: "Brokers", protocols: ["FIX 4.4"], body: "Positions, orders and executions for the strategic reserve." },
  { icon: "upload", title: "CSV upload", protocols: ["CSV", "XLSX"], body: "One-off statements or legacy systems, mapped with a template." },
];

const WEBHOOKS = [
  { event: "action.approved", desc: "A financial action reached its approval quorum", delivered: 412, p95: 180 },
  { event: "execution.confirmed", desc: "A partner confirmed execution", delivered: 388, p95: 220 },
  { event: "recon.exception", desc: "A bank line could not be matched", delivered: 57, p95: 160 },
  { event: "guard.blocked", desc: "Guard blocked an action at evaluation", delivered: 14, p95: 150 },
  { event: "sync.completed", desc: "A connector finished a sync cycle", delivered: 2_904, p95: 140 },
];

function escapeHtml(s: string) {
  return s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}
/** Tiny JSON highlighter: escape first, then wrap keys / strings / numbers / literals. */
function highlightJson(json: string) {
  return escapeHtml(json).replace(/("(?:[^"\\]|\\.)*")(\s*:)?|\b(-?\d+(?:\.\d+)?)\b|\b(true|false|null)\b/g, (m, str, colon, numb, lit) => {
    if (str) return colon ? `<span class="k">${str}</span>${colon}` : `<span class="s">${str}</span>`;
    if (numb) return `<span class="n">${numb}</span>`;
    if (lit) return `<span class="l">${lit}</span>`;
    return m;
  });
}

@Component({
  selector: "x-connect",
  standalone: true,
  imports: [...UI, ...FORMAT_PIPES],
  template: `<div class="page">
    <x-page-head eyebrow="Platform · Integrations" title="Connect" description="Every bank, ERP, broker and data feed that powers the ledger, forecast and Guard. Raw payloads are kept as evidence before anything is normalized.">
      <button class="btn-secondary" (click)="resyncAll()" [disabled]="!attention().length"><x-icon name="refresh" [size]="16" />Re-sync issues</button>
      <button class="btn-glow" (click)="onboard('New connector')"><x-icon name="plus" [size]="16" />Add connector</button>
    </x-page-head>

    <section class="grid g-4">
      <div class="card kpi glow rise">
        <div class="label"><x-icon name="connect" [size]="15" />Connectors healthy</div>
        <div class="value">{{ healthy() }}<span class="unit">/ {{ store.connectors().length }}</span></div>
        <div class="delta" [class.warn]="attention().length > 0" [class.pos]="!attention().length">
          {{ attention().length ? attention().length + " need attention" : "All feeds healthy" }}
        </div>
        <div class="health-bar">
          @for (c of store.connectors(); track c.id) {
            <span [class]="'hb ' + c.status" [attr.title]="c.name + ' · ' + c.status"></span>
          }
        </div>
      </div>
      <div class="card kpi rise" style="animation-delay: 40ms">
        <div class="label"><x-icon name="layers" [size]="15" />Records ingested · 24h</div>
        <div class="value">{{ records() | compact: null : 2 }}</div>
        <div class="delta faint">{{ nonMarket() | compact: null : 1 }} excl. market ticks</div>
      </div>
      <div class="card kpi rise" style="animation-delay: 80ms">
        <div class="label"><x-icon name="clock" [size]="15" />Median API latency</div>
        <div class="value">{{ medianLatency() }}<span class="unit">ms</span></div>
        <div class="delta faint">p95 {{ p95Latency() | num: 0 }} ms · excl. file feeds</div>
      </div>
      <div class="card kpi rise" style="animation-delay: 120ms">
        <div class="label"><x-icon name="target" [size]="15" />Data coverage</div>
        <div class="value">{{ coverage().toFixed(1) }}%</div>
        <div class="delta" [class.warn]="coverage() < 98" [class.pos]="coverage() >= 98">Avg. field coverage across feeds</div>
        <div class="meter" [class.pos]="coverage() >= 98" [class.warn]="coverage() < 98" style="margin-top: 12px"><span [style.width.%]="coverage()"></span></div>
      </div>
    </section>

    <section class="grid g-12">
      <div class="card span-12">
        <div class="card-head">
          <div>
            <h2>Data pipeline</h2>
            <div class="sub">From raw partner payloads to a reconciled ledger and a live forecast — every hop is evidenced</div>
          </div>
          <span class="badge" [class.pos]="!attention().length" [class.warn]="attention().length > 0"><span class="dot live"></span>{{ attention().length ? "Degraded input" : "Flowing" }}</span>
        </div>
        <div class="pipe" role="list">
          @for (s of pipeline(); track s.name; let last = $last) {
            <div class="stage" role="listitem" [class.warn]="s.tone === 'warn'">
              <span class="icon-tile" [class.accent]="s.tone === 'ok'" [class.warn]="s.tone === 'warn'" [class.ai]="s.tone === 'ai'"><x-icon [name]="s.icon" [size]="16" /></span>
              <div class="stage-name">{{ s.name }}</div>
              <div class="stage-count num">{{ s.count }}</div>
              <div class="xs faint">{{ s.unit }}</div>
              <span class="badge stage-badge" [class.pos]="s.tone === 'ok'" [class.warn]="s.tone === 'warn'" [class.ai]="s.tone === 'ai'">{{ s.status }}</span>
            </div>
            @if (!last) {
              <div class="flow" aria-hidden="true"><i></i><i></i><i></i></div>
            }
          }
        </div>
      </div>
    </section>

    <section class="col">
      <div class="row between wrap">
        <div>
          <h2 class="section-title">Connectors</h2>
          <div class="xs faint">{{ filtered().length }} of {{ store.connectors().length }} · scopes are least-privilege and reviewed quarterly</div>
        </div>
        <div class="row wrap" style="gap: 8px">
          <input class="input search" type="search" placeholder="Search name or protocol" [value]="query()" (input)="query.set($any($event.target).value)" aria-label="Search connectors" />
          <div class="seg" role="tablist" aria-label="Connector kind">
            @for (k of kinds; track k) {
              <button [class.on]="kind() === k" (click)="kind.set(k)" role="tab" [attr.aria-selected]="kind() === k">
                {{ k }} <span class="cnt">{{ countByKind()[k] }}</span>
              </button>
            }
          </div>
        </div>
      </div>

      <div class="conn-grid">
        @for (c of filtered(); track c.id) {
          <article class="card conn rise" [class.bad]="c.status === 'offline'" [class.deg]="c.status === 'degraded'">
            <div class="conn-head">
              <span class="icon-tile" [class.neg]="c.status === 'offline'" [class.warn]="c.status === 'degraded'"><x-icon [name]="kindIcon[c.kind]" [size]="16" /></span>
              <div class="grow" style="min-width: 0">
                <strong class="truncate conn-name">{{ c.name }}</strong>
                <div class="xs faint truncate">{{ c.protocol }} · {{ c.region }}</div>
              </div>
              <span [class]="'dot live sdot ' + c.status" [attr.aria-label]="'Status ' + c.status"></span>
            </div>
            <div class="row wrap" style="gap: 6px; margin-top: 12px">
              <span class="badge primary">{{ c.kind }}</span>
              <x-status [value]="c.status" />
            </div>
            <dl class="stats">
              <div>
                <dt>Last sync</dt>
                <dd class="num">{{ c.status === "syncing" ? "now" : rel(c.lastSync) }}</dd>
              </div>
              <div>
                <dt>Latency</dt>
                <dd class="num" [class.warn]="c.latencyMs > 2000">{{ c.latencyMs ? (c.latencyMs | num: 0) + " ms" : "batch" }}</dd>
              </div>
              <div>
                <dt>Records 24h</dt>
                <dd class="num">{{ c.records24h | compact: null : 1 }}</dd>
              </div>
            </dl>
            <div class="row between xs" style="margin-top: 12px">
              <span class="faint">Coverage</span>
              <span class="num" [class.neg]="c.coverage < 50" [class.warn]="c.coverage >= 50 && c.coverage < 100">{{ c.coverage }}%</span>
            </div>
            <div class="meter" [class.pos]="c.coverage === 100" [class.warn]="c.coverage < 100 && c.coverage >= 50" [class.neg]="c.coverage < 50" style="margin-top: 6px">
              <span [style.width.%]="c.coverage"></span>
            </div>
            <div class="scopes">
              @for (s of c.scopes; track s) {
                <code class="scope" [class.write]="s.includes(':initiate') || s === 'wires' || s === 'ach' || s === 'orders'">{{ s }}</code>
              }
            </div>
            <div class="conn-foot">
              <span class="xs faint">{{ hint(c) }}</span>
              <button class="btn-secondary btn-sm" (click)="store.resync(c.id)" [disabled]="c.status === 'syncing'" [attr.aria-label]="'Re-sync ' + c.name">
                @if (c.status === "syncing") {
                  <span class="spin" aria-hidden="true"></span>Syncing…
                } @else {
                  <x-icon name="refresh" [size]="13" />Re-sync
                }
              </button>
            </div>
          </article>
        } @empty {
          <div class="card empty span-all">No connectors match this filter.</div>
        }
      </div>
    </section>

    <section class="grid g-12">
      <div class="card span-5">
        <div class="card-head">
          <div>
            <h2>Add connector</h2>
            <div class="sub">Certified integrations · least-privilege scopes by default</div>
          </div>
          <span class="badge">Catalog</span>
        </div>
        <div class="catalog">
          @for (t of catalog; track t.title) {
            <button class="tile" (click)="onboard(t.title)">
              <span class="icon-tile accent"><x-icon [name]="t.icon" [size]="16" /></span>
              <span class="grow tile-text">
                <strong>{{ t.title }}</strong>
                <span class="xs faint">{{ t.body }}</span>
                <span class="row wrap" style="gap: 4px; margin-top: 4px">
                  @for (p of t.protocols; track p) {
                    <span class="proto">{{ p }}</span>
                  }
                </span>
              </span>
              <x-icon name="plus" [size]="14" class="tile-plus" />
            </button>
          }
        </div>
      </div>

      <div class="card span-7">
        <div class="card-head">
          <div>
            <h2>Build on <span class="gradient-text">xCAPITAL</span></h2>
            <div class="sub">REST API and signed webhooks · same scopes and evidence as the app</div>
          </div>
          <div class="seg" role="tablist" aria-label="Snippet">
            <button [class.on]="snippet() === 'rest'" (click)="snippet.set('rest')" role="tab" [attr.aria-selected]="snippet() === 'rest'">REST</button>
            <button [class.on]="snippet() === 'hook'" (click)="snippet.set('hook')" role="tab" [attr.aria-selected]="snippet() === 'hook'">Webhook</button>
          </div>
        </div>
        <div class="code">
          <div class="code-bar">
            @if (snippet() === "rest") {
              <span class="method">GET</span><span class="path">/v1/accounts?entity=it</span>
            } @else {
              <span class="method post">POST</span><span class="path">https://erp.northstar.example/hooks/xcap</span>
            }
            <span class="grow"></span>
            <span class="xs faint hide-sm">{{ snippet() === "rest" ? "200 OK · 84 ms" : "X-Xcap-Signature: v1=…" }}</span>
            <button class="btn-ghost btn-icon btn-sm" (click)="copySnippet()" aria-label="Copy snippet" title="Copy"><x-icon name="file" [size]="14" /></button>
          </div>
          <pre><code [innerHTML]="snippet() === 'rest' ? restHtml : hookHtml"></code></pre>
        </div>
        <div class="row between" style="margin: 16px 0 6px">
          <strong class="small">Webhooks</strong>
          <span class="xs faint">Last 30 days · HMAC-SHA256 signed · retried with backoff</span>
        </div>
        <div class="list">
          @for (w of webhooks; track w.event) {
            <div class="list-item hook">
              <span class="dot live pos"></span>
              <code class="evt">{{ w.event }}</code>
              <span class="xs faint grow truncate">{{ w.desc }}</span>
              <span class="num xs muted nowrap">{{ w.delivered | num: 0 }} · p95 {{ w.p95 }}ms</span>
            </div>
          }
        </div>
      </div>
    </section>
  </div>`,
  styles: [
    `
      .unit { font: 500 14px var(--x-font); color: var(--x-text-3); margin-left: 4px; }
      .health-bar { display: flex; gap: 3px; margin-top: 14px; }
      .hb { flex: 1; height: 8px; border-radius: 3px; background: var(--x-pos); }
      .hb.degraded { background: var(--x-warn); }
      .hb.offline { background: var(--x-neg); }
      .hb.syncing { background: var(--x-info); animation: cn-pulse 1s ease-in-out infinite alternate; }
      @keyframes cn-pulse { to { opacity: 0.4; } }

      .pipe { display: flex; align-items: stretch; gap: 0; }
      .stage { flex: 1 1 0; min-width: 0; display: flex; flex-direction: column; align-items: flex-start; gap: 4px; padding: 14px; border-radius: 14px; background: var(--x-surface-2); border: 1px solid var(--x-border); }
      .stage.warn { border-color: color-mix(in srgb, var(--x-warn) 40%, transparent); }
      .stage .icon-tile { margin-bottom: 6px; }
      .stage-name { font-size: 12.5px; font-weight: 600; }
      .stage-count { font-size: 19px; font-weight: 600; letter-spacing: -0.02em; }
      .stage-badge { margin-top: 6px; }
      .flow { position: relative; flex: 0 0 36px; align-self: center; height: 2px; background: repeating-linear-gradient(90deg, var(--x-border-strong) 0 4px, transparent 4px 8px); }
      .flow i { position: absolute; top: -2px; left: 0; width: 6px; height: 6px; border-radius: 50%; background: var(--x-accent); box-shadow: 0 0 8px var(--x-accent); animation: cn-flow-x 1.8s linear infinite; opacity: 0; }
      .flow i:nth-child(2) { animation-delay: 0.6s; }
      .flow i:nth-child(3) { animation-delay: 1.2s; }
      @keyframes cn-flow-x { 0% { left: 0; opacity: 0; } 15% { opacity: 1; } 85% { opacity: 1; } 100% { left: calc(100% - 6px); opacity: 0; } }
      @keyframes cn-flow-y { 0% { top: 0; opacity: 0; } 15% { opacity: 1; } 85% { opacity: 1; } 100% { top: calc(100% - 6px); opacity: 0; } }

      .section-title { font-size: 15px; font-weight: 600; }
      .search { width: 220px; max-width: 100%; }
      .seg { flex-wrap: wrap; }
      .cnt { font: 500 10.5px var(--x-font-mono); color: var(--x-text-3); margin-left: 2px; }
      .conn-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 16px; }
      .span-all { grid-column: 1 / -1; }
      .conn { display: flex; flex-direction: column; transition: border-color 0.15s, transform 0.15s; }
      .conn:hover { border-color: var(--x-border-strong); transform: translateY(-1px); }
      .conn.deg { border-color: color-mix(in srgb, var(--x-warn) 35%, var(--x-border)); }
      .conn.bad { border-color: color-mix(in srgb, var(--x-neg) 35%, var(--x-border)); }
      .conn-head { display: flex; align-items: center; gap: 11px; }
      .conn-name { display: block; font-size: 13.5px; }
      .sdot { color: var(--x-pos); }
      .sdot.degraded { color: var(--x-warn); }
      .sdot.offline { color: var(--x-neg); animation: none; }
      .sdot.syncing { color: var(--x-info); }
      .stats { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; margin: 14px 0 0; padding: 10px 0; border-top: 1px solid var(--x-border); border-bottom: 1px solid var(--x-border); }
      .stats dt { font-size: 10.5px; color: var(--x-text-3); text-transform: uppercase; letter-spacing: 0.08em; }
      .stats dd { margin: 4px 0 0; font-size: 13px; }
      .scopes { display: flex; flex-wrap: wrap; gap: 5px; margin-top: 12px; }
      .scope { font: 500 10.5px/1 var(--x-font-mono); padding: 4px 6px; border-radius: 5px; background: var(--x-surface-3); color: var(--x-text-2); }
      .scope.write { background: var(--x-warn-soft); color: var(--x-warn); }
      .conn-foot { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-top: auto; padding-top: 14px; }
      .spin { width: 12px; height: 12px; border-radius: 50%; border: 2px solid var(--x-border-strong); border-top-color: var(--x-primary); animation: cn-spin 0.7s linear infinite; }
      @keyframes cn-spin { to { transform: rotate(360deg); } }

      .catalog { display: flex; flex-direction: column; gap: 8px; }
      .tile { height: auto; padding: 12px; gap: 12px; justify-content: flex-start; align-items: flex-start; white-space: normal; text-align: left; font-weight: 400; background: var(--x-surface-2); border: 1px solid var(--x-border); color: var(--x-text); }
      .tile:hover { background: var(--x-surface-3); border-color: var(--x-border-strong); color: var(--x-text); }
      .tile-text { display: flex; flex-direction: column; gap: 3px; }
      .tile-text strong { font-size: 13px; }
      .tile-plus { color: var(--x-text-3); margin-top: 2px; }
      .tile:hover .tile-plus { color: var(--x-text); }
      .proto { font: 500 10.5px/1 var(--x-font-mono); padding: 3px 6px; border-radius: 5px; background: var(--x-accent-soft); color: var(--x-accent); }

      .code { border-radius: 12px; border: 1px solid var(--x-border); background: var(--x-canvas); overflow: hidden; }
      .code-bar { display: flex; align-items: center; gap: 8px; padding: 6px 6px 6px 12px; border-bottom: 1px solid var(--x-border); background: var(--x-surface-2); min-width: 0; }
      .method { font: 600 11px/1 var(--x-font-mono); padding: 4px 6px; border-radius: 5px; background: var(--x-pos-soft); color: var(--x-pos); }
      .method.post { background: var(--x-info-soft); color: var(--x-info); }
      .path { font: 500 12px var(--x-font-mono); color: var(--x-text); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; min-width: 0; }
      pre { margin: 0; padding: 14px 16px; max-height: 280px; overflow: auto; font: 12px/1.6 var(--x-font-mono); color: var(--x-text-2); }
      pre ::ng-deep .k { color: var(--x-primary); }
      pre ::ng-deep .s { color: var(--x-accent); }
      pre ::ng-deep .n { color: var(--x-warn); }
      pre ::ng-deep .l { color: var(--x-ai); }
      .hook { gap: 10px; padding: 9px 0; }
      .evt { font: 500 11.5px var(--x-font-mono); padding: 2px 6px; border-radius: 5px; background: var(--x-surface-3); color: var(--x-text); white-space: nowrap; }

      @media (max-width: 900px) {
        .pipe { flex-direction: column; }
        .stage { flex-direction: row; flex-wrap: wrap; align-items: center; gap: 10px; }
        .stage .icon-tile { margin: 0; }
        .stage-name { flex: 1; }
        .stage-count { font-size: 16px; }
        .stage-badge { margin: 0; }
        .stage > .xs { display: none; }
        .flow { flex-basis: 24px; width: 2px; height: 24px; align-self: flex-start; margin-left: 30px; background: repeating-linear-gradient(180deg, var(--x-border-strong) 0 4px, transparent 4px 8px); }
        .flow i { left: -2px; top: 0; animation-name: cn-flow-y; }
      }
      @media (max-width: 640px) {
        .search { width: 100%; }
        .hide-sm { display: none; }
        .hook .xs.faint { display: none; }
      }
      @media (prefers-reduced-motion: reduce) {
        .flow i { display: none; }
        .hb.syncing { animation: none; }
      }
    `,
  ],
})
export class ConnectPage implements OnDestroy {
  readonly store = inject(DemoStore);
  readonly kindIcon = KIND_ICON;
  readonly catalog = CATALOG;
  readonly webhooks = WEBHOOKS;
  readonly kinds: KindFilter[] = ["All", "Bank", "ERP", "Broker", "Market data", "Payroll", "Cards"];
  readonly kind = signal<KindFilter>("All");
  readonly query = signal("");
  readonly snippet = signal<"rest" | "hook">("rest");

  /** Ticks so "last sync" labels stay fresh while the page is open. */
  private readonly now = signal(Date.now());
  private timer = setInterval(() => this.now.set(Date.now()), 10_000);

  readonly healthy = computed(() => this.store.connectors().filter((c) => c.status === "healthy").length);
  readonly attention = computed(() => this.store.connectors().filter((c) => c.status === "degraded" || c.status === "offline"));
  readonly records = computed(() => this.store.connectors().reduce((s, c) => s + c.records24h, 0));
  readonly nonMarket = computed(() => this.store.connectors().filter((c) => c.kind !== "Market data").reduce((s, c) => s + c.records24h, 0));
  private readonly latencies = computed(() =>
    this.store
      .connectors()
      .map((c) => c.latencyMs)
      .filter((v) => v > 0)
      .sort((a, b) => a - b),
  );
  readonly medianLatency = computed(() => {
    const l = this.latencies();
    if (!l.length) return 0;
    const m = Math.floor(l.length / 2);
    return Math.round(l.length % 2 ? l[m] : (l[m - 1] + l[m]) / 2);
  });
  readonly p95Latency = computed(() => {
    const l = this.latencies();
    return l.length ? l[Math.min(l.length - 1, Math.ceil(l.length * 0.95) - 1)] : 0;
  });
  readonly coverage = computed(() => {
    const cs = this.store.connectors();
    return cs.reduce((s, c) => s + c.coverage, 0) / cs.length;
  });

  readonly countByKind = computed(() => {
    const out: Record<string, number> = { All: this.store.connectors().length };
    for (const c of this.store.connectors()) out[c.kind] = (out[c.kind] ?? 0) + 1;
    for (const k of this.kinds) out[k] ??= 0;
    return out;
  });

  readonly filtered = computed(() => {
    const k = this.kind();
    const q = this.query().trim().toLowerCase();
    const rank: Record<Connector["status"], number> = { offline: 0, degraded: 1, syncing: 2, healthy: 3 };
    return this.store
      .connectors()
      .filter((c) => (k === "All" || c.kind === k) && (!q || `${c.name} ${c.protocol} ${c.region}`.toLowerCase().includes(q)))
      .sort((a, b) => rank[a.status] - rank[b.status]);
  });

  readonly pipeline = computed(() => {
    const cs = this.store.connectors();
    const issues = this.attention().length;
    const tx = this.store.transactions();
    const exc = this.store.exceptions().length;
    const raw = this.records();
    return [
      { name: "Sources", icon: "connect", count: `${cs.length}`, unit: "connectors", status: issues ? `${issues} impaired` : "All live", tone: issues ? "warn" : "ok" },
      { name: "Ingest", icon: "download", count: compact(raw, null, 1), unit: "raw payloads · hashed as evidence", status: "Stored", tone: "ok" },
      { name: "Normalize", icon: "layers", count: compact(this.nonMarket(), null, 1), unit: "records mapped to schema", status: "99.8% mapped", tone: "ok" },
      { name: "Ledger", icon: "ledger", count: `${JOURNALS.length}`, unit: "journal entries generated", status: "Balanced", tone: "ok" },
      { name: "Reconcile", icon: "check", count: `${this.store.reconRate().toFixed(1)}%`, unit: `${tx.length} bank lines`, status: exc ? `${exc} exceptions` : "Clean", tone: exc ? "warn" : "ok" },
      { name: "Twin / Forecast", icon: "forecast", count: "13 wk", unit: "P10 / P50 / P90 paths", status: "Refreshed", tone: "ai" },
    ];
  });

  readonly restHtml = highlightJson(this.restSnippet());
  readonly hookHtml = highlightJson(this.hookSnippet());

  rel(iso: string) {
    return relTime(iso, this.now());
  }

  hint(c: Connector) {
    switch (c.status) {
      case "offline":
        return "Token expired · re-consent needed";
      case "degraded":
        return "Partner API slow · retrying";
      case "syncing":
        return "Pulling latest records…";
      default:
        return c.latencyMs ? "Streaming" : "Scheduled batch";
    }
  }

  resyncAll() {
    for (const c of this.attention()) this.store.resync(c.id);
  }

  onboard(kind: string) {
    this.store.toast("info", "Connector onboarding is disabled in the demo", `${kind} connections need partner credentials and a consent flow.`);
  }

  copySnippet() {
    const text = this.snippet() === "rest" ? `GET /v1/accounts?entity=it\n\n${this.restSnippet()}` : this.hookSnippet();
    try {
      void navigator.clipboard?.writeText(text)?.catch(() => undefined);
    } catch {
      /* clipboard unavailable */
    }
    this.store.toast("info", "Snippet copied");
  }

  ngOnDestroy() {
    clearInterval(this.timer);
  }

  private restSnippet() {
    const ent = entityById("it");
    const data = ACCOUNTS.filter((a) => a.entityId === "it")
      .slice(0, 2)
      .map((a) => ({ id: a.id, entity: ent.id, bank: a.bank, name: a.name, type: a.type, iban: a.iban, currency: a.ccy, balance: Math.round(a.balance * 100) / 100, available: Math.round(a.available * 100) / 100, sync: a.status }));
    return JSON.stringify({ object: "list", entity: ent.name, data, has_more: ACCOUNTS.filter((a) => a.entityId === "it").length > 2, evidence: "sha256:9f2c…e41a" }, null, 2);
  }

  private hookSnippet() {
    return JSON.stringify(
      {
        id: "evt_01J9X4K2T7",
        type: "action.approved",
        created: "2026-10-05T09:42:17Z",
        data: { action: "ACT-2048", kind: "Sweep", amount: 5200000, currency: "EUR", quorum: "2 of 2", guard: { result: "pass", policies: 6 } },
        evidence: { seq: 18422, hash: "sha256:3b7e…a90c" },
      },
      null,
      2,
    );
  }
}
