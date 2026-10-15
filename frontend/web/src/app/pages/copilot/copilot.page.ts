import { Component, ElementRef, OnDestroy, ViewChild, computed, inject, signal } from "@angular/core";
import { RouterLink } from "@angular/router";
import { DemoStore } from "../../core/store";
import { ACCOUNTS, COPILOT_PROMPTS, INSIGHTS } from "../../core/demo-data";
import { CopilotAnswer, CopilotSource } from "../../core/models";
import { UI } from "../../shared/ui";

const MODEL = "xcap-treasury-v2";

/** Icon + short category for each canned, grounded prompt (same order as COPILOT_PROMPTS). */
const PROMPT_META = [
  { icon: "liquidity", tag: "Liquidity", keys: ["liquidity", "cash", "move", "moved", "change", "changed", "week", "balance", "balances", "drivers", "bridge", "rose", "increase"] },
  { icon: "forecast", tag: "Forecast", keys: ["payroll", "vela", "late", "cover", "salary", "salaries", "slip", "customer", "receivable", "collections", "pays"] },
  { icon: "wallet", tag: "Yield", keys: ["idle", "yield", "sweep", "interest", "earning", "earn", "excess", "surplus", "rate", "fund", "money"] },
  { icon: "swap", tag: "FX", keys: ["fx", "currency", "currencies", "usd", "gbp", "chf", "hedge", "hedged", "hedging", "exposure", "forex", "dollar", "forward"] },
  { icon: "risk", tag: "Stress", keys: ["shock", "revenue", "runway", "stress", "downturn", "scenario", "recession", "drop", "decline", "survive"] },
];

/** Reasoning steps shown while the simulated model "thinks", per prompt. */
const STEPS: string[][] = [
  ["Reading ledger (214 postings)", "Building 7-day liquidity bridge", "Checking reconciliation exceptions"],
  ["Loading obligations (AR / AP)", "Running 13-week forecast", "Checking Guard policies"],
  [`Reading account balances (${ACCOUNTS.length} accounts)`, "Comparing yields to approved fund", "Checking Guard policies"],
  ["Aggregating forecast FX receipts", "Reading hedge book", "Checking Guard policies"],
  ["Running 13-week forecast", "Applying stress grid", "Checking Guard policies"],
];
const FALLBACK_STEPS = ["Parsing your question", "Searching ledger, forecast and policies", "Checking grounding coverage"];

const STOP = new Set(["the", "and", "for", "our", "are", "what", "why", "how", "can", "does", "did", "will", "would", "with", "this", "that", "next", "last", "from", "into", "about", "your", "you", "have", "has", "was", "were", "any", "much", "many", "is", "do", "we", "of", "to", "in", "on", "a", "an", "if", "it", "be", "me", "my", "us", "show", "tell", "please", "give"]);

interface Step {
  label: string;
  done: boolean;
}
interface Msg {
  id: number;
  role: "user" | "assistant";
  text: string;
  /** Rendered (escaped + **bold**) paragraphs streamed so far. */
  paras: string[];
  phase: "thinking" | "streaming" | "done";
  steps: Step[];
  answer?: CopilotAnswer;
  suggest?: string[];
  proposal?: { label: string; route: string; query?: Record<string, string> };
  feedback?: "up" | "down";
  ms?: number;
}

function escapeHtml(s: string) {
  return s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&#39;");
}
/** Escape first, then turn **x** into <strong>x</strong>; drop any dangling markers mid-stream. */
function md(s: string) {
  return escapeHtml(s)
    .replace(/\*\*(.+?)\*\*/g, "<strong>$1</strong>")
    .replace(/\*\*/g, "");
}
function tokens(s: string) {
  return s
    .toLowerCase()
    .replace(/[^a-z0-9\s]/g, " ")
    .split(/\s+/)
    .filter((w) => w.length > 1 && !STOP.has(w));
}
const sleep = (ms: number) => new Promise<void>((r) => setTimeout(r, ms));

