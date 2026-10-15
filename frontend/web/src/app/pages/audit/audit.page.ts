import { Component, DestroyRef, computed, inject, signal } from "@angular/core";
import { RouterLink } from "@angular/router";
import { DemoStore } from "../../core/store";
import { AUDIT_EVENTS, digest } from "../../core/demo-data";
import { FORMAT_PIPES, dateTime, relTime } from "../../core/format";
import { AuditEvent } from "../../core/models";
import { UI } from "../../shared/ui";

type ActorFilter = "all" | "human" | "ai" | "system";

interface Verification {
  ok: boolean;
  count: number;
  /** Seq of the first event that failed verification. */
  brokenSeq?: number;
  reason?: string;
  at: string;
  tampered: boolean;
  /** Store length at verification time, to flag events appended since. */
  storeLen: number;
}

const GENESIS = "0".repeat(64);

@Component({
  selector: "x-audit",
  standalone: true,
  imports: [RouterLink, ...UI, ...FORMAT_PIPES],
  template: `<div class="page">
    <x-page-head eyebrow="Control · Evidence" title="Audit trail" description="Every proposal, decision, submission and sync is appended to a hash-chained log. Each event commits to the one before it, so any silent edit breaks the chain from that point on.">
      <a class="btn btn-ghost" routerLink="/app/guard"><x-icon name="guard" [size]="16" />Guard</a>
      <button class="btn-secondary" (click)="exportPack()"><x-icon name="download" [size]="16" />Export evidence pack</button>
    </x-page-head>

    <section class="grid g-4">
      <div class="card kpi rise">
        <div class="label"><x-icon name="layers" [size]="15" />Events in chain</div>
        <div class="value num">{{ store.audit().length.toLocaleString("en-US") }}</div>
        <div class="delta" [class.pos]="sessionEvents() > 0" [class.faint]="sessionEvents() === 0">+{{ sessionEvents() }} <span class="faint">this session</span></div>
      </div>
      <div class="card kpi rise" style="animation-delay: 40ms">
        <div class="label"><x-icon name="link" [size]="15" />Chain head</div>
        <div class="value mono head-hash" [attr.title]="head().hash">{{ short(head().hash) }}</div>
        <div class="delta faint">seq {{ head().seq }} · {{ rel(head().at) }}</div>
      </div>
      <div class="card kpi rise" style="animation-delay: 80ms">
        <div class="label"><x-icon name="users" [size]="15" />Actors</div>
        <div class="value">{{ actorCounts().human + actorCounts().ai + actorCounts().system }}</div>
        <div class="actor-mix">
          <span class="xs"><i class="h"></i>{{ actorCounts().human }} human</span>
          <span class="xs"><i class="a"></i>{{ actorCounts().ai }} AI</span>
          <span class="xs"><i class="s"></i>{{ actorCounts().system }} system</span>
        </div>
      </div>
      <div class="card kpi rise" style="animation-delay: 120ms" [class.glow]="result()?.ok && !result()?.tampered">
        <div class="label"><x-icon name="guard" [size]="15" />Last verification</div>
        @if (result(); as r) {
          <div class="value" [class.pos]="r.ok" [class.neg]="!r.ok">{{ r.ok ? "Intact" : "Broken" }}</div>
          <div class="delta faint">{{ rel(r.at) }} · {{ r.count }} events{{ r.tampered ? " · tampered copy" : "" }}</div>
        } @else {
          <div class="value faint">Not run</div>
          <div class="delta faint">Run “Verify chain” below</div>
        }
      </div>
    </section>

    <section class="card verify rise" [class.ok]="result()?.ok" [class.bad]="result() && !result()!.ok">
      <div class="v-row">
        <span class="icon-tile" [class.pos]="result()?.ok" [class.neg]="result() && !result()!.ok" [class.accent]="!result()">
          <x-icon [name]="result() ? (result()!.ok ? 'check' : 'alert') : 'link'" [size]="18" />
        </span>
        <div class="grow">
          @if (running()) {
            <strong>Recomputing hashes… {{ progressCount() }} / {{ source().length }}</strong>
            <div class="xs faint">Checking each digest and its link to the previous event</div>
          } @else if (result()) {
            @if (result(); as r) {
            @if (r.ok) {
              <strong class="pos">Chain intact · {{ r.count }} events verified</strong>
              <div class="xs faint">Every digest recomputed and every prevHash link matches · {{ r.at | dtime }}
                @if (newSince() > 0) {
                  · <span class="warn">{{ newSince() }} new event{{ newSince() > 1 ? "s" : "" }} since — verify again</span>
                }
              </div>
            } @else {
              <strong class="neg">Chain broken at seq {{ r.brokenSeq }}</strong>
              <div class="xs faint">{{ r.reason }} · {{ r.count }} event{{ r.count === 1 ? "" : "s" }} verified before the break</div>
            }
            }
          } @else {
            <strong>Verify the evidence chain</strong>
            <div class="xs faint">Recompute every digest from its fields and the previous hash, then confirm the links.</div>
          }
          @if (tampered()) {
            <div class="xs warn" style="margin-top: 4px">Viewing a locally tampered copy — the live store is untouched.</div>
          }
        </div>
        <div class="row wrap v-actions">
          @if (tampered() || result()) {
            <button class="btn-ghost btn-sm" (click)="reset()" [disabled]="running()"><x-icon name="refresh" [size]="14" />Reset</button>
          }
          <button class="btn-secondary btn-sm" (click)="tamper()" [disabled]="running() || !!tampered()"><x-icon name="bolt" [size]="14" />Simulate tamper</button>
          <button class="btn-glow btn-sm" (click)="verify()" [disabled]="running()"><x-icon name="guard" [size]="14" />Verify chain</button>
        </div>
      </div>
      @if (running() || result()) {
        <div class="meter" [class.pos]="!running() && result()?.ok" [class.neg]="!running() && result() && !result()!.ok" [class.grad]="running()" style="margin-top: 14px">
          <span [style.width.%]="running() ? progress() * 100 : 100"></span>
        </div>
      }
    </section>

    <section class="card flush rise">
      <div class="card-head">
        <div>
          <h2>Event log</h2>
          <div class="sub">{{ visible().length }} of {{ source().length }} events · newest first</div>
        </div>
        <div class="row wrap tools">
          <label class="search">
            <x-icon name="search" [size]="15" />
            <input class="input" type="search" placeholder="Search actor, action, target" aria-label="Search events" [value]="query()" (input)="query.set($any($event.target).value)" />
          </label>
          <div class="seg" role="tablist" aria-label="Actor type">
            @for (f of actorFilters; track f.id) {
              <button role="tab" [class.on]="actor() === f.id" [attr.aria-selected]="actor() === f.id" (click)="actor.set(f.id)">{{ f.label }}</button>
            }
          </div>
        </div>
      </div>

      <ol class="log">
        @for (e of visible(); track e.seq) {
          <li class="ev" [class.open]="expanded() === e.seq" [class.broken]="isBroken(e.seq)" [class.after]="isAfterBreak(e.seq)">
            <button class="ev-row" (click)="toggle(e.seq)" [attr.aria-expanded]="expanded() === e.seq">
              <span class="time xs faint num">
                <span>{{ e.at | dtime }}</span>
                <span class="faint">{{ rel(e.at) }}</span>
              </span>
              <span class="rail"><span class="avatar round" [class.ai-av]="e.actorType === 'ai'" [class.sys-av]="e.actorType === 'system'">{{ initials(e.actor) }}</span></span>
              <span class="grow body">
                <span class="line1">
                  <strong class="small">{{ e.actor }}</strong>
                  <code class="act-code" [class]="'act-code ' + tone(e.action)">{{ e.action }}</code>
                  <span class="small muted target">{{ e.target }}</span>
                </span>
                <span class="xs faint truncate">{{ e.detail }}</span>
              </span>
              <span class="r-col">
                @if (isBroken(e.seq)) {
                  <span class="badge neg"><x-icon name="alert" [size]="11" />Mismatch</span>
                } @else if (isVerified(e.seq)) {
                  <span class="badge pos v-badge"><x-icon name="check" [size]="11" />Verified</span>
                }
                <span class="mono xs faint seq">#{{ e.seq }}</span>
                <x-icon class="chev" name="chevronDown" [size]="14" />
              </span>
            </button>
            @if (expanded() === e.seq) {
              <div class="ev-detail">
                <div class="kv"><span class="xs faint">Sequence</span><span class="mono small">{{ e.seq }}</span></div>
                <div class="kv"><span class="xs faint">Timestamp (UTC)</span><span class="mono small">{{ e.at }}</span></div>
                <div class="kv"><span class="xs faint">Actor type</span><span class="small">{{ e.actorType }}</span></div>
                <div class="hashes">
                  <div class="hash-line"><span class="xs faint lbl">prevHash</span><code class="mono">{{ e.prevHash }}</code></div>
                  <div class="link-glyph" aria-hidden="true"><x-icon name="link" [size]="14" /></div>
                  <div class="hash-line"><span class="xs faint lbl">hash</span><code class="mono">{{ e.hash }}</code></div>
                </div>
                <div class="xs faint mono preimage">digest("{{ e.seq }}|{{ e.at }}|{{ e.actor }}|{{ e.action }}|{{ e.target }}|{{ short(e.prevHash) }}")</div>
              </div>
            }
          </li>
        } @empty {
          <li class="empty">No events match this filter.</li>
        }
      </ol>
      <div class="note xs faint">
        <x-icon name="info" [size]="13" />
        <span>Demo note: the digest here is a simulated, non-cryptographic hash computed in your browser. In production, events are chained with SHA-256 and the chain head is anchored to external, independently held checkpoints.</span>
      </div>
    </section>
  </div>`,
  styles: [
    `
      .head-hash { font-size: 20px; letter-spacing: 0; }
      .actor-mix { display: flex; gap: 12px; flex-wrap: wrap; margin-top: 8px; color: var(--x-text-2); }
      .actor-mix i { display: inline-block; width: 8px; height: 8px; border-radius: 50%; margin-right: 5px; }
      .actor-mix .h { background: var(--x-primary); }
      .actor-mix .a { background: var(--x-ai); }
      .actor-mix .s { background: var(--x-accent); }

      .verify { transition: border-color .2s; }
      .verify.ok { border-color: color-mix(in srgb, var(--x-pos) 45%, transparent); }
      .verify.bad { border-color: color-mix(in srgb, var(--x-neg) 50%, transparent); background: linear-gradient(160deg, var(--x-neg-soft), var(--x-surface) 55%); }
      .v-row { display: flex; align-items: center; gap: 14px; flex-wrap: wrap; }
      .v-row > .grow { min-width: 220px; }
      .v-actions { gap: 8px; }

      .tools { gap: 10px; justify-content: flex-end; }
      .search { position: relative; display: flex; align-items: center; }
      .search x-icon { position: absolute; left: 10px; color: var(--x-text-3); pointer-events: none; }
      .search .input { padding-left: 32px; width: 260px; max-width: 100%; }

      .log { list-style: none; margin: 0; padding: 0 0 6px; }
      .ev { border-top: 1px solid var(--x-border); }
      .ev-row {
        display: flex; align-items: center; gap: 12px; width: 100%; height: auto; padding: 10px 18px;
        background: transparent; color: inherit; font-weight: 400; text-align: left; border-radius: 0; white-space: normal;
      }
      .ev-row:hover { background: var(--x-hover); color: inherit; }
      .ev.open .ev-row { background: var(--x-hover); }
      .ev.broken .ev-row { background: var(--x-neg-soft); }
      .ev.after .ev-row { opacity: .55; }
      .time { display: flex; flex-direction: column; width: 108px; flex-shrink: 0; gap: 2px; }
      .rail { position: relative; display: flex; align-self: stretch; align-items: center; }
      .rail::before { content: ""; position: absolute; left: 50%; top: -10px; bottom: -10px; width: 1px; background: var(--x-border-strong); }
      .rail .avatar { position: relative; width: 30px; height: 30px; font-size: 10.5px; background: var(--x-primary-soft); color: var(--x-primary); }
      .rail .avatar.ai-av { background: var(--x-ai-soft); color: var(--x-ai); }
      .rail .avatar.sys-av { background: var(--x-accent-soft); color: var(--x-accent); }
      .body { display: flex; flex-direction: column; gap: 3px; min-width: 0; }
      .line1 { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
      .act-code { font-size: 11px; padding: 2px 6px; border-radius: 6px; background: var(--x-surface-3); color: var(--x-text-2); }
      .act-code.pos { background: var(--x-pos-soft); color: var(--x-pos); }
      .act-code.neg { background: var(--x-neg-soft); color: var(--x-neg); }
      .act-code.ai { background: var(--x-ai-soft); color: var(--x-ai); }
      .act-code.info { background: var(--x-info-soft); color: var(--x-info); }
      .r-col { display: flex; align-items: center; gap: 10px; flex-shrink: 0; }
      .seq { min-width: 48px; text-align: right; }
      .chev { color: var(--x-text-3); transition: transform .15s; }
      .ev.open .chev { transform: rotate(180deg); }

      .ev-detail { padding: 4px 18px 16px 152px; display: flex; flex-wrap: wrap; gap: 10px 24px; animation: x-rise .25s var(--x-ease) both; }
      .kv { display: flex; flex-direction: column; gap: 2px; }
      .hashes { flex-basis: 100%; display: flex; flex-direction: column; gap: 2px; padding: 10px 12px; border-radius: 10px; background: var(--x-canvas); border: 1px solid var(--x-border); min-width: 0; }
      .hash-line { display: flex; gap: 10px; align-items: baseline; min-width: 0; }
      .hash-line .lbl { width: 62px; flex-shrink: 0; }
      .hash-line code { font-size: 11.5px; color: var(--x-text-2); word-break: break-all; }
      .ev.broken .hash-line:last-child code { color: var(--x-neg); }
      .link-glyph { color: var(--x-accent); padding-left: 24px; line-height: 0; }
      .preimage { flex-basis: 100%; word-break: break-all; }

      .note { display: flex; gap: 8px; align-items: flex-start; padding: 12px 18px 16px; border-top: 1px solid var(--x-border); }
      .note x-icon { margin-top: 1px; }

      @media (max-width: 760px) {
        .time { display: none; }
        .ev-row { padding: 10px 14px; }
        .ev-detail { padding: 4px 14px 14px; }
        .v-badge, .seq { display: none; }
        .tools, .search, .search .input { width: 100%; }
        .tools .seg { overflow-x: auto; }
      }
    `,
  ],
})
export class AuditPage {
  readonly store = inject(DemoStore);
  private destroyRef = inject(DestroyRef);

