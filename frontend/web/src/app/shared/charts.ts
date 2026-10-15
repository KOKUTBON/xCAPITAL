import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  Directive,
  ElementRef,
  Input,
  OnDestroy,
  computed,
  inject,
  signal,
} from "@angular/core";
import { Candle } from "../core/models";
import { compact } from "../core/format";

/* ───────────── shared helpers ───────────── */

export interface LineSeries {
  name: string;
  data: number[];
  color: string;
  area?: boolean;
  dashed?: boolean;
  width?: number;
}
export interface BarStack {
  name: string;
  data: number[];
  color: string;
}
export interface DonutSegment {
  label: string;
  value: number;
  color: string;
}
export interface Threshold {
  value: number;
  label: string;
  color?: string;
}

/** Round-number axis ticks covering [min, max]. */
function niceTicks(min: number, max: number, count = 5): number[] {
  if (min === max) {
    min -= 1;
    max += 1;
  }
  const span = max - min;
  const step0 = span / Math.max(1, count - 1);
  const mag = Math.pow(10, Math.floor(Math.log10(step0)));
  const norm = step0 / mag;
  const step = (norm < 1.5 ? 1 : norm < 3 ? 2 : norm < 7 ? 5 : 10) * mag;
  const start = Math.floor(min / step) * step;
  const ticks: number[] = [];
  for (let v = start; v <= max + step * 0.5; v += step) ticks.push(+v.toFixed(10));
  return ticks;
}

function linePath(xs: number[], ys: number[]): string {
  let d = "";
  for (let i = 0; i < xs.length; i++) d += (i ? "L" : "M") + xs[i].toFixed(1) + " " + ys[i].toFixed(1);
  return d;
}

/** Tracks the rendered width of the host so charts draw at true pixel size. */
@Directive()
abstract class Measured implements AfterViewInit, OnDestroy {
  protected host = inject(ElementRef<HTMLElement>);
  readonly width = signal(600);
  private ro?: ResizeObserver;
  ngAfterViewInit() {
    const el = this.host.nativeElement as HTMLElement;
    this.width.set(Math.max(120, el.clientWidth));
    this.ro = new ResizeObserver((e) => this.width.set(Math.max(120, Math.floor(e[0].contentRect.width))));
    this.ro.observe(el);
  }
  ngOnDestroy() {
    this.ro?.disconnect();
  }
}

const CHART_STYLES = `
  :host { display: block; position: relative; width: 100%; }
  svg { display: block; overflow: visible; }
  .axis { font: 500 10.5px var(--x-font-mono); fill: var(--x-text-3); }
  .grid { stroke: var(--x-grid); }
  .zero { stroke: var(--x-border-strong); }
  .cross { stroke: var(--x-text-3); stroke-dasharray: 3 3; }
  .tip {
    position: absolute; z-index: 5; pointer-events: none; min-width: 150px;
    padding: 9px 11px; border-radius: 10px; font-size: 12px;
    background: var(--x-glass); backdrop-filter: blur(12px); -webkit-backdrop-filter: blur(12px);
    box-shadow: var(--x-shadow-pop); color: var(--x-text);
    transform: translate(-50%, calc(-100% - 10px));
  }
  .tip b { display: block; margin-bottom: 5px; font: 600 11px var(--x-font-mono); color: var(--x-text-2); }
  .tip .r { display: flex; align-items: center; justify-content: space-between; gap: 14px; line-height: 1.7; }
  .tip .r span:first-child { display: flex; align-items: center; gap: 6px; color: var(--x-text-2); }
  .tip i { width: 8px; height: 8px; border-radius: 2px; display: inline-block; }
  .tip .v { font-family: var(--x-font-mono); font-variant-numeric: tabular-nums; }
`;

/* ───────────── Sparkline ───────────── */

