import { ChangeDetectionStrategy, Component, Input } from "@angular/core";
import { IconComponent } from "./icon.component";

/** Brand lockup: the x mark, optionally with the wordmark. */
@Component({
  selector: "x-logo",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<svg [attr.width]="size" [attr.height]="size" viewBox="0 0 64 64" aria-hidden="true">
      <defs>
        <linearGradient [attr.id]="gid" x1="12" y1="52" x2="52" y2="12" gradientUnits="userSpaceOnUse">
          <stop offset="0" stop-color="#7C5CFF" />
          <stop offset="1" stop-color="#3EE6D2" />
        </linearGradient>
      </defs>
      <rect width="64" height="64" rx="16" fill="#0B0F1F" />
      <rect x=".75" y=".75" width="62.5" height="62.5" rx="15.25" fill="none" stroke="#fff" stroke-opacity=".1" stroke-width="1.5" />
      <path d="M17.5 17.5 26 26 M38 38l8.5 8.5" fill="none" stroke="#E8ECF6" stroke-width="6.5" stroke-linecap="round" />
      <path d="M17.5 46.5 44 20 M31 18.5h14.5V33" fill="none" [attr.stroke]="'url(#' + gid + ')'" stroke-width="6.5" stroke-linecap="round" stroke-linejoin="round" />
    </svg>
    @if (wordmark) {
      <span class="wm"><span class="x">x</span>CAPITAL</span>
    }`,
  styles: [
    `:host{display:inline-flex;align-items:center;gap:10px}
     svg{flex-shrink:0;filter:drop-shadow(0 6px 16px rgba(91,140,255,.35))}
     .wm{font:700 18px/1 var(--x-font-display);letter-spacing:.04em;color:var(--x-text)}
     .x{background:var(--x-gradient);-webkit-background-clip:text;background-clip:text;color:transparent;letter-spacing:0}`,
  ],
})
export class LogoComponent {
  private static n = 0;
  readonly gid = "xlg" + LogoComponent.n++;
  @Input() size = 32;
  @Input() wordmark = true;
}

/** Standard page header with eyebrow, title, description and action slot. */
@Component({
  selector: "x-page-head",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<header class="page-head">
    <div>
      <div class="eyebrow">{{ eyebrow }}</div>
      <h1>{{ title }}</h1>
      @if (description) {
        <p>{{ description }}</p>
      }
    </div>
    <div class="actions"><ng-content /></div>
  </header>`,
})
export class PageHeadComponent {
  @Input() eyebrow = "";
  @Input() title = "";
  @Input() description = "";
}

/** Compact KPI tile. */
@Component({
  selector: "x-kpi",
  standalone: true,
  imports: [IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div class="card kpi">
    <div class="label">
      @if (icon) {
        <x-icon [name]="icon" [size]="15" />
      }
      {{ label }}
    </div>
    <div class="value">{{ value }}</div>
    @if (delta) {
      <div class="delta" [class.pos]="tone === 'pos'" [class.neg]="tone === 'neg'" [class.faint]="tone === 'neutral'">{{ delta }}</div>
    }
    <ng-content />
  </div>`,
  styles: [":host{display:block;min-width:0}.card{height:100%}"],
})
export class KpiComponent {
  @Input() label = "";
  @Input() value = "";
  @Input() delta = "";
  @Input() tone: "pos" | "neg" | "neutral" = "neutral";
  @Input() icon = "";
}

/** Status pill with semantic tone mapping for common workflow states. */
@Component({
  selector: "x-status",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span class="badge" [class]="'badge ' + tone"><span class="dot"></span>{{ label || value }}</span>`,
})
export class StatusComponent {
  @Input() value = "";
  @Input() label = "";
  get tone(): string {
    const v = this.value.toLowerCase();
    if (["executed", "reconciled", "healthy", "live", "pass", "approved", "active", "confirmed", "posted"].includes(v)) return "pos";
    if (["rejected", "blocked", "fail", "offline", "exception", "overdue", "critical"].includes(v)) return "neg";
    if (["pending", "warn", "degraded", "delayed", "at risk", "warning", "matched"].includes(v)) return "warn";
    if (["syncing", "scheduled", "manual", "info", "draft"].includes(v)) return "info";
    if (["ai", "opportunity"].includes(v)) return "ai";
    return "";
  }
}

export const UI = [LogoComponent, PageHeadComponent, KpiComponent, StatusComponent, IconComponent] as const;
