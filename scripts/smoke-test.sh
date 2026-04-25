#!/usr/bin/env bash
# Smoke-test the built jar by sending 4 JSON-RPC messages and printing the replies.
# Usage:  ./scripts/smoke-test.sh [path/to/mcp-server-java-0.1.0.jar]
# Run this after: mvn -DskipTests package

set -uo pipefail   # no -e: we want to print the log even on failure
JAR="${1:-target/mcp-server-java-0.1.0.jar}"
LOG=/tmp/mcp-smoke.log

if [[ ! -f "$JAR" ]]; then
  echo "Jar not found: $JAR" >&2
  echo "Build first:  mvn -DskipTests package" >&2
  exit 1
fi

echo "--- java version ---"
java -version
echo

echo "--- sending initialize / tools/list / echo / get_time / search_taiwan_news ---"
{
  echo '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"smoke","version":"0"}}}'
  echo '{"jsonrpc":"2.0","method":"notifications/initialized"}'
  echo '{"jsonrpc":"2.0","id":2,"method":"tools/list"}'
  echo '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"echo","arguments":{"text":"hello from smoke"}}}'
  echo '{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"get_time","arguments":{"zone":"UTC"}}}'
  echo '{"jsonrpc":"2.0","id":5,"method":"tools/call","params":{"name":"search_taiwan_news","arguments":{"keyword":"台北","limit":3}}}'
} | java -jar "$JAR" 2>"$LOG"
JAVA_EXIT=$?

echo
echo "--- java exit code: $JAVA_EXIT ---"
echo
echo "--- stderr log ($LOG) ---"
cat "$LOG" 2>/dev/null || echo "(no log file)"