@Component({
  selector: "x-spark",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<svg [attr.viewBox]="'0 0 100 ' + height" preserveAspectRatio="none" [attr.height]="height" width="100%" aria-hidden="true">
    @if (fill) {
      <path [attr.d]="area()" [attr.fill]="'url(#' + gid + ')'" />
    }
    <path [attr.d]="line()" fill="none" [attr.stroke]="color" stroke-width="1.6" vector-effect="non-scaling-stroke" stroke-linejoin="round" />
    <defs>
      <linearGradient [attr.id]="gid" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0" [attr.stop-color]="color" stop-opacity="0.28" />
        <stop offset="1" [attr.stop-color]="color" stop-opacity="0" />
      </linearGradient>
    </defs>
  </svg>`,
  styles: [":host{display:block;width:100%}svg{display:block}"],
})
export class SparklineComponent {
  private static n = 0;
  readonly gid = "spk" + SparklineComponent.n++;
  private _data = signal<number[]>([]);
  @Input() set data(v: number[]) {
    this._data.set(v ?? []);
  }
  @Input() color = "var(--x-primary)";
  @Input() height = 36;
  @Input() fill = true;

  private pts = computed(() => {
    const d = this._data();
    if (d.length < 2) return { xs: [0, 100], ys: [this.height / 2, this.height / 2] };
    const min = Math.min(...d);
    const max = Math.max(...d);
    const span = max - min || 1;
    return {
      xs: d.map((_, i) => (i / (d.length - 1)) * 100),
      ys: d.map((v) => this.height - 2 - ((v - min) / span) * (this.height - 4)),
    };
  });
  line = computed(() => linePath(this.pts().xs, this.pts().ys));
  area = computed(() => this.line() + `L100 ${this.height}L0 ${this.height}Z`);
}

/* ───────────── Line / area chart ───────────── */

@Component({
  selector: "x-line-chart",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<svg [attr.width]="width()" [attr.height]="height" (mousemove)="onMove($event)" (mouseleave)="hover.set(-1)" role="img" [attr.aria-label]="ariaLabel">
      <defs>
        @for (s of series; track s.name; let i = $index) {
          <linearGradient [attr.id]="gid + i" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0" [attr.stop-color]="s.color" stop-opacity="0.26" />
            <stop offset="1" [attr.stop-color]="s.color" stop-opacity="0" />
          </linearGradient>
        }
      </defs>
      @for (t of geo().ticks; track t) {
        <line class="grid" [attr.x1]="pad.l" [attr.x2]="width() - pad.r" [attr.y1]="geo().y(t)" [attr.y2]="geo().y(t)" />
        <text class="axis" [attr.x]="pad.l - 8" [attr.y]="geo().y(t) + 3.5" text-anchor="end">{{ yFormat(t) }}</text>
      }
      @for (l of geo().xLabels; track l.i) {
        <text class="axis" [attr.x]="geo().x(l.i)" [attr.y]="height - 6" text-anchor="middle">{{ l.text }}</text>
      }
      @if (band && geo().bandPath) {
        <path [attr.d]="geo().bandPath" [attr.fill]="band.color || 'var(--x-primary)'" fill-opacity="0.12" />
      }
      @if (threshold) {
        <line [attr.x1]="pad.l" [attr.x2]="width() - pad.r" [attr.y1]="geo().y(threshold.value)" [attr.y2]="geo().y(threshold.value)" [attr.stroke]="threshold.color || 'var(--x-neg)'" stroke-dasharray="5 4" stroke-width="1.2" />
        <text class="axis" [attr.x]="width() - pad.r" [attr.y]="geo().y(threshold.value) - 6" text-anchor="end" [style.fill]="threshold.color || 'var(--x-neg)'">{{ threshold.label }}</text>
      }
      @for (s of geo().paths; track s.name; let i = $index) {
        @if (s.area) {
          <path [attr.d]="s.area" [attr.fill]="'url(#' + gid + i + ')'" />
        }
        <path [attr.d]="s.line" fill="none" [attr.stroke]="s.color" [attr.stroke-width]="s.width" [attr.stroke-dasharray]="s.dashed ? '5 4' : null" stroke-linejoin="round" stroke-linecap="round" />
      }
      @if (hover() >= 0) {
        <line class="cross" [attr.x1]="geo().x(hover())" [attr.x2]="geo().x(hover())" [attr.y1]="pad.t" [attr.y2]="height - pad.b" />
        @for (s of series; track s.name) {
          <circle [attr.cx]="geo().x(hover())" [attr.cy]="geo().y(s.data[hover()])" r="4" [attr.fill]="s.color" stroke="var(--x-surface)" stroke-width="2" />
        }
      }
    </svg>
    @if (hover() >= 0) {
      <div class="tip" [style.left.px]="geo().x(hover())" [style.top.px]="pad.t + 4">
        <b>{{ labels[hover()] }}</b>
        @for (s of series; track s.name) {
          <div class="r"><span><i [style.background]="s.color"></i>{{ s.name }}</span><span class="v">{{ tipFormat(s.data[hover()]) }}</span></div>
        }
        @if (band) {
          <div class="r"><span><i [style.background]="band.color || 'var(--x-primary)'" style="opacity:.4"></i>P10 – P90</span><span class="v">{{ yFormat(band.lo[hover()]) }} – {{ yFormat(band.hi[hover()]) }}</span></div>
        }
      </div>
    }`,
  // Distinct inline styles avoid Angular 21 cache collisions (angular-cli#33497).
  styles: [CHART_STYLES + "/* line-chart */"],
})
export class LineChartComponent extends Measured {
  private static n = 0;
  readonly gid = "lc" + LineChartComponent.n++;
  private _series = signal<LineSeries[]>([]);
  private _band = signal<{ lo: number[]; hi: number[]; color?: string } | undefined>(undefined);
  private _threshold = signal<Threshold | undefined>(undefined);
  @Input() set series(v: LineSeries[]) {
    this._series.set(v ?? []);
  }
  get series() {
    return this._series();
  }
  @Input() set band(v: { lo: number[]; hi: number[]; color?: string } | undefined) {
    this._band.set(v);
  }
  get band() {
    return this._band();
  }
  @Input() set threshold(v: Threshold | undefined) {
    this._threshold.set(v);
  }
  get threshold() {
    return this._threshold();
  }
  @Input() labels: string[] = [];
  @Input() height = 240;
  @Input() xTicks = 6;
  @Input() yFormat: (v: number) => string = (v) => compact(v, "EUR");
  @Input() tipFormat: (v: number) => string = (v) => compact(v, "EUR", 2);
  @Input() ariaLabel = "Line chart";
  @Input() zeroBased = false;

