# Easy TP

Client-side Fabric mod for Minecraft **1.21.11**. Press **P** to open a GUI with two lists of online players:

- **Left – Teleport (who):** the players to teleport. An **EVERYONE** row sits at the top.
- **Right – Destination (where):** pick one player.

Pick a destination on the right, then:

- Click a player on the left to run `/tp <player> <destination>` immediately.
- Click **EVERYONE** to select and teleport all online players to the destination.
- Shift+click players (or EVERYONE) to multi-select without teleporting; clicking a selected player then teleports all selected players.

You can teleport yourself too. If the target and destination are the same player, nothing is sent.

Only you need the mod. It sends the vanilla `/tp` command through the normal command system, so you need OP / `/tp` permission on the server. If the server rejects the command, an error is shown in the GUI.

The key can be rebound in **Options > Controls > Easy TP**.

## Build

```
gradle wrapper   # once, if the wrapper is missing
./gradlew build
```

The jar is in `build/libs/`. Requires Java 21 and Fabric API.
