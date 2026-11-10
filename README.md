# NoShow

Cron & background-job monitoring for Spring Boot — know when a scheduled job *doesn't* run.

NoShow uses the **dead man's switch** (heartbeat) pattern: each monitored job
gets a unique ping URL and is expected to call it on a schedule. When a
check-in doesn't arrive in time, NoShow raises the alarm. The absence of a
signal is the alert.

This repository is **Milestone 1 — the walking skeleton**. You can create a
check, get its ping URL, send a ping, and see status on a dashboard. Alerting
(email, Slack, webhook), the late-detection evaluator, multi-instance safety
and the Spring Boot starter come in later milestones — see the build brief.

## Requirements

- Java 21+
- Docker (for the local Postgres) and Docker Compose
- Gradle (or let your IDE import the Gradle project; this repo has no committed
  wrapper — run `gradle wrapper` once to generate `./gradlew` if you want it)

## Run it

1. Start Postgres:

   ```bash
   docker compose up -d
   ```

2. Run the app:

   ```bash
   gradle bootRun
   ```

   Flyway creates the schema on first start. The app listens on
   `http://localhost:8080`.

3. Open the dashboard at <http://localhost:8080>, create a check, and copy its
   ping URL.

4. Send a ping (what a cron job would do as its last step):

   ```bash
   curl http://localhost:8080/ping/<your-token>        # success
   curl http://localhost:8080/ping/<your-token>/start  # optional: job started
   curl http://localhost:8080/ping/<your-token>/fail   # optional: job failed
   ```

   Both GET and POST work, so a plain `curl` in a crontab is enough:

   ```cron
   0 2 * * * /opt/backup.sh && curl -fsS http://localhost:8080/ping/<your-token>
   ```

   Refresh the dashboard — the check flips to **UP** with its last ping time.

## Run the tests

Tests use Testcontainers, which starts a throwaway Postgres in Docker — no
local database needed, but Docker must be running:

```bash
gradle test
```

## Configuration

Overridable via environment variables (see `src/main/resources/application.yml`):

| Variable      | Default                                       |
|---------------|-----------------------------------------------|
| `DB_URL`      | `jdbc:postgresql://localhost:5432/noshow`     |
| `DB_USER`     | `noshow`                                      |
| `DB_PASSWORD` | `noshow`                                      |
| `PORT`        | `8080`                                        |

## Project layout

```
src/main/java/io/noshow/
  domain/   Check, PingEvent, CheckStatus, PingKind
  repo/     Spring Data JPA repositories
  service/  CheckService (create/list), PingService (ingest)
  web/      CheckController (dashboard), PingController (ping endpoint)
src/main/resources/
  db/migration/V1__init.sql   Flyway schema
  templates/checks.html       Thymeleaf dashboard
```

## What's next (from the build brief)

- **M2** — late-detection evaluator, grace periods, up → down → up state machine
- **M3** — email alerts + recovery (outbox pattern)
- **M4** — Slack & webhook channels
- **M5** — multi-instance safety (database locking)
- **M6** — Spring Boot starter: monitor a `@Scheduled` method with one annotation
- **M7** — timezone/DST hardening and polish