  readonly pad = { l: 58, r: 14, t: 14, b: 26 };
  readonly hover = signal(-1);

  readonly geo = computed(() => {
    const series = this._series();
    const band = this._band();
    const th = this._threshold();
    const w = this.width();
    const n = Math.max(2, ...series.map((s) => s.data.length));
    let vals = series.flatMap((s) => s.data);
    if (band) vals = vals.concat(band.lo, band.hi);
    if (th) vals.push(th.value);
    if (this.zeroBased) vals.push(0);
    let min = Math.min(...vals);
    let max = Math.max(...vals);
    const padV = (max - min) * 0.08 || 1;
    min -= padV;
    max += padV;
    const ticks = niceTicks(min, max, 5).filter((t) => t >= min && t <= max);
    const innerW = w - this.pad.l - this.pad.r;
    const innerH = this.height - this.pad.t - this.pad.b;
    const x = (i: number) => this.pad.l + (i / (n - 1)) * innerW;
    const y = (v: number) => this.pad.t + (1 - (v - min) / (max - min)) * innerH;
    const xs = Array.from({ length: n }, (_, i) => x(i));
    const paths = series.map((s) => {
      const ys = s.data.map(y);
      const line = linePath(xs.slice(0, s.data.length), ys);
      return {
        name: s.name,
        color: s.color,
        dashed: s.dashed,
        width: s.width ?? 2,
        line,
        area: s.area ? line + `L${xs[s.data.length - 1]} ${this.height - this.pad.b}L${xs[0]} ${this.height - this.pad.b}Z` : null,
      };
    });
    let bandPath: string | null = null;
    if (band && band.lo.length) {
      const up = band.hi.map((v, i) => `${i ? "L" : "M"}${x(i).toFixed(1)} ${y(v).toFixed(1)}`).join("");
      const dn = band.lo.map((v, i) => `L${x(i).toFixed(1)} ${y(v).toFixed(1)}`).reverse().join("");
      bandPath = up + dn + "Z";
    }
    const step = Math.max(1, Math.ceil(n / this.xTicks));
    const xLabels = this.labels
      .map((text, i) => ({ text, i }))
      .filter((l) => l.i % step === 0 || l.i === n - 1)
      .filter((l, k, arr) => !(l.i === n - 1 && k > 0 && l.i - arr[k - 1].i < step * 0.6));
    return { x, y, ticks, paths, bandPath, xLabels, n, innerW };
  });

