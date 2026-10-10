import { AfterViewInit, Component, ElementRef, HostListener, OnDestroy, computed, inject, signal } from "@angular/core";
import { RouterLink } from "@angular/router";
import { DemoStore, MarketFeed } from "../../core/store";
import { ThemeService } from "../../core/theme.service";
import {
  ACCOUNTS,
  DEFAULT_SCENARIO,
  GROUP_LIQUIDITY_HISTORY,
  HOLDINGS,
  INSTRUMENTS,
  PERFORMANCE,
  TODAY,
  TOTAL_LIQUIDITY,
  runForecast,
} from "../../core/demo-data";
import { FORMAT_PIPES, compact, num, pct } from "../../core/format";
import { CHARTS, LineSeries } from "../../shared/charts";
import { UI } from "../../shared/ui";

const DAY = 86_400_000;
const REPO = "https://github.com/KOKUTBON/xCAPITAL";

type NavTarget = "product" | "platform" | "control" | "capital";

interface Stage {
  key: string;
  icon: string;
  title: string;
  actor: string;
  body: string;
  proof: string;
}

const STAGES: Stage[] = [
  {
    key: "propose",
    icon: "copilot",
    title: "Proposal",
    actor: "AI copilot or a person",
    body: "A sweep, payment, FX hedge or rebalance is drafted with its rationale, source evidence and a versioned payload hash.",
    proof: "payload v1 · sha-256 sealed",
  },
  {
    key: "guard",
    icon: "guard",
    title: "Guard evaluates",
    actor: "Deterministic policy engine",
    body: "Limits, mandates, dual-control thresholds, sanctions screening and the 13-week buffer are checked before anyone is asked to decide.",
    proof: "9 policies · pass / block",
  },
  {
    key: "approve",
    icon: "users",
    title: "People authorize",
    actor: "Named approvers",
    body: "Approval chains follow the policy outcome. Proposers cannot approve their own requests; any payload change resets the chain.",
    proof: "quorum · segregation of duties",
  },
  {
    key: "execute",
    icon: "send",
    title: "Execution",
    actor: "Bank & broker partners",
    body: "Guard re-validates at submission, then the instruction is sent once with an idempotency key. The kill switch freezes everything instantly.",
    proof: "idempotent · kill-switch aware",
  },
  {
    key: "reconcile",
    icon: "ledger",
    title: "Reconciled",
    actor: "Ledger engine",
    body: "The partner confirmation is matched to the bank statement line and the journal entry. Exceptions route to a controller.",
    proof: "statement ↔ journal match",
  },
  {
    key: "evidence",
    icon: "audit",
    title: "Evidence",
    actor: "Audit chain",
    body: "Every step is appended to a hash-chained log, so auditors can replay who proposed, who checked, who approved and what moved.",
    proof: "prev-hash linked · tamper evident",
  },
];

