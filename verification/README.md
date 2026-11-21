# Loaded maid scan regression

The two periodic maintenance scans must cover all loaded maids in the current
dimension, including maids saved before this addon was installed. They must not
load chunks or depend on active order machines. `TaskManager.getCachedMaids()` is
a machine-radius task-assignment cache; it is empty without an active machine
and cannot supply a complete maintenance scan.

Production changes are limited to the NeoForge 1.21.1 `MaidUtils` scanner,
dimension-local timeout handling and server-side entity-leave cleanup. The
100-tick scheduling interval, persistent identifiers and binding data stay the
same. The existing upstream fix3 cooking-memory guards are retained.

## Reproduce

Requirements: Linux/WSL, Java 21, Python 3 and network access to the pinned public
inputs. Run from a clean checkout. The following commands use an optional local
HTTP proxy; choose a proxy reachable from your environment.

```bash
python3 verification/build_release.py --proxy http://127.0.0.1:7890 --harness
python3 verification/fetch_runtime.py --proxy http://127.0.0.1:7890
mkdir -p verification/server
cd verification/server
java -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7890 \
     -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7890 \
     -jar ../runtime-inputs/neoforge-21.1.248-installer.jar --installServer
cd ../..
python3 verification/run_isolated.py
```

The build selects NeoForge 21.1.248 without changing the module's default version.
Dependency downloads are checked against SHA-256 manifests. `regressionJar` is
a separate test mod, built through an opt-in Gradle init script; no test classes
or dependency JARs are included in the normal production JAR. Do not install
`loadedscan-regression.jar` in a real world.

Running the harness accepts the Minecraft EULA by writing `eula=true` in its
private test directory. It generates synthetic worlds, uses no player login,
binds only to `127.0.0.1:25684`, forces test chunks and stops each server phase
automatically. Production scans themselves do not force chunks. Existing test
worlds/results cause a refusal to run; archive the directory and reinstall the
test server before repeating. No player save, configuration or modpack is used.

## Fixed assertions

| Phase | Purpose and expected outcome |
| --- | --- |
| `seed` | Business absent; save three maids in the overworld and Nether, including one about 4104 blocks away. |
| `baseline` | Add published fix2 to a seed copy; expect the infinite-AABB error to reproduce. |
| `cache_baseline` | Add published fix3 to another seed copy with no active machine; expect `scheduled_binding_add_without_bauble_tick` to fail, even though AABB errors are zero. |
| `fixed` | Add the PR build to another seed copy; all assertions pass and AABB errors remain zero for at least five real minutes. |
| `reload` | Restart the fixed world; saved maids, items, certificates and reconstructed bindings remain available. |
| `scheduled` | Use only the real BusinessManager schedule for binding add/update/remove and both recovery paths; all assertions pass. |

The runner treats each baseline's specifically asserted defect as an expected
positive control. Inspect both the phase outcome and its nested assertion
report; a successful baseline reproduction is not a successful functional test.

Assertions also cover empty dimensions, remote loaded maids, dimension isolation,
unchanged loaded chunk counts, notice-board bindings, the 199/200-tick grace
boundary, category changes, active task markers, real cooking requests and
registered cooking memories, multiple timed-out maids, actual dimension travel,
real chunk unload/reload and stale transient reference cleanup. UUIDs, inventory,
baubles, owner, favorability and persistent sentinel data are checked after save
and reload. The scheduled binding tests put the certificate in ordinary inventory
so bauble ticks cannot substitute for the scheduled scan.

## Validation limits

This is a dedicated-server regression, not full modpack acceptance. Client
inventory UI, integrated-server pause/resume, real end-to-end restaurant orders,
health-certificate right-click UI, Sable dynamic subworld restaurant setups and
Microsoft OpenJDK 21.0.7 are not tested here. Active task guards are tested, but
they do not establish that every recipe and restaurant workflow works. Historic
unrelated startup failures are outside the scope of this patch.

The minimal dependency set can report unrelated Kaleidoscope recipe errors and
a Sable `create:flywheel` tag error because Create is absent. Assertions target
the two maintenance scans and their behavior; they do not require all other log
messages to disappear.

## Recorded results (2026-11-21)

Tested against upstream main `531d81a095f83a45bac049f36e72a637acd7b661`
(fix3), using Ubuntu OpenJDK 21.0.13.0.3 and the pinned dependencies.
`build regressionJar -x test -Pneo_version=21.1.248` completed successfully.
The empty upstream JUnit task is skipped; functional assertions run on the
isolated dedicated server instead.

| Phase | Server ticks | Real running time | AABB errors | Outcome |
| --- | ---: | ---: | ---: | --- |
| `seed` | 100 | 4.93 s | 0 | PASS |
| `baseline` | 600 | 29.92 s | 108 | Expected infinite-AABB reproduction |
| `cache_baseline` | 350 | 17.41 s | 0 | Expected scheduled binding failure at tick 350 |
| `fixed` | 6400 | 319.92 s | 0 | PASS |
| `reload` | 300 | 14.91 s | 0 | PASS |
| `scheduled` | 1200 | 59.92 s | 0 | PASS |

The patched `fixed`, `reload` and `scheduled` phases contain 188 assertion records,
all passing. Both baseline defects reproduced as expected; fix3's baseline has
an intentional failed binding assertion and its associated exception record.
The fixed server ran without players for 319.92 seconds. Production JAR inspection
also passed 10 checks: metadata/version, loaded-entity enumeration and both scan
callers, unload cleanup, absence of infinite constants, test classes, nested JARs,
bundled dependency classes and client references in the modified server classes.

Validation boundaries above still apply; this does not prove complete restaurant
workflows or a user's full modpack.