  onMove(e: MouseEvent) {
    const g = this.geo();
    const rect = (e.currentTarget as SVGElement).getBoundingClientRect();
    const px = e.clientX - rect.left - this.pad.l;
    const i = Math.round((px / g.innerW) * (g.n - 1));
    this.hover.set(i < 0 || i >= g.n ? -1 : i);
  }
}

/* ───────────── Stacked bar chart (+ optional line) ───────────── */

@Component({
  selector: "x-bar-chart",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<svg [attr.width]="width()" [attr.height]="height" (mousemove)="onMove($event)" (mouseleave)="hover.set(-1)" role="img" [attr.aria-label]="ariaLabel">
      @for (t of geo().ticks; track t) {
        <line [class]="t === 0 ? 'zero' : 'grid'" [attr.x1]="pad.l" [attr.x2]="width() - pad.r" [attr.y1]="geo().y(t)" [attr.y2]="geo().y(t)" />
        <text class="axis" [attr.x]="pad.l - 8" [attr.y]="geo().y(t) + 3.5" text-anchor="end">{{ yFormat(t) }}</text>
      }
      @if (hover() >= 0) {
        <rect [attr.x]="geo().cx(hover()) - geo().slot / 2" [attr.y]="pad.t" [attr.width]="geo().slot" [attr.height]="height - pad.t - pad.b" fill="var(--x-hover)" rx="6" />
      }
      @for (r of geo().rects; track $index) {
        <rect [attr.x]="r.x" [attr.y]="r.y" [attr.width]="r.w" [attr.height]="r.h" [attr.fill]="r.color" rx="2.5" [attr.opacity]="hover() < 0 || hover() === r.i ? 1 : 0.45" />
      }
      @if (threshold) {
        <line [attr.x1]="pad.l" [attr.x2]="width() - pad.r" [attr.y1]="geo().y(threshold.value)" [attr.y2]="geo().y(threshold.value)" [attr.stroke]="threshold.color || 'var(--x-neg)'" stroke-dasharray="5 4" stroke-width="1.2" />
        <text class="axis" [attr.x]="width() - pad.r" [attr.y]="geo().y(threshold.value) - 6" text-anchor="end" [style.fill]="threshold.color || 'var(--x-neg)'">{{ threshold.label }}</text>
      }
      @if (geo().linePath) {
        <path [attr.d]="geo().linePath" fill="none" [attr.stroke]="line!.color" stroke-width="2.2" stroke-linejoin="round" />
        @for (p of geo().linePts; track $index) {
          <circle [attr.cx]="p.x" [attr.cy]="p.y" r="3.2" [attr.fill]="line!.color" stroke="var(--x-surface)" stroke-width="1.5" />
        }
      }
      @for (l of labels; track $index) {
        @if ($index % geo().labelStep === 0) {
          <text class="axis" [attr.x]="geo().cx($index)" [attr.y]="height - 6" text-anchor="middle">{{ l }}</text>
        }
      }
    </svg>
    @if (hover() >= 0) {
      <div class="tip" [style.left.px]="geo().cx(hover())" [style.top.px]="pad.t + 4">
        <b>{{ labels[hover()] }}</b>
        @for (s of stacks; track s.name) {
          @if (s.data[hover()]) {
            <div class="r"><span><i [style.background]="s.color"></i>{{ s.name }}</span><span class="v">{{ tipFormat(s.data[hover()]) }}</span></div>
          }
        }
        @if (line) {
          <div class="r"><span><i [style.background]="line.color"></i>{{ line.name }}</span><span class="v">{{ tipFormat(line.data[hover()]) }}</span></div>
        }
      </div>
    }`,
  // Distinct inline styles avoid Angular 21 cache collisions (angular-cli#33497).
  styles: [CHART_STYLES + "/* bar-chart */"],
})
export class BarChartComponent extends Measured {
  private _stacks = signal<BarStack[]>([]);
  private _line = signal<BarStack | undefined>(undefined);
  private _threshold = signal<Threshold | undefined>(undefined);
  @Input() set stacks(v: BarStack[]) {
    this._stacks.set(v ?? []);
  }
  get stacks() {
    return this._stacks();
  }
  @Input() set line(v: BarStack | undefined) {
    this._line.set(v);
  }
  get line() {
    return this._line();
  }
  @Input() set threshold(v: Threshold | undefined) {
    this._threshold.set(v);
  }
  get threshold() {
    return this._threshold();
  }
  @Input() labels: string[] = [];
  @Input() height = 260;
  @Input() yFormat: (v: number) => string = (v) => compact(v, "EUR");
  @Input() tipFormat: (v: number) => string = (v) => compact(v, "EUR", 2);
  @Input() ariaLabel = "Bar chart";

  readonly pad = { l: 58, r: 14, t: 14, b: 26 };
  readonly hover = signal(-1);

  readonly geo = computed(() => {
    const stacks = this._stacks();
    const line = this._line();
    const th = this._threshold();
    const w = this.width();
    const n = this.labels.length || stacks[0]?.data.length || 1;
    const posTot = Array.from({ length: n }, (_, i) => stacks.reduce((s, st) => s + Math.max(0, st.data[i] || 0), 0));
    const negTot = Array.from({ length: n }, (_, i) => stacks.reduce((s, st) => s + Math.min(0, st.data[i] || 0), 0));
    let vals = [...posTot, ...negTot, 0];
    if (line) vals = vals.concat(line.data);
    if (th) vals.push(th.value);
    let min = Math.min(...vals);
    let max = Math.max(...vals);
    const padV = (max - min) * 0.06 || 1;
    max += padV;
    if (min < 0) min -= padV;
    const ticks = niceTicks(min, max, 6).filter((t) => t >= min && t <= max);
    const innerW = w - this.pad.l - this.pad.r;
    const innerH = this.height - this.pad.t - this.pad.b;
    const slot = innerW / n;
    const bw = Math.max(4, Math.min(34, slot * 0.58));
    const cx = (i: number) => this.pad.l + slot * (i + 0.5);
    const y = (v: number) => this.pad.t + (1 - (v - min) / (max - min)) * innerH;
    const rects: { x: number; y: number; w: number; h: number; color: string; i: number }[] = [];
    for (let i = 0; i < n; i++) {
      let up = 0;
      let dn = 0;
      for (const st of stacks) {
        const v = st.data[i] || 0;
        if (!v) continue;
        const from = v > 0 ? up : dn;
        const to = from + v;
        if (v > 0) up = to;
        else dn = to;
        const y1 = y(Math.max(from, to));
        const y2 = y(Math.min(from, to));
        rects.push({ x: cx(i) - bw / 2, y: y1, w: bw, h: Math.max(1, y2 - y1 - 1), color: st.color, i });
      }
    }
    const linePts = line ? line.data.map((v, i) => ({ x: cx(i), y: y(v) })) : [];
    const linePath = line ? linePts.map((p, i) => `${i ? "L" : "M"}${p.x.toFixed(1)} ${p.y.toFixed(1)}`).join("") : null;
    const labelStep = Math.max(1, Math.ceil(n / Math.max(2, Math.floor(innerW / 54))));
    return { ticks, y, cx, slot, rects, linePts, linePath, labelStep, n, innerW };
  });

  onMove(e: MouseEvent) {
    const g = this.geo();
    const rect = (e.currentTarget as SVGElement).getBoundingClientRect();
    const i = Math.floor((e.clientX - rect.left - this.pad.l) / g.slot);
    this.hover.set(i < 0 || i >= g.n ? -1 : i);
  }
}

/* ───────────── Donut ───────────── */

@Component({
  selector: "x-donut",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div class="wrap" [style.width.px]="size" [style.height.px]="size">
    <svg [attr.width]="size" [attr.height]="size" [attr.viewBox]="'0 0 ' + size + ' ' + size" role="img" [attr.aria-label]="ariaLabel">
      <circle [attr.cx]="size / 2" [attr.cy]="size / 2" [attr.r]="r" fill="none" stroke="var(--x-surface-3)" [attr.stroke-width]="thickness" />
      @for (a of arcs(); track a.label; let i = $index) {
        <circle
          [attr.cx]="size / 2" [attr.cy]="size / 2" [attr.r]="r" fill="none"
          [attr.stroke]="a.color" [attr.stroke-width]="active() === i ? thickness + 6 : thickness"
          [attr.stroke-dasharray]="a.len + ' ' + (circ - a.len)"
          [attr.stroke-dashoffset]="-a.offset"
          [attr.transform]="'rotate(-90 ' + size / 2 + ' ' + size / 2 + ')'"
          [attr.opacity]="active() < 0 || active() === i ? 1 : 0.35"
          (mouseenter)="active.set(i)" (mouseleave)="active.set(-1)"
          style="transition: opacity .15s, stroke-width .15s; cursor: pointer"
        />
      }
    </svg>
    <div class="center">
      @if (active() >= 0) {
        <span class="lbl">{{ arcs()[active()].label }}</span>
        <strong>{{ arcs()[active()].pct.toFixed(1) }}%</strong>
      } @else {
        <ng-content />
      }
    </div>
  </div>`,
  styles: [
    `:host{display:inline-block}
     .wrap{position:relative}
     svg{display:block}
     .center{position:absolute;inset:0;display:grid;place-content:center;text-align:center;pointer-events:none;padding:0 18%}
     .lbl{font-size:11px;color:var(--x-text-3)}
     strong{font:600 20px var(--x-font-display)}`,
  ],
})
export class DonutComponent {
  private _segments = signal<DonutSegment[]>([]);
  @Input() set segments(v: DonutSegment[]) {
    this._segments.set(v ?? []);
  }
  @Input() size = 180;
  @Input() thickness = 18;
  @Input() ariaLabel = "Allocation";
  readonly active = signal(-1);
  get r() {
    return this.size / 2 - this.thickness / 2 - 4;
  }
  get circ() {
    return 2 * Math.PI * this.r;
  }
  arcs = computed(() => {
    const segs = this._segments();
    const total = segs.reduce((s, x) => s + x.value, 0) || 1;
    const circ = 2 * Math.PI * (this.size / 2 - this.thickness / 2 - 4);
    const gap = segs.length > 1 ? 2 : 0;
    let off = 0;
    return segs.map((s) => {
      const full = (s.value / total) * circ;
      const arc = { ...s, len: Math.max(0.5, full - gap), offset: off, pct: (s.value / total) * 100 };
      off += full;
      return arc;
    });
  });
}

