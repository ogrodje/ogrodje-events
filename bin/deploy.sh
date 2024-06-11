#!/usr/bin/env bash
set -e
DOCKER_IMAGE=${DOCKER_IMAGE:-"ghcr.io/ogrodje/ogrodje-events:latest"}
DOCKER_HOST="ssh://low"

docker rm -f ogrodje-events && \
docker pull ${DOCKER_IMAGE} && \
  docker run -d \
    --name ogrodje-events \
    -e HYGRAPH_ENDPOINT=${HYGRAPH_ENDPOINT} \
    -e DATABASE_URL=jdbc:sqlite:/tmp/ogrodje_events.db \
    -e SYNC_DELAY="1 hour" \
    -p 0.0.0.0:3000:7006 \
    ghcr.io/ogrodje/ogrodje-events:latest
