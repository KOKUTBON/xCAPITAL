import { Component, HostListener, OnDestroy, computed, inject, signal } from "@angular/core";
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from "@angular/router";
import { filter } from "rxjs";
import { DemoStore, MarketFeed } from "../core/store";
import { ThemeService } from "../core/theme.service";
import { ENTITIES, INSTRUMENTS } from "../core/demo-data";
import { IconComponent } from "../shared/icon.component";
import { LogoComponent } from "../shared/ui";
import { CommandPaletteComponent } from "./command-palette.component";
import { NAV, NAV_GROUPS } from "./nav";

const RAIL_KEY = "xc-rail";

@Component({
  selector: "x-shell",
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, IconComponent, LogoComponent, CommandPaletteComponent],
  template: `<a href="#main" class="skip" (click)="$event.preventDefault(); focusMain()">Skip to content</a>
    <div class="shell" [class.rail]="rail()">
      @if (menu()) {
        <button class="backdrop" aria-label="Close navigation" (click)="menu.set(false)"></button>
      }
      <aside class="side" [class.open]="menu()" aria-label="Workspace navigation">
        <div class="brand">
          <a routerLink="/" aria-label="xCAPITAL home"><x-logo [size]="30" [wordmark]="!rail()" /></a>
          <button class="btn-ghost btn-icon rail-btn" (click)="toggleRail()" [attr.aria-label]="rail() ? 'Expand sidebar' : 'Collapse sidebar'">
            <x-icon name="sidebar" [size]="17" />
          </button>
        </div>

        <button class="org" title="Northstar Dynamics Group">
          <span class="org-av">ND</span>
          <span class="org-copy"><strong>Northstar Dynamics</strong><small>Group · 5 entities</small></span>
          <x-icon name="chevronDown" [size]="14" />
        </button>

        <nav>
          @for (g of groups; track g.code) {
            <div class="grp">
              <div class="grp-label">{{ g.label }}</div>
              @for (n of itemsFor(g.code); track n.path) {
                <a [routerLink]="n.path" routerLinkActive="on" ariaCurrentWhenActive="page" (click)="menu.set(false)" [attr.title]="rail() ? n.label : null">
                  <x-icon [name]="n.icon" [size]="18" />
                  <span class="lbl">{{ n.label }}</span>
                  @if (n.path === '/app/approvals' && store.awaitingYou().length) {
                    <span class="count">{{ store.awaitingYou().length }}</span>
                  }
                </a>
              }
            </div>
          }
        </nav>

        <div class="side-foot">
          <div class="principle">
            <x-icon name="guard" [size]="16" />
            <span><b>AI proposes. Guard evaluates. People approve.</b></span>
          </div>
          <div class="me">
            <span class="avatar round me-av">{{ store.org.persona.initials }}</span>
            <span class="me-copy"><strong>{{ store.org.persona.name }}</strong><small>{{ store.org.persona.role }}</small></span>
          </div>
        </div>
      </aside>

      <div class="main">
        <div class="demo-bar">
          <span class="dot live" style="color: var(--x-accent)"></span>
          <span><b>Interactive demo</b> · All companies, banks, balances and market prices are simulated. No real money moves.</span>
          <a href="https://github.com/KOKUTBON/xCAPITAL" target="_blank" rel="noopener">Source on GitHub <x-icon name="external" [size]="12" /></a>
        </div>
        <header class="top">
          <button class="btn-ghost btn-icon menu-btn" aria-label="Open navigation" (click)="menu.set(true)"><x-icon name="menu" /></button>
          <div class="crumbs">
            <span class="faint">{{ current().groupLabel }}</span>
            <x-icon name="chevronRight" [size]="14" />
            <strong>{{ current().label }}</strong>
          </div>
          <button class="omni" (click)="palette.set(true)">
            <x-icon name="search" [size]="16" /><span>Search or jump to…</span><kbd>⌘K</kbd>
          </button>
          <div class="top-actions">
            <label class="entity">
              <x-icon name="building" [size]="15" />
              <select [value]="store.entity()" (change)="store.entity.set($any($event.target).value)" aria-label="Entity filter">
                <option value="all">All entities</option>
                @for (e of entities; track e.id) {
                  <option [value]="e.id">{{ e.short }}</option>
                }
              </select>
            </label>
            @if (store.killSwitch()) {
              <span class="badge neg"><x-icon name="power" [size]="12" />Kill switch ON</span>
            }
            <span class="env"><span class="dot"></span>SANDBOX</span>
            <button class="btn-ghost btn-icon" (click)="theme.toggle()" [attr.aria-label]="theme.theme() === 'dark' ? 'Switch to light theme' : 'Switch to dark theme'">
              <x-icon [name]="theme.theme() === 'dark' ? 'sun' : 'moon'" />
            </button>
            <a class="btn btn-ghost btn-icon bell" routerLink="/app/approvals" aria-label="Approvals inbox">
              <x-icon name="bell" />
              @if (store.awaitingYou().length) {
                <span class="pip"></span>
              }
            </a>
          </div>
        </header>
        <div class="tape" aria-label="Simulated market ticker" (mouseenter)="feed.paused.set(true)" (mouseleave)="feed.paused.set(false)">
          <div class="tape-track">
            @for (k of [0, 1]; track k) {
              @for (s of tape; track s) {
                <a class="tk" [routerLink]="'/app/markets'" [queryParams]="{ s: s }" [attr.aria-hidden]="k === 1 ? 'true' : null" [attr.tabindex]="k === 1 ? -1 : null">
                  <b>{{ s }}</b>
                  <span class="num" [class.flash-up]="q()[s].tick === 'up'" [class.flash-dn]="q()[s].tick === 'down'">{{ fmtPx(s) }}</span>
                  <span class="num" [class.pos]="q()[s].changePct >= 0" [class.neg]="q()[s].changePct < 0">{{ q()[s].changePct >= 0 ? "▲" : "▼" }} {{ abs(q()[s].changePct).toFixed(2) }}%</span>
                </a>
              }
            }
          </div>
        </div>

        <main id="main" tabindex="-1" class="content">
          <router-outlet />
        </main>

        <footer class="status">
          <span><span class="dot live" style="color: var(--x-pos)"></span>All systems nominal</span>
          <span class="hide-sm"><x-icon name="connect" [size]="13" />{{ healthy() }}/{{ store.connectors().length }} connectors healthy</span>
          <span class="hide-sm"><x-icon name="audit" [size]="13" />Audit head <code>{{ store.audit()[store.audit().length - 1].hash.slice(0, 12) }}</code></span>
          <span class="grow"></span>
          <span><x-icon name="clock" [size]="13" /><code>{{ clock() }}</code></span>
        </footer>
      </div>
    </div>

    @if (palette()) {
      <x-command-palette (closed)="palette.set(false)" />
    }

    <div class="toasts" aria-live="polite">
      @for (t of store.toasts(); track t.id) {
        <div class="toast" [class]="'toast ' + t.tone" role="status">
          <x-icon [name]="t.tone === 'pos' ? 'check' : t.tone === 'neg' ? 'alert' : t.tone === 'warn' ? 'flag' : 'info'" [size]="16" />
          <div class="grow">
            <strong>{{ t.title }}</strong>
            @if (t.body) {
              <p>{{ t.body }}</p>
            }
          </div>
          <button class="btn-ghost btn-icon btn-sm" aria-label="Dismiss" (click)="store.dismissToast(t.id)"><x-icon name="x" [size]="14" /></button>
        </div>
      }
    </div>`,
  styleUrl: "./shell.component.css",
})
export class ShellComponent implements OnDestroy {
  readonly store = inject(DemoStore);
  readonly feed = inject(MarketFeed);
  readonly theme = inject(ThemeService);
  private router = inject(Router);

