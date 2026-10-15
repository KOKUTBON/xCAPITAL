# xCAPITAL web

Angular 21 standalone application that hosts both the xCAPITAL product site and the interactive workspace demo.

- **Live demo:** https://kokutbon.github.io/xCAPITAL/
- **Data:** fully simulated and deterministic (`src/app/core/demo-data.ts`). The demo makes no network calls.

## Structure

```
src/
├── app/
│   ├── core/         # domain models, seeded demo data, signal store, market feed, formatting
│   ├── shared/       # icons, UI primitives, SVG chart engine
│   ├── layout/       # workspace shell, navigation, command palette
│   └── pages/        # landing + 12 workspace modules (lazy-loaded)
├── assets/brand/     # xCAPITAL mark, logos, social card
└── styles.css        # design tokens and global primitives
```

Modules:

- **Treasury:** Command Center, Liquidity, Cash Forecast, Ledger & Reconciliation
- **Control:** Approvals, Guard, Audit Trail
- **Capital:** Portfolio, Markets, Risk
- **Intelligence:** Copilot
- **Platform:** Connect

## Commands

Install dependencies:

```bash
npm ci
```

Start the dev server at http://localhost:4200:

```bash
npm start
```

Create a production build in `dist/web/browser`:

```bash
npm run build
```

Build for GitHub Pages (base href `/xCAPITAL/`):

```bash
npm run build:pages
```

The `deploy-pages.yml` workflow builds and publishes the demo on every push to `main` that touches `frontend/web`.

See [docs/WEB_DESIGN_SYSTEM.md](../docs/WEB_DESIGN_SYSTEM.md) for tokens, components and conventions.
