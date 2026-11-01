#!/usr/bin/env bash

set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
credentials_path="${GROOVSTA_FIREBASE_CREDENTIALS_PATH:-${HOME}/.config/groovsta/firebase-service-account.json}"
root_env_path="${repository_root}/.env"

if [[ ! -r "${credentials_path}" ]]; then
  echo "Firebase service-account file is missing or unreadable: ${credentials_path}" >&2
  exit 1
fi

if [[ ! -r "${root_env_path}" ]]; then
  echo "Root environment file is missing or unreadable: ${root_env_path}" >&2
  exit 1
fi

export GROOVSTA_PUSH_ENABLED="${GROOVSTA_PUSH_ENABLED:-true}"
export GROOVSTA_FIREBASE_ENABLED="${GROOVSTA_FIREBASE_ENABLED:-true}"
export GROOVSTA_GOOGLE_AUTH_ENABLED="${GROOVSTA_GOOGLE_AUTH_ENABLED:-true}"
export GROOVSTA_FIREBASE_PROJECT_ID="${GROOVSTA_FIREBASE_PROJECT_ID:-groovsta-dev}"
export GROOVSTA_FIREBASE_CREDENTIALS_PATH="${credentials_path}"

cd "${repository_root}"
docker compose --env-file "${root_env_path}" -f turn-server/compose.yaml up -d
mvn -pl api-app -am -DskipTests clean install
exec mvn -pl api-app spring-boot:run
