package com.massivecraft.factions.listeners;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import com.massivecraft.factions.Board;
import com.massivecraft.factions.FLocation;
import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FPlayers;
import com.massivecraft.factions.Faction;
import com.massivecraft.factions.Factions;
import com.massivecraft.factions.FactionsPlugin;
import com.massivecraft.factions.config.file.MainConfig;
import com.massivecraft.factions.data.MemoryFPlayer;
import com.massivecraft.factions.gui.GUI;
import com.massivecraft.factions.perms.PermissibleAction;
import com.massivecraft.factions.perms.Relation;
import com.massivecraft.factions.perms.Role;
import com.massivecraft.factions.struct.Permission;
import com.massivecraft.factions.util.TL;
import com.massivecraft.factions.util.TextUtil;
import com.massivecraft.factions.util.VisualizeUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.NumberConversions;


public final class FactionsPlayerListener extends AbstractListener {

  private final FactionsPlugin plugin;

  // Holds the next time a player can have a map shown.
  private final HashMap<UUID, Long> showTimes = new HashMap<>();
  private final Map<String, InteractAttemptSpam> interactSpammers = new HashMap<>();

  public FactionsPlayerListener(final FactionsPlugin plugin) {
    this.plugin = plugin;
    for (final Player player : plugin.getServer().getOnlinePlayers()) {
      initPlayer(player);
    }
  }

  public static boolean preventCommand(String fullCmd, final Player player) {
    final MainConfig.Factions.Protection protection =
        FactionsPlugin.getInstance().conf().factions().protection();
    if ((protection.getTerritoryNeutralDenyCommands().isEmpty() &&
        protection.getTerritoryEnemyDenyCommands().isEmpty() &&
        protection.getPermanentFactionMemberDenyCommands().isEmpty() &&
        protection.getWildernessDenyCommands().isEmpty() &&
        protection.getTerritoryAllyDenyCommands().isEmpty() &&
        protection.getWarzoneDenyCommands().isEmpty())) {
      return false;
    }

    fullCmd = fullCmd.toLowerCase();

    final FPlayer me = FPlayers.getInstance().getByPlayer(player);

    final String shortCmd;  // command without the slash at the beginning
    if (fullCmd.startsWith("/")) {
      shortCmd = fullCmd.substring(1);
    } else {
      shortCmd = fullCmd;
      fullCmd = "/" + fullCmd;
    }

    if (me.hasFaction() &&
        !me.isAdminBypassing() &&
        !protection.getPermanentFactionMemberDenyCommands().isEmpty() &&
        me.getFaction().isPermanent() &&
        isCommandInSet(fullCmd, shortCmd, protection.getPermanentFactionMemberDenyCommands())) {
      me.msg(TL.PLAYER_COMMAND_PERMANENT, fullCmd);
      return true;
    }

    final Faction at = Board.getInstance().getFactionAt(me.getLastStoodAt());
    if (at.isWilderness() && !protection.getWildernessDenyCommands().isEmpty() && !me
        .isAdminBypassing() && isCommandInSet(fullCmd, shortCmd,
        protection.getWildernessDenyCommands())) {
      me.msg(TL.PLAYER_COMMAND_WILDERNESS, fullCmd);
      return true;
    }

    final Relation rel = at.getRelationTo(me);
    if (at.isNormal() && rel.isAlly() && !protection.getTerritoryAllyDenyCommands().isEmpty() && !me
        .isAdminBypassing() && isCommandInSet(fullCmd, shortCmd,
        protection.getTerritoryAllyDenyCommands())) {
      me.msg(TL.PLAYER_COMMAND_ALLY, fullCmd);
      return true;
    }

    if (at.isNormal() && rel.isNeutral() && !protection.getTerritoryNeutralDenyCommands().isEmpty() && !me
        .isAdminBypassing() && isCommandInSet(fullCmd, shortCmd,
        protection.getTerritoryNeutralDenyCommands())) {
      me.msg(TL.PLAYER_COMMAND_NEUTRAL, fullCmd);
      return true;
    }

    if (at.isNormal() && rel.isEnemy() && !protection.getTerritoryEnemyDenyCommands().isEmpty() && !me
        .isAdminBypassing() && isCommandInSet(fullCmd, shortCmd,
        protection.getTerritoryEnemyDenyCommands())) {
      me.msg(TL.PLAYER_COMMAND_ENEMY, fullCmd);
      return true;
    }

    if (at.isWarZone() && !protection.getWarzoneDenyCommands().isEmpty() && !me
        .isAdminBypassing() && isCommandInSet(fullCmd, shortCmd, protection.getWarzoneDenyCommands())) {
      me.msg(TL.PLAYER_COMMAND_WARZONE, fullCmd);
      return true;
    }

    return false;
  }