  readonly actorFilters: { id: ActorFilter; label: string }[] = [
    { id: "all", label: "All" },
    { id: "human", label: "Human" },
    { id: "ai", label: "AI" },
    { id: "system", label: "System" },
  ];
  readonly actor = signal<ActorFilter>("all");
  readonly query = signal("");
  readonly expanded = signal<number | null>(null);

  /** Local, tampered copy of the chain (never written back to the store). */
  readonly tampered = signal<AuditEvent[] | null>(null);
  readonly running = signal(false);
  readonly progress = signal(0);
  readonly result = signal<Verification | null>(null);

  private timer?: ReturnType<typeof setInterval>;

  constructor() {
    this.destroyRef.onDestroy(() => clearInterval(this.timer));
  }

  /** The chain being viewed and verified, oldest first. */
  readonly source = computed(() => this.tampered() ?? this.store.audit());
  readonly sessionEvents = computed(() => this.store.audit().length - AUDIT_EVENTS.length);
  readonly head = computed(() => this.store.audit()[this.store.audit().length - 1]);
  readonly actorCounts = computed(() => {
    const sets = { human: new Set<string>(), ai: new Set<string>(), system: new Set<string>() };
    for (const e of this.store.audit()) sets[e.actorType].add(e.actor);
    return { human: sets.human.size, ai: sets.ai.size, system: sets.system.size };
  });
  readonly progressCount = computed(() => Math.round(this.progress() * this.source().length));
  readonly newSince = computed(() => {
    const r = this.result();
    return r && !r.tampered ? this.store.audit().length - r.storeLen : 0;
  });

