#!/usr/bin/env bash

set -euo pipefail

CSV_FILE="${1:-docs/github-roadmap-import.csv}"
REPO="${2:-}"
RESOLVED_REPO=""

if ! command -v gh >/dev/null 2>&1; then
  echo "GitHub CLI 'gh' is required."
  exit 1
fi

if [[ ! -f "$CSV_FILE" ]]; then
  echo "CSV file not found: $CSV_FILE"
  exit 1
fi

gh_repo_args() {
  if [[ -n "$REPO" ]]; then
    printf '%s\n' "--repo" "$REPO"
  fi
}

resolve_repo() {
  if [[ -n "$RESOLVED_REPO" ]]; then
    printf '%s\n' "$RESOLVED_REPO"
    return 0
  fi

  if [[ -n "$REPO" ]]; then
    RESOLVED_REPO="$REPO"
    printf '%s\n' "$RESOLVED_REPO"
    return 0
  fi

  RESOLVED_REPO="$(gh repo view --json nameWithOwner --jq '.nameWithOwner')"
  printf '%s\n' "$RESOLVED_REPO"
}

ensure_label() {
  local label="$1"

  if [[ -z "$label" ]]; then
    return 0
  fi

  if gh label list $(gh_repo_args) --limit 200 --search "$label" 2>/dev/null | awk '{print $1}' | grep -Fxq "$label"; then
    return 0
  fi

  gh label create "$label" $(gh_repo_args) --color BFDADC --description "Imported by roadmap script" >/dev/null
}

ensure_milestone() {
  local milestone="$1"
  local repo_name
  repo_name="$(resolve_repo)"

  if [[ -z "$milestone" ]]; then
    return 0
  fi

  if gh api "repos/$repo_name/milestones?state=all&per_page=100" --jq '.[].title' 2>/dev/null | grep -Fxq "$milestone"; then
    return 0
  fi

  gh api \
    --method POST \
    "repos/$repo_name/milestones" \
    -f title="$milestone" >/dev/null
}

create_issue() {
  local ticket_id="$1"
  local title="$2"
  local labels="$3"
  local milestone="$4"
  local phase="$5"
  local priority="$6"
  local target_date="$7"
  local release="$8"

  local body
  body=$(
    printf '%s\n\n%s\n%s\n%s\n%s\n%s\n' \
      "Imported from roadmap." \
      "Ticket ID: $ticket_id" \
      "Phase: $phase" \
      "Priority: $priority" \
      "Target Date: $target_date" \
      "Release: $release"
  )

  local -a cmd
  cmd=(gh issue create --title "[$ticket_id] $title" --body "$body")

  if [[ -n "$REPO" ]]; then
    cmd+=(--repo "$REPO")
  fi

  if [[ -n "$milestone" ]]; then
    ensure_milestone "$milestone"
    cmd+=(--milestone "$milestone")
  fi

  IFS=';' read -r -a label_array <<< "$labels"
  for label in "${label_array[@]}"; do
    if [[ -n "$label" ]]; then
      ensure_label "$label"
      cmd+=(--label "$label")
    fi
  done

  "${cmd[@]}"
}

tail -n +2 "$CSV_FILE" | while IFS=, read -r ticket_id title labels milestone phase priority target_date release; do
  create_issue "$ticket_id" "$title" "$labels" "$milestone" "$phase" "$priority" "$target_date" "$release"
done

echo "Roadmap import complete."