@Component({
  selector: "x-copilot",
  standalone: true,
  imports: [RouterLink, ...UI],
  template: `<div class="page">
    <x-page-head eyebrow="Intelligence · Grounded assistant" title="Copilot" description="Ask about cash, forecasts, exposure and policy. Every answer is grounded in your ledger, forecast and Guard policies, cites its sources, and can only propose — never execute.">
      <span class="badge ai"><x-icon name="lock" [size]="12" />Read-only</span>
      <button class="btn-secondary" (click)="newChat()" [disabled]="!messages().length"><x-icon name="plus" [size]="16" />New chat</button>
    </x-page-head>

    <section class="grid g-12">
      <div class="card flush span-8 chat">
        <div class="thread" #thread aria-live="polite" aria-label="Conversation">
          @if (!messages().length) {
            <div class="hero rise">
              <div class="mark"><x-icon name="copilot" [size]="30" /></div>
              <h2 class="hero-title">{{ greeting }}, {{ firstName }}. <span class="gradient-text">What should we look into?</span></h2>
              <p class="muted hero-sub">I can explain cash movements, test forecast scenarios, find idle cash and summarize exposure. I read your data — I can't move money.</p>
              <div class="suggest">
                @for (p of prompts; track p.prompt) {
                  <button class="sug" (click)="ask(p.prompt)" [style.animation-delay.ms]="$index * 50">
                    <span class="icon-tile" [class.ai]="$index % 2 === 0" [class.accent]="$index % 2 === 1"><x-icon [name]="meta[$index].icon" [size]="16" /></span>
                    <span class="grow sug-text">
                      <span class="eyebrow">{{ meta[$index].tag }}</span>
                      <span class="sug-q">{{ p.prompt }}</span>
                    </span>
                    <x-icon name="arrow" [size]="14" class="sug-go" />
                  </button>
                }
              </div>
            </div>
          } @else {
            @for (m of messages(); track m.id) {
              @if (m.role === "user") {
                <div class="msg user rise">
                  <div class="bubble">{{ m.text }}</div>
                </div>
              } @else {
                <div class="msg bot rise">
                  <span class="bot-av" aria-hidden="true"><x-icon name="copilot" [size]="15" /></span>
                  <div class="bot-body">
                    @if (m.phase === "thinking") {
                      <div class="steps" aria-label="Copilot is working">
                        @for (s of m.steps; track s.label) {
                          <div class="step" [class.done]="s.done">
                            @if (s.done) {
                              <x-icon name="check" [size]="13" class="pos" />
                            } @else {
                              <span class="spin" aria-hidden="true"></span>
                            }
                            <span>{{ s.label }}</span>
                          </div>
                        }
                      </div>
                    } @else {
                      <div class="reasoned xs faint"><x-icon name="check" [size]="12" />Reasoned over {{ m.steps.length }} steps</div>
                      @for (p of m.paras; track $index) {
                        <p class="para" [innerHTML]="p"></p>
                      }
                      @if (m.phase === "streaming") {
                        <span class="caret" aria-hidden="true"></span>
                      }
                      @if (m.phase === "done") {
                        @if (m.answer?.table; as t) {
                          <div class="table-wrap ans-table rise">
                            <table class="table">
                              <thead>
                                <tr>
                                  @for (h of t.head; track $index) {
                                    <th>{{ h }}</th>
                                  }
                                </tr>
                              </thead>
                              <tbody>
                                @for (r of t.rows; track $index) {
                                  <tr>
                                    @for (c of r; track $index) {
                                      <td [class.num]="$index > 0" [class.pos]="c.startsWith('+')" [class.neg]="c.startsWith('−')" [class.warn]="c === 'Below band'">{{ c }}</td>
                                    }
                                  </tr>
                                }
                              </tbody>
                            </table>
                          </div>
                        }
                        @if (m.suggest?.length) {
                          <div class="chips rise">
                            @for (s of m.suggest; track s) {
                              <button class="chip" (click)="ask(s)"><x-icon name="copilot" [size]="12" />{{ s }}</button>
                            }
                          </div>
                        }
                        @if (m.answer?.sources?.length) {
                          <div class="chips rise">
                            <span class="xs faint">Sources</span>
                            @for (s of m.answer!.sources; track s.label) {
                              <a class="chip src" [routerLink]="s.route"><x-icon name="link" [size]="12" />{{ s.label }}</a>
                            }
                          </div>
                        }
                        @if (m.proposal; as pr) {
                          <div class="proposal rise">
                            <div class="grow small">
                              <strong>Proposal ready</strong>
                              <div class="xs faint">Copilot cannot execute. Guard evaluates it and a person approves.</div>
                            </div>
                            <a class="btn btn-glow btn-sm" [routerLink]="pr.route" [queryParams]="pr.query">{{ pr.label }}<x-icon name="arrow" [size]="14" /></a>
                          </div>
                        }
                        <div class="meta">
                          <span class="xs faint">
                            @if (m.answer) {
                              Grounded in {{ m.answer.sources.length + 1 }} sources
                            } @else {
                              No grounded answer
                            }
                            · model {{ model }} (simulated) · {{ ((m.ms ?? 0) / 1000).toFixed(1) }}s
                          </span>
                          <span class="grow"></span>
                          <button class="btn-ghost btn-icon btn-sm ib" (click)="copy(m)" aria-label="Copy answer" title="Copy">
                            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="8" y="8" width="12" height="12" rx="2.5" /><path d="M16 8V6.5A2.5 2.5 0 0 0 13.5 4h-7A2.5 2.5 0 0 0 4 6.5v7A2.5 2.5 0 0 0 6.5 16H8" /></svg>
                          </button>
                          <button class="btn-ghost btn-icon btn-sm ib" [class.on]="m.feedback === 'up'" (click)="rate(m, 'up')" aria-label="Helpful" title="Helpful" [attr.aria-pressed]="m.feedback === 'up'">
                            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M7 10v10H4.5A1.5 1.5 0 0 1 3 18.5v-7A1.5 1.5 0 0 1 4.5 10zM7 10l4-7a2.5 2.5 0 0 1 2.5 2.8L13 10h5.6a2 2 0 0 1 2 2.3l-1.2 6.5a2 2 0 0 1-2 1.7H7" /></svg>
                          </button>
                          <button class="btn-ghost btn-icon btn-sm ib" [class.on]="m.feedback === 'down'" (click)="rate(m, 'down')" aria-label="Not helpful" title="Not helpful" [attr.aria-pressed]="m.feedback === 'down'">
                            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" style="transform: rotate(180deg)"><path d="M7 10v10H4.5A1.5 1.5 0 0 1 3 18.5v-7A1.5 1.5 0 0 1 4.5 10zM7 10l4-7a2.5 2.5 0 0 1 2.5 2.8L13 10h5.6a2 2 0 0 1 2 2.3l-1.2 6.5a2 2 0 0 1-2 1.7H7" /></svg>
                          </button>
                        </div>
                      }
                    }
                  </div>
                </div>
              }
            }
          }
        </div>

        <form class="composer" (submit)="$event.preventDefault(); submit()">
          @if (messages().length && !busy()) {
            <div class="quick">
              @for (p of followUps(); track p.prompt) {
                <button type="button" class="chip" (click)="ask(p.prompt)">{{ p.prompt }}</button>
              }
            </div>
          }
          <div class="box" [class.busy]="busy()">
            <textarea
              #box
              rows="1"
              [value]="draft()"
              (input)="onInput($any($event.target))"
              (keydown.enter)="onEnter($any($event))"
              placeholder="Ask about liquidity, forecast, FX exposure, idle cash…"
              aria-label="Message Copilot"
            ></textarea>
            <button type="submit" class="btn-glow btn-icon send" [disabled]="busy() || !draft().trim()" aria-label="Send message">
              <x-icon name="send" [size]="16" />
            </button>
          </div>
          <div class="foot xs faint">
            <x-icon name="lock" [size]="12" /> Read-only · cannot move money
            <span class="grow"></span>
            <span class="hide-sm"><kbd>Enter</kbd> to send · <kbd>Shift</kbd>+<kbd>Enter</kbd> new line</span>
          </div>
        </form>
      </div>

      <div class="span-4 side">
        <div class="card">
          <div class="card-head">
            <div>
              <h2>Context</h2>
              <div class="sub">What Copilot can see — and what it can never do</div>
            </div>
            <span class="badge pos"><span class="dot live"></span>Grounded</span>
          </div>
          <div class="xs faint label">Read-only access</div>
          <div class="list">
            @for (c of canRead; track c.name) {
              <a class="list-item scope" [routerLink]="c.route">
                <span class="icon-tile accent sm"><x-icon [name]="c.icon" [size]="14" /></span>
                <div class="grow" style="min-width: 0">
                  <strong class="small">{{ c.name }}</strong>
                  <div class="xs faint truncate">{{ c.detail }}</div>
                </div>
                <code class="scope-code">{{ c.scope }}</code>
              </a>
            }
          </div>
          <div class="divider"></div>
          <div class="xs faint label">Not permitted</div>
          <div class="list">
            @for (c of cannot; track c) {
              <div class="list-item deny">
                <span class="icon-tile neg sm"><x-icon name="lock" [size]="14" /></span>
                <span class="small grow">{{ c }}</span>
                <span class="badge neg">Denied</span>
              </div>
            }
          </div>
          <div class="callout principle">
            <x-icon name="guard" [size]="18" class="ai" />
            <div class="xs muted">AI proposes. Guard evaluates. People approve. Everything is evidenced.</div>
          </div>
        </div>

        <div class="card">
          <div class="card-head">
            <div>
              <h2><span class="gradient-text">Live</span> insights</h2>
              <div class="sub">Detected from today's data · ask Copilot to explain</div>
            </div>
            <span class="badge ai">{{ insights.length }}</span>
          </div>
          <div class="list">
            @for (i of insights; track i.id) {
              <div class="list-item ins">
                <span [class]="'sev ' + i.severity"></span>
                <div class="grow" style="min-width: 0">
                  <a class="ins-title" [routerLink]="i.route">{{ i.title }}</a>
                  <div class="xs faint">{{ i.metric }} · {{ (i.confidence * 100).toFixed(0) }}% confidence</div>
                </div>
                <button class="btn-ghost btn-sm" (click)="ask(i.title)" [disabled]="busy()" [attr.aria-label]="'Ask Copilot about ' + i.title">Ask</button>
              </div>
            }
          </div>
        </div>
      </div>
    </section>
  </div>`,
  styles: [
    `
      .chat { display: flex; flex-direction: column; height: max(560px, calc(100vh - 210px)); }
      .thread { flex: 1; overflow-y: auto; padding: 22px 22px 8px; scroll-behavior: smooth; display: flex; flex-direction: column; gap: 18px; }
      .hero { margin: auto; max-width: 720px; width: 100%; text-align: center; padding: 16px 0; }
      .mark { width: 60px; height: 60px; margin: 0 auto 18px; border-radius: 18px; display: grid; place-items: center; color: #fff; background: var(--x-gradient); box-shadow: 0 14px 40px -12px rgba(124, 92, 255, 0.7); }
      .hero-title { font: 600 24px/1.25 var(--x-font-display); letter-spacing: -0.02em; }
      .hero-sub { margin: 10px auto 22px; max-width: 540px; font-size: 13px; }
      .suggest { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; text-align: left; }
      .sug { height: auto; padding: 12px; gap: 12px; justify-content: flex-start; white-space: normal; text-align: left; background: var(--x-surface-2); border: 1px solid var(--x-border); color: var(--x-text); font-weight: 400; animation: x-rise 0.45s var(--x-ease) both; }
      .sug:hover { background: var(--x-surface-3); border-color: var(--x-border-strong); color: var(--x-text); }
      .sug:last-child:nth-child(odd) { grid-column: span 2; }
      .sug-text { display: flex; flex-direction: column; gap: 5px; }
      .sug-q { font-size: 13px; line-height: 1.35; }
      .sug-go { color: var(--x-text-3); transition: transform 0.15s; }
      .sug:hover .sug-go { transform: translateX(2px); color: var(--x-text); }

      .msg { display: flex; gap: 12px; max-width: 100%; }
      .msg.user { justify-content: flex-end; }
      .bubble { max-width: min(560px, 85%); padding: 10px 14px; border-radius: 14px 14px 4px 14px; background: var(--x-primary-soft); border: 1px solid color-mix(in srgb, var(--x-primary) 25%, transparent); font-size: 13.5px; line-height: 1.5; white-space: pre-wrap; overflow-wrap: anywhere; }
      .bot-av { width: 30px; height: 30px; flex-shrink: 0; border-radius: 10px; display: grid; place-items: center; color: #fff; background: var(--x-gradient); }
      .bot-body { flex: 1; min-width: 0; max-width: 720px; }
      .steps { display: flex; flex-direction: column; gap: 7px; padding: 10px 12px; border-radius: 12px; background: var(--x-surface-2); border: 1px solid var(--x-border); width: fit-content; max-width: 100%; }
      .step { display: flex; align-items: center; gap: 9px; font: 500 12px/1.2 var(--x-font-mono); color: var(--x-text-3); }
      .step.done { color: var(--x-text-2); }
      .spin { width: 13px; height: 13px; border-radius: 50%; border: 2px solid var(--x-ai-soft); border-top-color: var(--x-ai); animation: cp-spin 0.7s linear infinite; flex-shrink: 0; }
      @keyframes cp-spin { to { transform: rotate(360deg); } }
      .reasoned { display: flex; align-items: center; gap: 5px; margin-bottom: 6px; }
      .para { font-size: 13.5px; line-height: 1.6; color: var(--x-text); margin-bottom: 8px; overflow-wrap: anywhere; }
      .para ::ng-deep strong { font-weight: 650; color: var(--x-text); }
      .caret { display: inline-block; width: 7px; height: 15px; border-radius: 2px; background: var(--x-ai); vertical-align: -2px; animation: cp-blink 0.9s steps(2) infinite; }
      @keyframes cp-blink { 50% { opacity: 0; } }
      .ans-table { margin: 6px 0 12px; border: 1px solid var(--x-border); border-radius: 12px; }
      .ans-table td.num { white-space: nowrap; }
      .chips { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; margin: 8px 0; }
      .chip { display: inline-flex; align-items: center; gap: 5px; height: 26px; padding: 0 10px; border-radius: 999px; font: 500 12px/1 var(--x-font); background: var(--x-surface-2); color: var(--x-text-2); border: 1px solid var(--x-border); white-space: nowrap; max-width: 100%; overflow: hidden; text-overflow: ellipsis; }
      .chip:hover { background: var(--x-surface-3); color: var(--x-text); border-color: var(--x-border-strong); }
      .chip.src { color: var(--x-accent); }
      .proposal { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; margin: 10px 0 4px; padding: 12px; border-radius: 12px; background: var(--x-ai-soft); border: 1px solid color-mix(in srgb, var(--x-ai) 30%, transparent); }
      .meta { display: flex; align-items: center; gap: 2px; flex-wrap: wrap; margin-top: 8px; padding-top: 8px; border-top: 1px dashed var(--x-border); }
      .ib { width: 28px; color: var(--x-text-3); }
      .ib.on { color: var(--x-ai); background: var(--x-ai-soft); }

      .composer { padding: 10px 16px 14px; border-top: 1px solid var(--x-border); background: var(--x-surface); }
      .quick { display: flex; gap: 6px; overflow-x: auto; padding-bottom: 8px; scrollbar-width: none; }
      .quick .chip { flex-shrink: 0; }
      .box { display: flex; align-items: flex-end; gap: 8px; padding: 8px 8px 8px 14px; border-radius: 14px; background: var(--x-surface-2); border: 1px solid var(--x-border-strong); transition: border-color 0.15s, box-shadow 0.15s; }
      .box:focus-within { border-color: var(--x-primary); box-shadow: 0 0 0 3px var(--x-primary-soft); }
      .box.busy { opacity: 0.85; }
      textarea { flex: 1; min-width: 0; resize: none; border: 0; outline: none; background: transparent; font-size: 13.5px; line-height: 1.5; padding: 6px 0; max-height: 160px; }
      textarea::placeholder { color: var(--x-text-3); }
      .send { flex-shrink: 0; border-radius: 10px; }
      .foot { display: flex; align-items: center; gap: 6px; margin-top: 8px; }
      kbd { font: 500 10.5px var(--x-font-mono); padding: 1px 5px; border-radius: 4px; border: 1px solid var(--x-border-strong); background: var(--x-surface-2); color: var(--x-text-2); }

      .side { display: flex; flex-direction: column; gap: 16px; min-width: 0; }
      .label { text-transform: uppercase; letter-spacing: 0.1em; font-family: var(--x-font-mono); margin-bottom: 2px; }
      .icon-tile.sm { width: 28px; height: 28px; border-radius: 8px; }
      .scope { color: inherit; border-radius: 10px; margin: 0 -8px; padding: 9px 8px; }
      .scope:hover { background: var(--x-hover); color: inherit; }
      .scope-code { font: 500 10.5px var(--x-font-mono); padding: 2px 6px; border-radius: 5px; background: var(--x-surface-3); color: var(--x-text-2); white-space: nowrap; }
      .deny { padding: 8px 0; }
      .principle { margin-top: 14px; align-items: center; padding: 10px 12px; }
      .ins { gap: 10px; }
      .ins-title { display: block; font-size: 12.5px; font-weight: 600; color: var(--x-text); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
      .ins-title:hover { color: var(--x-primary); }
      .sev { align-self: stretch; width: 3px; border-radius: 3px; flex-shrink: 0; background: var(--x-info); }
      .sev.warning { background: var(--x-warn); }
      .sev.critical { background: var(--x-neg); }
      .sev.opportunity { background: var(--x-pos); }

      @media (max-width: 1200px) {
        .chat { height: max(540px, 78vh); }
      }
      @media (max-width: 640px) {
        .thread { padding: 16px 12px 8px; }
        .suggest { grid-template-columns: minmax(0, 1fr); }
        .sug:last-child:nth-child(odd) { grid-column: auto; }
        .hero-title { font-size: 20px; }
        .composer { padding: 10px 12px 12px; }
        .bot-av { display: none; }
        .bubble { max-width: 92%; }
        .hide-sm { display: none; }
      }
    `,
  ],
})
export class CopilotPage implements OnDestroy {
  readonly store = inject(DemoStore);
  readonly prompts = COPILOT_PROMPTS;
  readonly meta = PROMPT_META;
  readonly insights = INSIGHTS;
  readonly model = MODEL;
  readonly firstName = this.store.org.persona.name.split(" ")[0];
  readonly greeting = (() => {
    const h = new Date().getHours();
    return h < 12 ? "Good morning" : h < 18 ? "Good afternoon" : "Good evening";
  })();

