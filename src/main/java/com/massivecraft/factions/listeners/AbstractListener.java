package com.massivecraft.factions.listeners;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import com.massivecraft.factions.Board;
import com.massivecraft.factions.FLocation;
import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FPlayers;
import com.massivecraft.factions.Faction;
import com.massivecraft.factions.FactionsPlugin;
import com.massivecraft.factions.config.file.MainConfig;
import com.massivecraft.factions.perms.PermissibleAction;
import com.massivecraft.factions.perms.Relation;
import com.massivecraft.factions.struct.Permission;
import com.massivecraft.factions.util.TL;
import com.massivecraft.factions.util.TextUtil;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Wither;
import org.bukkit.entity.WitherSkull;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Listener;


public abstract class AbstractListener implements Listener {

  public boolean playerCanInteractHere(final Player player, final Location location) {
    final String name = player.getName();
    if (FactionsPlugin.getInstance().conf().factions().protection().getPlayersWhoBypassAllProtection()
        .contains(name)) {
      return true;
    }

    final FPlayer me = FPlayers.getInstance().getByPlayer(player);
    if (me.isAdminBypassing()) {
      return true;
    }

    final FLocation loc = new FLocation(location);
    final Faction otherFaction = Board.getInstance().getFactionAt(loc);

    if (FactionsPlugin.getInstance().getLandRaidControl().isRaidable(otherFaction)) {
      return true;
    }

    final MainConfig.Factions.Protection protection =
        FactionsPlugin.getInstance().conf().factions().protection();
    if (otherFaction.isWilderness()) {
      if (!protection.isWildernessDenyUsage() || protection.getWorldsNoWildernessProtection()
          .contains(location.getWorld().getName())) {
        return true; // This is not faction territory. Use whatever you like here.
      }
      me.msg(TL.PLAYER_USE_WILDERNESS, "this");
      return false;
    } else if (otherFaction.isSafeZone()) {
      if (!protection.isSafeZoneDenyUsage() || Permission.MANAGE_SAFE_ZONE.has(player)) {
        return true;
      }
      me.msg(TL.PLAYER_USE_SAFEZONE, "this");
      return false;
    } else if (otherFaction.isWarZone()) {
      if (!protection.isWarZoneDenyUsage() || Permission.MANAGE_WAR_ZONE.has(player)) {
        return true;
      }
      me.msg(TL.PLAYER_USE_WARZONE, "this");

      return false;
    }

    final boolean access = otherFaction.hasAccess(me, PermissibleAction.ITEM);

    // Cancel if we are not in our own territory
    if (!access) {
      me.msg(TL.PLAYER_USE_TERRITORY, "this", otherFaction.getTag(me.getFaction()));
      return false;
    }

    // Also cancel if player doesn't have ownership rights for this claim
    if (FactionsPlugin.getInstance().conf().factions().ownedArea().isEnabled() && FactionsPlugin
        .getInstance().conf().factions().ownedArea().isDenyUsage() && !otherFaction
        .playerHasOwnershipRights(me, loc)) {
      me.msg(TL.PLAYER_USE_OWNED, "this", otherFaction.getOwnerListString(loc));
      return false;
    }

    return true;
  }

  protected void handleExplosion(final Location loc, final Entity boomer, final Cancellable event,
                                 final List<Block> blockList) {
    if (!FactionsPlugin.getInstance().worldUtil().isEnabled(loc.getWorld())) {
      return;
    }

    if (explosionDisallowed(boomer, new FLocation(loc))) {
      event.setCancelled(true);
      return;
    }

    // tnt can only destroy blocks in at most 4 chunks with default explosion radii
    final List<Chunk> disallowedChunks = new ArrayList<>(4);

    final ListIterator<Block> blockListIterator = blockList.listIterator();
    while (blockListIterator.hasNext()) {
      final Chunk chunk = blockListIterator.next().getChunk();
      // if chunk has already been checked remove block
      if (disallowedChunks.contains(chunk)) {
        blockListIterator.remove();
      }
      // check if explosion is allowed in chunk
      else {
        if (explosionDisallowed(boomer, new FLocation(chunk))) {
          disallowedChunks.add(chunk);
          blockListIterator.remove();
        }
      }
    }

    if ((boomer instanceof TNTPrimed || boomer instanceof ExplosiveMinecart) && FactionsPlugin
        .getInstance().conf().exploits().isTntWaterlog()) {
      // TNT in water/lava doesn't normally destroy any surrounding blocks, which is usually desired
      // behavior, but...
      // this change below provides workaround for waterwalling providing perfect protection,
      // and makes cheap (non-obsidian) TNT cannons require minor maintenance between shots
      final Block center = loc.getBlock();
      if (center.isLiquid()) {
        // a single surrounding block in all 6 directions is broken if the material is weak enough
        final Block[] targets = new Block[6];
        targets[0] = center.getRelative(0, 0, 1);
        targets[1] = center.getRelative(0, 0, 1);
        targets[2] = center.getRelative(0, 1, 0);
        targets[3] = center.getRelative(0, -1, 0);
        targets[4] = center.getRelative(1, 0, 0);
        targets[5] = center.getRelative(-1, 0, 0);

        for (final Block target : targets) {
          // TODO get resistance value via NMS for future-proofing
          switch (target.getType()) {
            case AIR:
            case BEDROCK:
            case WATER:
            case LAVA:
            case OBSIDIAN:
            case PORTAL:
            case ENCHANTMENT_TABLE:
            case ANVIL:
            case ENDER_PORTAL:
            case ENDER_PORTAL_FRAME:
            case ENDER_CHEST:
              continue;
          }
          if (!explosionDisallowed(boomer, new FLocation(target.getLocation()))) {
            target.breakNaturally();
          }
        }
      }
    }
  }

