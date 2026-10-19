#!/usr/bin/env bash
set +e
OUT="${1:-probe-out}"
[ -n "$GH_TOKEN" ] || exit 0
rm -rf /tmp/crawl-pub && mkdir -p /tmp/crawl-pub && cp -r "$OUT"/. /tmp/crawl-pub/ 2>/dev/null
cd /tmp/crawl-pub || exit 0
git init -q
git add -A
git -c user.name=tv-crawl -c user.email=tv-crawl@users.noreply.github.com commit -qm "crawl ${GITHUB_SHA}" >/dev/null
git push -q -f "https://x-access-token:${GH_TOKEN}@github.com/${GITHUB_REPOSITORY}.git" HEAD:refs/heads/tv-crawl-results
