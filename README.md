# Sclera

**Eye tracking and gaze controls for Minecraft VR.**

Look at blocks, navigate menus and inventories, and aim with your eyes while keeping your VR controllers in control. No eye tracker? Use **Head Tracking** to control the same gaze interactions with your headset's direction.

**Version 1.8.1 · Minecraft Java 1.20.1 · Fabric / Forge · Visor**

[Download Sclera](https://github.com/dojyaan42/Sclera/releases) · [Visor](https://modrinth.com/mod/visor) · [Visor Essentials](https://modrinth.com/mod/visoressentials)

## Requirements

| Component | What you need |
| --- | --- |
| Minecraft | Java Edition **1.20.1** |
| Mod loader | **Fabric** or **Forge**; use a loader version compatible with your chosen Visor build. Forge requires **47.4.23 or later in the 47.x series**. |
| VR support | **Visor**, using a **0.5 or 0.6** build for Minecraft 1.20.1 and your loader, plus its required dependencies and a working VR runtime |
| Eye tracking | An eye-tracking device with **VRCFaceTracking**, or **PS VR2 with PSVR2Toolkit** for direct tracking |
| Without eye tracking | Select **Aim Source: Head**; eye-tracking hardware and software are unnecessary for this mode |

[Visor Essentials](https://modrinth.com/mod/visoressentials) adds the **bow and trident aiming reticle**. You can use Sclera's other gaze controls without it. Choose an Essentials release compatible with your installed Visor version.

PS VR2 feedback options require a working **PSVR2Toolkit** installation and active VR controllers. These options are independently configurable and **off by default**.

## Install Sclera

1. Create or open a **Minecraft 1.20.1** instance using **Fabric or Forge**.
2. Install [Visor](https://modrinth.com/mod/visor) and the dependencies required by that Visor release. Confirm that the instance launches correctly in VR.
3. Download the **Sclera 1.8.1 JAR for your loader** from [Releases](https://github.com/dojyaan42/Sclera/releases). Use the Fabric file for Fabric or the Forge file for Forge. The `-sources.jar` and GitHub's source-code ZIP are for developers.
4. Close Minecraft and place the Sclera JAR in that instance's **`mods` folder**. Remove any older Sclera JAR from the same folder so only one version is installed. Install compatible Visor Essentials if you want the bow/trident aiming reticle.
5. Start the instance in VR and open **Sclera Eye Tracking** from Visor's addon settings. On Fabric, you can also open Sclera's settings through Mod Menu; on Forge, use the Mods screen's configuration button.

Use your launcher's **Open folder** action to find the correct instance. Each modpack or launcher profile may have its own `mods` folder.

## Set up your tracking

### Head Tracking

In Sclera settings, set **Aim Source** to **Head**. Your headset's center direction controls aiming and gaze interactions. VRCFaceTracking is unnecessary for this mode.

Start with **Hybrid** mode to combine headset aiming with your controllers.

### Eye tracking with VRCFaceTracking

1. Install [VRCFaceTracking](https://docs.vrcft.io/docs/intro/getting-started) and configure the module for your eye-tracking device using its hardware guide.
2. Configure the tracking software to send compatible eye-gaze OSC data to **`127.0.0.1:9000`** on the PC running Minecraft. Sclera also listens on **UDP 9002** as an alternative input port.
3. In Sclera, open **Advanced Settings → PS VR2 Settings…** and set **Eye Tracking Source** to **VRCFT**, even if you use a headset other than PS VR2.
4. Return to the main Sclera screen, set **Aim Source** to **Eyes**, and check that the pointer follows your eyes.
5. Select **Calibrate Center**, look directly at the green center dot, and pull your controller trigger to align the pointer.

Keep VRCFaceTracking and the required hardware module running while playing. If another application is using UDP 9000, configure the sender to use 9002 instead.

### Direct PS VR2 tracking

1. Install and configure **PSVR2Toolkit** using its [installation guide](https://github.com/BnuuySolutions/PSVR2Toolkit/wiki/Installation). Make sure your installation includes `psvr2_toolkit_capi.dll` so Sclera can connect to the Toolkit.
2. Start your PS VR2 runtime and Minecraft in VR.
3. Open **Advanced Settings → PS VR2 Settings…** and select **Eye Tracking Source: PSVR2**. Sclera also selects this source automatically when it detects the Toolkit at startup.
4. Set **Aim Source** to **Eyes**, then run **Calibrate Center**.

Direct PSVR2 tracking does not need VRCFaceTracking. If the source shows **Inactive** or the feedback controls show **Unavailable**, check that the Toolkit is installed and running. For a nonstandard installation, set the following Minecraft JVM argument to the directory containing `psvr2_toolkit_capi.dll`:

```text
-Dsclera.psvr2toolkit.path="C:\path\to\toolkit\win64"
```

### Choose how to interact

- **Hybrid:** combine gaze or head tracking with controller input.
- **Eyes Only / Head Only:** use the selected tracking source for gaze interactions.
- **Hands Only:** use your controllers.
- **Tracking in Chat:** found under **Advanced Settings → 2D Menus & Inventories** and enabled by default. Turn it off to give your controllers control of chat and its virtual keyboard, hiding the gaze pointer while typing.

In **PS VR2 Settings**, enable **Damage Feedback**, **Hit Feedback**, or **Block Break Feedback** individually if desired. All three start disabled.

## Single-player and multiplayer

### Single-player

Install Sclera in your game and play. All single-player features work from that one installation; there is no separate server to set up.

### Multiplayer

**To use Enderman eye-gaze detection, Hit Feedback, and Block Break Feedback, install Sclera 1.8.1 on both the server and your own game.**

For a dedicated server:

1. Use a Minecraft **1.20.1** server with Fabric or Forge.
2. Install the server-compatible Visor build and its required dependencies.
3. Place the matching Sclera JAR in the server's `mods` folder and restart the server.
4. Each player who wants these features installs Sclera 1.8.1 in their own game.

Other players can join without Sclera on their clients, but they will not have these features. The server must still have Sclera installed. VRCFaceTracking, eye-tracking hardware and PSVR2Toolkit run on the player's PC; they are unnecessary on a dedicated server.

## Features in 1.8.1

### Enderman gaze detection

Catch an Enderman's attention by looking directly at it with your eyes, even when your headset faces another direction. In Head Tracking mode, aim your headset at the Enderman instead. Wearing a carved pumpkin still protects you. These features need Sclera on the server when playing multiplayer.

### Bow and trident reticles

Aim bows and tridents where you look, using either your eyes or the direction of your headset. The aiming reticle follows your gaze, and the hand crosshair stays hidden while you aim. Install compatible **Visor Essentials** to use this reticle.

### Chat and interface interaction

Navigate menus, inventories, containers and settings with your gaze. If you prefer to type with your controllers, turn off **Tracking in Chat** to hide the gaze pointer in chat and its virtual keyboard.

### PS VR2 haptic feedback

| Option | Feedback |
| --- | --- |
| **Damage Feedback** | Both controllers vibrate when you take damage. |
| **Hit Feedback** | When you hit a target, the controller you attacked or fired with vibrates. Bow hits vibrate both controllers. |
| **Block Break Feedback** | When you finish breaking a block, the controller you used vibrates. |

Each option is independent and **disabled by default**. Hit Feedback and Block Break Feedback need Sclera on the server as described above.

Look around menus and inventories to move the pointer, which gently snaps to nearby inventory slots. When mining, your target stays selected until you finish breaking the block. You can also enable blink actions to click or interact by blinking.

## Troubleshooting

| Problem | Check |
| --- | --- |
| Sclera does not appear or Minecraft fails to load | Check Minecraft 1.20.1, the loader, Visor's dependencies, and that only one Sclera JAR is installed in the correct instance. |
| No eye-tracking input | Set Aim Source to Eyes and choose the correct Eye Tracking Source. For VRCFT, check the hardware module and OSC destination. For PSVR2, check the Toolkit. |
| The pointer is offset | Run Calibrate Center with an active eye-tracking signal. |
| Gaze interferes with typing | Turn off Tracking in Chat. |
| The bow/trident reticle is missing | Check compatible Visor Essentials, gaze bow aiming, and Eyes or Head Tracking mode. |
| PS VR2 feedback is unavailable or silent | Check the Toolkit, active VR controllers and the individual feedback toggle. For Hit/Block Break Feedback, also check server installation. |
| Endermen ignore eye gaze in multiplayer | Check Sclera on both server and client, an active eye-tracking signal, and normal Enderman conditions such as pumpkin protection. |

## Build from source

To compile Sclera yourself, use **JDK 21** and the included Gradle wrapper. The resulting mod runs on Java 17.

From the repository root on Windows:

```powershell
# Fabric
.\gradlew.bat "-Ploader_version=0.19.5" build

# Forge
.\gradlew.bat -p forge build
```

Outputs:

- Fabric: `build/libs/Sclera-1.8.1.jar`
- Forge: `forge/build/libs/Sclera-1.8.1-forge.jar`

The default compile-time Visor version is `0.6.0-beta`. Add `"-PvisorVersion=0.5.0"` to either command to compile against the older API. Gradle downloads dependencies; local dependency JARs are unnecessary. See [Forge build details](forge/README.md).

## License

[MIT](LICENSE).