  readonly visible = computed(() => {
    const a = this.actor();
    const q = this.query().trim().toLowerCase();
    return [...this.source()]
      .reverse()
      .filter((e) => a === "all" || e.actorType === a)
      .filter((e) => !q || `${e.actor} ${e.action} ${e.target}`.toLowerCase().includes(q));
  });

  /* ───────── verification ───────── */

  private check(events: AuditEvent[]): Omit<Verification, "at" | "tampered" | "storeLen"> {
    for (let i = 0; i < events.length; i++) {
      const e = events[i];
      const expectedPrev = i === 0 ? GENESIS : events[i - 1].hash;
      if (e.prevHash !== expectedPrev) {
        return { ok: false, count: i, brokenSeq: e.seq, reason: `prevHash does not match the hash of seq ${i === 0 ? "genesis" : events[i - 1].seq}` };
      }
      const recomputed = digest(`${e.seq}|${e.at}|${e.actor}|${e.action}|${e.target}|${e.prevHash}`);
      if (recomputed !== e.hash) {
        return { ok: false, count: i, brokenSeq: e.seq, reason: `Recomputed digest ${this.short(recomputed)} ≠ stored ${this.short(e.hash)}` };
      }
    }
    return { ok: true, count: events.length };
  }

  verify() {
    if (this.running()) return;
    const events = this.source();
    const isTampered = !!this.tampered();
    const outcome = this.check(events);
    this.result.set(null);
    this.running.set(true);
    this.progress.set(0);
    const start = performance.now();
    const duration = 1200;
    // When broken, the progress bar stops at the failing event.
    const stopAt = outcome.ok ? 1 : Math.max(0.05, (outcome.count + 1) / events.length);
    clearInterval(this.timer);
    this.timer = setInterval(() => {
      const t = Math.min(1, (performance.now() - start) / duration);
      this.progress.set(t * stopAt);
      if (t >= 1) {
        clearInterval(this.timer);
        this.running.set(false);
        this.result.set({ ...outcome, at: new Date().toISOString(), tampered: isTampered, storeLen: this.store.audit().length });
        if (!outcome.ok && outcome.brokenSeq != null) this.expanded.set(outcome.brokenSeq);
      }
    }, 30);
  }

