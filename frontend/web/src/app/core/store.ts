import { Injectable, OnDestroy, computed, signal } from "@angular/core";
import {
  ACCOUNTS,
  ACTIONS,
  AUDIT_EVENTS,
  CONNECTORS,
  INSTRUMENTS,
  ORG,
  POLICIES,
  TRANSACTIONS,
  digest,
  toEur,
} from "./demo-data";
import { AuditEvent, FinancialAction, Policy, Quote, Toast, Txn } from "./models";

/**
 * In-memory demo state. Everything a visitor does (approve, reject, toggle a
 * policy, resolve an exception) mutates signals here and is reflected across
 * the workspace and appended to the simulated audit chain. A page reload
 * restores the seeded scenario.
 */
@Injectable({ providedIn: "root" })
export class DemoStore {
  readonly org = ORG;
  readonly actions = signal<FinancialAction[]>(structuredClone(ACTIONS));
  readonly policies = signal<Policy[]>(structuredClone(POLICIES));
  readonly audit = signal<AuditEvent[]>([...AUDIT_EVENTS]);
  readonly transactions = signal<Txn[]>(structuredClone(TRANSACTIONS));
  readonly connectors = signal(structuredClone(CONNECTORS));
  readonly killSwitch = signal(false);
  readonly toasts = signal<Toast[]>([]);
  /** Entity filter applied by the top bar ("all" or an entity id). */
  readonly entity = signal<string>("all");

  readonly pendingActions = computed(() => this.actions().filter((a) => a.status === "pending"));
  readonly awaitingYou = computed(() =>
    this.pendingActions().filter((a) => a.chain.some((s) => s.you && s.status === "pending")),
  );
  readonly accounts = computed(() => {
    const e = this.entity();
    return e === "all" ? ACCOUNTS : ACCOUNTS.filter((a) => a.entityId === e);
  });
  readonly totalLiquidity = computed(() => this.accounts().reduce((s, a) => s + toEur(a.balance, a.ccy), 0));
  readonly exceptions = computed(() => this.transactions().filter((t) => t.status === "exception"));
  readonly reconRate = computed(() => {
    const tx = this.transactions();
    const done = tx.filter((t) => t.status === "reconciled" || t.status === "matched").length;
    return (done / tx.length) * 100;
  });

  private toastId = 0;

  /* ───────── actions & approvals ───────── */

  approve(id: string) {
    if (this.killSwitch()) {
      this.toast("neg", "Kill switch is active", "New approvals and submissions are frozen.");
      return;
    }
    const a = this.actions().find((x) => x.id === id);
    if (!a || a.status !== "pending") return;
    const now = new Date().toISOString();
    const chain = a.chain.map((s) => (s.you && s.status === "pending" ? { ...s, status: "approved" as const, at: now } : s));
    // Promote the next waiting approver, if any.
    const nextIdx = chain.findIndex((s) => s.status === "waiting");
    const stillPending = nextIdx >= 0;
    if (stillPending) chain[nextIdx] = { ...chain[nextIdx], status: "pending" };
    this.patchAction(id, { chain, status: stillPending ? "pending" : "approved" });
    this.log(ORG.persona.name, "human", "action.approved", id, `Approved v${a.version} · payload ${a.payloadHash.slice(0, 10)}…`);

    if (stillPending) {
      this.toast("info", `${id} approved by you`, `Waiting for ${chain[nextIdx].name}.`);
      // Simulate the next approver acting a few seconds later.
      setTimeout(() => {
        const cur = this.actions().find((x) => x.id === id);
        if (!cur || cur.status !== "pending" || this.killSwitch()) return;
        const c2 = cur.chain.map((s, i) => (i === nextIdx ? { ...s, status: "approved" as const, at: new Date().toISOString() } : s));
        this.patchAction(id, { chain: c2, status: "approved" });
        this.log(c2[nextIdx].name, "human", "action.approved", id, "Final approval · quorum reached");
        this.execute(id);
      }, 4200);
    } else {
      this.toast("pos", `${id} fully approved`, "Submitting to the partner…");
      this.execute(id);
    }
  }

  reject(id: string, reason = "Rejected by approver") {
    const a = this.actions().find((x) => x.id === id);
    if (!a || a.status !== "pending") return;
    const now = new Date().toISOString();
    const chain = a.chain.map((s) => (s.you ? { ...s, status: "rejected" as const, at: now } : s));
    this.patchAction(id, { chain, status: "rejected" });
    this.log(ORG.persona.name, "human", "action.rejected", id, reason);
    this.toast("warn", `${id} rejected`, "The proposer has been notified.");
  }

  private execute(id: string) {
    setTimeout(() => {
      if (this.killSwitch()) {
        this.patchAction(id, { status: "blocked" });
        this.log("Guard", "system", "guard.blocked", id, "Kill switch active at submission");
        return;
      }
      this.log("Guard", "system", "guard.revalidated", id, "Pre-submission re-check passed · balances, mandate, beneficiary");
      this.log("Execution", "system", "execution.submitted", id, `Idempotency key ${digest(id).slice(0, 12)}`);
      setTimeout(() => {
        this.patchAction(id, { status: "executed" });
        this.log("Execution", "system", "execution.confirmed", id, "Partner confirmed · reconciliation scheduled");
        this.toast("pos", `${id} executed`, "Partner confirmed. The ledger posting will reconcile automatically.");
      }, 1600);
    }, 900);
  }

