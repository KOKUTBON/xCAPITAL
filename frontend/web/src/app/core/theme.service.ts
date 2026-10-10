import { Injectable, effect, signal } from "@angular/core";

export type Theme = "dark" | "light";
const KEY = "xc-theme";

function stored(): Theme | null {
  try {
    const v = localStorage.getItem(KEY);
    return v === "dark" || v === "light" ? v : null;
  } catch {
    return null;
  }
}

/** Dark by default; remembers an explicit choice per browser. */
@Injectable({ providedIn: "root" })
export class ThemeService {
  readonly theme = signal<Theme>(stored() ?? "dark");

  constructor() {
    effect(() => {
      const t = this.theme();
      document.documentElement.setAttribute("data-theme", t);
      document.querySelector('meta[name="theme-color"]')?.setAttribute("content", t === "dark" ? "#05070d" : "#f3f5fa");
    });
  }

  toggle() {
    const next: Theme = this.theme() === "dark" ? "light" : "dark";
    this.theme.set(next);
    try {
      localStorage.setItem(KEY, next);
    } catch {
      /* storage unavailable — theme applies for this session only */
    }
  }
}
