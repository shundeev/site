#!/usr/bin/env bash
# Managed by dev-kit (`.shared/bin/kit init`). Do not edit; change managed/pretooluse.sh in dev-kit.
#
# The project's PreToolUse(Bash) entry point. It lives in the project, not in .shared/, so it
# exists even when the submodule is empty. Settings run it with `onFailure: "block"`: if this
# script cannot start or crashes, the command is blocked, not allowed (ADR-0001, rule 3).
#
# - dev-kit present: the safety guard first (.shared/claude/hooks/safety-net.sh: destructive commands and secret
#   reads, #20), then the delivery guard for Bash and the design approval gate for edits. Read, Grep and Glob
#   go to the safety guard only.
# - dev-kit missing: allow only the commands that repair it, refuse everything else with
#   exit 2 and a message naming the repair. The owner can still run anything with `!`.
#
# Known gaps: the repair check matches the start of the raw command text; it does not parse
# shell. That is enough to keep the repair path open; it is not a security boundary.
set -uo pipefail

input="$(cat)"
root="${CLAUDE_PROJECT_DIR:-.}"
guard="$root/.shared/claude/hooks/delivery-guard.sh"

# Bash goes to the delivery guard; Edit/Write/NotebookEdit to the design approval gate (#26).
tool="$(printf '%s' "$input" | sed -n 's/.*"tool_name"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' | head -n1)"
if [ -r "$guard" ]; then
  out="$(bash "$root/.shared/claude/hooks/safety-net.sh" <<<"$input")"; rc=$?
  if [ "$rc" -eq 0 ] && [ -z "$out" ]; then
    case "$tool" in
      Read|Grep|Glob) ;;
      Edit|Write|NotebookEdit|MultiEdit) out="$(bb "$root/.shared/bin/kit" gate --hook <<<"$input")"; rc=$? ;;
      *) out="$(bash "$guard" <<<"$input")"; rc=$? ;;
    esac
  fi
  [ -n "$out" ] && printf '%s\n' "$out"
  # A refusal is friction: the recorder keeps it with its call (#55). It never changes the verdict.
  case "$out" in
    *'"permissionDecision":"deny"'*)
      rec="$root/.shared/claude/hooks/friction.sh"
      [ -r "$rec" ] && jq -c --argjson o "$out" '. + {reason: $o.hookSpecificOutput.permissionDecisionReason}' \
        <<<"$input" 2>/dev/null | bash "$rec" HookDenied
      ;;
  esac
  exit "$rc"
fi

# Extract tool_input.command without jq, which may be what is missing.
cmd="$(printf '%s' "$input" | sed -n 's/.*"command"[[:space:]]*:[[:space:]]*"\(\([^"\\]\|\\.\)*\)".*/\1/p' | head -n1)"

case "$cmd" in
  "git submodule update"*|"git submodule init"*|"git submodule status"*|"git status"*| \
  ".shared/bin/kit init"*|"bb .shared/bin/kit init"*|".shared/bin/kit doctor"*|"bb .shared/bin/kit doctor"*)
    exit 0 ;;
esac

echo "Refused: dev-kit is not checked out (.shared/ is empty), so its guards cannot run and only the repair commands are allowed, reads of files included. Run: git submodule update --init && .shared/bin/kit init" >&2
exit 2
