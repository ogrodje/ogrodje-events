#!/usr/bin/env bash
set -ex
DOCKER_IMAGE=${DOCKER_IMAGE:-"ghcr.io/ogrodje/ogrodje-events:latest"}
export DOCKER_COMPOSE_FLAGS=${DOCKER_COMPOSE_FLAGS:="-f docker-compose.dev.yml"}

./bin/og-wrap.sh $@
