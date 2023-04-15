# Teambots

A FabricMC mod adding various commands for `nations.minecraft.best` server.

This is currently tested only on Minecraft 1.19.2.

## Features

- Faction management (called "nations" here)
- Safely allow players to spawn Carpet bots
- The End lock until specified time, including fancy opening
- Inbox system, so that some system messages sent to offline players will be stored and displayed to them anyway

## Commands

### `/bot` - Carpet bot management

This command can be used to spawn and kill Carpet bots. They behave like normal players, so can be used for things like
farm AFK-ing.

For now, only a single bot per nation is allowed. Only players who belong to a nation can use this command, but they do
not have to be a leader.

-   `/bot set <coords>` - spawn a new bot at `coords` or teleport an existing bot. `coords` must be at most 5 blocks away from you. (TODO: make distance configurable.)
-   `/bot remove` - remove a bot.

### `/endopening`

Check when The End will be opened.

### `/nation` - Nation management

The `/nation` command can be used by players and admins to create, join and manage nations. It uses vanilla teams for
this, so that you can remove and modify nations using the `/team` command. The only non-vanilla feature added is
leaders, which can be managed using `/nation admin`.

`/nation` commands (except `list` and `admin`) may have a cooldown configured (see [configuration](#configuration)).

-   `/nation create <name> <color>` - create a new nation and become its leader
-   `/nation invite <player>` - invite a new player to a nation (as a leader).
-   `/nation kick <player>` - kick a player from your nation (as a leader)
-   `/nation leave` - leave a nation. Possible only if you are not its leader
-   `/nation list` - display nations and their members.
-   `/nation remove` - remove your nation (as a leader; you need to kick all the members first)
-   `/nation set name <name>` - change display name of your nation (as a leader).
-   `/nation set color <color>` - change color of your nation (as a leader)
-   `/nation admin` - OP commands for managing nations
    -   `/nation admin setleader <team> <leader>` - set nation leader
    -   `/nation admin clearleader <team>` - remove nation leader, should be done before `/team remove`.

### `/worldsize` - Check current world size

This command displays current world size. If it is moving, displays the target size and time left.

## Configuration

There is a configuration file at `config/teambots.json`:

```json
{
    "endOpeningTime": "<Unix timestamp of the moment when The end will be opened for players. Default 0 - always opened>",
    "nationCommandCooldown": "<Time players need to wait before they can use /nation command again>"
}
```

## Dependencies

-   Java >=17
-   Minecraft ~1.19
-   Fabric loader >=0.14.9
-   Fabric API >=0.67.1
-   Carpet >=1.4.84