  readonly canRead = [
    { name: "Ledger", icon: "ledger", scope: "ledger:read", detail: "Accounts, postings, reconciliation", route: "/app/ledger" },
    { name: "Forecast", icon: "forecast", scope: "forecast:read", detail: "13-week model, scenarios, obligations", route: "/app/forecast" },
    { name: "Policies", icon: "guard", scope: "guard:read", detail: "Guard controls, mandates, limits", route: "/app/guard" },
    { name: "Market data", icon: "markets", scope: "market:read", detail: "Quotes, FX, rates, reserve holdings", route: "/app/markets" },
  ];
  readonly cannot = ["Initiate payments or transfers", "Change mandates or policies", "Approve its own proposals"];

  @ViewChild("thread") private thread?: ElementRef<HTMLElement>;
  @ViewChild("box") private box?: ElementRef<HTMLTextAreaElement>;

  readonly messages = signal<Msg[]>([]);
  readonly draft = signal("");
  readonly busy = signal(false);
  /** Prompts not yet asked in this conversation. */
  readonly followUps = computed(() => {
    const asked = new Set(this.messages().filter((m) => m.role === "user").map((m) => m.text));
    return this.prompts.filter((p) => !asked.has(p.prompt)).slice(0, 3);
  });

  private seq = 0;
  /** Bumped on "New chat" / destroy so in-flight streams stop. */
  private gen = 0;

