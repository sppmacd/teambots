package net.pumpkincell.teambots;

import net.fabricmc.loader.api.FabricLoader;
import org.spongepowered.include.com.google.gson.Gson;

import java.io.FileNotFoundException;
import java.io.FileReader;

public class Config {
    // if 0, end is always opened
    private long endOpeningTime = 0;
    private int nationCommandCooldown = 10; // in seconds

    public long getTimeLeftToEndOpeningMS() {
        return endOpeningTime * 1000 - System.currentTimeMillis();
    }

    public static Config load() throws FileNotFoundException {
        var configPath = FabricLoader.getInstance().getConfigDir().resolve("teambots.json");
        var gson = new Gson();
        return gson.fromJson(new FileReader(configPath.toString()), Config.class);
    }

    public int getNationCommandCooldownMS() {
        return nationCommandCooldown * 1000;
    }

    public long getEndOpeningTimeMS() {
        return endOpeningTime * 1000;
    }
}
