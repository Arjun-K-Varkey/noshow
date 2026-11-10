#!/usr/bin/env bash
#
# NoShow — GitHub setup for spec-driven, Agile (milestone = sprint) development.
#
# Run this ONCE from the project root, on your own machine, with the GitHub CLI
# authenticated (`gh auth login`) and the empty repo already created.
#
#   cd noshow
#   bash scripts/setup-github.sh
#
# It will: push the Milestone 1 code + specs, create the M2 milestone and its
# six story issues (linked to specs/M2-late-detection-evaluator.md), and create
# a GitHub Project board and add the issues to it.

set -euo pipefail

OWNER="Arjun-K-Varkey"
REPO="noshow"
SLUG="$OWNER/$REPO"
SPEC="specs/M2-late-detection-evaluator.md"
PROJECT_TITLE="NoShow"

echo "==> Verifying gh auth"
gh auth status >/dev/null

# --- 1. Initial commit + push ------------------------------------------------
if [ ! -d .git ]; then
  echo "==> git init + initial commit"
  git init -b main
  git add .
  git commit -m "Milestone 1: walking skeleton + M1/M2 specs

Create checks, per-check ping URLs, success ping marks a check UP,
Thymeleaf dashboard, Postgres + Flyway, Testcontainers test.
Specs committed under specs/ as the source of truth."
  git remote add origin "https://github.com/$SLUG.git" 2>/dev/null || true
  git push -u origin main
else
  echo "==> git repo already initialised; skipping init (push manually if needed)"
fi

# --- 2. Label + M2 milestone -------------------------------------------------
echo "==> Ensuring label 'sprint:M2'"
gh label create "sprint:M2" --repo "$SLUG" --color 1D76DB \
  --description "Milestone 2 — late-detection evaluator" 2>/dev/null || true

echo "==> Creating milestone 'M2 — Late-detection evaluator'"
gh api "repos/$SLUG/milestones" -f title="M2 — Late-detection evaluator" \
  -f state="open" \
  -f description="The dead man's switch: UP -> LATE -> DOWN state machine via a background evaluator. Spec: $SPEC" \
  >/dev/null 2>&1 || echo "    (milestone may already exist — continuing)"

# --- 3. Story issues ---------------------------------------------------------
SPEC_LINK="See spec: \`$SPEC\`."
create_issue () {
  local title="$1"; local body="$2"
  echo "==> Issue: $title"
  gh issue create --repo "$SLUG" \
    --title "$title" \
    --body "$body"$'\n\n'"$SPEC_LINK" \
    --milestone "M2 — Late-detection evaluator" \
    --label "sprint:M2"
}

create_issue "M2.1 Clock bean + read time from it" \
"Introduce a \`java.time.Clock\` bean and refactor services to read 'now' from it instead of \`Instant.now()\`, so the evaluator's time can be driven deterministically in tests.

Acceptance: no \`Instant.now()\` in services; tests can inject a fixed/adjustable Clock."

create_issue "M2.2 V2 migration: check_event + deadline math" \
"Add Flyway V2: \`check_event(id, check_id, from_status, to_status, at, reason)\`. Split schedule math into \`expected_at = last_success + period\` and \`deadline = expected_at + grace\` (stored or derived).

Acceptance: migration applies cleanly; existing checks unaffected; model exposes expected_at and deadline."

create_issue "M2.3 Evaluator sweep: UP -> LATE -> DOWN" \
"Add a @Scheduled evaluator (configurable interval, default 30s) that transitions due checks: UP->LATE past expected_at within grace, LATE->DOWN past deadline. Skip PAUSED.

Acceptance: a check with a passed deadline becomes DOWN within one sweep; a check within grace shows LATE."

create_issue "M2.4 Recovery: late/down -> up on success ping" \
"On a success ping, return a LATE/DOWN check to UP and recompute expected_at/deadline. Record every transition in check_event.

Acceptance: down->up on ping with deadline recomputed; each change writes one check_event row (no duplicates on repeated sweeps)."

create_issue "M2.5 Dashboard: status + last transition + history" \
"Show current status and the time of the last transition on the dashboard; expose per-check event history.

Acceptance: LATE/DOWN render distinctly; last transition time shown; history lists check_event rows newest first."

create_issue "M2.6 State-machine tests (controlled Clock)" \
"Testcontainers tests covering: up->late->down as the clock advances; down->up on ping; grace=0 goes straight up->down; paused untouched; check_event matches transitions exactly.

Acceptance: all cases pass; no duplicate events on repeated sweeps."

# --- 4. Project board --------------------------------------------------------
echo "==> Creating Project board '$PROJECT_TITLE'"
PROJECT_NUMBER="$(gh project create --owner "$OWNER" --title "$PROJECT_TITLE" --format json 2>/dev/null | \
  python3 -c 'import sys,json; print(json.load(sys.stdin)["number"])' 2>/dev/null || true)"

if [ -n "${PROJECT_NUMBER:-}" ]; then
  echo "    Project #$PROJECT_NUMBER created. Adding M2 issues..."
  for n in $(gh issue list --repo "$SLUG" --label "sprint:M2" --state open --json number --jq '.[].number'); do
    gh project item-add "$PROJECT_NUMBER" --owner "$OWNER" \
      --url "https://github.com/$SLUG/issues/$n" >/dev/null && echo "    + #$n"
  done
  echo "    Board ready. Rename the default Status columns to"
  echo "    Backlog / In progress / In review / Done in the Project UI."
else
  echo "    Could not auto-create the Project (needs the 'project' gh scope:"
  echo "    run 'gh auth refresh -s project'). Create it in the UI and add the"
  echo "    sprint:M2 issues, or re-run after refreshing the scope."
fi

echo
echo "==> Done. Milestone M2, six stories, and the board are set up."
echo "    Next: implement M2.1 against the spec, open a PR that Closes the issue."
