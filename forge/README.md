# Sclera 1.8.1 — Forge 1.20.1

This is an independent Forge build of the existing Sclera sources. The root
Fabric project remains unchanged.

From the repository root (JDK 21 to run Gradle; output targets Java 17):

```powershell
.\gradlew.bat -p forge build
```

Output: `forge/build/libs/Sclera-1.8.1-forge.jar`.

Install with Minecraft 1.20.1, Forge 47.4.23 or later in the 47.x series, and
Visor 0.5.0 or later. Visor Essentials remains optional at runtime; use a
version compatible with your Visor installation. Oculus 1.8.0 supplies the
optional Iris shader integration on Forge. The default build dependency is
Visor 0.6.0-beta; pass `-PvisorVersion=0.5.0` to compile against the older API.

The Forge build uses Architectury Loom solely as a build tool; users do not
need Architectury API, Fabric Loader or a Fabric compatibility bridge.

## Loader adaptations

- Native Forge entry point registers the same Sclera Visor addon, with the same
  initialization. Forge's Mods screen opens the existing settings screen.
- `ForgePlatform` supplies mod detection and Forge's configuration directory.
  Early mixin selection uses `FMLLoader`, which is available before `ModList`.
- The build copies shared sources into `build/generated/sources/sclera` and
  changes only Fabric Loader references and the two literal Minecraft class
  descriptors used in external Visor/Iris mixins. Original source is untouched.
- Oculus is recognized as the Forge counterpart of Iris. Its matching classes
  use the original VRS shadow/composition hooks.
- Forge metadata and the mixin refmap replace Fabric metadata. Use Minecraft's
  built-in JNA 5.12.1: nesting another copy prevents Forge's module layer from
  starting. Sclera's native API calls and structure layouts are unchanged.

Tracking, filtering, OSC, PSVR2 integration, adaptive triggers, VRS algorithms,
UI, defaults, assets and configuration formats come from the original source.
Do not edit generated Java files; they are recreated by `generateForgeSources`.

The original Fabric build remains `./gradlew build` from the repository root.
