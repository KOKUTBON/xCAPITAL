import { Component, DestroyRef, computed, inject, signal } from "@angular/core";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { ActivatedRoute, Router, RouterLink } from "@angular/router";
import { DemoStore, MarketFeed } from "../../core/store";
import { INSTRUMENTS, rng } from "../../core/demo-data";
import { Instrument, Quote } from "../../core/models";
import { FORMAT_PIPES, compact, num, pct } from "../../core/format";
import { CHARTS } from "../../shared/charts";
import { UI } from "../../shared/ui";

const KINDS: Instrument["kind"][] = ["Index", "FX", "Rates", "Equity", "Commodity", "Crypto"];
const KIND_LABEL: Record<Instrument["kind"], string> = { Index: "Indices", FX: "FX", Rates: "Rates", Equity: "Equities", Commodity: "Commodities", Crypto: "Crypto" };
const RANGES = [
  { key: "1M", n: 22 },
  { key: "3M", n: 66 },
  { key: "6M", n: 140 },
] as const;
type RangeKey = (typeof RANGES)[number]["key"];
const ORDER_TYPES = ["Market", "Limit", "Stop"] as const;
type OrderType = (typeof ORDER_TYPES)[number];

/** Stable per-symbol seed so the simulated book shape is deterministic. */
const seedOf = (s: string) => [...s].reduce((h, c) => (h * 31 + c.charCodeAt(0)) >>> 0, 7);

interface BookLevel {
  price: number;
  size: number;
  cum: number;
}