@Component({
  selector: "x-landing",
  standalone: true,
  imports: [RouterLink, ...UI, ...CHARTS, ...FORMAT_PIPES],
  styleUrl: "./landing.page.css",
  template: `<div class="lp" [class.io]="ioReady">
    <div class="bg" aria-hidden="true">
      <div class="bg-grid"></div>
      <div class="bg-orb o1"></div>
      <div class="bg-orb o2"></div>
      <div class="bg-orb o3"></div>
      <div class="bg-noise"></div>
    </div>

    <!-- ───────────── Nav ───────────── -->
    <header class="nav" [class.scrolled]="scrolled()">
      <div class="nav-in">
        <a routerLink="/" class="brand" aria-label="xCAPITAL home" (click)="toTop($event)"><x-logo [size]="28" /></a>
        <nav class="links" aria-label="Sections">
          @for (l of navLinks; track l.id) {
            <button class="nl" [class.on]="active() === l.id" (click)="go(l.id)">{{ l.label }}</button>
          }
        </nav>
        <div class="nav-actions">
          <a class="btn btn-ghost btn-icon" [href]="repo" target="_blank" rel="noopener" aria-label="xCAPITAL source on GitHub (opens in a new tab)"><x-icon name="github" [size]="18" /></a>
          <button class="btn-ghost btn-icon" (click)="theme.toggle()" [attr.aria-label]="theme.theme() === 'dark' ? 'Switch to light theme' : 'Switch to dark theme'">
            <x-icon [name]="theme.theme() === 'dark' ? 'sun' : 'moon'" [size]="18" />
          </button>
          <a class="btn btn-glow nav-cta" routerLink="/app/overview">Launch live demo <x-icon name="arrow" [size]="15" /></a>
          <button class="btn-ghost btn-icon menu-btn" (click)="menu.set(!menu())" [attr.aria-expanded]="menu()" aria-controls="lp-menu" [attr.aria-label]="menu() ? 'Close menu' : 'Open menu'">
            <x-icon [name]="menu() ? 'x' : 'menu'" [size]="18" />
          </button>
        </div>
      </div>
      @if (menu()) {
        <div class="sheet" id="lp-menu">
          @for (l of navLinks; track l.id) {
            <button class="sheet-link" (click)="go(l.id)">{{ l.label }} <x-icon name="chevronRight" [size]="16" /></button>
          }
          <a class="btn btn-glow btn-lg" routerLink="/app/overview">Launch live demo <x-icon name="arrow" [size]="16" /></a>
        </div>
      }
    </header>

    <main>
      <!-- ───────────── Hero ───────────── -->
      <section class="hero" id="product">
        <div class="wrap hero-copy">
          <span class="pill rise"><span class="dot live"></span>Interactive demo · simulated data</span>
          <h1 class="rise" style="animation-delay: 60ms">The financial operating system for <span class="gradient-text">modern companies</span></h1>
          <p class="lede rise" style="animation-delay: 120ms">
            Unified cash across every bank, a 13-week forecast with confidence bands, governed approvals and capital &amp; risk intelligence in one calm workspace.
            <span class="motto">AI proposes. Guard evaluates. People approve.</span>
          </p>
          <div class="ctas rise" style="animation-delay: 180ms">
            <a class="btn btn-glow btn-lg" routerLink="/app/overview">Launch live demo <x-icon name="arrow" [size]="16" /></a>
            <a class="btn btn-secondary btn-lg" [href]="repo" target="_blank" rel="noopener"><x-icon name="github" [size]="16" />View source on GitHub</a>
          </div>
          <ul class="stats rise" style="animation-delay: 240ms">
            <li><strong class="num">{{ totalLabel }}</strong><span>simulated liquidity across {{ accountCount }} accounts</span></li>
            <li><strong class="num">13-week</strong><span>P10 / P90 cash forecast</span></li>
            <li><strong class="num">100%</strong><span>of actions hash-chained as evidence</span></li>
            <li><strong class="num">0</strong><span>autonomous money movements</span></li>
          </ul>
        </div>

        <!-- Product preview: real components on live simulated state -->
        <div class="wrap preview-stage">
          <div class="preview-glow" aria-hidden="true"></div>
          <div class="preview" [style.--tilt]="tilt()" aria-label="Live preview of the xCAPITAL Command Center" role="figure">
            <div class="chrome">
              <span class="lights" aria-hidden="true"><i></i><i></i><i></i></span>
              <span class="url"><x-icon name="lock" [size]="12" />kokutbon.github.io/xCAPITAL/#/app/overview</span>
              <span class="live-tag"><span class="dot live"></span>Live</span>
            </div>
            <div class="pv-body">
              <aside class="pv-rail" aria-hidden="true">
                <x-logo [size]="24" [wordmark]="false" />
                @for (i of railIcons; track i) {
                  <span class="ri" [class.on]="i === 'overview'"><x-icon [name]="i" [size]="16" /></span>
                }
              </aside>
              <div class="pv-main">
                <div class="pv-head">
                  <div>
                    <div class="eyebrow">Command Center · Northstar Dynamics Group</div>
                    <div class="pv-title">Good morning, Alex</div>
                  </div>
                  <span class="badge ai"><x-icon name="copilot" [size]="12" />3 insights</span>
                </div>
                <div class="pv-kpis">
                  <div class="pv-kpi">
                    <span class="lbl">Group liquidity</span>
                    <strong class="num">{{ store.totalLiquidity() | compact: "EUR" : 2 }}</strong>
                    <span class="xs" [class.pos]="change7d >= 0" [class.neg]="change7d < 0">{{ fmtPct(change7d) }} <span class="faint">7d</span></span>
                  </div>
                  <div class="pv-kpi">
                    <span class="lbl">13-week low (P10)</span>
                    <strong class="num">{{ baseLow.p10 | compact: "EUR" : 1 }}</strong>
                    <span class="xs faint">{{ baseLow.label }} · buffer €18M</span>
                  </div>
                  <div class="pv-kpi">
                    <span class="lbl">Awaiting approval</span>
                    <strong class="num">{{ store.awaitingYou().length }}</strong>
                    <span class="xs faint">{{ store.pendingActions().length }} pending in total</span>
                  </div>
                  <div class="pv-kpi">
                    <span class="lbl">Reconciled · 30d</span>
                    <strong class="num">{{ store.reconRate().toFixed(1) }}%</strong>
                    <div class="meter pos"><span [style.width.%]="store.reconRate()"></span></div>
                  </div>
                </div>
                <div class="pv-grid">
                  <div class="pv-card pv-chart">
                    <div class="pv-card-h"><strong>Liquidity trend</strong><span class="xs faint">60 days · EUR</span></div>
                    <x-line-chart [series]="heroSeries" [labels]="heroLabels" [height]="176" [xTicks]="4" [threshold]="{ value: 18_000_000, label: 'Buffer €18M', color: 'var(--x-warn)' }" ariaLabel="Group liquidity, last 60 days" />
                  </div>
                  <div class="pv-card pv-quotes">
                    <div class="pv-card-h"><strong>Markets</strong><span class="xs faint">simulated feed</span></div>
                    @for (q of tape(); track q.symbol) {
                      <div class="q" [class.up]="q.tick === 'up'" [class.down]="q.tick === 'down'">
                        <span class="q-sym">{{ q.symbol }}</span>
                        <span class="num q-px">{{ fmtNum(q.price, q.decimals) }}</span>
                        <span class="num q-ch" [class.pos]="q.changePct >= 0" [class.neg]="q.changePct < 0">{{ fmtPct(q.changePct) }}</span>
                      </div>
                    }
                  </div>
                  <div class="pv-card pv-actions">
                    <div class="pv-card-h"><strong>Approvals inbox</strong><span class="xs faint">evaluated by Guard</span></div>
                    @for (a of previewActions(); track a.id) {
                      <div class="pa">
                        <span class="icon-tile" [class.ai]="a.origin === 'AI'" [class.neg]="a.status === 'blocked'"><x-icon [name]="kindIcon(a.kind)" [size]="15" /></span>
                        <div class="grow" style="min-width: 0">
                          <div class="truncate pa-t">{{ a.title }}</div>
                          <div class="xs faint truncate">{{ a.id }} · {{ a.origin === "AI" ? "AI proposed" : a.requestedBy }}</div>
                        </div>
                        <span class="num small hide-xs">{{ a.amount | compact: a.ccy : 1 }}</span>
                        <x-status [value]="a.status" />
                      </div>
                    }
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- ───────────── Built for ───────────── -->
      <section class="wrap roles reveal" aria-label="Built for">
        <span class="eyebrow">Built for the people who own the balance sheet</span>
        <div class="role-row">
          @for (r of roles; track r.label) {
            <span class="role"><x-icon [name]="r.icon" [size]="15" />{{ r.label }}</span>
          }
        </div>
      </section>

      <!-- ───────────── Platform bento ───────────── -->
      <section class="wrap sec" id="platform">
        <div class="sec-head reveal">
          <span class="eyebrow">Platform</span>
          <h2>One system of record for cash, control and capital</h2>
          <p>Ten modules share one ledger, one policy engine and one evidence chain. Every tile below opens the working module in the demo.</p>
        </div>
        <div class="bento">
          <a class="tile t-wide reveal" routerLink="/app/liquidity">
            <div class="tile-top"><span class="icon-tile accent"><x-icon name="liquidity" [size]="17" /></span><span class="go"><x-icon name="arrow" [size]="16" /></span></div>
            <h3>Treasury &amp; Liquidity</h3>
            <p>Real-time positions across {{ accountCount }} accounts, 5 entities and 4 currencies, normalised to EUR.</p>
            <div class="tile-vis">
              <div class="big num">{{ store.totalLiquidity() | compact: "EUR" : 2 }}</div>
              <x-spark [data]="liq90" color="var(--x-accent)" [height]="54" />
            </div>
          </a>
          <a class="tile reveal" routerLink="/app/forecast">
            <div class="tile-top"><span class="icon-tile"><x-icon name="forecast" [size]="17" /></span><span class="go"><x-icon name="arrow" [size]="16" /></span></div>
            <h3>13-week Forecast &amp; Scenarios</h3>
            <p>Direct-method forecast with P10/P90 bands and stress scenarios.</p>
            <div class="bars" aria-hidden="true">
              @for (b of netBars; track $index) {
                <i [style.height.%]="b.h" [class.neg]="b.neg"></i>
              }
            </div>
          </a>

          <a class="tile reveal" routerLink="/app/ledger">
            <div class="tile-top"><span class="icon-tile pos"><x-icon name="ledger" [size]="17" /></span><span class="go"><x-icon name="arrow" [size]="16" /></span></div>
            <h3>Ledger &amp; Reconciliation</h3>
            <p>Statement lines matched to journals with confidence scores.</p>
            <div class="tile-vis">
              <div class="row between small"><span class="muted">Auto-reconciled</span><span class="num">{{ store.reconRate().toFixed(1) }}%</span></div>
              <div class="meter pos"><span [style.width.%]="store.reconRate()"></span></div>
              <div class="xs faint">{{ store.exceptions().length }} exceptions routed to controllers</div>
            </div>
          </a>
          <a class="tile reveal" routerLink="/app/approvals">
            <div class="tile-top"><span class="icon-tile"><x-icon name="actions" [size]="17" /></span><span class="go"><x-icon name="arrow" [size]="16" /></span></div>
            <h3>Governed Approvals</h3>
            <p>Policy-driven chains, quorum and segregation of duties.</p>
            <div class="tile-vis chain" aria-hidden="true">
              <span class="node done"><x-icon name="check" [size]="12" /></span><i></i>
              <span class="node done"><x-icon name="check" [size]="12" /></span><i></i>
              <span class="node wait">AM</span><i class="dim"></i>
              <span class="node">CF</span>
            </div>
            <div class="xs faint">{{ store.pendingActions().length }} actions pending · {{ blockedCount() }} blocked</div>
          </a>
          <a class="tile reveal" routerLink="/app/guard">
            <div class="tile-top"><span class="icon-tile warn"><x-icon name="guard" [size]="17" /></span><span class="go"><x-icon name="arrow" [size]="16" /></span></div>
            <h3>Guard policy engine</h3>
            <p>Limits, mandates and controls, plus a one-click kill switch.</p>
            <div class="tile-vis">
              <div class="row between small"><span class="muted">Active policies</span><span class="num">{{ activePolicies() }}/{{ store.policies().length }}</span></div>
              <div class="row between small">
                <span class="muted">Kill switch</span>
                <span class="ks" [class.on]="store.killSwitch()"><span></span>{{ store.killSwitch() ? "Engaged" : "Armed" }}</span>
              </div>
            </div>
          </a>

          <a class="tile reveal" routerLink="/app/audit">
            <div class="tile-top"><span class="icon-tile accent"><x-icon name="audit" [size]="17" /></span><span class="go"><x-icon name="arrow" [size]="16" /></span></div>
            <h3>Audit evidence chain</h3>
            <p>Every material event, hash-linked to the one before it.</p>
            <div class="tile-vis hashes">
              @for (e of lastEvents(); track e.seq) {
                <div class="hash"><span class="faint">#{{ e.seq }}</span><code>{{ e.hash.slice(0, 14) }}</code></div>
              }
            </div>
          </a>
          <a class="tile reveal" routerLink="/app/portfolio">
            <div class="tile-top"><span class="icon-tile ai"><x-icon name="portfolio" [size]="17" /></span><span class="go"><x-icon name="arrow" [size]="16" /></span></div>
            <h3>Capital · reserve portfolio</h3>
            <p>Mandate-bound strategic reserve with drift and rebalancing.</p>
            <div class="tile-vis">
              <div class="alloc" aria-hidden="true">
                @for (a of allocation; track a.label) {
                  <span [style.flex]="a.value" [style.background]="a.color"></span>
                }
              </div>
              <div class="xs faint">{{ ytdLabel }} 1Y · {{ allocation.length }} asset classes</div>
            </div>
          </a>
          <a class="tile reveal" routerLink="/app/markets">
            <div class="tile-top"><span class="icon-tile"><x-icon name="markets" [size]="17" /></span><span class="go"><x-icon name="arrow" [size]="16" /></span></div>
            <h3>Markets &amp; Risk</h3>
            <p>Streaming quotes, VaR, factor exposures and stress tests.</p>
            <div class="tile-vis">
              <div class="row between small">
                <span class="muted">EUR/USD</span>
                <span class="num" [class.pos]="eurusd().changePct >= 0" [class.neg]="eurusd().changePct < 0">{{ fmtNum(eurusd().price, 4) }}</span>
              </div>
              <x-spark [data]="eurusdCloses" color="var(--x-s1)" [height]="34" />
            </div>
          </a>

          <a class="tile t-half reveal" routerLink="/app/copilot">
            <div class="tile-top"><span class="icon-tile ai"><x-icon name="copilot" [size]="17" /></span><span class="go"><x-icon name="arrow" [size]="16" /></span></div>
            <h3>AI Copilot</h3>
            <p>Answers grounded in your ledger, forecast and policies — and drafts actions that still go through Guard and people.</p>
            <div class="tile-vis chat" aria-hidden="true">
              <div class="msg me">Can we sweep excess EUR into the money-market fund?</div>
              <div class="msg bot"><x-icon name="copilot" [size]="13" />Yes — sweeping €4.5M keeps the P10 low point €1.6M above the buffer. Drafted as ACT-2048 for your approval.</div>
            </div>
          </a>
          <a class="tile t-half reveal" routerLink="/app/connect">
            <div class="tile-top"><span class="icon-tile pos"><x-icon name="connect" [size]="17" /></span><span class="go"><x-icon name="arrow" [size]="16" /></span></div>
            <h3>Connect</h3>
            <p>Banks, ERP and brokers over PSD2, EBICS, SWIFT and REST — with health, latency and coverage monitoring.</p>
            <div class="tile-vis conns">
              @for (c of store.connectors().slice(0, 6); track c.id) {
                <span class="conn" [attr.data-s]="c.status"><span class="dot"></span>{{ c.name }}</span>
              }
            </div>
          </a>
        </div>
      </section>

      <!-- ───────────── Control pipeline ───────────── -->
      <section class="sec control" id="control">
        <div class="wrap">
          <div class="sec-head reveal">
            <span class="eyebrow">How control works</span>
            <h2>Nothing moves until it has been <span class="gradient-text">checked, approved and evidenced</span></h2>
            <p>Intelligence accelerates the work. It never holds the keys. Select a stage to see what happens there.</p>
          </div>

          <div class="pipe reveal" [style.--p]="stage() / (stages.length - 1)">
            <div class="pipe-track" aria-hidden="true"><div class="pipe-fill"></div><div class="pulse"></div></div>
            <ol class="pipe-steps">
              @for (s of stages; track s.key; let i = $index) {
                <li>
                  <button class="step" [class.on]="stage() === i" [class.past]="stage() > i" (click)="pick(i)" [attr.aria-pressed]="stage() === i">
                    <span class="step-ic"><x-icon [name]="s.icon" [size]="18" /></span>
                    <span class="step-n num">0{{ i + 1 }}</span>
                    <span class="step-t">{{ s.title }}</span>
                  </button>
                </li>
              }
            </ol>
            <div class="stage-card" aria-live="polite">
              <div class="sc-l">
                <span class="eyebrow">Stage 0{{ stage() + 1 }} · {{ current().actor }}</span>
                <h3>{{ current().title }}</h3>
                <p>{{ current().body }}</p>
              </div>
              <code class="sc-proof">{{ current().proof }}</code>
            </div>
          </div>

          <blockquote class="principle reveal">
            <x-icon name="guard" [size]="22" />
            <p>“AI proposes. Deterministic controls evaluate. Authorized people approve. <span class="gradient-text">Every material decision is traceable.</span>”</p>
          </blockquote>
        </div>
      </section>

      <!-- ───────────── Mini demo ───────────── -->
      <section class="wrap sec" id="capital">
        <div class="demo reveal">
          <div class="demo-copy">
            <span class="eyebrow">Capital intelligence · try it</span>
            <h2>What happens to cash if revenue moves?</h2>
            <p>Drag the shock. The same deterministic forecast engine that powers the workspace recomputes all 13 weeks, the P10 low point and headroom against the €18M operating buffer.</p>
            <div class="slider">
              <div class="row between">
                <label for="shock" class="small muted">Revenue shock</label>
                <span class="num shock-v" [class.neg]="shock() < 0" [class.pos]="shock() > 0">{{ shock() > 0 ? "+" : shock() < 0 ? "−" : "" }}{{ abs(shock()) }}%</span>
              </div>
              <input id="shock" type="range" min="-20" max="10" step="1" [value]="shock()" (input)="shock.set(+$any($event.target).value)" aria-describedby="shock-out" />
              <div class="row between xs faint"><span>−20%</span><span>Base</span><span>+10%</span></div>
              <div class="presets">
                @for (p of presets; track p) {
                  <button class="btn-secondary btn-sm" [class.sel]="shock() === p" (click)="shock.set(p)">{{ p > 0 ? "+" : p < 0 ? "−" : "" }}{{ abs(p) }}%</button>
                }
              </div>
            </div>
            <div class="demo-kpis" id="shock-out">
              <div>
                <span class="lbl">Week-13 closing</span>
                <strong class="num">{{ scen().closing | compact: "EUR" : 1 }}</strong>
                <span class="xs num" [class.pos]="scen().closingDelta >= 0" [class.neg]="scen().closingDelta < 0">{{ scen().closingDelta >= 0 ? "+" : "" }}{{ scen().closingDelta | compact: "EUR" : 1 }} vs base</span>
              </div>
              <div>
                <span class="lbl">P10 low point</span>
                <strong class="num" [class.neg]="scen().headroom < 0">{{ scen().low | compact: "EUR" : 1 }}</strong>
                <span class="xs faint">{{ scen().lowWeek }}</span>
              </div>
              <div>
                <span class="lbl">Buffer headroom</span>
                <strong class="num" [class.pos]="scen().headroom >= 0" [class.neg]="scen().headroom < 0">{{ scen().headroom >= 0 ? "+" : "" }}{{ scen().headroom | compact: "EUR" : 1 }}</strong>
                <span class="xs" [class.pos]="scen().headroom >= 0" [class.neg]="scen().headroom < 0">{{ scen().headroom >= 0 ? "Policy LIM-03 passes" : "Guard would flag LIM-03" }}</span>
              </div>
            </div>
            <a class="btn btn-secondary" routerLink="/app/forecast">Open the full forecast <x-icon name="arrow" [size]="15" /></a>
          </div>
          <div class="demo-chart">
            <div class="row between demo-chart-h">
              <strong class="small">Projected operating cash · 13 weeks</strong>
              <div class="legend">
                <span><i style="background: var(--x-primary)"></i>Scenario</span>
                <span><i style="background: var(--x-text-3)"></i>Base</span>
              </div>
            </div>
            <x-line-chart [series]="scenSeries()" [labels]="weekLabels" [band]="scenBand()" [threshold]="{ value: buffer, label: 'Buffer €18M', color: 'var(--x-warn)' }" [height]="290" [xTicks]="7" ariaLabel="13-week forecast under the selected revenue shock" />
          </div>
        </div>
      </section>

      <!-- ───────────── Engineering ───────────── -->
      <section class="wrap sec" id="engineering">
        <div class="sec-head reveal">
          <span class="eyebrow">Engineering</span>
          <h2>Built like production software</h2>
          <p>This demo is the web client of a larger open-source platform. The workspace runs entirely in the browser on a seeded, deterministic dataset.</p>
        </div>
        <div class="eng">
          <div class="eng-card reveal">
            <span class="icon-tile accent"><x-icon name="overview" [size]="17" /></span>
            <h3>Web</h3>
            <ul>
              <li>Angular 21 standalone components with signals and computed state</li>
              <li>Custom SVG chart engine — line, bar, donut, candles, sparklines</li>
              <li>Zero UI dependencies, lazy-loaded routes, dark &amp; light tokens</li>
            </ul>
          </div>
          <div class="eng-card reveal">
            <span class="icon-tile"><x-icon name="cpu" [size]="17" /></span>
            <h3>Platform <span class="faint small">in the repo</span></h3>
            <ul>
              <li>Java 21 / Spring Boot services: auth, gateway, portfolio, simulation</li>
              <li>Python AI service for forecasting and copilot reasoning</li>
              <li>Flutter mobile client · PostgreSQL persistence</li>
            </ul>
          </div>
          <div class="eng-card reveal">
            <span class="icon-tile pos"><x-icon name="check" [size]="17" /></span>
            <h3>Quality</h3>
            <ul>
              <li>Tenant isolation matrix across every service boundary</li>
              <li>Execution approval boundary: no path from AI to money movement</li>
              <li>CI gates with required checks and branch protection</li>
            </ul>
          </div>
          <div class="tree reveal" aria-label="Repository layout">
            <div class="tree-h"><span class="lights" aria-hidden="true"><i></i><i></i><i></i></span><span class="xs faint">KOKUTBON/xCAPITAL</span></div>
            <pre><code><span class="c1">xCAPITAL/</span>
├─ frontend/web        <span class="cm"># Angular 21 · this site</span>
├─ mobile/             <span class="cm"># Flutter</span>
├─ backend/
│  ├─ auth-service     <span class="cm"># Java 21 · Spring Boot</span>
│  ├─ gateway
│  ├─ portfolio-service
│  └─ simulation-service
├─ ai/ai-service       <span class="cm"># Python</span>
└─ docs/               <span class="cm"># HLD · LLD · ADRs</span></code></pre>
          </div>
        </div>
        <div class="chips reveal">
          @for (t of tech; track t) {
            <span class="chip">{{ t }}</span>
          }
        </div>
        <div class="eng-cta reveal">
          <a class="btn btn-secondary" [href]="repo + '/tree/main/docs'" target="_blank" rel="noopener">Read the architecture <x-icon name="external" [size]="14" /></a>
          <a class="btn btn-ghost" [href]="repo" target="_blank" rel="noopener"><x-icon name="github" [size]="16" />Browse the code</a>
        </div>
      </section>

      <!-- ───────────── Final CTA ───────────── -->
      <section class="wrap sec">
        <div class="final reveal">
          <div class="final-in">
            <x-logo [size]="44" [wordmark]="false" />
            <h2>See it run on a live, simulated company</h2>
            <p>Approve a payment, trip the kill switch, resolve a reconciliation exception, stress the reserve portfolio. Everything you do is evidenced.</p>
            <div class="ctas">
              <a class="btn btn-glow btn-lg" routerLink="/app/overview">Launch live demo <x-icon name="arrow" [size]="16" /></a>
              <a class="btn btn-secondary btn-lg" [href]="repo" target="_blank" rel="noopener"><x-icon name="github" [size]="16" />Star on GitHub</a>
            </div>
          </div>
        </div>
      </section>
    </main>

    <footer class="foot">
      <div class="wrap foot-in">
        <div class="foot-brand">
          <x-logo [size]="26" />
          <p class="small muted">Designed and built by <a href="https://github.com/reiidoda" target="_blank" rel="noopener">Rei Doda</a></p>
        </div>
        <nav class="foot-links" aria-label="Footer">
          <a routerLink="/app/overview">Live demo</a>
          <a [href]="repo" target="_blank" rel="noopener">GitHub</a>
          <a [href]="repo + '/tree/main/docs'" target="_blank" rel="noopener">Docs</a>
        </nav>
      </div>
      <div class="wrap foot-legal xs faint">
        <p>xCAPITAL is a portfolio demonstration. Companies, banks, people, balances and market prices shown are simulated. Not investment advice.</p>
        <p>© 2026 xCAPITAL</p>
      </div>
    </footer>
  </div>`,
})
export class LandingPage implements AfterViewInit, OnDestroy {
  readonly store = inject(DemoStore);
  readonly feed = inject(MarketFeed);
  readonly theme = inject(ThemeService);
  private host = inject(ElementRef<HTMLElement>);

