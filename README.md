# Solo Master

Solo Master is a personal mastery coach for learning software development deeply.

Instead of rewarding content consumption, it asks the learner to explain, write, diagnose, refactor, and recognize code before providing help. Weak areas return through targeted practice until the learner can demonstrate the skill without assistance.

## Current status

Solo Master has a verified .NET 10 foundation with a deterministic quality gate (`scripts/verify.sh`), CI, and security scanning. Learning features have not been implemented yet.

The initial subject is C# and .NET. The proposed first vertical slice covers loops and boundary errors using short retrieval-first learning sessions, deterministic code checks, targeted feedback, and delayed unaided mastery checks.

## Working product hypothesis

> After learning one small C# topic with Solo Master, the learner can explain and solve a fresh problem several days later without AI assistance better than they could using ordinary notes or tutorials.

## Proposed stack

- .NET 10 LTS
- ASP.NET Core Razor Pages
- EF Core 10 and SQLite
- `Microsoft.Extensions.AI.IChatClient` at the AI infrastructure boundary
- xUnit-based unit, integration, architecture, and browser tests where appropriate
- Local-first, single-user modular monolith

The product and technical choices remain proposals until the planning decisions are accepted.
