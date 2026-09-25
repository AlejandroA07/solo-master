# Solo Master

Solo Master is a personal mastery coach for learning software development deeply.

Instead of rewarding content consumption, it asks the learner to explain, write, diagnose, refactor, and recognize code before providing help. Weak areas return through targeted practice until the learner can demonstrate the skill without assistance.

## Current status

Solo Master has a verified Java 21 / Spring Boot foundation with a deterministic quality gate (`scripts/verify.sh`), CI, and security scanning. Learning features have not been implemented yet.

The initial subject is Java and Spring Boot (switched from C# and .NET; see ADR-0003 in `docs/dev/decisions.md`). The roadmap's first vertical slice is being re-targeted to Java; older planning documents still describe it in C# terms.

## Working product hypothesis

> After learning one small Java topic with Solo Master, the learner can explain and solve a fresh problem several days later without AI assistance better than they could using ordinary notes or tutorials.

## Stack

- Java 21 LTS, built with Gradle (Kotlin DSL) and the committed wrapper
- Spring Boot with Spring MVC and Thymeleaf server-rendered pages
- JUnit 5 and Spring Boot Test
- Spotless with google-java-format
- Proposed: SQLite for persistence; AI client library to be chosen in an ADR
- Local-first, single-user modular monolith

## Run it

```bash
./gradlew bootRun        # http://127.0.0.1:8080
./scripts/verify.sh      # full quality gate
```