  readonly repo = REPO;
  readonly stages = STAGES;
  readonly navLinks: { id: NavTarget; label: string }[] = [
    { id: "product", label: "Product" },
    { id: "platform", label: "Platform" },
    { id: "control", label: "Control" },
    { id: "capital", label: "Capital" },
  ];
  readonly railIcons = ["overview", "liquidity", "forecast", "ledger", "actions", "guard", "portfolio", "markets"];
  readonly roles = [
    { label: "CFO", icon: "building" },
    { label: "Treasury", icon: "liquidity" },
    { label: "Controllers", icon: "ledger" },
    { label: "Investment committee", icon: "portfolio" },
    { label: "Auditors", icon: "audit" },
  ];
  readonly tech = ["Angular 21", "TypeScript", "Signals", "SVG charts", "Java 21", "Spring Boot", "Spring Cloud Gateway", "Python", "Flutter", "PostgreSQL", "Docker", "GitHub Actions"];
  readonly presets = [-20, -10, -5, 0, 5, 10];

  readonly menu = signal(false);
  readonly scrolled = signal(false);
  readonly active = signal<NavTarget | "">("");
  readonly tilt = signal(1);
  readonly stage = signal(0);
  readonly shock = signal(-8);
  ioReady = false;

  readonly fmtPct = (v: number) => pct(v, 2);
  readonly fmtNum = (v: number, d: number) => num(v, d);
  readonly abs = Math.abs;