  private patchAction(id: string, patch: Partial<FinancialAction>) {
    this.actions.update((list) => list.map((a) => (a.id === id ? { ...a, ...patch } : a)));
  }

  /* ───────── Guard ───────── */

  toggleKillSwitch() {
    const on = !this.killSwitch();
    this.killSwitch.set(on);
    this.log(ORG.persona.name, "human", on ? "guard.kill_switch.on" : "guard.kill_switch.off", "Execution", on ? "All new submissions frozen" : "Submissions resumed");
    this.toast(on ? "neg" : "pos", on ? "Kill switch engaged" : "Kill switch released", on ? "No new financial actions can be submitted." : "Execution is available again.");
  }

  togglePolicy(id: string) {
    let next = false;
    this.policies.update((ps) =>
      ps.map((p) => {
        if (p.id !== id) return p;
        next = !p.active;
        return { ...p, active: next, version: p.version + 1 };
      }),
    );
    this.log(ORG.persona.name, "human", next ? "policy.activated" : "policy.suspended", id, "New policy version published");
    this.toast(next ? "pos" : "warn", `${id} ${next ? "activated" : "suspended"}`);
  }

  /* ───────── reconciliation ───────── */

  resolveException(txId: string, how: "match" | "adjust" = "match") {
    this.transactions.update((list) =>
      list.map((t) => (t.id === txId ? { ...t, status: "reconciled", confidence: 1, journalRef: "JE-" + (92000 + Number(t.id.slice(3)) % 1000), exceptionReason: undefined } : t)),
    );
    this.log(ORG.persona.name, "human", how === "match" ? "recon.manual_match" : "recon.adjusted", txId, how === "match" ? "Matched manually with evidence" : "Adjustment journal created");
    this.toast("pos", `${txId} reconciled`);
  }

  resync(connectorId: string) {
    this.connectors.update((cs) => cs.map((c) => (c.id === connectorId ? { ...c, status: "syncing" as const } : c)));
    setTimeout(() => {
      this.connectors.update((cs) =>
        cs.map((c) => (c.id === connectorId ? { ...c, status: "healthy" as const, lastSync: new Date().toISOString(), latencyMs: Math.min(c.latencyMs || 400, 900), coverage: 100 } : c)),
      );
      const c = this.connectors().find((x) => x.id === connectorId);
      this.log("Connect", "system", "sync.completed", c?.name ?? connectorId, "Manual re-sync completed");
      this.toast("pos", `${c?.name} synced`);
    }, 2200);
  }

  /* ───────── audit + toasts ───────── */

  log(actor: string, actorType: AuditEvent["actorType"], action: string, target: string, detail: string) {
    this.audit.update((evs) => {
      const prev = evs[evs.length - 1];
      const seq = prev.seq + 1;
      const at = new Date().toISOString();
      const hash = digest(`${seq}|${at}|${actor}|${action}|${target}|${prev.hash}`);
      return [...evs, { seq, at, actor, actorType, action, target, detail, hash, prevHash: prev.hash }];
    });
  }

  toast(tone: Toast["tone"], title: string, body?: string) {
    const id = ++this.toastId;
    this.toasts.update((t) => [...t, { id, tone, title, body }]);
    setTimeout(() => this.dismissToast(id), 5200);
  }
  dismissToast(id: number) {
    this.toasts.update((t) => t.filter((x) => x.id !== id));
  }
}

/** Simulated streaming quotes: a bounded random walk ticking every ~1.5s. */
@Injectable({ providedIn: "root" })
export class MarketFeed implements OnDestroy {
  readonly quotes = signal<Record<string, Quote>>(
    Object.fromEntries(
      INSTRUMENTS.map((i) => {
        const c = i.candles;
        const prev = c[c.length - 2].c;
        const price = c[c.length - 1].c;
        return [i.symbol, { symbol: i.symbol, price, prev, change: price - prev, changePct: ((price - prev) / prev) * 100, tick: "flat" as const }];
      }),
    ),
  );
  readonly paused = signal(false);
  private timer = setInterval(() => this.tick(), 1500);

  private tick() {
    if (this.paused() || document.hidden) return;
    this.quotes.update((q) => {
      const next = { ...q };
      for (const ins of INSTRUMENTS) {
        if (Math.random() > 0.55) {
          next[ins.symbol] = { ...next[ins.symbol], tick: "flat" };
          continue;
        }
        const cur = next[ins.symbol];
        const vol = ins.kind === "FX" ? 0.00018 : ins.kind === "Rates" ? 0.0009 : ins.kind === "Crypto" ? 0.0016 : 0.0007;
        const pull = ((ins.base - cur.price) / ins.base) * 0.05;
        const price = cur.price * (1 + (Math.random() - 0.5) * 2 * vol + pull);
        next[ins.symbol] = {
          ...cur,
          price,
          change: price - cur.prev,
          changePct: ((price - cur.prev) / cur.prev) * 100,
          tick: price > cur.price ? "up" : price < cur.price ? "down" : "flat",
        };
      }
      return next;
    });
  }

  ngOnDestroy() {
    clearInterval(this.timer);
  }
}
