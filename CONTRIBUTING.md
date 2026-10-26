# Contributing

Work is tracked in the Linear project **Golden Golem Plugin** (team Labs, issues `LAB-xx`). The
design spec is linked from the project. Read your issue and the spec before writing code.

## Ground rules

- **One issue, one PR.** Branch `lab-xx-short-name`, PR title `LAB-xx: what it does`.
- **Stay in your files.** Each issue lists the files it owns. If you need a change in a shared
  file (`GoldenGolem`, `Services`, `GolemController`, `GoldenGolemPlugin`, `Keys`, another
  issue's files), keep it to the smallest possible addition and call it out in the PR body under
  **Shared changes**. Never rename or change the signature of something another issue uses.
- **No new dependencies** without the tech lead's OK.
- **CI is the compiler.** There may be no local JDK. Push, then wait for the `build` check with
  `gh pr checks --watch`. Never merge red.

## Code standards

- Java 25, Paper API 26.3 only (no NMS, no reflection into server internals).
- **Main thread only** for anything touching entities, blocks, inventories or PDC. No blocking
  I/O on the main thread. No async tasks unless the issue says so.
- **Golem data goes through `GoldenGolem`.** Don't read or write golem PDC keys anywhere else.
- **State changes from outside a handler use `controller.transition(...)`**, so `onEnter`/`onExit`
  run. Handlers change state only by returning it. Set a blocked reason with
  `StateHandler.block(golem, reason)`.
- **Per-tick code must be cheap.** The controller ticks every golem every tick. No full-area
  scans in one tick (use `AreaScanner`), no `getNearbyEntities` with big radii every tick, cache
  what you can per golem and prune it when the golem leaves the state.
- **Never lose or duplicate items.** Every path that moves items (menu, deposit, death, tool
  swap) must leave the total item count unchanged. Think about the golem dying or unloading
  mid-action.
- Nullability: annotate with `org.jspecify.annotations.Nullable`; everything else is non-null.
- Match the surrounding style: 4-space indent, short Javadoc on public types and non-obvious
  methods saying *why*, no commented-out code, no `TODO` without an issue key.

## Tests

- **Split pure logic from Bukkit glue** and unit-test the pure part (JUnit 5, `src/test/java`).
  Every issue says what must be tested.
- There is no server in tests. `Material` constants, `name()` and `==` work. Anything that hits
  a registry or the server does not: `new ItemStack(...)`, `Material.isBlock()`,
  `createBlockData()`, `Tag.*`, `Bukkit.*`. Design the pure part around plain values (enums,
  ints, `BlockPos`, small records) so it doesn't need those.
- Test names say the behavior: `returnsWhenStorageIsFull`, not `test1`.

## Pull requests

Use the PR template. In particular:

- **Design notes**: decisions a reviewer should check, and anything you chose differently from
  the issue and why.
- **In-game checklist**: the manual steps a human should run on a test server to see it work.
  CI can't run the game, so this is how features get verified before release.

## Review checklist

Reviewers (human or agent) check, in order:

1. **Correctness**: does it do what the issue and spec say? State transitions match the
   diagram? Edge cases from the spec handled?
2. **Items and data**: no dupes or loss; PDC only via `GoldenGolem`; old saved data still loads.
3. **Threading and performance**: main thread only; nothing expensive per tick.
4. **Scope**: stays in its files; shared changes are minimal and called out.
5. **Tests**: the pure logic is tested, tests would fail if the logic broke.
6. **Readability**: names, small methods, comments explain why.

Leave findings as PR review comments with a severity: **blocker** (must fix), **should**
(fix unless you argue it), **nit** (optional).