@Component({
  selector: "x-markets",
  standalone: true,
  imports: [RouterLink, ...UI, ...CHARTS, ...FORMAT_PIPES],
  template: `<div class="page">
    <x-page-head eyebrow="Capital · Markets" title="Markets" description="Indices, FX, rates and the instruments held in the reserve portfolio. Quotes stream from a simulated feed; orders can only be drafted as governed proposals.">
      <span class="badge info"><x-icon name="bolt" [size]="12" />Simulated feed</span>
      <button class="btn-secondary" (click)="togglePause()" [attr.aria-pressed]="feed.paused()">
        <x-icon [name]="feed.paused() ? 'play' : 'pause'" [size]="15" />{{ feed.paused() ? "Resume feed" : "Pause feed" }}
      </button>
      <a class="btn btn-ghost" routerLink="/app/portfolio"><x-icon name="portfolio" [size]="15" />Portfolio</a>
    </x-page-head>

    <section class="movers card" aria-label="Market movers">
      <div class="movers-head">
        <span class="dot" [class.live]="!feed.paused()"></span>
        <span class="xs faint">{{ feed.paused() ? "Paused" : "Live" }} · Movers</span>
      </div>
      <div class="movers-list">
        @for (m of movers(); track m.symbol) {
          <button class="mover" [class.sel]="selected() === m.symbol" (click)="select(m.symbol)">
            <span class="sym">{{ m.symbol }}</span>
            <span class="num small">{{ fmtPrice(m.symbol, m.price) }}</span>
            <span class="num xs chg" [class.pos]="m.changePct > 0" [class.neg]="m.changePct < 0">{{ m.changePct | pct: 2 }}</span>
          </button>
        }
      </div>
    </section>

    <section class="grid g-12 terminal">
      <!-- Watchlist -->
      <div class="card flush span-3 watch">
        <div class="card-head">
          <div>
            <h2>Watchlist</h2>
            <div class="sub">{{ instruments.length }} instruments</div>
          </div>
        </div>
        <div class="search">
          <x-icon name="search" [size]="14" />
          <input class="input" type="search" placeholder="Filter symbol or name" [value]="query()" (input)="query.set($any($event.target).value)" aria-label="Filter watchlist" />
        </div>
        <div class="wl" role="listbox" aria-label="Instruments">
          @for (g of groups(); track g.kind) {
            <div class="wl-group">{{ g.label }}</div>
            @for (i of g.items; track i.symbol) {
              <button class="wl-row" role="option" [attr.aria-selected]="selected() === i.symbol" [class.sel]="selected() === i.symbol" (click)="select(i.symbol)">
                <div class="wl-id">
                  <span class="sym">{{ i.symbol }}</span>
                  <span class="xs faint truncate">{{ i.name }}</span>
                </div>
                <x-spark class="wl-spark" [data]="sparkOf(i)" [color]="q(i.symbol).changePct >= 0 ? 'var(--x-pos)' : 'var(--x-neg)'" [height]="24" [fill]="false" />
                <div class="wl-px">
                  @for (p of [q(i.symbol).price]; track p) {
                    <span class="num small px" [class.up]="q(i.symbol).tick === 'up'" [class.down]="q(i.symbol).tick === 'down'">{{ fmtPrice(i.symbol, p) }}</span>
                  }
                  <span class="num xs" [class.pos]="q(i.symbol).changePct > 0" [class.neg]="q(i.symbol).changePct < 0">{{ q(i.symbol).changePct | pct: 2 }}</span>
                </div>
              </button>
            }
          } @empty {
            <div class="empty small">No instruments match “{{ query() }}”.</div>
          }
        </div>
      </div>

      <!-- Main panel -->
      <div class="card span-6 main">
        <div class="inst-head">
          <div class="grow" style="min-width: 0">
            <div class="row" style="gap: 8px">
              <h2 class="inst-sym">{{ ins().symbol }}</h2>
              <span class="badge">{{ ins().kind }}</span>
              <span class="badge">{{ ins().venue }}</span>
            </div>
            <div class="small muted" style="margin-top: 4px">{{ ins().name }} · quoted in {{ ins().ccy }}</div>
          </div>
          <div class="seg" role="tablist" aria-label="Chart range">
            @for (r of ranges; track r.key) {
              <button [class.on]="range() === r.key" (click)="range.set(r.key)" role="tab" [attr.aria-selected]="range() === r.key">{{ r.key }}</button>
            }
          </div>
        </div>

        <div class="price-row">
          @for (p of [quote().price]; track p) {
            <span class="big num px" [class.up]="quote().tick === 'up'" [class.down]="quote().tick === 'down'">{{ fmtPrice(ins().symbol, p) }}</span>
          }
          <span class="num chg-lg" [class.pos]="quote().change > 0" [class.neg]="quote().change < 0">
            <x-icon [name]="quote().change >= 0 ? 'up' : 'down'" [size]="14" />
            {{ quote().change >= 0 ? "+" : "−" }}{{ fmtPrice(ins().symbol, abs(quote().change)) }} ({{ quote().changePct | pct: 2 }})
          </span>
          <span class="xs faint">vs prev close</span>
        </div>

        <div class="ranges">
          <div class="rng">
            <span class="xs faint">Day range</span>
            <div class="rng-bar"><span class="rng-dot" [style.left.%]="dayRange().pos"></span></div>
            <div class="row between xs num muted"><span>{{ fmtPrice(ins().symbol, dayRange().lo) }}</span><span>{{ fmtPrice(ins().symbol, dayRange().hi) }}</span></div>
          </div>
          <div class="rng">
            <span class="xs faint">{{ range() }} range</span>
            <div class="rng-bar"><span class="rng-dot" [style.left.%]="periodRange().pos"></span></div>
            <div class="row between xs num muted"><span>{{ fmtPrice(ins().symbol, periodRange().lo) }}</span><span>{{ fmtPrice(ins().symbol, periodRange().hi) }}</span></div>
          </div>
        </div>

        <x-candles [candles]="candles()" [last]="quote().price" [height]="380" [decimals]="ins().decimals" />

        <div class="stats">
          @for (s of stats(); track s.label) {
            <div class="stat">
              <span class="xs faint">{{ s.label }}</span>
              <strong class="num">{{ s.value }}</strong>
            </div>
          }
        </div>
      </div>

      <!-- Right column -->
      <div class="span-3 side">
        <div class="card">
          <div class="card-head">
            <div>
              <h2>Order book</h2>
              <div class="sub">Simulated depth · {{ ins().symbol }}</div>
            </div>
            <span class="xs faint num">Spread {{ fmtPrice(ins().symbol, book().spread) }}</span>
          </div>
          <div class="book" aria-label="Order book depth">
            <div class="book-hd xs faint"><span>Price</span><span>Size</span><span>Total</span></div>
            @for (l of book().asks; track $index) {
              <div class="lvl ask">
                <span class="depth" [style.width.%]="(l.cum / book().max) * 100"></span>
                <span class="num neg">{{ fmtPrice(ins().symbol, l.price) }}</span>
                <span class="num">{{ fmtSize(l.size) }}</span>
                <span class="num faint">{{ fmtSize(l.cum) }}</span>
              </div>
            }
            <div class="mid">
              <span class="num" [class.pos]="quote().tick === 'up'" [class.neg]="quote().tick === 'down'">{{ fmtPrice(ins().symbol, quote().price) }}</span>
              <span class="xs faint">mid</span>
            </div>
            @for (l of book().bids; track $index) {
              <div class="lvl bid">
                <span class="depth" [style.width.%]="(l.cum / book().max) * 100"></span>
                <span class="num pos">{{ fmtPrice(ins().symbol, l.price) }}</span>
                <span class="num">{{ fmtSize(l.size) }}</span>
                <span class="num faint">{{ fmtSize(l.cum) }}</span>
              </div>
            }
          </div>
        </div>

        <div class="card ticket">
          <div class="card-head">
            <div>
              <h2>Trade ticket</h2>
              <div class="sub">Governed · proposal only</div>
            </div>
            <x-icon name="guard" [size]="18" />
          </div>
          <div class="seg side-seg" role="tablist" aria-label="Side">
            <button [class.on]="side() === 'Buy'" [class.buy]="side() === 'Buy'" (click)="side.set('Buy')" role="tab" [attr.aria-selected]="side() === 'Buy'">Buy</button>
            <button [class.on]="side() === 'Sell'" [class.sell]="side() === 'Sell'" (click)="side.set('Sell')" role="tab" [attr.aria-selected]="side() === 'Sell'">Sell</button>
          </div>
          <label class="field">
            Quantity
            <input class="input num" type="number" min="0" [step]="qtyStep()" [value]="qty()" (input)="qty.set(+$any($event.target).value || 0)" />
          </label>
          <div class="field">
            Order type
            <div class="seg" role="tablist" aria-label="Order type">
              @for (t of orderTypes; track t) {
                <button [class.on]="orderType() === t" (click)="setType(t)" role="tab" [attr.aria-selected]="orderType() === t">{{ t }}</button>
              }
            </div>
          </div>
          @if (orderType() !== "Market") {
            <label class="field">
              {{ orderType() === "Limit" ? "Limit price" : "Stop price" }}
              <input class="input num" type="number" min="0" [step]="priceStep()" [value]="limitPx()" (input)="limitPx.set(+$any($event.target).value || 0)" />
            </label>
          }
          <div class="est">
            <div class="row between small"><span class="muted">Est. notional</span><span class="num">{{ notional() | compact: ins().ccy === "%" ? "EUR" : ins().ccy : 2 }}</span></div>
            <div class="row between small"><span class="muted">Reference price</span><span class="num">{{ fmtPrice(ins().symbol, refPx()) }}</span></div>
          </div>
          <button class="btn-glow" style="width: 100%" [disabled]="qty() <= 0" (click)="propose()"><x-icon name="send" [size]="15" />Create proposal</button>
          <p class="xs faint note"><x-icon name="lock" [size]="12" /> Direct execution is disabled: every order becomes a governed action.</p>
        </div>
      </div>
    </section>
  </div>`,
  styles: [
    `
      .movers { display: flex; align-items: center; gap: 14px; padding: 10px 14px; }
      .movers-head { display: flex; align-items: center; gap: 7px; white-space: nowrap; }
      .movers-list { display: flex; gap: 8px; overflow-x: auto; scrollbar-width: none; }
      .mover { height: 30px; padding: 0 10px; gap: 8px; background: var(--x-surface-2); border: 1px solid var(--x-border); color: var(--x-text); white-space: nowrap; }
      .mover:hover { background: var(--x-hover); }
      .mover.sel { border-color: var(--x-primary); background: var(--x-primary-soft); }
      .sym { font: 600 12.5px var(--x-font-mono); letter-spacing: .02em; }
      .terminal { align-items: start; }
      .watch .card-head { padding: 16px 16px 0; margin-bottom: 10px; }
      .search { position: relative; padding: 0 12px 10px; color: var(--x-text-3); }
      .search x-icon { position: absolute; left: 23px; top: 10px; }
      .search .input { width: 100%; padding-left: 30px; height: 32px; font-size: 12.5px; }
      .wl { max-height: 760px; overflow-y: auto; padding: 0 6px 8px; }
      .wl-group { padding: 12px 10px 5px; font: 600 10px var(--x-font-mono); letter-spacing: .14em; text-transform: uppercase; color: var(--x-text-3); }
      .wl-row { width: 100%; height: auto; display: grid; grid-template-columns: minmax(0, 1fr) 54px auto; align-items: center; gap: 10px; padding: 8px 10px; border-radius: 9px; background: transparent; color: inherit; font-weight: 400; text-align: left; }
      .wl-row:hover { background: var(--x-hover); }
      .wl-row.sel { background: var(--x-primary-soft); box-shadow: inset 2px 0 0 var(--x-primary); }
      .wl-id { display: flex; flex-direction: column; min-width: 0; gap: 2px; }
      .wl-px { display: flex; flex-direction: column; align-items: flex-end; gap: 2px; }
      .px { padding: 1px 4px; margin: -1px -4px; border-radius: 4px; }
      .px.up { animation: flash-up 1s var(--x-ease); }
      .px.down { animation: flash-down 1s var(--x-ease); }
      @keyframes flash-up { from { background: var(--x-pos-soft); color: var(--x-pos); } }
      @keyframes flash-down { from { background: var(--x-neg-soft); color: var(--x-neg); } }
      .inst-head { display: flex; align-items: flex-start; gap: 12px; flex-wrap: wrap; }
      .inst-sym { font: 600 20px var(--x-font-mono); letter-spacing: .01em; }
      .price-row { display: flex; align-items: baseline; gap: 12px; flex-wrap: wrap; margin: 14px 0 12px; }
      .big { font: 600 34px/1 var(--x-font-display); letter-spacing: -0.02em; font-variant-numeric: tabular-nums; }
      .chg-lg { display: inline-flex; align-items: center; gap: 4px; font-size: 14px; }
      .ranges { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 18px; margin-bottom: 12px; }
      .rng { display: flex; flex-direction: column; gap: 6px; }
      .rng-bar { position: relative; height: 4px; border-radius: 999px; background: linear-gradient(90deg, var(--x-neg-soft), var(--x-surface-3), var(--x-pos-soft)); }
      .rng-dot { position: absolute; top: 50%; width: 10px; height: 10px; border-radius: 50%; background: var(--x-primary); border: 2px solid var(--x-surface); transform: translate(-50%, -50%); transition: left .4s var(--x-ease); }
      .stats { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 1px; margin-top: 14px; border-radius: 12px; overflow: hidden; background: var(--x-border); border: 1px solid var(--x-border); }
      .stat { display: flex; flex-direction: column; gap: 4px; padding: 10px 12px; background: var(--x-surface); }
      .stat strong { font-size: 14px; font-weight: 600; }
      .side { display: flex; flex-direction: column; gap: 16px; min-width: 0; }
      .book { display: flex; flex-direction: column; font-size: 12px; }
      .book-hd, .lvl { display: grid; grid-template-columns: 1.2fr 1fr 1fr; gap: 6px; padding: 3px 6px; }
      .book-hd span:not(:first-child), .lvl span:not(:first-child):not(.depth) { text-align: right; }
      .lvl { position: relative; border-radius: 4px; }
      .lvl > span:not(.depth) { position: relative; }
      .depth { position: absolute; top: 1px; bottom: 1px; right: 0; border-radius: 3px; transition: width .5s var(--x-ease); }
      .ask .depth { background: var(--x-neg-soft); }
      .bid .depth { background: var(--x-pos-soft); }
      .mid { display: flex; align-items: center; justify-content: space-between; padding: 7px 6px; margin: 4px 0; border-top: 1px solid var(--x-border); border-bottom: 1px solid var(--x-border); font-size: 14px; font-weight: 600; }
      .ticket { display: flex; flex-direction: column; gap: 12px; }
      .ticket .card-head { margin-bottom: 0; color: var(--x-text-3); }
      .side-seg { display: grid; grid-template-columns: 1fr 1fr; }
      .side-seg button.on.buy { background: var(--x-pos-soft); color: var(--x-pos); }
      .side-seg button.on.sell { background: var(--x-neg-soft); color: var(--x-neg); }
      .ticket .field .seg { align-self: flex-start; }
      .ticket .input { width: 100%; }
      .est { display: flex; flex-direction: column; gap: 6px; padding: 10px 12px; border-radius: 10px; background: var(--x-surface-2); border: 1px solid var(--x-border); }
      .note { display: flex; align-items: flex-start; gap: 6px; line-height: 1.45; }
      .note x-icon { margin-top: 2px; flex-shrink: 0; }
      @media (max-width: 1200px) {
        .side { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); }
        .wl { max-height: 360px; }
      }
      @media (max-width: 760px) {
        .side { grid-template-columns: minmax(0, 1fr); }
        .stats { grid-template-columns: repeat(2, minmax(0, 1fr)); }
        .ranges { grid-template-columns: minmax(0, 1fr); gap: 10px; }
        .big { font-size: 28px; }
      }
    `,
  ],
})
export class MarketsPage {
  readonly store = inject(DemoStore);
  readonly feed = inject(MarketFeed);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly instruments = INSTRUMENTS;
  readonly ranges = RANGES;
  readonly orderTypes = ORDER_TYPES;
  readonly abs = Math.abs;

