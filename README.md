# RevPrac

RevPrac is a Minecraft practice core plugin project for Modern Paper 1.21.11, created under the MIT License by XreatLabz.

The long-term goal is a StrikePractice-like practice foundation for duels, kits, arenas, queues, match lifecycle, player stats, and operator-friendly configuration. The current base is intentionally small: it proves the plugin can compile, load, and enable on Paper before feature modules are added.

## Status

- Repository bootstrap: complete
- Target platform: Paper/Minecraft 1.21.11
- Build system: Gradle 9.5.0 with Kotlin DSL and Java 21 toolchain
- Plugin base: API-only `JavaPlugin` entrypoint with `plugin.yml`
- Runtime smoke: Paper 1.21.11 boot check via `scripts/smoke-run-paper.sh`

## Project Knowledge

This repository follows an agent-legible workflow inspired by OpenAI's harness engineering approach. Start with:

- [AGENTS.md](AGENTS.md) for agent entry instructions.
- [docs/README.md](docs/README.md) for the documentation map.
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
