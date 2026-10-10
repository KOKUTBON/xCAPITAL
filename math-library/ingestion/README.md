# Continuous Ingestion

This folder powers the automated, scheduled ingestion of new research metadata.

## What it does
- Pulls **recent arXiv** papers relevant to:
  - q-fin portfolio management (q-fin.PM)
  - risk management (q-fin.RM)
  - ML / forecasting / optimization (stat.ML, cs.LG)
- Stores paper metadata JSON in:
  - `math-library/papers/inbox/`
- Runs daily via GitHub Actions (workflow: `continuous-ingestion.yml`)
- Opens a PR with changes for you to review/merge.

## Why a PR (instead of auto-merge)?
Because “everything ever published” requires **human or policy-driven curation**:
- tagging (portfolio optimization vs tails vs microstructure)
- extracting actual formulas
- implementing and testing

## Curating a paper into a formula
1) Pick an inbox entry:
   - `math-library/papers/inbox/<arxiv_id>.json`
2) Generate stubs + registry entry:
   - `python math-library/tools/generate_formula_stub.py math-library/papers/inbox/<file>.json`
3) Implement in:
   - Java plugin (backend) OR Python plugin (AI service)
4) Add tests and mark status `implemented` in `formulas.json`