  private static boolean isCommandInSet(final String fullCmd, final String shortCmd,
                                        final Set<String> set) {
    for (String string : set) {
      if (string == null) {
        continue;
      }
      string = string.toLowerCase();
      if (fullCmd.startsWith(string) || shortCmd.startsWith(string)) {
        return true;
      }
    }
    return false;
  }

  @EventHandler(priority = EventPriority.NORMAL)
  public void onPlayerJoin(final PlayerJoinEvent event) {
    initPlayer(event.getPlayer());
    this.plugin.updatesOnJoin(event.getPlayer());
  }

  private void initPlayer(final Player player) {
    // Make sure that all online players do have a fplayer.
    final FPlayer me = FPlayers.getInstance().getByPlayer(player);
    ((MemoryFPlayer) me).setName(player.getName());

    this.plugin.getLandRaidControl().onJoin(me);
    // Update the lastLoginTime for this fplayer
    me.setLastLoginTime(System.currentTimeMillis());

    // Store player's current FLocation and notify them where they are
    me.setLastStoodAt(new FLocation(player.getLocation()));

    me.login(); // set kills / deaths

    if (me.isSpyingChat() && !player.hasPermission(Permission.CHATSPY.node)) {
      me.setSpyingChat(false);
      FactionsPlugin.getInstance().log(Level.INFO,
          "Found %s spying chat without permission on login. Disabled their chat spying.",
          player.getName());
    }

    if (me.isAdminBypassing() && !player.hasPermission(Permission.BYPASS.node)) {
      me.setIsAdminBypassing(false);
      FactionsPlugin.getInstance().log(Level.INFO,
          "Found %s on admin Bypass without permission on login. Disabled it for them.",
          player.getName());
    }

    if (plugin.worldUtil().isEnabled(player.getWorld())) {
      this.initFactionWorld(me);
    }
  }

  private void initFactionWorld(final FPlayer me) {
    // Check for Faction announcements. Let's delay this so they actually see it.
    new BukkitRunnable() {
      @Override
      public void run() {
        if (me.isOnline()) {
          me.getFaction().sendUnreadAnnouncements(me);
        }
      }
    }.runTaskLater(FactionsPlugin.getInstance(), 33L); // Don't ask me why.

    final Faction myFaction = me.getFaction();
    if (!myFaction.isWilderness()) {
      for (final FPlayer other : myFaction.getFPlayersWhereOnline(true)) {
        if (other != me && other.isMonitoringJoins()) {
          other.msg(TL.FACTION_LOGIN, me.getName());
        }
      }
    }

    // If they have the permission, don't let them autoleave. Bad inverted setter :\
    me.setAutoLeave(!me.getPlayer().hasPermission(Permission.AUTO_LEAVE_BYPASS.node));
    me.setTakeFallDamage(true);
    if (plugin.conf().commands().fly().isEnable() && me.isFlying()) { // TODO allow flight to continue
      me.setFlying(false);
    }

    if (FactionsPlugin.getInstance().getSeeChunkUtil() != null) {
      FactionsPlugin.getInstance().getSeeChunkUtil()
          .updatePlayerInfo(UUID.fromString(me.getId()), me.isSeeingChunk());
    }
  }

