# Buildkite CI

One pipeline, `aapv`, in the `z8dn` org. Its only stored step is
`buildkite-agent pipeline upload .buildkite/pipeline.yml` — every real step comes
from that file at the commit being built, so pipeline changes are reviewed like
any other code.

Deliberately **one** pipeline, not one per workflow. Each Buildkite pipeline
needs its own GitHub webhook; the migration started with four and three never
had one, so they never ran. Express variation with `if:` on steps instead of
adding pipelines. If a step never fires, check
`gh api repos/z8dn/advanced-android-project-view/hooks` against the pipeline's
`provider.webhook_url` before anything else.

| Trigger | Runs |
|---|---|
| branch push / PR | build, test, verify |
| push to main | ...plus the release draft |
| manual, `RUN_UI_TESTS=true` | UI tests |

**Qodana and publishing are not here.** Both stay on GitHub Actions, for the
same reason: each needs to launch a full Android Studio, which does not fit in
4 GB. See the root `CLAUDE.md`.

A Publish Plugin step did live here briefly and was reverted. `publishPlugin`
pulls in `buildSearchableOptions`, which starts an IDE to build the marketplace
search index; build 92 died in that task with `Gradle build daemon disappeared
unexpectedly`. `-x buildSearchableOptions` would have made it pass, but every
published release would then ship without its search index. Don't re-add it
while the agents are this size.

Releasing is therefore a handoff: Buildkite drafts the release on `main`, you
publish the draft, and publishing fires the GitHub `release` event that
`.github/workflows/release.yml` listens for. Nothing here keys off tags.

## Agents

A single hosted queue, `default`: `LINUX_AMD64_2X4` — 2 vCPU, 4 GB, linux/amd64
only. There are no macOS or Windows agents, which is why the UI test matrix was
reduced to Linux.

Memory is the binding constraint on nearly everything here. When a step dies
with no useful stack trace, suspect the agent before suspecting Gradle.

## Gotchas

These each cost real debugging time:

- **Cluster secrets are not in the job environment.** Read them with
  `buildkite-agent secret get KEY`. YAML written as `$${SOME_TOKEN?}` never
  resolves, even when the secret exists. A missing secret gives a clean
  `404 Secret not found`, not a syntax error.
- **`if:` and `branches:` are mutually exclusive** on a step. Put the branch
  restriction inside the condition.
- **A step skipped by `if:` shows as `broken`** — that means "filtered out", not
  "failed". Publish, UI Tests and Release Draft are `broken` on an ordinary
  branch build, and that is correct.
- **`buildkite-agent` is not inside the docker plugin's container** unless the
  step sets `mount-buildkite-agent: true`. Needed for artifact download and
  secret reads.
- **Buildkite interpolates single-`$` variables at upload time**, including
  inside comments. Use `$$` for anything the shell should see.
- **`export VAR="$(cmd)"` swallows `cmd`'s exit status** — the status reported
  is `export`'s own, so it is 0 even under `set -e`. A failed
  `buildkite-agent secret get` then yields an empty string and the step carries
  on. Assign on one line, `export` on the next.
- **Hosted agent instance shape cannot be changed via the REST API.** `PATCH`
  silently ignores a `hosted_agents` body and returns the old shape;
  `bk queue update` only exposes `--description` and `--retry-agent-affinity`.
  Use the web UI. Renaming a pipeline *does* work via PATCH and preserves the
  webhook URL.
- **The `bk` CLI keeps its token in the macOS keychain**, so under a sandbox
  every REST call returns 401 even though `~/.config/bk.yaml` looks configured.

## Memory design

Two decisions keep this inside 4 GB. Do not undo either while the agents are
this size.

**Verify reuses Build's artifact rather than recompiling.** Build uploads the
plugin zip; Verify downloads it and runs `gradle verifyPlugin -x buildPlugin`.
That works with no build-script support because `verifyPlugin.archiveFile`
defaults to `buildPlugin.archiveFile`, which resolves to
`build/distributions/<name>-<version>.zip` — exactly where the download lands,
and Gradle only needs the input present at execution time. Skipping the rebuild
means no Kotlin daemon, which is what used to push the step over the limit.

**CI-only Gradle settings live in `gradle/ci.properties`**, appended to
`$GRADLE_USER_HOME/gradle.properties` where Gradle reads it natively and it
outranks the project's own file. Local builds never load it. See that file for
why each value is what it is.

If the verifier ever needs more heap, note it is a `JavaExec`, so use
`tasks.verifyPlugin { maxHeapSize = ... }` — **never**
`intellijPlatform.pluginVerification.freeArgs`, whose contents go to the
verifier's CLI rather than the JVM and corrupt the argument list.

## Useful commands

```bash
bk build view -p aapv                       # most recent build on this branch
bk build list -p aapv                       # recent builds
bk build create -p aapv -e RUN_UI_TESTS=true # run the UI test step
bk secret list --cluster-uuid <uuid>        # what secrets exist
```

Cluster `a4da0d9f-be57-4ed3-ac5b-1ccceab79c7b`, queue
`6069f2e4-0dae-42b5-ada6-36a76366d092`.