  /* ───── hero preview ───── */
  readonly accountCount = ACCOUNTS.length;
  readonly totalLabel = compact(TOTAL_LIQUIDITY, "EUR", 1);
  readonly change7d = ((GROUP_LIQUIDITY_HISTORY[89] - GROUP_LIQUIDITY_HISTORY[82]) / GROUP_LIQUIDITY_HISTORY[82]) * 100;
  readonly liq90 = GROUP_LIQUIDITY_HISTORY;
  readonly heroSeries: LineSeries[] = [{ name: "Group liquidity", data: GROUP_LIQUIDITY_HISTORY.slice(-60), color: "var(--x-accent)", area: true }];
  readonly heroLabels = Array.from({ length: 60 }, (_, i) => new Date(TODAY - (59 - i) * DAY).toLocaleDateString("en-GB", { day: "2-digit", month: "short" }));

  private readonly base = runForecast(DEFAULT_SCENARIO);
  readonly baseLow = this.base.reduce((m, w) => (w.p10 < m.p10 ? w : m), this.base[0]);
  readonly weekLabels = this.base.map((w) => w.label);
  readonly buffer = DEFAULT_SCENARIO.minBuffer;

  readonly previewActions = computed(() =>
    [...this.store.actions()]
      .sort((a, b) => this.rank(a.status) - this.rank(b.status))
      .slice(0, 3),
  );
  private readonly tapeSymbols = ["EURUSD", "SX5E", "DE10Y", "NVDA", "XAU", "BTC"];
  readonly tape = computed(() => {
    const q = this.feed.quotes();
    return this.tapeSymbols.map((s) => ({ ...q[s], decimals: INSTRUMENTS.find((i) => i.symbol === s)?.decimals ?? 2 }));
  });