  @EventHandler(priority = EventPriority.NORMAL)
  public void onPlayerQuit(final PlayerQuitEvent event) {
    final FPlayer me = FPlayers.getInstance().getByPlayer(event.getPlayer());

    FactionsPlugin.getInstance().getLandRaidControl().onQuit(me);
    // and update their last login time to point to when the logged off, for auto-remove routine
    me.setLastLoginTime(System.currentTimeMillis());

    me.logout(); // cache kills / deaths

    // if player is waiting for fstuck teleport but leaves, remove
    if (FactionsPlugin.getInstance().getStuckMap().containsKey(me.getPlayer().getUniqueId())) {
      FPlayers.getInstance().getByPlayer(me.getPlayer()).msg(TL.COMMAND_STUCK_CANCELLED);
      FactionsPlugin.getInstance().getStuckMap().remove(me.getPlayer().getUniqueId());
      FactionsPlugin.getInstance().getTimers().remove(me.getPlayer().getUniqueId());
    }

    final Faction myFaction = me.getFaction();
    if (!myFaction.isWilderness()) {
      myFaction.memberLoggedOff();
    }

    if (!myFaction.isWilderness()) {
      for (final FPlayer player : myFaction.getFPlayersWhereOnline(true)) {
        if (player != me && player.isMonitoringJoins()) {
          player.msg(TL.FACTION_LOGOUT, me.getName());
        }
      }
    }

    if (FactionsPlugin.getInstance().getSeeChunkUtil() != null) {
      FactionsPlugin.getInstance().getSeeChunkUtil()
          .updatePlayerInfo(UUID.fromString(me.getId()), false);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onPlayerMove(final PlayerMoveEvent event) {
    if (!plugin.worldUtil().isEnabled(event.getPlayer().getWorld())) {
      return;
    }

    final Player player = event.getPlayer();
    final FPlayer me = FPlayers.getInstance().getByPlayer(player);

    final Location locFrom = event.getFrom();
    final Location locTo = event.getTo();
    // (not) moving between blocks
    if (locFrom.getBlockX() != locTo.getBlockX() || locFrom.getBlockZ() != locTo.getBlockZ()) {
      VisualizeUtil.clear(player); // clear visualization
      if (me.isWarmingUp()) {
        me.clearWarmup();
        me.msg(TL.WARMUPS_CANCELLED);
      }
    }

    // (not) moving between chunks
    if (locFrom.getBlockX() >> 4 == locTo.getBlockX() >> 4 &&
        locFrom.getBlockZ() >> 4 == locTo.getBlockZ() >> 4 && locFrom.getWorld() == locTo.getWorld()) {
      return;
    }

    final FLocation fLocFrom = me.getLastStoodAt();
    final FLocation fLocTo = new FLocation(locTo);

    // set new flocation
    if (fLocFrom.equals(fLocTo)) {
      return;
    } else {
      me.setLastStoodAt(fLocTo);
    }

    if (me.getAutoClaimFor() != null) {
      me.attemptClaim(me.getAutoClaimFor(), event.getTo(), true);
    } else if (me.isAutoSafeClaimEnabled()) {
      if (!Permission.MANAGE_SAFE_ZONE.has(player)) {
        me.setIsAutoSafeClaimEnabled(false);
      } else {
        if (!Board.getInstance().getFactionAt(fLocTo).isSafeZone()) {
          Board.getInstance().setFactionAt(Factions.getInstance().getSafeZone(), fLocTo);
          me.msg(TL.PLAYER_SAFEAUTO);
        }
      }
    } else if (me.isAutoWarClaimEnabled()) {
      if (!Permission.MANAGE_WAR_ZONE.has(player)) {
        me.setIsAutoWarClaimEnabled(false);
      } else {
        if (!Board.getInstance().getFactionAt(fLocTo).isWarZone()) {
          Board.getInstance().setFactionAt(Factions.getInstance().getWarZone(), fLocTo);
          me.msg(TL.PLAYER_WARAUTO);
        }
      }
    }

    // Did we change "host"(faction)?
    final Faction factionFrom = Board.getInstance().getFactionAt(fLocFrom);
    final Faction factionTo = Board.getInstance().getFactionAt(fLocTo);
    final boolean changedFaction = factionFrom != factionTo;

    if (plugin.conf().commands().fly().isEnable() && changedFaction && !me.isAdminBypassing()) {
      final boolean canFly = me.canFlyAtLocation();
      if (me.isFlying() && !canFly) {
        me.setFlying(false);
      } else if (me.isAutoFlying() && !me.isFlying() && canFly) {
        me.setFlying(true);
      }
    }

    if (me.isMapAutoUpdating()) {
      if (!showTimes.containsKey(player.getUniqueId()) || (showTimes.get(player.getUniqueId()) < System
          .currentTimeMillis())) {
        me.sendFancyMessage(Board.getInstance().getMap(me, fLocTo, player.getLocation().getYaw()));
        showTimes.put(player.getUniqueId(),
            System.currentTimeMillis() + FactionsPlugin.getInstance().conf().commands().map()
                .getCooldown());
      }
    } else {
      final Faction myFaction = me.getFaction();
      final String ownersTo = myFaction.getOwnerListString(fLocTo);

      if (changedFaction) {
        me.sendFactionHereMessage(factionFrom);
        if (FactionsPlugin.getInstance().conf().factions().ownedArea().isEnabled() && FactionsPlugin
            .getInstance().conf().factions().ownedArea()
            .isMessageOnBorder() && myFaction == factionTo && !ownersTo.isEmpty()) {
          me.sendMessage(TL.GENERIC_OWNERS.format(ownersTo));
        }
      } else if (FactionsPlugin.getInstance().conf().factions().ownedArea()
          .isEnabled() && FactionsPlugin.getInstance().conf().factions().ownedArea()
          .isMessageInsideTerritory() && myFaction == factionTo && !myFaction.isWilderness()) {
        final String ownersFrom = myFaction.getOwnerListString(fLocFrom);
        if (FactionsPlugin.getInstance().conf().factions().ownedArea()
            .isMessageByChunk() || !ownersFrom.equals(ownersTo)) {
          if (!ownersTo.isEmpty()) {
            me.sendMessage(TL.GENERIC_OWNERS.format(ownersTo));
          } else if (!TL.GENERIC_PUBLICLAND.toString().isEmpty()) {
            me.sendMessage(TL.GENERIC_PUBLICLAND.toString());
          }
        }
      }
    }
  }

  @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
  public void onPlayerInteract(final PlayerInteractEntityEvent event) {
    if (!plugin.worldUtil().isEnabled(event.getPlayer().getWorld())) {
      return;
    }

    switch (event.getRightClicked().getType()) {
      case ITEM_FRAME:
        if (!canPlayerUseBlock(event.getPlayer(), Material.ITEM_FRAME,
            event.getRightClicked().getLocation(), false)) {
          event.setCancelled(true);
        }
        break;
      case HORSE:
      case PIG:
      case LEASH_HITCH:
      case MINECART_CHEST:
      case MINECART_FURNACE:
      case MINECART_HOPPER:
        if (!FactionsPlugin.getInstance().conf().factions().protection().getEntityInteractExceptions()
            .contains(event.getRightClicked().getType().name()) &&
            !this.playerCanInteractHere(event.getPlayer(), event.getRightClicked().getLocation())) {
          event.setCancelled(true);
        }
        break;
    }
  }

  @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
  public void onPlayerInteract(final PlayerInteractEvent event) {
    if (!plugin.worldUtil().isEnabled(event.getPlayer().getWorld())) {
      return;
    }

    // prevent pearling within block placement distance
    if (event.getAction() == Action.RIGHT_CLICK_BLOCK &&
        event.getPlayer().getItemInHand().getType() == Material.ENDER_PEARL) {

      event.setCancelled(true);
      return;
    }

    // only need to check right-clicks and physical as of MC 1.4+; good performance boost
    if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.PHYSICAL) {
      return;
    }

    final Block block = event.getClickedBlock();

    // check if clicked on air
    if (block == null) {
      return;
    }

    final Player player = event.getPlayer();

    if (!canPlayerUseBlock(player, block.getType(), block.getLocation(), false)) {
      event.setCancelled(true);
      if (block.getType().name().endsWith("_PLATE")) {
        return;
      }
      if (FactionsPlugin.getInstance().conf().exploits().isInteractionSpam()) {
        final String name = player.getName();
        InteractAttemptSpam attempt = interactSpammers.get(name);
        if (attempt == null) {
          attempt = new InteractAttemptSpam();
          interactSpammers.put(name, attempt);
        }
        final int count = attempt.increment();
        if (count >= 10) {
          final FPlayer me = FPlayers.getInstance().getByPlayer(player);
          me.msg(TL.PLAYER_OUCH);
          player.damage(NumberConversions.floor((double) count / 10));
        }
      }
      return;
    }

    if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
      return;  // only interested on right-clicks for below
    }

    final ItemStack item;
    if ((item = event.getItem()) != null) {
      boolean ohNo = false;
      switch (item.getType()) {
        case ARMOR_STAND:
          //case ENDER_CRYSTAL:
        case MINECART:
        case STORAGE_MINECART:
        case COMMAND_MINECART:
        case EXPLOSIVE_MINECART:
        case POWERED_MINECART:
        case HOPPER_MINECART:
          ohNo = true;
      }
      if (ohNo &&
          !FactionsPlugin.getInstance().conf().factions().specialCase().getIgnoreBuildMaterials()
              .contains(item.getType()) &&
          !FactionsBlockListener.playerCanBuildDestroyBlock(event.getPlayer(),
              event.getClickedBlock().getRelative(event.getBlockFace()).getLocation(),
              PermissibleAction.BUILD, false)) {
        event.setCancelled(true);
      }
    }

    if (!playerCanUseItemHere(player, block.getLocation(), event.getMaterial(), false)) {
      event.setCancelled(true);
    }
  }