/* ───────────── Candlestick chart ───────────── */

@Component({
  selector: "x-candles",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<svg [attr.width]="width()" [attr.height]="height" (mousemove)="onMove($event)" (mouseleave)="hover.set(-1)" role="img" aria-label="Price chart">
      @for (t of geo().ticks; track t) {
        <line class="grid" [attr.x1]="pad.l" [attr.x2]="width() - pad.r" [attr.y1]="geo().y(t)" [attr.y2]="geo().y(t)" />
        <text class="axis" [attr.x]="width() - pad.r + 6" [attr.y]="geo().y(t) + 3.5">{{ fmt(t) }}</text>
      }
      @for (c of geo().bars; track $index) {
        <rect [attr.x]="c.x - geo().bw / 2" [attr.y]="c.vy" [attr.width]="geo().bw" [attr.height]="c.vh" [attr.fill]="c.up ? 'var(--x-pos)' : 'var(--x-neg)'" opacity="0.18" />
        <line [attr.x1]="c.x" [attr.x2]="c.x" [attr.y1]="c.hy" [attr.y2]="c.ly" [attr.stroke]="c.up ? 'var(--x-pos)' : 'var(--x-neg)'" stroke-width="1" />
        <rect [attr.x]="c.x - geo().bw / 2" [attr.y]="c.by" [attr.width]="geo().bw" [attr.height]="c.bh" [attr.fill]="c.up ? 'var(--x-pos)' : 'var(--x-neg)'" rx="1" />
      }
      @if (last != null) {
        <line [attr.x1]="pad.l" [attr.x2]="width() - pad.r" [attr.y1]="geo().y(last)" [attr.y2]="geo().y(last)" stroke="var(--x-primary)" stroke-dasharray="2 3" />
        <rect [attr.x]="width() - pad.r + 2" [attr.y]="geo().y(last) - 9" [attr.width]="pad.r - 4" height="18" rx="4" fill="var(--x-primary)" />
        <text [attr.x]="width() - pad.r + 6" [attr.y]="geo().y(last) + 4" style="font: 600 10.5px var(--x-font-mono); fill: #fff">{{ fmt(last) }}</text>
      }
      @for (l of geo().xl; track l.i) {
        <text class="axis" [attr.x]="l.x" [attr.y]="height - 6" text-anchor="middle">{{ l.t }}</text>
      }
      @if (hover() >= 0) {
        <line class="cross" [attr.x1]="geo().bars[hover()].x" [attr.x2]="geo().bars[hover()].x" [attr.y1]="pad.t" [attr.y2]="height - pad.b" />
      }
    </svg>
    @if (hover() >= 0) {
      <div class="tip" [style.left.px]="geo().bars[hover()].x" [style.top.px]="pad.t + 4">
        <b>{{ dateOf(hover()) }}</b>
        <div class="r"><span>Open</span><span class="v">{{ fmt(candles[hover()].o) }}</span></div>
        <div class="r"><span>High</span><span class="v">{{ fmt(candles[hover()].h) }}</span></div>
        <div class="r"><span>Low</span><span class="v">{{ fmt(candles[hover()].l) }}</span></div>
        <div class="r"><span>Close</span><span class="v">{{ fmt(candles[hover()].c) }}</span></div>
      </div>
    }`,
  // Distinct inline styles avoid Angular 21 cache collisions (angular-cli#33497).
  styles: [CHART_STYLES + "/* candle-chart */"],
})
export class CandleChartComponent extends Measured {
  private _candles = signal<Candle[]>([]);
  private _last = signal<number | null>(null);
  @Input() set candles(v: Candle[]) {
    this._candles.set(v ?? []);
  }
  get candles() {
    return this._candles();
  }
  @Input() set last(v: number | null) {
    this._last.set(v);
  }
  get last() {
    return this._last();
  }
  @Input() height = 360;
  @Input() decimals = 2;
  readonly pad = { l: 8, r: 70, t: 14, b: 26 };
  readonly hover = signal(-1);
  fmt = (v: number) => v.toLocaleString("en-US", { minimumFractionDigits: this.decimals, maximumFractionDigits: this.decimals });
  dateOf = (i: number) => new Date(this._candles()[i].t).toLocaleDateString("en-GB", { day: "2-digit", month: "short", year: "numeric" });

  readonly geo = computed(() => {
    const cs = this._candles();
    const last = this._last();
    const w = this.width();
    const n = cs.length || 1;
    let min = Math.min(...cs.map((c) => c.l));
    let max = Math.max(...cs.map((c) => c.h));
    if (last != null) {
      min = Math.min(min, last);
      max = Math.max(max, last);
    }
    const padV = (max - min) * 0.06;
    min -= padV;
    max += padV;
    const innerW = w - this.pad.l - this.pad.r;
    const volH = (this.height - this.pad.t - this.pad.b) * 0.18;
    const innerH = this.height - this.pad.t - this.pad.b;
    const slot = innerW / n;
    const bw = Math.max(1.5, slot * 0.62);
    const y = (v: number) => this.pad.t + (1 - (v - min) / (max - min)) * innerH;
    const vmax = Math.max(...cs.map((c) => c.v));
    const base = this.height - this.pad.b;
    const bars = cs.map((c, i) => {
      const x = this.pad.l + slot * (i + 0.5);
      const up = c.c >= c.o;
      const vh = (c.v / vmax) * volH;
      return { x, up, hy: y(c.h), ly: y(c.l), by: y(Math.max(c.o, c.c)), bh: Math.max(1, Math.abs(y(c.o) - y(c.c))), vy: base - vh, vh };
    });
    const ticks = niceTicks(min, max, 6).filter((t) => t >= min && t <= max);
    const every = Math.max(1, Math.ceil(n / Math.max(2, Math.floor(innerW / 80))));
    const xl = cs
      .map((c, i) => ({ i, x: this.pad.l + slot * (i + 0.5), t: new Date(c.t).toLocaleDateString("en-GB", { day: "2-digit", month: "short" }) }))
      .filter((l) => l.i % every === 0);
    return { bars, y, bw, ticks, xl, slot, n };
  });

  onMove(e: MouseEvent) {
    const g = this.geo();
    const rect = (e.currentTarget as SVGElement).getBoundingClientRect();
    const i = Math.floor((e.clientX - rect.left - this.pad.l) / g.slot);
    this.hover.set(i < 0 || i >= g.n ? -1 : i);
  }
}

export const CHARTS = [SparklineComponent, LineChartComponent, BarChartComponent, DonutComponent, CandleChartComponent] as const;
