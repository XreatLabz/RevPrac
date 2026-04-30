# Harness Engineering Model

RevPrac adopts an agent-legible workflow inspired by OpenAI's harness engineering article: humans steer, agents execute, and the repository itself carries the context needed to do reliable work.

## Local Operating Model

- Humans define goals, constraints, acceptance criteria, and product taste.
- Agents perform implementation, review, verification, and documentation updates.
- The repository is the system of record. If context matters, encode it in tracked files.
- `AGENTS.md` stays short and acts as a table of contents.
- `docs/` holds durable project knowledge, decisions, and operating rules.

## Agent Legibility

Future contributors should optimize for code and docs that agents can inspect, validate, and modify without relying on hidden chat history.

- Prefer clear module boundaries over clever coupling.
- Prefer explicit configuration and schemas over guessed data shapes.
- Prefer verifiable commands over prose-only claims.
- Prefer small plans and decision logs over long-lived oral context.

## Feedback Loops

As the project gains code, add commands and tooling that let agents prove their work:

- Build and test commands.
- Local Paper server startup checks.
- Static checks for formatting, dependency direction, and documentation freshness.
- Focused smoke tests for critical practice flows.

## Documentation Discipline

When an agent discovers a missing rule, ambiguous boundary, repeated mistake, or useful workflow, the fix should be encoded in docs or tooling so future runs inherit it.
