## graphify

## Project guidance

Read `CLAUDE.md` as the detailed source of truth for this repository. The key rules are:

- This is a Kotlin/JVM (Java 17) multi-module Gradle application. Build with `./gradlew build`; run locally with `./gradlew :app:run` from the repository root. Never run the fat jar locally.
- There are no repository tests, linter, or CI; verification is compilation and a local run where configuration permits. Use the version in `gradle.properties` and put French release notes in `doc/changelog.txt`.
- Local runtime configuration belongs in the gitignored `modules/common/src/main/resources/config.properties`. Copy the dev template to that exact filename and verify `db.name=fg_dev` before running. Never expose credentials, database dumps, member data, or config files in commits, documentation, fixtures, or logs.
- `fg_dev` is not anonymised and shares a server with `fg_prod`; use synthetic Discord IDs for seeds and tests. The OGS league is shared by dev and production, so do not trigger league actions casually from a local run.
- Preserve the module architecture: `XModule` owns startup, `XService` owns scheduled work, and `db/XDatabaseAccessor` owns SQL. Use `DATE_ZONE` (`Europe/Paris`) for time and the project logger for logging.
- Code and comments are English; user-facing Discord and website text is French. Dependencies go through `libs.versions.toml`.
- Do not reintroduce the removed FOX, IGS, FFG, or EGF platform abstractions. Check the relevant `doc/` plans and API contracts before changing league, house, or backend routes.

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

When the user types `/graphify`, use the installed graphify skill or instructions before doing anything else.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- Dirty graphify-out/ files are expected after hooks or incremental updates; dirty graph files are not a reason to skip graphify. Only skip graphify if the task is about stale or incorrect graph output, or the user explicitly says not to use it.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
