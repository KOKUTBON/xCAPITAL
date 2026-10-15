# xCAPITAL Math Library (Living Library)

You requested **“everything ever published.”** In practice, that means the system must be a **living, extensible library**
that can continuously add new formulas and models.

This repository implements that approach:

- A **Formula Registry** (machine-readable) describing formulas, assumptions, citations, parameters, and implementations.
- A **Plugin Interface** for adding new methods without rewriting the backend.
- A **Paper Metadata Store** (citations + tags) without bundling copyrighted PDFs.

> Note: It is not feasible to ship a static codebase containing *all mathematics ever published*.
> The correct enterprise solution is a **continuous ingestion + modular plugin architecture**.

## What this folder contains
- `registry/formulas.json` — the canonical registry (IDs, categories, parameters, references)
- `plugins/java/` — Java plugin interface and example stubs (backend side)
- `plugins/python/` — Python plugin interface and example stubs (AI / research side)
- `papers/` — metadata entries for papers/books (BibTeX/JSON), not the full texts
- `tools/` — scripts to validate the registry and generate docs

## Workflow
1) Add a formula entry to `registry/formulas.json`
2) Implement the formula in Java or Python plugin
3) Run validation: `python tools/validate_registry.py`
4) Backend loads registry for discovery endpoints
