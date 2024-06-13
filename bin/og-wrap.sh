#!/usr/bin/env bash
set -ex

OG_DEV_HOME=${OG_DEV_HOME:=$(pwd)}
DOCKER_COMPOSE_FLAGS=${DOCKER_COMPOSE_FLAGS:=""}

if [[ -z "${OG_DEV_HOME}" ]]; then
  echo "OG_DEV_HOME environment variable is not set!" && exit 255
fi

# shellcheck disable=SC2068
cd "$OG_DEV_HOME" &&
  docker compose \
    -f docker-compose.yml ${DOCKER_COMPOSE_FLAGS} \
    --project-name og-events \
    --project-directory . $@