  private boolean explosionDisallowed(final Entity boomer, final FLocation location) {
    final Faction faction = Board.getInstance().getFactionAt(location);
    final boolean online = faction.hasPlayersOnline();
    if (faction.noExplosionsInTerritory() || (faction.isPeaceful() && FactionsPlugin.getInstance().conf()
        .factions().specialCase().isPeacefulTerritoryDisableBoom())) {
      // faction is peaceful and has explosions set to disabled
      return true;
    }
    final MainConfig.Factions.Protection protection =
        FactionsPlugin.getInstance().conf().factions().protection();
    if (boomer instanceof Creeper && ((faction.isWilderness() && protection
        .isWildernessBlockCreepers() && !protection.getWorldsNoWildernessProtection()
        .contains(location.getWorldName())) ||
        (faction.isNormal() && (online ? protection.isTerritoryBlockCreepers() :
                                protection.isTerritoryBlockCreepersWhenOffline())) ||
        (faction.isWarZone() && protection.isWarZoneBlockCreepers()) ||
        faction.isSafeZone())) {
      // creeper which needs prevention
      return true;
    } else if (
        (boomer instanceof Fireball || boomer instanceof WitherSkull || boomer instanceof Wither) && ((faction
            .isWilderness() && protection.isWildernessBlockFireballs() && !protection
            .getWorldsNoWildernessProtection().contains(location.getWorldName())) ||
            (faction.isNormal() && (online ? protection.isTerritoryBlockFireballs() :
                                    protection.isTerritoryBlockFireballsWhenOffline())) ||
            (faction.isWarZone() && protection.isWarZoneBlockFireballs()) ||
            faction.isSafeZone())) {
      // ghast fireball which needs prevention
      // it's a bit crude just using fireball protection for Wither boss too, but I'd rather not add
      // in a whole new set of xxxBlockWitherExplosion or whatever
      return true;
    } else if ((boomer instanceof TNTPrimed || boomer instanceof ExplosiveMinecart) && ((faction
        .isWilderness() && protection.isWildernessBlockTNT() && !protection
        .getWorldsNoWildernessProtection().contains(location.getWorldName())) ||
        (faction.isNormal() && (online ? protection.isTerritoryBlockTNT() :
                                protection.isTerritoryBlockTNTWhenOffline())) ||
        (faction.isWarZone() && protection.isWarZoneBlockTNT()) ||
        (faction.isSafeZone() && protection.isSafeZoneBlockTNT()))) {
      // TNT which needs prevention
      return true;
    } else {
      return (faction.isWilderness() && protection.isWildernessBlockOtherExplosions() && !protection
          .getWorldsNoWildernessProtection().contains(location.getWorldName())) ||
          (faction.isNormal() && (online ? protection.isTerritoryBlockOtherExplosions() :
                                  protection.isTerritoryBlockOtherExplosionsWhenOffline())) ||
          (faction.isWarZone() && protection.isWarZoneBlockOtherExplosions()) ||
          (faction.isSafeZone() && protection.isSafeZoneBlockOtherExplosions());
    }
  }