  public boolean playerCanUseItemHere(final Player player, final Location location, final Material material,
                                      final boolean justCheck) {
    final String name = player.getName();
    final MainConfig.Factions facConf = FactionsPlugin.getInstance().conf().factions();
    if (facConf.protection().getPlayersWhoBypassAllProtection().contains(name)) {
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

    if (otherFaction.hasPlayersOnline()) {
      if (!facConf.protection().getTerritoryDenyUsageMaterials().contains(material)) {
        return true; // Item isn't one we're preventing for online factions.
      }
    } else {
      if (!facConf.protection().getTerritoryDenyUsageMaterialsWhenOffline().contains(material)) {
        return true; // Item isn't one we're preventing for offline factions.
      }
    }

    if (otherFaction.isWilderness()) {
      if (!facConf.protection().isWildernessDenyUsage() || facConf.protection()
          .getWorldsNoWildernessProtection().contains(location.getWorld().getName())) {
        return true; // This is not faction territory. Use whatever you like here.
      }

      if (!justCheck) {
        me.msg(TL.PLAYER_USE_WILDERNESS, TextUtil.getMaterialName(material));
      }

      return false;
    } else if (otherFaction.isSafeZone()) {
      if (!facConf.protection().isSafeZoneDenyUsage() || Permission.MANAGE_SAFE_ZONE.has(player)) {
        return true;
      }

      if (!justCheck) {
        me.msg(TL.PLAYER_USE_SAFEZONE, TextUtil.getMaterialName(material));
      }

      return false;
    } else if (otherFaction.isWarZone()) {
      if (!facConf.protection().isWarZoneDenyUsage() || Permission.MANAGE_WAR_ZONE.has(player)) {
        return true;
      }

      if (!justCheck) {
        me.msg(TL.PLAYER_USE_WARZONE, TextUtil.getMaterialName(material));
      }

      return false;
    }

    if (!otherFaction.hasAccess(me, PermissibleAction.ITEM)) {
      if (!justCheck) {
        me.msg(TL.PLAYER_USE_TERRITORY, TextUtil.getMaterialName(material),
            otherFaction.getTag(me.getFaction()));
      }
      return false;
    }

    // Also cancel if player doesn't have ownership rights for this claim
    if (facConf.ownedArea().isEnabled() && facConf.ownedArea().isDenyUsage() && !otherFaction
        .playerHasOwnershipRights(me, loc)) {
      if (!justCheck) {
        me.msg(TL.PLAYER_USE_OWNED, TextUtil.getMaterialName(material),
            otherFaction.getOwnerListString(loc));
      }

      return false;
    }

    return true;
  }

  @EventHandler(priority = EventPriority.HIGH)
  public void onPlayerRespawn(final PlayerRespawnEvent event) {
    if (!plugin.worldUtil().isEnabled(event.getPlayer().getWorld())) {
      return;
    }

    final FPlayer me = FPlayers.getInstance().getByPlayer(event.getPlayer());

    FactionsPlugin.getInstance().getLandRaidControl().onRespawn(me);

    final Location home = me.getFaction().getHome();
    final MainConfig.Factions facConf = FactionsPlugin.getInstance().conf().factions();
    if (facConf.homes().isEnabled() &&
        facConf.homes().isTeleportToOnDeath() &&
        home != null &&
        (facConf.landRaidControl().power().isRespawnHomeFromNoPowerLossWorlds() || !facConf
            .landRaidControl().power().getWorldsNoPowerLoss()
            .contains(event.getPlayer().getWorld().getName()))) {
      event.setRespawnLocation(home);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onTeleport(final PlayerTeleportEvent event) {
    final FPlayer me = FPlayers.getInstance().getByPlayer(event.getPlayer());
    final boolean isEnabled = plugin.worldUtil().isEnabled(event.getTo().getWorld());
    if (!isEnabled) {
      if (me.isFlying()) {
        me.setFlying(false);
      }
      return;
    }
    if (!event.getFrom().getWorld().equals(event.getTo().getWorld()) && !plugin.worldUtil()
        .isEnabled(event.getPlayer().getWorld())) {
      FactionsPlugin.getInstance().getLandRaidControl().update(me);
      this.initFactionWorld(me);
    }

    final FLocation to = new FLocation(event.getTo());
    me.setLastStoodAt(to);

    // Check the location they're teleporting to and check if they can fly there.
    if (plugin.conf().commands().fly().isEnable() && !me.isAdminBypassing()) {
      final boolean canFly = me.canFlyAtLocation(to);
      if (me.isFlying() && !canFly) {
        me.setFlying(false, false);
      } else if (me.isAutoFlying() && !me.isFlying() && canFly) {
        me.setFlying(true);
      }
    }

  }

  // For some reason onPlayerInteract() sometimes misses bucket events depending on distance (something
  // like 2-3 blocks away isn't detected),
  // but these separate bucket events below always fire without fail
  @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
  public void onPlayerBucketEmpty(final PlayerBucketEmptyEvent event) {
    if (!plugin.worldUtil().isEnabled(event.getPlayer().getWorld())) {
      return;
    }

    final Block block = event.getBlockClicked();
    final Player player = event.getPlayer();

    if (!playerCanUseItemHere(player, block.getLocation(), event.getBucket(), false)) {
      event.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
  public void onPlayerBucketFill(final PlayerBucketFillEvent event) {
    if (!plugin.worldUtil().isEnabled(event.getPlayer().getWorld())) {
      return;
    }

    final Block block = event.getBlockClicked();
    final Player player = event.getPlayer();

    if (!playerCanUseItemHere(player, block.getLocation(), event.getBucket(), false)) {
      event.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.HIGH)
  public void onPlayerInteractGUI(final InventoryClickEvent event) {
    if (!plugin.worldUtil().isEnabled(event.getWhoClicked().getWorld())) {
      return;
    }

    final Inventory clickedInventory = getClickedInventory(event);
    if (clickedInventory == null) {
      return;
    }
    if (clickedInventory.getHolder() instanceof GUI) {
      event.setCancelled(true);
      final GUI<?> ui = (GUI<?>) clickedInventory.getHolder();
      ui.click(event.getRawSlot(), event.getClick());
    }
  }

  private Inventory getClickedInventory(final InventoryClickEvent event) {
    final int rawSlot = event.getRawSlot();
    final InventoryView view = event.getView();
    if (rawSlot < 0 || rawSlot >= view
        .countSlots()) { // < 0 check also covers situation of InventoryView.OUTSIDE (-999)
      return null;
    }
    if (rawSlot < view.getTopInventory().getSize()) {
      return view.getTopInventory();
    } else {
      return view.getBottomInventory();
    }
  }

  @EventHandler(priority = EventPriority.HIGH)
  public void onPlayerMoveGUI(final InventoryDragEvent event) {
    if (!plugin.worldUtil().isEnabled(event.getWhoClicked().getWorld())) {
      return;
    }

    if (event.getInventory().getHolder() instanceof GUI) {
      event.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
  public void onPlayerKick(final PlayerKickEvent event) {
    final FPlayer badGuy = FPlayers.getInstance().getByPlayer(event.getPlayer());
    if (badGuy == null) {
      return;
    }

    // if player was banned (not just kicked), get rid of their stored info
    if (FactionsPlugin.getInstance().conf().factions().other().isRemovePlayerDataWhenBanned() && event
        .getReason().equals("Banned by admin.")) {
      if (badGuy.getRole() == Role.ADMIN) {
        badGuy.getFaction().promoteNewLeader();
      }

      badGuy.leave(false);
      badGuy.remove();
    }
  }

  @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
  public void onPlayerCommandPreprocess(final PlayerCommandPreprocessEvent event) {
    if (!plugin.worldUtil().isEnabled(event.getPlayer().getWorld())) {
      return;
    }

    if (FactionsPlayerListener.preventCommand(event.getMessage(), event.getPlayer())) {
      if (plugin.logPlayerCommands()) {
        plugin.getLogger()
            .info("[PLAYER_COMMAND] " + event.getPlayer().getName() + ": " + event.getMessage());
      }
      event.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void onPlayerPreLogin(final PlayerLoginEvent event) {
    FPlayers.getInstance().getByPlayer(event.getPlayer());
  }

  private static class InteractAttemptSpam {

    private int attempts = 0;
    private long lastAttempt = System.currentTimeMillis();

    // returns the current attempt count
    public int increment() {
      final long Now = System.currentTimeMillis();
      if (Now > lastAttempt + 2000) {
        attempts = 1;
      } else {
        attempts++;
      }
      lastAttempt = Now;
      return attempts;
    }

  }

}
