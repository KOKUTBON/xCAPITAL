import { Pipe, PipeTransform } from "@angular/core";
import { Ccy } from "./models";

const SYMBOL: Record<string, string> = { EUR: "€", USD: "$", GBP: "£", CHF: "CHF " };

/** €1,234,567 — full precision money, no decimals by default. */
export function money(v: number, ccy: Ccy | string = "EUR", decimals = 0): string {
  const sign = v < 0 ? "−" : "";
  const abs = Math.abs(v).toLocaleString("en-US", {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  });
  return `${sign}${SYMBOL[ccy] ?? ccy + " "}${abs}`;
}

/** €48.6M / €920K — compact money for KPIs and axes. */
export function compact(v: number, ccy: Ccy | string | null = "EUR", digits = 1): string {
  const abs = Math.abs(v);
  const sign = v < 0 ? "−" : "";
  let s: string;
  if (abs >= 1e9) s = (abs / 1e9).toFixed(digits) + "B";
  else if (abs >= 1e6) s = (abs / 1e6).toFixed(digits) + "M";
  else if (abs >= 1e3) s = (abs / 1e3).toFixed(abs >= 1e5 ? 0 : digits) + "K";
  else s = abs.toFixed(0);
  return `${sign}${ccy ? SYMBOL[ccy] ?? ccy + " " : ""}${s}`;
}

/** +1.24% with explicit sign. */
export function pct(v: number, digits = 2, signed = true): string {
  const s = Math.abs(v).toFixed(digits) + "%";
  if (!signed) return (v < 0 ? "−" : "") + s;
  return (v > 0 ? "+" : v < 0 ? "−" : "") + s;
}

export function num(v: number, decimals = 2): string {
  return v.toLocaleString("en-US", { minimumFractionDigits: decimals, maximumFractionDigits: decimals });
}

const DAY = 86_400_000;
export function relTime(iso: string, now = Date.now()): string {
  const d = now - new Date(iso).getTime();
  const future = d < 0;
  const a = Math.abs(d);
  let s: string;
  if (a < 60_000) s = `${Math.max(1, Math.round(a / 1000))}s`;
  else if (a < 3_600_000) s = `${Math.round(a / 60_000)}m`;
  else if (a < DAY) s = `${Math.round(a / 3_600_000)}h`;
  else s = `${Math.round(a / DAY)}d`;
  return future ? `in ${s}` : `${s} ago`;
}

export function shortDate(iso: string): string {
  return new Date(iso).toLocaleDateString("en-GB", { day: "2-digit", month: "short" });
}
export function dateTime(iso: string): string {
  return new Date(iso).toLocaleString("en-GB", {
    day: "2-digit",
    month: "short",
    hour: "2-digit",
    minute: "2-digit",
  });
}

@Pipe({ name: "money", standalone: true })
export class MoneyPipe implements PipeTransform {
  transform(v: number | null | undefined, ccy: Ccy | string = "EUR", decimals = 0): string {
    return v == null ? "—" : money(v, ccy, decimals);
  }
}

@Pipe({ name: "compact", standalone: true })
export class CompactPipe implements PipeTransform {
  transform(v: number | null | undefined, ccy: Ccy | string | null = "EUR", digits = 1): string {
    return v == null ? "—" : compact(v, ccy, digits);
  }
}

@Pipe({ name: "pct", standalone: true })
export class PctPipe implements PipeTransform {
  transform(v: number | null | undefined, digits = 2, signed = true): string {
    return v == null ? "—" : pct(v, digits, signed);
  }
}

@Pipe({ name: "num", standalone: true })
export class NumPipe implements PipeTransform {
  transform(v: number | null | undefined, decimals = 2): string {
    return v == null ? "—" : num(v, decimals);
  }
}

@Pipe({ name: "rel", standalone: true })
export class RelTimePipe implements PipeTransform {
  transform(iso: string | null | undefined): string {
    return iso ? relTime(iso) : "—";
  }
}

@Pipe({ name: "sdate", standalone: true })
export class ShortDatePipe implements PipeTransform {
  transform(iso: string | null | undefined): string {
    return iso ? shortDate(iso) : "—";
  }
}

@Pipe({ name: "dtime", standalone: true })
export class DateTimePipe implements PipeTransform {
  transform(iso: string | null | undefined): string {
    return iso ? dateTime(iso) : "—";
  }
}

/** Convenience bundle for component `imports`. */
export const FORMAT_PIPES = [MoneyPipe, CompactPipe, PctPipe, NumPipe, RelTimePipe, ShortDatePipe, DateTimePipe] as const;