  onInput(el: HTMLTextAreaElement) {
    this.draft.set(el.value);
    this.autoGrow(el);
  }
  onEnter(e: KeyboardEvent) {
    if (e.shiftKey || e.isComposing) return;
    e.preventDefault();
    this.submit();
  }
  submit() {
    const q = this.draft().trim();
    if (!q || this.busy()) return;
    this.ask(q);
  }

  newChat() {
    this.gen++;
    this.messages.set([]);
    this.busy.set(false);
    this.draft.set("");
    if (this.box) this.autoGrow(this.box.nativeElement, true);
  }

  async ask(question: string) {
    if (this.busy()) return;
    const gen = this.gen;
    const started = performance.now();
    this.busy.set(true);
    this.draft.set("");
    if (this.box) this.autoGrow(this.box.nativeElement, true);

    const idx = this.match(question);
    const answer = idx >= 0 ? this.prompts[idx] : undefined;
    const steps = (idx >= 0 ? STEPS[idx] : FALLBACK_STEPS).map((label) => ({ label, done: false }));
    const id = ++this.seq;
    this.messages.update((ms) => [
      ...ms,
      { id: ++this.seq, role: "user", text: question, paras: [], phase: "done", steps: [] },
      { id, role: "assistant", text: "", paras: [], phase: "thinking", steps },
    ]);
    this.scroll();

    for (let i = 0; i < steps.length; i++) {
      await sleep(300 + (i === 1 ? 120 : 0));
      if (gen !== this.gen) return;
      this.patch(id, (m) => ({ ...m, steps: m.steps.map((s, j) => (j <= i ? { ...s, done: true } : s)) }));
    }
    await sleep(160);
    if (gen !== this.gen) return;

    const paragraphs = answer
      ? answer.answer
      : [
          "I couldn't ground that question in the data I have access to, so I won't guess.",
          "I work best on **liquidity movements**, **13-week forecast scenarios**, **idle cash and yield**, **FX exposure and hedging**, and **stress tests**. Try one of these:",
        ];
    this.patch(id, (m) => ({ ...m, phase: "streaming", answer }));

    for (let p = 0; p < paragraphs.length; p++) {
      const words = paragraphs[p].split(" ");
      let acc = "";
      for (let w = 0; w < words.length; w++) {
        acc += (w ? " " : "") + words[w];
        const html = md(acc);
        this.patch(id, (m) => {
          const paras = [...m.paras];
          paras[p] = html;
          return { ...m, paras };
        });
        if (w % 6 === 0) this.scroll();
        await sleep(12);
        if (gen !== this.gen) return;
      }
      await sleep(80);
      if (gen !== this.gen) return;
    }

    this.patch(id, (m) => ({
      ...m,
      phase: "done",
      ms: performance.now() - started,
      proposal: answer?.proposal ? this.proposalLink(answer.proposal) : undefined,
      suggest: answer ? undefined : this.prompts.slice(0, 4).map((p) => p.prompt),
    }));
    this.busy.set(false);
    this.scroll();
    this.box?.nativeElement.focus({ preventScroll: true });
  }

