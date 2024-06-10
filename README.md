# Ogrodje Events

This is a tool and a service for collecting internal and external events we are passionate about.

## Development

```bash
sbt run
```

## Docker

```bash
docker run --rm
  -e HYGRAPH_ENDPOINT=${HYGRAPH_ENDPOINT} \
  -e DATBASE_URL="jdbc:sqlite:/tmp/ogrodje_events_pom.db" \
  -e SYNC_DELAY="1 hour" \
  -p 3000:7006 \
  ghcr.io/ogrodje/ogrodje-events
```

\- [Oto Brglez](https://github.com/otobrglez)
