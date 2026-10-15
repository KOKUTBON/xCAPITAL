import { AfterViewInit, Component, ElementRef, EventEmitter, Output, ViewChild, computed, inject, signal } from "@angular/core";
import { Router } from "@angular/router";
import { IconComponent } from "../shared/icon.component";
import { ThemeService } from "../core/theme.service";
import { DemoStore } from "../core/store";
import { ACTIONS, INSTRUMENTS } from "../core/demo-data";
import { NAV } from "./nav";

interface Cmd {
  id: string;
  label: string;
  hint: string;
  icon: string;
  section: string;
  run: () => void;
}

@Component({
  selector: "x-command-palette",
  standalone: true,
  imports: [IconComponent],
  template: `<div class="scrim" (click)="closed.emit()"></div>
    <div class="palette" role="dialog" aria-modal="true" aria-label="Command palette" (keydown)="onKey($event)">
      <div class="search">
        <x-icon name="search" />
        <input #q [value]="query()" (input)="query.set(q.value); idx.set(0)" placeholder="Search modules, actions, instruments…" aria-label="Search" />
        <kbd>esc</kbd>
      </div>
      <div class="results">
        @for (c of results(); track c.id; let i = $index) {
          @if (i === 0 || results()[i - 1].section !== c.section) {
            <div class="sec">{{ c.section }}</div>
          }
          <button class="item" [class.on]="i === idx()" (mouseenter)="idx.set(i)" (click)="exec(c)">
            <span class="ico"><x-icon [name]="c.icon" [size]="16" /></span>
            <span class="grow">{{ c.label }}</span>
            <span class="hint">{{ c.hint }}</span>
          </button>
        } @empty {
          <div class="empty">No matches for “{{ query() }}”</div>
        }
      </div>
      <div class="foot"><span><kbd>↑</kbd><kbd>↓</kbd> navigate</span><span><kbd>↵</kbd> open</span></div>
    </div>`,
  styles: [
    `:host{position:fixed;inset:0;z-index:100;display:grid;place-items:start center;padding-top:12vh}
     .scrim{position:absolute;inset:0;background:rgba(3,5,10,.55);backdrop-filter:blur(4px)}
     .palette{position:relative;width:min(640px,calc(100vw - 32px));border-radius:16px;background:var(--x-surface);box-shadow:var(--x-shadow-pop);overflow:hidden;animation:x-rise .2s var(--x-ease)}
     .search{display:flex;align-items:center;gap:10px;padding:14px 16px;border-bottom:1px solid var(--x-border);color:var(--x-text-3)}
     .search input{flex:1;border:0;background:transparent;outline:none;font-size:15px}
     .results{max-height:52vh;overflow:auto;padding:6px}
     .sec{padding:10px 10px 4px;font:600 10px var(--x-font-mono);letter-spacing:.14em;color:var(--x-text-3);text-transform:uppercase}
     .item{width:100%;height:auto;justify-content:flex-start;gap:10px;padding:9px 10px;border-radius:10px;background:transparent;color:var(--x-text);font-weight:400}
     .item:hover,.item.on{background:var(--x-primary-soft);color:var(--x-text)}
     .ico{display:grid;place-items:center;width:28px;height:28px;border-radius:8px;background:var(--x-surface-3);color:var(--x-text-2)}
     .item.on .ico{color:var(--x-primary)}
     .grow{text-align:left}
     .hint{font-size:11px;color:var(--x-text-3)}
     .foot{display:flex;gap:16px;padding:9px 14px;border-top:1px solid var(--x-border);font-size:11px;color:var(--x-text-3)}
     .foot kbd{margin-right:3px}
     .empty{padding:28px;text-align:center;color:var(--x-text-3)}`,
  ],
})
export class CommandPaletteComponent implements AfterViewInit {
  @Output() closed = new EventEmitter<void>();
  @ViewChild("q") input!: ElementRef<HTMLInputElement>;
  private router = inject(Router);
  private theme = inject(ThemeService);
  private store = inject(DemoStore);
  readonly query = signal("");
  readonly idx = signal(0);

  private all: Cmd[] = [
    ...NAV.map((n) => ({ id: n.path, label: n.label, hint: n.keywords.split(" ").slice(0, 3).join(" · "), icon: n.icon, section: "Go to", run: () => this.router.navigateByUrl(n.path) })),
    ...ACTIONS.filter((a) => a.status === "pending").map((a) => ({ id: a.id, label: `${a.id} · ${a.title}`, hint: a.kind, icon: "actions", section: "Approvals", run: () => this.router.navigate(["/app/approvals"], { queryParams: { id: a.id } }) })),
    ...INSTRUMENTS.map((i) => ({ id: "m-" + i.symbol, label: `${i.symbol} — ${i.name}`, hint: i.kind, icon: "markets", section: "Instruments", run: () => this.router.navigate(["/app/markets"], { queryParams: { s: i.symbol } }) })),
    { id: "theme", label: "Toggle light / dark theme", hint: "Appearance", icon: "sun", section: "Commands", run: () => this.theme.toggle() },
    { id: "kill", label: "Toggle execution kill switch", hint: "Guard", icon: "power", section: "Commands", run: () => this.store.toggleKillSwitch() },
    { id: "home", label: "Back to product site", hint: "Landing", icon: "external", section: "Commands", run: () => this.router.navigateByUrl("/") },
  ];

  readonly results = computed(() => {
    const q = this.query().trim().toLowerCase();
    if (!q) return this.all.filter((c) => c.section !== "Instruments").slice(0, 16);
    return this.all.filter((c) => (c.label + " " + c.hint + " " + c.section).toLowerCase().includes(q)).slice(0, 20);
  });

  ngAfterViewInit() {
    setTimeout(() => this.input.nativeElement.focus());
  }

  exec(c: Cmd) {
    c.run();
    this.closed.emit();
  }

  onKey(e: KeyboardEvent) {
    const n = this.results().length;
    if (e.key === "Escape") this.closed.emit();
    else if (e.key === "ArrowDown") {
      this.idx.set((this.idx() + 1) % Math.max(1, n));
      e.preventDefault();
    } else if (e.key === "ArrowUp") {
      this.idx.set((this.idx() - 1 + n) % Math.max(1, n));
      e.preventDefault();
    } else if (e.key === "Enter" && n) this.exec(this.results()[this.idx()]);
  }
}