  readonly selected = signal<string>("SX5E");
  readonly range = signal<RangeKey>("3M");
  readonly query = signal("");
  readonly side = signal<"Buy" | "Sell">("Buy");
  readonly qty = signal(100);
  readonly orderType = signal<OrderType>("Market");
  readonly limitPx = signal(0);

  constructor() {
    this.route.queryParamMap.pipe(takeUntilDestroyed(inject(DestroyRef))).subscribe((p) => {
      const s = p.get("s")?.toUpperCase();
      if (s && INSTRUMENTS.some((i) => i.symbol === s)) this.selected.set(s);
    });
  }

  /* ───── quotes ───── */
  q(symbol: string): Quote {
    return this.feed.quotes()[symbol];
  }
  readonly ins = computed(() => INSTRUMENTS.find((i) => i.symbol === this.selected()) ?? INSTRUMENTS[0]);
  readonly quote = computed(() => this.feed.quotes()[this.ins().symbol]);

  fmtPrice(symbol: string, v: number): string {
    const i = INSTRUMENTS.find((x) => x.symbol === symbol);
    return num(v, i?.decimals ?? 2) + (i?.kind === "Rates" ? "%" : "");
  }
  fmtSize(v: number): string {
    return v >= 1000 ? compact(v, null, 1) : v.toFixed(0);
  }

