# Issue Triage Workflow

## Purpose
Create predictable intake quality and routing for all new issues and pull requests.

## Required Intake Data
New issues must include:
1. `Type` (bug, feature, or engineering task template)
2. `Area` (`backend`, `frontend`, `mobile`, `ai`, `docs`, `devops`)
3. `Priority` (`P1`, `P2`, `P3`)
4. `Suggested owner` (team/person role)
5. `Acceptance criteria` or reproducible steps

This data is collected via `.github/ISSUE_TEMPLATE/*.yml`.

## Label Taxonomy
Use these namespaces consistently:
1. `type:*` (example: `type:feature`, `type:test`, `type:docs`, `type:security`)
2. `area:*` (example: `area:backend`, `area:mobile`, `area:docs`, `area:devops`)
3. `priority:*` (`priority:P1`, `priority:P2`, `priority:P3`)

## Triage States

| State | Entry Criteria | Exit Criteria | Owner |
| --- | --- | --- | --- |
| `NEW` | Issue created from template | Type/area/priority reviewed | Triage maintainer |
| `CLASSIFIED` | Labels + owner + milestone assigned | Scope/acceptance agreed | Area owner |
| `READY` | Scope is implementation-ready | Work started | Assignee |
| `IN_PROGRESS` | Assignee actively delivering | PR open and linked | Assignee |
| `IN_REVIEW` | PR open with validation evidence | PR merged or changes requested | Reviewer(s) |
| `DONE` | Merged to `main` | N/A | Assignee |
| `BLOCKED` | External dependency prevents progress | Blocker resolved | Assignee + triage maintainer |

## SLA Targets
1. `P1`: triage within 1 business day.
2. `P2`: triage within 3 business days.
3. `P3`: triage within 5 business days.

## Milestone Assignment Rules
1. `P1` items must be assigned to the active milestone or explicit hotfix milestone.
2. Security/tenant/isolation regressions must be assigned before any new feature intake.
3. `P2/P3` features/tasks go to the next planned milestone unless marked as dependencies for active milestone work.

## Ownership Rules
1. Primary owner is determined by `area`.
2. Cross-area issues must name one primary owner and at least one secondary reviewer area.
3. If ownership is unclear, triage maintainer assigns temporary ownership within SLA and requests clarification.

## Definition of Triage Complete
An issue is triage-complete when all are true:
1. Type, area, and priority are explicit.
2. Owner is assigned.
3. Milestone is assigned or intentionally deferred.
4. Acceptance criteria are testable.
5. Dependencies/blockers are listed.
