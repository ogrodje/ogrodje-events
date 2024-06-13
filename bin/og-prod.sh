#!/usr/bin/env bash
set -ex
DOCKER_IMAGE=${DOCKER_IMAGE:-"ghcr.io/ogrodje/ogrodje-events:latest"}
export DOCKER_HOST="ssh://low"
export DOCKER_COMPOSE_FLAGS=${DOCKER_COMPOSE_FLAGS:="-f docker-compose.prod.yml"}

./bin/og-dev.sh $@
