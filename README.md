# RevPrac

RevPrac is a Minecraft practice core plugin project for Modern Paper 1.21.11, created under the MIT License by XreatLabz.

The long-term goal is a StrikePractice-like practice foundation for duels, kits, arenas, queues, match lifecycle, player stats, and operator-friendly configuration. The current implementation includes the core bootstrap, player-session safety, arena and kit setup, direct duels, queue matchmaking, SQLite-backed player profiles and ratings, completed match history, per-kit stats, ranked rating progression, and a self-facing `/stats` command.

## Status

- Repository bootstrap: complete
- Target platform: Paper/Minecraft 1.21.11
- Build system: Gradle 9.5.0 with Kotlin DSL and Java 21 toolchain
- Plugin base: API-only `JavaPlugin` entrypoint with `plugin.yml`
- Implemented gameplay: arena/kit setup, direct duels, ranked and unranked queues, and match settlement
- Implemented persistence: SQLite/Flyway player profiles, ratings, match history, per-kit stats, and ranked progression
- Player command surface: `/duel`, `/queue`, and self-only `/stats`
- Runtime smoke: Paper 1.21.11 boot check via `scripts/smoke-run-paper.sh`

## Project Knowledge

This repository follows an agent-legible workflow inspired by OpenAI's harness engineering approach. Start with:

- [AGENTS.md](AGENTS.md) for agent entry instructions.
- [docs/README.md](docs/README.md) for the documentation map.
- [ROADMAP.md](ROADMAP.md) for the current implementation path and phase gates.
- [docs/PRODUCT.md](docs/PRODUCT.md) for product direction.
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for planned boundaries.
- [docs/BUILDING.md](docs/BUILDING.md) for build and verification commands.
- [docs/HARNESS_ENGINEERING.md](docs/HARNESS_ENGINEERING.md) for the working model.
- [docs/DECISIONS.md](docs/DECISIONS.md) for project decisions.

## Verification

Use the Gradle wrapper for local work:

```bash
./gradlew spotlessCheck test jacocoTestReport jar
./scripts/smoke-run-paper.sh
```

Future implementation should use official PaperMC guidance as the primary source for project setup and API usage. Keep the base API-only unless a documented feature requires Paper internals.

## License

RevPrac is licensed under the [MIT License](LICENSE).
