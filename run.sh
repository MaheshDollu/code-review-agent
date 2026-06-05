#!/bin/bash

# ─────────────────────────────────────────────────────────────
#  Code Review Agent — startup script
#  Usage: ./run.sh
# ─────────────────────────────────────────────────────────────

set -e

# Check required env vars
if [ -z "$GROQ_API_KEY" ]; then
  echo "❌  GROQ_API_KEY is not set."
  echo "    Get a free key at https://console.groq.com"
  echo "    Then: export GROQ_API_KEY=gsk_..."
  exit 1
fi

if [ -z "$GITHUB_TOKEN" ]; then
  echo "❌  GITHUB_TOKEN is not set."
  echo "    Create one at https://github.com/settings/tokens"
  echo "    Needs: repo (read) + pull_requests (write) scopes"
  echo "    Then: export GITHUB_TOKEN=ghp_..."
  exit 1
fi

echo "✅  GROQ_API_KEY set"
echo "✅  GITHUB_TOKEN set"
echo ""
echo "🚀  Building and starting Code Review Agent..."
echo ""

mvn spring-boot:run -q

