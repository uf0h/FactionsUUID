package com.massivecraft.factions.integration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import com.massivecraft.factions.FactionsPlugin;
import com.sk89q.worldedit.BlockVector;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.DefaultFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import static com.sk89q.worldguard.bukkit.BukkitUtil.toVector;


/**
 * Worldguard Region Checking.
 *
 * @author Spathizilla
 */
public class Worldguard {

    private static WorldGuardPlugin worldguard;

    public static Plugin getWorldguard() {
        return worldguard;
    }

    public static void setup(final Plugin wg) {
        worldguard = (WorldGuardPlugin) wg;
        final String version = worldguard.getDescription().getVersion();
        if (version.startsWith("6")) {
            FactionsPlugin.getInstance().log("Found support for WorldGuard version " + version);
        } else {
            FactionsPlugin.getInstance()
                .log(Level.WARNING, "Found WorldGuard but couldn't support this version: " + version);
        }
    }

    // PVP Flag check
    // Returns:
    //   True: PVP is allowed
    //   False: PVP is disallowed
    public static boolean isPVP(final Player player) {
        final Location loc = player.getLocation();
        final World world = loc.getWorld();
        final Vector pt = toVector(loc);

        final RegionManager regionManager = worldguard.getRegionManager(world);
        final ApplicableRegionSet set = regionManager.getApplicableRegions(pt);
        return set.allows(DefaultFlag.PVP);
    }

    // Check if player can build at location by worldguards rules.
    // Returns:
    //	True: Player can build in the region.
    //	False: Player can not build in the region.
    public static boolean playerCanBuild(final Player player, final Location loc) {
        final World world = loc.getWorld();
        final Vector pt = toVector(loc);

        return worldguard.getRegionManager(world).getApplicableRegions(pt).size() > 0 && worldguard
            .canBuild(player, loc);
    }

    // Check for Regions in chunk the chunk
    // Returns:
    //   True: Regions found within chunk
    //   False: No regions found within chunk
    public static boolean checkForRegionsInChunk(final Chunk chunk) {
        final World world = chunk.getWorld();
        final int minChunkX = chunk.getX() << 4;
        final int minChunkZ = chunk.getZ() << 4;
        final int maxChunkX = minChunkX + 15;
        final int maxChunkZ = minChunkZ + 15;

        final int worldHeight = world.getMaxHeight(); // Allow for heights other than default

        final BlockVector minChunk = new BlockVector(minChunkX, 0, minChunkZ);
        final BlockVector maxChunk = new BlockVector(maxChunkX, worldHeight, maxChunkZ);

        final RegionManager regionManager = worldguard.getRegionManager(world);
        final ProtectedCuboidRegion region = new ProtectedCuboidRegion("wgfactionoverlapcheck", minChunk, maxChunk);
        final Map<String, ProtectedRegion> allregions = regionManager.getRegions();
        final Collection<ProtectedRegion> allregionslist = new ArrayList<>(allregions.values());
        final List<ProtectedRegion> overlaps;
        boolean foundregions = false;

        try {
            overlaps = region.getIntersectingRegions(allregionslist);
            foundregions = overlaps != null && !overlaps.isEmpty();
        } catch (final Exception e) {
            e.printStackTrace();
        }

        return foundregions;
    }

}
