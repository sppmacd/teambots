# Teambots

A FabricMC mod adding various commands for `nations.minecraft.best` server.

This is currently only for Minecraft 1.19.2.

## Commands

### `/bot` - Carpet bot management

This command can be used to spawn and kill Carpet bots. They behave like normal players, so can be used e.g. for farm
AFK-ing.

For now, only a single bot per nation is allowed. Only players that belong to a nation can use this command, but they
don't need to be a leader.

- `/bot set <coords>` - Spawn a new bot at `coords` or teleport an existing bot. `coords` must be at most 5 blocks away
  from you.
- `/bot remove` - Remove a bot.

### `/nation` - Nation management

`/nation` command can be used by players and admins to create, join and manage nations. It uses vanilla teams for this,
so that you can remove and modify nations using `/team` command. The only non-vanilla feature added are leaders, which
can be managed with `/nation admin`.

- `/nation add <player>` - add new player to a nation (as a leader) 
- `/nation create <name> <color>` - create a new nation and become its leader
- `/nation kick <player>` - kick a player from your nation (as a leader)
- `/nation leave` - leave a nation. Possible only if you are not its leader
- `/nation list` - display nations and their members
- `/nation remove` - remove your nation (as a leader; you need to kick all the members first)
- `/nation admin` - OP commands for managing nations
  - `/nation admin setleader <team> <leader>` - set nation leader
  - `/nation admin clearleader <team>` - remove nation leader, should be done before `/team remove`.

## Dependencies

* Java >=17
* Minecraft ~1.19
* Fabric loader >=0.14.9
* Fabric API >=0.67.1
* Carpet >=1.4.84
