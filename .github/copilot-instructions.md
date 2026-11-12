# Copilot instructions

## Build and test

- Use Java 25 and the Gradle wrapper.
- `./gradlew build` builds the default Minecraft 26.1.2 target and runs Gradle verification.
- CI also builds Minecraft 26.2 and 26.3:
  - `./gradlew --no-daemon build -Pminecraft_version='26.2' -Pfabric_api_version='0.161.0+26.2'`
  - `./gradlew --no-daemon build -Pminecraft_version='26.3' -Pfabric_api_version='0.166.2+26.3'`
- Run tests with `./gradlew test`. There are currently no test classes under `src/test`; to run one when added, use `./gradlew test --tests 'fully.qualified.TestClass'` (append `.testMethod` to select a method).
- For in-game behavior, follow `docs/CLIENT-TESTPLAN.md`.

## Architecture

- `Yuletide` is the Fabric entrypoint. It registers commands and event callbacks and wires the feature managers. Gameplay is server-side, including integrated singleplayer; multiplayer clients do not need the mod. The calendar uses a vanilla chest menu rather than a client-specific screen.
- Keep lifecycle-dependent work in the appropriate phase: configuration that does not need a world or registry loads in `onInitialize`; gift parsing and world data load after the server starts; active entities and calendar state are cleaned up or saved when it stops.
- Feature logic lives in managers under `dev.yuletide` feature packages; command classes delegate to those managers.
- Shared Java code is in `src/main/java`. Gradle adds `src/minecraft/${minecraft_version}/java` for the selected target. Keep Minecraft API differences behind the matching `MinecraftCompat` implementation in each supported version directory rather than adding version-specific calls to shared code.

## Repository conventions

- Shipped defaults are in `src/main/resources/yuletide` and are copied to `config/yuletide` only when a file is missing. Existing user files are preserved on reload, so read configuration through `Cfg`'s dotted paths and typed accessors with defaults for missing or older settings.
- Add user-facing message keys to both `messages_en.json` and `messages_de.json`; send messages through `Messages`/`Text` so legacy color codes and placeholders are handled consistently.
- Advent-calendar progress is world data in `<world>/data/yuletide_calendar.json`, keyed by year and player UUID; do not treat it as global mod configuration.
- Register command names and aliases in `Yuletide`. Command implementations extend `CommandBase`, which supplies position-based arguments, suggestions, localized replies, and the Op-level check (`isOp()`); explicitly check for a player where a command cannot run from the console.
- `README.md` describes the implemented feature scope. `docs/PLAN.md` also contains the future Part B roadmap; do not assume those proposed features are implemented.