  /* ───── bento ───── */
  readonly netBars = (() => {
    const nets = this.base.map((w) => w.net);
    const max = Math.max(...nets.map(Math.abs));
    return nets.map((n) => ({ h: 12 + (Math.abs(n) / max) * 88, neg: n < 0 }));
  })();
  readonly blockedCount = computed(() => this.store.actions().filter((a) => a.status === "blocked").length);
  readonly activePolicies = computed(() => this.store.policies().filter((p) => p.active).length);
  readonly lastEvents = computed(() => this.store.audit().slice(-3).reverse());
  readonly eurusd = computed(() => this.feed.quotes()["EURUSD"]);
  readonly eurusdCloses = (INSTRUMENTS.find((i) => i.symbol === "EURUSD")?.candles ?? []).slice(-60).map((c) => c.c);
  readonly allocation = (() => {
    const colors: Record<string, string> = { "Money market": "var(--x-s2)", "Fixed income": "var(--x-s1)", Equity: "var(--x-s3)", Commodity: "var(--x-s4)", Cash: "var(--x-s7)" };
    const by: Record<string, number> = {};
    for (const h of HOLDINGS) by[h.assetClass] = (by[h.assetClass] ?? 0) + h.weight;
    return Object.entries(by)
      .sort((a, b) => b[1] - a[1])
      .map(([label, value]) => ({ label, value, color: colors[label] ?? "var(--x-s7)" }));
  })();
  readonly ytdLabel = pct((PERFORMANCE.portfolio[PERFORMANCE.portfolio.length - 1] / PERFORMANCE.portfolio[0] - 1) * 100, 1);

