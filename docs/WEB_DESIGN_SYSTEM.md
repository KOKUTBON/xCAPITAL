# xCAPITAL web design system

The Angular client in `frontend/web` implements the xCAPITAL workspace and the public product site. This document describes the system as it is built; the brand itself is in [branding/BRAND_GUIDE.md](../branding/BRAND_GUIDE.md).

## Principles

1. **Calm density.** Treasury teams scan many numbers at once. Use whitespace, alignment and tabular figures instead of borders and colour.
2. **State is colour, colour is state.** Green, red, amber and violet carry fixed meanings (positive, negative or blocked, needs attention, AI-originated). They are never decorative.
3. **Provenance is visible.** Every proposal shows who or what created it, what Guard checked, and who approved it.
4. **Dark first, light ready.** Every colour is a token, and both themes are tested.

## Tokens

All tokens are CSS custom properties on `:root`, defined in `src/styles.css` and re-declared under `[data-theme="light"]`:

- Surfaces: `--x-canvas`, `--x-bg`, `--x-surface`, `--x-surface-2`, `--x-surface-3`, `--x-hover`, `--x-border`, `--x-border-strong`
- Text: `--x-text`, `--x-text-2`, `--x-text-3`
- Semantic: `--x-primary`, `--x-accent`, `--x-pos`, `--x-neg`, `--x-warn`, `--x-info`, `--x-ai`. Each has a `-soft` tint for backgrounds.
- Data series: `--x-s1` … `--x-s7`
- Brand: `--x-gradient`, `--x-cyan`, `--x-violet`
- Shape and motion: `--x-radius-sm|-|-lg`, `--x-ease`, `--x-shadow`, `--x-shadow-pop`, `--x-glass`

## Primitives

Global classes cover:

- Layout: `.page`, `.grid` with `.g-2/.g-3/.g-4/.g-12` and `.span-N`
- Cards: `.card` (`.flush`, `.glow`), `.card-head`, `.kpi`
- Buttons and controls: the default primary button, `.btn-glow`, `.btn-secondary`, `.btn-ghost`, `.btn-pos`, `.btn-neg`, sizes; `.seg` segmented controls; `.input`, `.field`, `.switch`
- Data display: `.table`, `.badge` with tones, `.meter`, `.list-item`, `.icon-tile`, `.legend`, `.callout`
- Text helpers: `.num`, `.eyebrow`, `.pos/.neg/.warn/.ai`

Shared Angular components:

| Component | File | Purpose |
| --- | --- | --- |
| `x-icon` | `shared/icon.component.ts` | 60+ line icons on a 24 px grid with a 1.75 stroke |
| `x-logo`, `x-page-head`, `x-kpi`, `x-status` | `shared/ui.ts` | Brand lockup, page header, KPI tile, state pill with automatic tone |
| `x-spark`, `x-line-chart`, `x-bar-chart`, `x-donut`, `x-candles` | `shared/charts.ts` | Dependency-free SVG charts: resize-aware, crosshair tooltips, confidence bands, thresholds |

## Shell

`layout/shell.component.ts` provides:

- A collapsible sidebar grouped into Treasury, Control, Capital, Intelligence and Platform
- A glass top bar with breadcrumbs, a ⌘K command palette, an entity filter, the kill-switch state and the theme toggle
- A live simulated ticker tape that pauses on hover
- A status bar showing connector health and the audit-chain head
- Toast notifications

## Data and state

- `core/demo-data.ts` generates a deterministic, seeded dataset anchored to today's date. The company, banks and people are fictional; market tickers use simulated prices.
- `core/store.ts` holds the interactive state in signals: approvals, the kill switch, policies, reconciliation and connector syncs. Every mutation appends to the simulated hash-chained audit log.
- `MarketFeed` provides the bounded random-walk quote stream.

## Accessibility

- Visible focus rings and a skip link
- `aria-current` on navigation and labels on icon-only buttons
- `prefers-reduced-motion` disables animation
- Charts expose an `aria-label`; key figures are always available as text next to the chart

## Deployment

The app uses hash routing so deep links work on static hosting. To build for GitHub Pages:

```bash
npm run build:pages
```

This sets the base href to `/xCAPITAL/`. `src/404.html` redirects unknown paths back into the hash router.