  public boolean canPlayerUseBlock(final Player player, final Material material, final Location location,
                                   final boolean justCheck) {
    if (FactionsPlugin.getInstance().conf().factions().protection().getPlayersWhoBypassAllProtection()
        .contains(player.getName())) {
      return true;
    }

    final FPlayer me = FPlayers.getInstance().getByPlayer(player);
    if (me.isAdminBypassing()) {
      return true;
    }

    final FLocation loc = new FLocation(location);
    final Faction otherFaction = Board.getInstance().getFactionAt(loc);

    // no door/chest/whatever protection in wilderness, war zones, or safe zones
    if (!otherFaction.isNormal()) {
      switch (material) {
        case ITEM_FRAME:
        case ARMOR_STAND:
          return playerCanInteractHere(player, location);
      }
      return true;
    }

    if (FactionsPlugin.getInstance().getLandRaidControl().isRaidable(otherFaction)) {
      return true;
    }

    PermissibleAction action = null;

    switch (material) {
      case LEVER:
        action = PermissibleAction.LEVER;
        break;
      case STONE_BUTTON:
      case WOOD_BUTTON:
        action = PermissibleAction.BUTTON;
        break;
      case DARK_OAK_DOOR:
      case ACACIA_DOOR:
      case BIRCH_DOOR:
      case IRON_DOOR:
      case JUNGLE_DOOR:
      case SPRUCE_DOOR:
      case IRON_TRAPDOOR:
      case TRAP_DOOR:
      case WOODEN_DOOR: // TODO: check all perms again
        action = PermissibleAction.DOOR;
        break;
      case CHEST:
      case ENDER_CHEST:
      case TRAPPED_CHEST:
      case FURNACE:
      case DROPPER:
      case DISPENSER:
      case HOPPER:
      case CAULDRON:
      case BREWING_STAND:
      case ITEM_FRAME:
      case JUKEBOX:
      case ARMOR_STAND:
      case DIODE:
      case ENCHANTMENT_TABLE:
      case SOIL:
      case BEACON:
      case ANVIL:
      case FLOWER_POT:
        action = PermissibleAction.CONTAINER;
        break;
      default:
        // Check for doors that might have diff material name in old version.
        if (material.name().contains("DOOR") || material.name().contains("GATE")) {
          action = PermissibleAction.DOOR;
        }
        if (material.name().contains("BUTTON")) {
          action = PermissibleAction.BUTTON;
        }
        // Lazier than checking all the combinations
        if (material.name().contains("SHULKER") || material.name().contains("ANVIL") || material
            .name().startsWith("POTTED")) {
          action = PermissibleAction.CONTAINER;
        }
        if (material.name().endsWith("_PLATE")) {
          action = PermissibleAction.PLATE;
        }
        if (material.name().contains("SIGN")) {
          action = PermissibleAction.ITEM;
        }
        break;
    }

    if (action == null) {
      return true;
    }

    // Ignored types
    if (action == PermissibleAction.CONTAINER && FactionsPlugin.getInstance().conf().factions()
        .protection().getContainerExceptions().contains(material)) {
      return true;
    }

    // F PERM check runs through before other checks.
    if (!otherFaction.hasAccess(me, action)) {
      if (action != PermissibleAction.PLATE) {
        me.msg(TL.GENERIC_NOPERMISSION, action);
      }
      return false;
    }

    // Dupe fix.
    final Faction myFaction = me.getFaction();
    final Relation rel = myFaction.getRelationTo(otherFaction);
    if (FactionsPlugin.getInstance().conf().exploits().doPreventDuping() &&
        (!rel.isMember() || !otherFaction.playerHasOwnershipRights(me, loc))) {
      final Material mainHand = player.getItemInHand().getType();

      // Check if material is at risk for dupe in either hand.
      if (isDupeMaterial(mainHand)) {
        return false;
      }
    }

    // Also cancel if player doesn't have ownership rights for this claim
    if (FactionsPlugin.getInstance().conf().factions().ownedArea().isEnabled() && FactionsPlugin
        .getInstance().conf().factions().ownedArea().isProtectMaterials() && !otherFaction
        .playerHasOwnershipRights(me, loc)) {
      if (!justCheck) {
        me.msg(TL.PLAYER_USE_OWNED, TextUtil.getMaterialName(material),
            otherFaction.getOwnerListString(loc));
      }

      return false;
    }

    return true;
  }

  private boolean isDupeMaterial(final Material material) {
    if (material.name().toUpperCase().contains("SIGN")) {
      return true;
    }

    switch (material) {
      case CHEST:
      case TRAPPED_CHEST:
      case DARK_OAK_DOOR:
      case ACACIA_DOOR:
      case BIRCH_DOOR:
      case JUNGLE_DOOR:
      case WOODEN_DOOR:
      case SPRUCE_DOOR:
      case IRON_DOOR:
        return true;
      default:
        break;
    }

    return false;
  }

}