  /* ───── control pipeline ───── */
  readonly current = computed(() => STAGES[this.stage()]);
  private autoplay = true;
  private timer?: ReturnType<typeof setInterval>;

  /* ───── scenario demo ───── */
  private readonly scenWeeks = computed(() => runForecast({ ...DEFAULT_SCENARIO, revenueShock: this.shock() }));
  readonly scen = computed(() => {
    const w = this.scenWeeks();
    const low = w.reduce((m, x) => (x.p10 < m.p10 ? x : m), w[0]);
    const closing = w[w.length - 1].closing;
    return {
      closing,
      closingDelta: closing - this.base[this.base.length - 1].closing,
      low: low.p10,
      lowWeek: low.label,
      headroom: low.p10 - DEFAULT_SCENARIO.minBuffer,
    };
  });
  readonly scenSeries = computed<LineSeries[]>(() => [
    { name: "Scenario", data: this.scenWeeks().map((w) => w.closing), color: "var(--x-primary)", area: true, width: 2.2 },
    { name: "Base case", data: this.base.map((w) => w.closing), color: "var(--x-text-3)", dashed: true, width: 1.4 },
  ]);
  readonly scenBand = computed(() => ({ lo: this.scenWeeks().map((w) => w.p10), hi: this.scenWeeks().map((w) => w.p90), color: "var(--x-primary)" }));