  /* ───── watchlist ───── */
  private readonly sparkBase = new Map(INSTRUMENTS.map((i) => [i.symbol, i.candles.slice(-30).map((c) => c.c)]));
  sparkOf(i: Instrument): number[] {
    return [...this.sparkBase.get(i.symbol)!.slice(1), this.q(i.symbol).price];
  }
  readonly groups = computed(() => {
    const term = this.query().trim().toLowerCase();
    const match = (i: Instrument) => !term || i.symbol.toLowerCase().includes(term) || i.name.toLowerCase().includes(term);
    return KINDS.map((k) => ({ kind: k, label: KIND_LABEL[k], items: INSTRUMENTS.filter((i) => i.kind === k && match(i)) })).filter((g) => g.items.length);
  });
  readonly movers = computed(() => {
    const qs = Object.values(this.feed.quotes()).sort((a, b) => b.changePct - a.changePct);
    return [...qs.slice(0, 4), ...qs.slice(-4).reverse()];
  });

  select(symbol: string) {
    this.selected.set(symbol);
    this.limitPx.set(0);
    this.router.navigate([], { relativeTo: this.route, queryParams: { s: symbol }, replaceUrl: true });
  }

  togglePause() {
    this.feed.paused.update((p) => !p);
  }

  /* ───── chart + stats ───── */
  readonly candles = computed(() => {
    const n = RANGES.find((r) => r.key === this.range())!.n;
    return this.ins().candles.slice(-n);
  });
  readonly dayRange = computed(() => {
    const last = this.ins().candles[this.ins().candles.length - 1];
    const p = this.quote().price;
    const lo = Math.min(last.l, p);
    const hi = Math.max(last.h, p);
    return { lo, hi, pos: ((p - lo) / (hi - lo || 1)) * 100 };
  });
  readonly periodRange = computed(() => {
    const cs = this.candles();
    const p = this.quote().price;
    const lo = Math.min(p, ...cs.map((c) => c.l));
    const hi = Math.max(p, ...cs.map((c) => c.h));
    return { lo, hi, pos: ((p - lo) / (hi - lo || 1)) * 100 };
  });
  private readonly volAnn = computed(() => {
    const closes = this.ins().candles.slice(-61).map((c) => c.c);
    const rets = closes.slice(1).map((c, i) => Math.log(c / closes[i]));
    const mean = rets.reduce((s, r) => s + r, 0) / rets.length;
    const variance = rets.reduce((s, r) => s + (r - mean) ** 2, 0) / (rets.length - 1);
    return Math.sqrt(variance * 252) * 100;
  });
  readonly stats = computed(() => {
    const i = this.ins();
    const cs = i.candles;
    const last = cs[cs.length - 1];
    const s = i.symbol;
    const avgVol = cs.slice(-20).reduce((t, c) => t + c.v, 0) / 20;
    const p = this.quote().price;
    return [
      { label: "Open", value: this.fmtPrice(s, last.o) },
      { label: "High", value: this.fmtPrice(s, Math.max(last.h, p)) },
      { label: "Low", value: this.fmtPrice(s, Math.min(last.l, p)) },
      { label: "Prev close", value: this.fmtPrice(s, this.quote().prev) },
      { label: "Avg volume · 20D", value: compact(avgVol, null, 2) },
      { label: "Volatility · 60D ann.", value: pct(this.volAnn(), 1, false) },
    ];
  });

