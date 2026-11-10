# Easy TP

Client-side Fabric mod for Minecraft **1.21.11**. Press **P** to open a GUI with two lists of online players:

- **Left – Destination:** pick one player.
- **Right – Target:** click a player to run `/tp <target> <destination>` immediately.
  - Shift+click toggles multi-selection; clicking a selected player teleports all selected players.

Only you need the mod. It sends the vanilla `/tp` command through the normal command system, so you need OP / `/tp` permission on the server. If the server rejects the command, an error is shown in the GUI.

The key can be rebound in **Options > Controls > Easy TP**.

## Build

```
gradle wrapper   # once, if the wrapper is missing
./gradlew build
```

The jar is in `build/libs/`. Requires Java 21 and Fabric API.
