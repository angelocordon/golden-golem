# WORKFLOW.md

Agent rules for this repo, read by Ada's agents from [ada-agents](https://github.com/angelocordon/ada-agents). `CONTRIBUTING.md` holds the code standards and review checklist; this file holds what an agent needs on top of it, so a dispatch prompt only carries the issue, files owned and the cross-issue contract.

## Build and CI
- **CI is the compiler.** There may be no local JDK. Java 25, Gradle, Paper API 26.3.
- Two checks on every PR (`.github/workflows/build.yml`):
  - `build`: `gradle build` (compile and unit tests), uploads `golden-golem.jar`.
  - `smoke`: boots a real Paper 26.3 server with that jar (`scripts/smoke-test.sh run`) and fails if the plugin doesn't load cleanly.
- Both must be green **on a branch up to date with main**, on the final commit, before merge. Never merge red.
- Watch a PR: `gh pr checks <n> --watch`. On failure: `gh run view --log-failed`; for `smoke`, download the `smoke-server-log` artifact.
- A `v*` tag builds the release jar with that version and publishes a GitHub release. Only the lead tags.

## Conventions
- Linear project **Golden Golem Plugin**, team Labs, issues `LAB-xx`.
- Branch `lab-xx-short-name`; PR title `LAB-xx: what it does`; reference the issue ID in every commit.
- Commit trailer: `Co-Authored-By: Claude <model name> <noreply@anthropic.com>` for the model that wrote the commit.
- PR template: `.github/pull_request_template.md`. Fill in every section, including "Edge cases found (fixed now / punted, why)".
- Shared files: `GoldenGolem`, `Services`, `GolemController`, `GoldenGolemPlugin`, `Keys`. Smallest possible change, called out under "Shared changes"; never rename or change a signature another issue uses.
- **Never merge** unless you are the lead or the user said so.

## Reference material (permanent homes)
- Paper source (patches and CraftBukkit): https://github.com/PaperMC/Paper. Pin a commit whose `gradle.properties` says `mcVersion=26.3` and link to it.
- Decompiled vanilla 26.3: https://github.com/0khs/minecraft_source_26.3. Use it for entity, AI, pathfinding and event behaviour that Paper's patches don't show.
- Design spec: linked from the Linear project.

## Deploy
- **Target:** Hyperion (homelab), Docker container `minecraft` (`itzg/minecraft-server`), Paper 26.3 with Geyser, Floodgate and CreeperNoGrief. Config lives in the homelab repo at `hyperion/minecraft/compose.yaml` and is applied with that repo's Ansible playbook.
- **Access:** join the tailnet with `ada-agents` `scripts/tailscale-up.sh` (tag `tag:ada-agent`), then `ssh -o ProxyCommand='tailscale --socket=$HOME/.tailscale/ada.sock nc %h %p' ada@hyperion` (the script uses userspace networking on its own socket, so plain `ssh ada@hyperion` has no route; it prints this command with the socket it used). The `ada` user's sudo allows exactly four commands: `docker ps`, `docker logs minecraft`, `docker exec minecraft rcon-cli list` and `docker restart minecraft`. Anything else (compose, systemctl, files under `/home/angelo`) is refused by design; do not look for another way in.
- **How a release reaches it:** `PLUGINS` points at `releases/latest/download/golden-golem.jar`, which downloads on every container start. Deploying a new release is a restart: `sudo docker restart minecraft`. **First confirm `smoke` is green on the tagged commit** (`gh run list --commit <sha>`): the release is published by `build` without waiting for `smoke`, so a restart can otherwise load a jar that failed to boot.
- **Active-use check before a restart:** `sudo docker exec minecraft rcon-cli list`. If anyone is online, do not restart; report who and wait.
- **Backup:** the nightly `minecraft-backup.timer` (3am Pacific) keeps one copy of the world. Agents cannot trigger a backup. If the release changes saved data (PDC format, config migration), stop and ask the user to run `sudo systemctl start minecraft-backup` first.
- **Verify:** `sudo docker logs minecraft` shows `Done (...)!` and `GoldenGolem` enabled with the expected version, and no new errors.
- **Rollback and config changes:** these go through the homelab playbook, which agents cannot run. Report what is needed: pin the golden-golem URL in `hyperion/minecraft/compose.yaml` from `releases/latest/download/` to `releases/download/vX.Y.Z/` for the previous release, then the user runs `ansible-playbook hyperion/playbook.yml` (it recreates the container).

## Core code digest
- TODO (LAB-48): `docs/core-classes.md`, one paragraph per core class. Until it exists, the packages under `src/main/java/.../goldengolem/` are: `golem` (data, registry, controller, states), `golem/state` (one handler per state), `golem/movement` (pathing and AI takeover), `work` (scanning, harvest and break rules, deposit), `link` and `chest` (golden chests and linking), `menu`, `build`, `lifecycle` (death drops, healing, load/unload).

## Risk map
- **High (reviewer-deep):** the golem state machine (`GolemController`, `StateHandler`, anything in `golem/state/`, `BlockedReason`); item movement and block drops (`work/Deposit`, `work/StorageMerge`, `work/ToolSwap`, `work/BlockBreaker`, `work/HarvestRule`, `menu/`, `lifecycle/`, `chest/`, `link/`); movement and AI takeover (`golem/movement/`); entity identity and load/unload (`golem/GolemRegistry`, `lifecycle/GolemLifecycle`, `lifecycle/GolemLifecycleListener`); any listener whose behaviour depends on Paper event order or cancellation; golem PDC format (`GoldenGolem`, `Keys`).
- **Medium/low (reviewer-light):** config, feedback and messages, `build/` look, docs, CI.

## Project failure classes
Reviewers check these in addition to their generic checklist.
- **Item conservation.** Items duplicated or lost on any path that moves them: menu edits, deposit, tool swap, death drops, chunk or world unload mid-action, owner logout, inventory open, full chest, tool breaking. Total item count must be unchanged.
- **Tick cost and chunk loading.** The controller ticks every golem every tick: no full-area scans in one tick (use `AreaScanner`), no large `getNearbyEntities`, no reads that force chunk loads.
- **Paper event ordering.** Event order, priority, cancellation and same-tick vs next-tick, checked against the pinned Paper source. Example: `BlockDropItemEvent` carries every drop, not only the block's own.
- **Copper golem behaviour outside the AI step.** `setAware(false)` stops only the brain. Weathering (statues discard the entity and its data), door opening in the node evaluator, and attribute speed still apply.
- **Entity identity.** Cross-world teleport and unload/reload give a new entity object under the same UUID; anything holding an entity reference must rebind.
- **Saved data.** Old PDC data must still load.

## Manual verification
- PRs list steps under "In-game checklist" for a Paper 26.3 test server, Java and Bedrock (Geyser) where it matters. CI can't run the game.
