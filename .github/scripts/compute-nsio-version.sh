#!/usr/bin/env bash
set -euo pipefail

STATE_FILE=".versioning/nsio-version-state.json"
SOURCE_SHA="${GITHUB_SHA:-$(git rev-parse HEAD)}"

if [[ ! -f "$STATE_FILE" ]]; then
  echo "Missing version state: $STATE_FILE" >&2
  exit 1
fi
if [[ "$(git rev-parse --is-shallow-repository)" == "true" ]]; then
  echo "Full Git history is required to calculate the NSiO commit ordinal." >&2
  exit 1
fi
if ! git cat-file -e "$SOURCE_SHA^{commit}"; then
  echo "Source commit $SOURCE_SHA is not available locally." >&2
  exit 1
fi

COMMIT_NUMBER="${NSIO_BUILD_NUMBER:-$(git rev-list --count "$SOURCE_SHA")}"
if [[ "$COMMIT_NUMBER" -lt 1 || "$COMMIT_NUMBER" -gt 99999 ]]; then
  echo "Commit ordinal is outside the documented versionCode encoding range: $COMMIT_NUMBER" >&2
  exit 1
fi

python3 - "$STATE_FILE" "$COMMIT_NUMBER" "$SOURCE_SHA" <<'PY'
import datetime as dt
import json
import os
import sys
from zoneinfo import ZoneInfo

state_path, commit_text, source_sha = sys.argv[1:]
with open(state_path, encoding="utf-8") as handle:
    state = json.load(handle)

base_major = int(state["base_major"])
baseline = dt.date.fromisoformat(state["baseline_month"] + "-01")
overhaul_count = int(state.get("major_overhaul_count", 0))
if base_major < 1 or overhaul_count < 0:
    raise SystemExit("Invalid MAJOR version state.")

today = dt.datetime.now(ZoneInfo("Asia/Jakarta")).date()
month_delta = (today.year - baseline.year) * 12 + today.month - baseline.month
if month_delta < 0:
    raise SystemExit("Current Asia/Jakarta month is earlier than the stored version baseline.")

major = base_major + month_delta + overhaul_count
day = today.day
day_of_year = today.timetuple().tm_yday
commit = int(commit_text)

# Keep published release versions monotonic even when the main-branch commit
# ordinal is lower than a release previously published from another branch.
# Re-runs of the exact same source commit reuse its existing release version.
import subprocess
tags = subprocess.run(
    ["git", "tag", "--list", "v.*"],
    check=True, capture_output=True, text=True
).stdout.splitlines()

def parse_tag(tag):
    value = tag.strip()
    if value.startswith(("v.", "V.")):
        value = value[2:]
    elif value.startswith(("v", "V")):
        value = value[1:]
    parts = value.split(".")
    if len(parts) != 3 or not all(part.isdigit() for part in parts):
        return None
    return tuple(map(int, parts))

source_tags = subprocess.run(
    ["git", "tag", "--points-at", source_sha],
    check=True, capture_output=True, text=True
).stdout.splitlines()
source_versions = [parse_tag(tag) for tag in source_tags]
source_versions = [v for v in source_versions if v and v[0] == major and v[1] == day]
if source_versions:
    commit = max(source_versions)[2]
else:
    existing_ordinals = [
        parsed[2] for tag in tags
        if (parsed := parse_tag(tag)) is not None
        and parsed[0] == major and parsed[1] == day
    ]
    commit = max(commit, max(existing_ordinals, default=0) + 1)

# Collision-safe monotonic Android encoding:
# MAJOR block (50M) + day-of-year block (100K) + repository commit ordinal.
version_code = major * 50_000_000 + day_of_year * 100_000 + commit
if version_code > 2_100_000_000:
    raise SystemExit("Computed Android versionCode exceeds the safe Play/Android integer limit.")

version_name = f"{major}.{day}.{commit}"
tag = f"v.{version_name}"
values = {
    "NSIO_VERSION_NAME": version_name,
    "NSIO_VERSION_TAG": tag,
    "NSIO_VERSION_CODE": str(version_code),
    "NSIO_MAJOR": str(major),
    "NSIO_DAY": str(day),
    "NSIO_DAY_OF_YEAR": str(day_of_year),
    "NSIO_COMMIT_NUMBER": str(commit),
    "NSIO_MONTH_TRANSITIONS": str(month_delta),
    "NSIO_MAJOR_OVERHAUL_COUNT": str(overhaul_count),
    "NSIO_SOURCE_SHA": source_sha,
}
with open("nsio-version.env", "w", encoding="utf-8") as handle:
    for key, value in values.items():
        handle.write(f"{key}={value}\n")
github_env = os.environ.get("GITHUB_ENV")
if github_env:
    with open(github_env, "a", encoding="utf-8") as handle:
        for key, value in values.items():
            handle.write(f"{key}={value}\n")
for key, value in values.items():
    print(f"{key}={value}")
PY