  /* ───── order book (simulated) ───── */
  readonly book = computed(() => {
    const ins = this.ins();
    const mid = this.quote().price;
    const tick = mid * (ins.kind === "FX" ? 0.00004 : ins.kind === "Rates" ? 0.0004 : ins.kind === "Crypto" ? 0.0003 : 0.0002);
    const r = rng(seedOf(ins.symbol));
    const unit = ins.kind === "FX" ? 250_000 : ins.kind === "Rates" ? 5_000_000 : ins.kind === "Crypto" ? 2 : ins.kind === "Index" ? 40 : 600;
    const level = (k: number): number => {
      const base = unit * (0.6 + r.next() * 1.4) * (1 + k * 0.18);
      const jitter = 0.85 + Math.random() * 0.3;
      return Math.max(1, base * jitter);
    };
    let cumA = 0;
    let cumB = 0;
    const asks: BookLevel[] = [];
    const bids: BookLevel[] = [];
    for (let k = 0; k < 8; k++) {
      const a = level(k);
      const b = level(k);
      cumA += a;
      cumB += b;
      asks.push({ price: mid + tick * (k + 0.5), size: a, cum: cumA });
      bids.push({ price: mid - tick * (k + 0.5), size: b, cum: cumB });
    }
    return { asks: asks.reverse(), bids, spread: tick, max: Math.max(cumA, cumB) };
  });

