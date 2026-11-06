# toro-plugin-compat

Patches and build scripts that keep the TORO-Server (torosaba.net) plugins running on
**Paper 26.3** while upstream has not released 26.3 compatible versions.
Private repository - do not publish (contains a derivative of a commercial plugin, see Vehicles).

Deployed jars live on the server in `/home/toroserver/main/plugins/`; the replaced originals are in
`/home/toroserver/main/plugin_jars_old_20261029/`.

| Plugin | Upstream version | What was done | License |
|---|---|---|---|
| CoreProtect | 24.1 (CE) | cherry-pick upstream commit `68739a2` "Target Paper 26.3" onto tag `v24.1`; build with `-Dproject.branch=master` | Apache-2.0 style (PlayPro) |
| Shopkeepers | 2.28.1 | upstream tag already ships a `v26_3_R1_paper` NMS module; only `dist` trimmed to that module so the build does not need Spigot BuildTools | GPL-3.0 |
| MyPet | 3.14.3 | new `nms/v26_3_R1` compat module (copy of `v26_2_R1`, adapted to the Paper 26.3 NMS changes) merged into the official 3.14.3 jar | GPL-3.0 |
| EssentialsX | 2.22.0 | `VersionUtil`: add 26.2 / 26.3 to the supported versions (removes the "unsupported server version" error) | GPL-3.0 |
| Vehicles | 14.4 (closed source) | NMS reflection layer rewritten for Mojang mappings, see below | commercial |

`patches/<Plugin>/*.patch` are `git format-patch` files against the upstream tag named above.
`work/` (clones, downloaded server jars, build output) and `dist/` (built jars) are not tracked.

## Toolchain (Windows)

* JDK 25 (Gradle toolchain: `%USERPROFILE%\.gradle\jdks\eclipse_adoptium-25-*`), JDK 26 for `javac`/`jar`
* Maven (`scoop install maven`), Gradle wrappers of the upstream projects, Python 3 (Vehicles jar assembly)
* `work/refs` = files copied from the server: `main/versions/26.3/paper-26.3.jar`,
  `main/cache/mojang_26.3.jar`, `main/libraries/`, `main/plugins/MyPet-3.14.3.jar` (original),
  plus `deps/annotations.jar` and `deps/lombok.jar` from Maven Central.

## Build and deploy

### CoreProtect
```
git clone https://github.com/PlayPro/CoreProtect work/CoreProtect && cd work/CoreProtect
git checkout -b toro-24.1-mc26.3 v24.1 && git am ../../patches/CoreProtect/*.patch
mvn -B -DskipTests -Dproject.branch=master clean package      # -> target/CoreProtect-24.1.jar
```
`project.branch` must be set, otherwise the plugin refuses to start ("branch has not been set").
Deployed as `CoreProtect-24.1-toro26.3.jar`. First start migrates the database to v24.1 (MySQL/TiDB).

### Shopkeepers
```
git clone https://github.com/Shopkeepers/Shopkeepers work/Shopkeepers && cd work/Shopkeepers
git checkout -b toro-2.28.1-mc26.3 v2.28.1 && git am ../../patches/Shopkeepers/*.patch
JAVA_HOME=<jdk25> ./gradlew :shopkeepers-dist:assemble -x test   # -> build/Shopkeepers-2.28.1.jar
```
Deployed as `Shopkeepers-2.28.1-toro26.3.jar`. 2.28 migrates `config.yml` (v11 -> v12); the pre-upgrade
data was saved to `plugin_jars_old_20261029/Shopkeepers-data-backup-pre2.28.1.tgz`.