  copy(m: Msg) {
    const text = [
      ...(m.answer?.answer ?? m.paras.map((p) => p.replace(/<[^>]+>/g, ""))).map((p) => p.replace(/\*\*/g, "")),
      ...(m.answer?.table ? [m.answer.table.head.join("\t"), ...m.answer.table.rows.map((r) => r.join("\t"))] : []),
      ...(m.answer?.sources.length ? ["Sources: " + m.answer.sources.map((s: CopilotSource) => s.label).join("; ")] : []),
    ].join("\n\n");
    try {
      void navigator.clipboard?.writeText(text)?.catch(() => undefined);
    } catch {
      /* clipboard unavailable — still confirm in the demo */
    }
    this.store.toast("info", "Answer copied", "Includes the table and cited sources.");
  }

  rate(m: Msg, v: "up" | "down") {
    this.patch(m.id, (x) => ({ ...x, feedback: x.feedback === v ? undefined : v }));
    this.store.toast(v === "up" ? "pos" : "info", "Thanks for the feedback", v === "up" ? "Marked as helpful. Used to evaluate grounding quality." : "Flagged for review by the treasury AI owner.");
  }

  ngOnDestroy() {
    this.gen++;
  }

  /** Best canned answer by keyword overlap, or -1 if the score is too low. */
  private match(q: string): number {
    const exact = this.prompts.findIndex((p) => p.prompt.toLowerCase() === q.trim().toLowerCase());
    if (exact >= 0) return exact;
    const qt = tokens(q);
    if (!qt.length) return -1;
    let best = -1;
    let bestScore = 0;
    this.prompts.forEach((p, i) => {
      const vocab = new Set([...tokens(p.prompt), ...PROMPT_META[i].keys]);
      let score = 0;
      for (const w of qt) {
        if (vocab.has(w)) score += 1;
        else if (w.length >= 5 && [...vocab].some((v) => v.length >= 5 && (v.startsWith(w.slice(0, 5)) || w.startsWith(v.slice(0, 5))))) score += 0.6;
      }
      if (score > bestScore) {
        bestScore = score;
        best = i;
      }
    });
    return bestScore >= 1 ? best : -1;
  }

  private proposalLink(label: string): Msg["proposal"] {
    if (label.includes("ACT-2048")) return { label, route: "/app/approvals", query: { id: "ACT-2048" } };
    if (label.includes("Forecast")) return { label, route: "/app/forecast" };
    return { label, route: "/app/approvals" };
  }

  private patch(id: number, fn: (m: Msg) => Msg) {
    this.messages.update((ms) => ms.map((m) => (m.id === id ? fn(m) : m)));
  }

  private scroll() {
    requestAnimationFrame(() => {
      const el = this.thread?.nativeElement;
      if (el) el.scrollTop = el.scrollHeight;
    });
  }

  private autoGrow(el: HTMLTextAreaElement, reset = false) {
    if (reset) el.value = "";
    el.style.height = "auto";
    el.style.height = Math.min(el.scrollHeight, 160) + "px";
  }
}