  /* ───── ticket ───── */
  readonly qtyStep = computed(() => (this.ins().kind === "FX" ? 100_000 : this.ins().kind === "Rates" ? 1_000_000 : this.ins().kind === "Crypto" ? 0.1 : 10));
  readonly priceStep = computed(() => Math.pow(10, -this.ins().decimals));
  readonly refPx = computed(() => (this.orderType() !== "Market" && this.limitPx() > 0 ? this.limitPx() : this.quote().price));
  readonly notional = computed(() => {
    const k = this.ins().kind;
    if (k === "FX" || k === "Rates") return this.qty() * (k === "FX" ? this.refPx() : 1);
    return this.qty() * this.refPx();
  });

  setType(t: OrderType) {
    this.orderType.set(t);
    if (t !== "Market" && !this.limitPx()) this.limitPx.set(+this.quote().price.toFixed(this.ins().decimals));
  }

  propose() {
    const i = this.ins();
    const px = this.orderType() === "Market" ? "at market" : `${this.orderType().toLowerCase()} ${this.fmtPrice(i.symbol, this.refPx())}`;
    const detail = `${this.side()} ${num(this.qty(), this.qty() % 1 ? 2 : 0)} ${i.symbol} ${px}`;
    this.store.log(this.store.org.persona.name, "human", "proposal.drafted", i.symbol, `${detail} · routed to Guard`);
    this.store.toast("info", "Proposal drafted — routed to Approvals for Guard evaluation", `${detail}. Direct execution is disabled: every order becomes a governed action.`);
  }
}
