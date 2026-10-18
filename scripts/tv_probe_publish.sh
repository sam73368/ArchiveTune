#!/usr/bin/env bash
set +e
OUT="${1:-probe-out}"
[ -n "$GH_TOKEN" ] || exit 0
rm -rf /tmp/probe-pub && mkdir -p /tmp/probe-pub && cp -r "$OUT"/. /tmp/probe-pub/ 2>/dev/null
cd /tmp/probe-pub || exit 0
git init -q
git add -A
git -c user.name=tv-probe -c user.email=tv-probe@users.noreply.github.com commit -qm "probe ${GITHUB_SHA}" >/dev/null
git push -q -f "https://x-access-token:${GH_TOKEN}@github.com/${GITHUB_REPOSITORY}.git" HEAD:refs/heads/tv-probe-results