  tamper() {
    const copy = this.store.audit().map((e) => ({ ...e }));
    // Silently rewrite history: turn a rejection into an approval (or alter a mid-chain target).
    let idx = copy.findIndex((e) => e.action === "action.rejected");
    if (idx < 0) idx = Math.floor(copy.length / 2);
    const e = copy[idx];
    copy[idx] = e.action === "action.rejected" ? { ...e, action: "action.approved", detail: "Step 2/2 approved" } : { ...e, target: e.target + "-X" };
    this.tampered.set(copy);
    this.result.set(null);
    this.expanded.set(null);
    this.actor.set("all");
    this.query.set("");
    this.store.toast("warn", "Local copy tampered", `seq ${e.seq} rewritten in this view only. Run “Verify chain” to detect it.`);
  }

  reset() {
    clearInterval(this.timer);
    this.tampered.set(null);
    this.result.set(null);
    this.running.set(false);
    this.progress.set(0);
    this.expanded.set(null);
  }

  /* ───────── row state ───────── */

  isBroken(seq: number) {
    const r = this.result();
    return !this.running() && !!r && !r.ok && r.brokenSeq === seq;
  }
  isAfterBreak(seq: number) {
    const r = this.result();
    return !this.running() && !!r && !r.ok && r.brokenSeq != null && seq > r.brokenSeq;
  }
  isVerified(seq: number) {
    const r = this.result();
    if (!r || this.running()) return false;
    if (r.ok) return seq <= this.source()[r.count - 1]?.seq;
    return r.brokenSeq != null && seq < r.brokenSeq;
  }

  toggle(seq: number) {
    this.expanded.set(this.expanded() === seq ? null : seq);
  }

  tone(action: string) {
    if (/rejected|blocked|kill_switch\.on|suspended/.test(action)) return "neg";
    if (/approved|executed|confirmed|settled|posted|passed|revalidated|evaluated|activated|matched/.test(action)) return "pos";
    if (action.startsWith("action.proposed")) return "ai";
    if (/sync|submitted|created|amended/.test(action)) return "info";
    return "";
  }

  initials(name: string) {
    return name
      .replace(/[^A-Za-z0-9 ]/g, " ")
      .split(/\s+/)
      .filter(Boolean)
      .map((p) => p[0])
      .join("")
      .slice(0, 2)
      .toUpperCase();
  }
  short(h: string) {
    return `${h.slice(0, 8)}…${h.slice(-4)}`;
  }
  rel(iso: string) {
    return relTime(iso);
  }

  exportPack() {
    const r = this.result();
    this.store.toast(
      "pos",
      "Evidence pack exported (demo)",
      `${this.store.audit().length} events · head ${this.short(this.head().hash)}${r && r.ok && !r.tampered ? " · verified " + dateTime(r.at) : ""}`,
    );
  }
}