  readonly groups = NAV_GROUPS;
  readonly entities = ENTITIES;
  readonly tape = ["SPX", "SX5E", "FTSEMIB", "EURUSD", "EURGBP", "EURCHF", "DE10Y", "US10Y", "MSFT", "NVDA", "ASML", "SAP", "AAPL", "XAU", "BRENT", "BTC"];
  readonly q = this.feed.quotes;
  readonly abs = Math.abs;

  readonly rail = signal(this.readRail());
  readonly menu = signal(false);
  readonly palette = signal(false);
  readonly url = signal(this.router.url);
  readonly clock = signal(this.now());
  private timer = setInterval(() => this.clock.set(this.now()), 1000);

  readonly current = computed(() => {
    const path = this.url().split("?")[0];
    const n = NAV.find((x) => path.startsWith(x.path)) ?? NAV[0];
    return { ...n, groupLabel: NAV_GROUPS.find((g) => g.code === n.group)!.label };
  });
  readonly healthy = computed(() => this.store.connectors().filter((c) => c.status === "healthy" || c.status === "syncing").length);

  constructor() {
    this.router.events.pipe(filter((e) => e instanceof NavigationEnd)).subscribe((e) => {
      this.url.set((e as NavigationEnd).urlAfterRedirects);
      document.getElementById("main")?.scrollTo?.({ top: 0 });
      window.scrollTo({ top: 0 });
    });
  }

  itemsFor(code: string) {
    return NAV.filter((n) => n.group === code);
  }

  fmtPx(s: string) {
    const ins = INSTRUMENTS.find((i) => i.symbol === s)!;
    return this.q()[s].price.toLocaleString("en-US", { minimumFractionDigits: ins.decimals, maximumFractionDigits: ins.decimals });
  }

  toggleRail() {
    this.rail.set(!this.rail());
    try {
      localStorage.setItem(RAIL_KEY, this.rail() ? "1" : "0");
    } catch {}
  }
  private readRail() {
    try {
      return localStorage.getItem(RAIL_KEY) === "1";
    } catch {
      return false;
    }
  }
  focusMain() {
    document.getElementById("main")?.focus();
  }
  private now() {
    return new Date().toISOString().slice(11, 19) + " UTC";
  }

  @HostListener("document:keydown", ["$event"])
  onKey(e: KeyboardEvent) {
    if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "k") {
      e.preventDefault();
      this.palette.set(!this.palette());
    } else if (e.key === "Escape") {
      this.menu.set(false);
    }
  }

  ngOnDestroy() {
    clearInterval(this.timer);
  }
}
