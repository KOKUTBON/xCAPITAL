# Engineering Reference Foundations

This project documentation set is aligned to the technical foundations from your attached library. The table maps source material to concrete platform decisions.

## Decision Mapping

| Source Manual | Applied Areas in This Repo | Resulting Docs |
| --- | --- | --- |
| `OReilly.Fundamentals.of.Software.Architecture.2020.1.pdf` | architecture characteristics, tradeoff analysis, modular decomposition | `docs/ARCHITECTURE.md`, `docs/HLD.md`, `docs/SOFTWARE_DESIGN.md` |
| `Designing Data-Intensive Applications ... .pdf` | data ownership, consistency models, event-driven integration | `docs/DATABASE_STRATEGY.md`, `docs/DISTRIBUTED_SYSTEM_DESIGN.md` |
| `API Desing Patterns.pdf` | resource design, versioning, idempotency, API lifecycle | `docs/API_DESIGN_SECURITY.md`, `docs/SOFTWARE_REQUIREMENTS.md` |
| `API Security in Action.pdf` | token/federation hardening, threat modeling, defense-in-depth controls | `docs/API_DESIGN_SECURITY.md`, `docs/SCALABILITY_PERFORMANCE.md` |
| `Site Reliability Engineering.pdf` | SLOs, error budgets, incident management, operational maturity | `docs/SOFTWARE_QUALITY_METRICS.md`, `docs/OPERATIONS_MAINTENANCE.md` |
| `Art of Unit Testing.pdf` | test pyramid, isolation strategy, regression controls | `docs/TEST_STRATEGY.md` |
| `Building.Microservices.pdf` | service boundaries, integration patterns, deployment flow | `docs/PROJECT_STRUCTURE.md`, `docs/DISTRIBUTED_SYSTEM_DESIGN.md` |
| `Designing Machine Learning Systems.pdf` | ML lifecycle, deployment safety, monitoring, retraining | `docs/AI_ML_DS_STRATEGY.md` |
| `mathematics-for-machine-learning-...pdf` | model assumptions, probability/statistics grounding | `docs/AI_ML_DS_STRATEGY.md` |
| `SuttonBartoIPRLBook2ndEd.pdf` | policy-learning baseline framing and evaluation discipline | `docs/AI_ML_DS_STRATEGY.md` |
| `SEv3.pdf` | software engineering lifecycle and quality discipline | `docs/SOFTWARE_REQUIREMENTS.md`, `docs/SCM.md`, `docs/SOFTWARE_QUALITY_METRICS.md` |
| `Clean Code.pdf` and `Effective Java (2017, Addison-Wesley).pdf` | maintainability conventions and code-level quality expectations | `docs/SOFTWARE_DESIGN.md`, `docs/OPERATIONS_MAINTENANCE.md` |

## Application Notes
- The docs intentionally separate **current implementation** and **target enterprise architecture**.
- Financial consistency rules are biased toward correctness over write throughput.
- Cross-domain extensibility is designed around event contracts and independent schema evolution.