  private io?: IntersectionObserver;
  private navIo?: IntersectionObserver;
  private readonly reduced = typeof matchMedia === "function" && matchMedia("(prefers-reduced-motion: reduce)").matches;

  constructor() {
    this.ioReady = typeof IntersectionObserver !== "undefined";
    if (!this.reduced) {
      this.timer = setInterval(() => {
        if (this.autoplay && !document.hidden) this.stage.update((s) => (s + 1) % STAGES.length);
      }, 2600);
    }
    this.onScroll();
  }

  ngAfterViewInit() {
    if (!this.ioReady) return;
    const root = this.host.nativeElement as HTMLElement;
    this.io = new IntersectionObserver(
      (entries) => {
        for (const e of entries) {
          if (e.isIntersecting) {
            e.target.classList.add("in");
            this.io?.unobserve(e.target);
          }
        }
      },
      { rootMargin: "0px 0px -8% 0px", threshold: 0.08 },
    );
    root.querySelectorAll(".reveal").forEach((el) => this.io!.observe(el));

    this.navIo = new IntersectionObserver(
      (entries) => {
        for (const e of entries) {
          if (!e.isIntersecting) continue;
          const id = e.target.id;
          this.active.set(this.navLinks.some((l) => l.id === id) ? (id as NavTarget) : "");
        }
      },
      { rootMargin: "-45% 0px -50% 0px" },
    );
    for (const id of [...this.navLinks.map((l) => l.id), "engineering"]) {
      const el = root.querySelector("#" + id);
      if (el) this.navIo.observe(el);
    }
  }