### MyPet
```
git clone --depth 1 https://github.com/MyPetORG/MyPet work/MyPet     # 72203e1
cd work/MyPet && git am ../../patches/MyPet/*.patch                   # adds nms/v26_3_R1
scripts/mypet-compile.ps1     # javac of the new module against the server's own jars
scripts/mypet-package.ps1     # official 3.14.3 jar + v26_3_R1 classes + versionmatcher.csv + manifest -> dist/MyPet-3.14.3-mc26.3.jar
```
The upstream Gradle build needs a private Maven repository (`repo.userderezzed.dev`) for the Spigot jars, which is why the module is compiled with plain `javac`
and merged into the released jar instead. The `de.keyle.knbt` imports are pointed at the relocated
`de.Keyle.MyPet.util.nbt` package the released jar uses. Changes against 26.2:
`asCraftMirror` -> `asBukkitMirror`, sound getters / `getPickupItem` now public, `swing` -> `swingForAttack`,
`drop(stack, bool)` -> `drop(stack, bool, Prediction)`, `setInvulnerable` -> `setPermanentlyInvulnerable`,
removed `forceDrops`, removed Axe/Shovel/Hoe/Sign item classes (covered by the TOOL component check).

### EssentialsX
```
git clone https://github.com/EssentialsX/Essentials work/Essentials && cd work/Essentials
git checkout -b toro-2.22.0-mc26.3 2.22.0 && git am ../../patches/Essentials/*.patch
JAVA_HOME=<jdk25> ./gradlew :EssentialsX:build -x test
```

### Vehicles (closed source, version 14.4 by Pollitoyeye)
The plugin reflects into Spigot-obfuscated NMS names per Minecraft version (`EntityUtils.initReflection`
crashed with `ArrayIndexOutOfBoundsException` because the CraftBukkit package no longer contains
`v1_xx_Rx`). Everything NMS related is concentrated in three classes, which are replaced:

* `vehicles/src/.../utils/EntityUtils.java` - same public API, Mojang names, input via `Player#getCurrentInput()`
* `vehicles/src/.../utils/StorageUtils.java` - `ItemStack#serializeAsBytes` (old NBT blobs are upgraded on read). The stored `ArrayList<byte[]>` is read through a filter limited to that shape. Data that cannot be read stays in the storage as a named paper item holding the original bytes, and is read again the next time the vehicle is placed.
* `CustomHeadCreator` - head profile built with Paper's `PlayerProfile` (the rest of the class is untouched)

Steps: decompile with Vineflower (`java -jar vineflower.jar Vehicles-14.4.jar src`), apply the
`CustomHeadCreator` change (see `vehicles/CustomHeadCreator.patch`), compile the three classes against the
original jar + Paper 26.3 jars, then `python -I vehicles/build_jar.py` copies every other entry of the
original jar byte for byte. Keep the replaced classes free of nested classes (lambdas are fine) so that the
patched jar has the same entries as the original.

#### Testing StorageUtils
`vehicles/test/.../StorageUtilsReadTest.java` checks the stored list format and its limits with the JDK only.
It is not part of the jar. Compile it against the compiled classes, then run it with the jar build's classpath:
```
javac --release 25 -encoding UTF-8 -cp <classes> -d <test> vehicles/test/es/pollitoyeye/vehicles/utils/StorageUtilsReadTest.java
java -cp <test>;<classes>;<classpath of the jar build> es.pollitoyeye.vehicles.utils.StorageUtilsReadTest
```
It exits with 1 if a check fails. What happens to unreadable data in the storage needs a server and is not covered.

The plugin contains anti-piracy code (online licence/ban check against spigotleakprotector.com, jar
integrity checks that overwrite the jar when they fail). That code was **not modified or bypassed**; the
patched jar keeps all untouched entries (incl. timestamps) as they were. Make sure the licence is valid
before deploying; keep the original jar (`plugin_jars_old_20261029/Vehicles(14.4).jar`).

## Known remaining WARN/ERROR lines (not plugin faults)

* ProtocolLib: "Version (MC: 26.3.0) has not yet been tested" - works.
* dynmap: `Invalid rotate error - models_1.txt` - model file of the mod renderer, harmless.
* LunaChat / Multiverse-Core: handled by the parent session.

## Status (verified from the server startup log only, no in-game test)

All five plugins above enable without errors on Paper 26.3-227. In-game behaviour is untested; Vehicles in particular (driving input, trunks, Mecha/Submarine packets) has only been checked for successful initialisation.

The `cp namecolor`-style 'Could not register alias' warnings already existed before this work and are unrelated.