  ngOnDestroy() {
    this.io?.disconnect();
    this.navIo?.disconnect();
    if (this.timer) clearInterval(this.timer);
  }

  @HostListener("window:scroll")
  onScroll() {
    const y = typeof window !== "undefined" ? window.scrollY : 0;
    this.scrolled.set(y > 8);
    // Preview flattens as the visitor scrolls into it.
    this.tilt.set(this.reduced ? 0.4 : Math.max(0, 1 - y / 420));
  }

  @HostListener("document:keydown.escape")
  closeMenu() {
    this.menu.set(false);
  }

  go(id: NavTarget) {
    this.menu.set(false);
    const el = (this.host.nativeElement as HTMLElement).querySelector<HTMLElement>("#" + id);
    el?.scrollIntoView({ behavior: this.reduced ? "auto" : "smooth", block: "start" });
  }

  toTop(ev: Event) {
    ev.preventDefault();
    this.menu.set(false);
    window.scrollTo({ top: 0, behavior: this.reduced ? "auto" : "smooth" });
  }

  pick(i: number) {
    this.autoplay = false;
    this.stage.set(i);
  }

  kindIcon(kind: string) {
    return kind === "FX" ? "swap" : kind === "Sweep" ? "liquidity" : kind === "Rebalance" || kind === "Investment" ? "portfolio" : "send";
  }

  private rank(status: string) {
    return status === "pending" ? 0 : status === "blocked" ? 1 : 2;
  }
}
