package com.massivecraft.factions.data.json;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FPlayers;
import com.massivecraft.factions.FactionsPlugin;
import com.massivecraft.factions.data.MemoryFPlayer;
import com.massivecraft.factions.data.MemoryFPlayers;
import com.massivecraft.factions.util.DiscUtil;

public class JSONFPlayers extends MemoryFPlayers {

  private final File file;

  public JSONFPlayers() {
    if (FactionsPlugin.getInstance().getServerUUID() == null) {
      FactionsPlugin.getInstance().grumpException(new RuntimeException());
    }
    file = new File(FactionsPlugin.getInstance().getDataFolder(), "data/players.json");
  }

  public Gson getGson() {
    return FactionsPlugin.getInstance().getGson();
  }

  public void setGson(Gson gson) {
    // NOOP
  }

  public void convertFrom(MemoryFPlayers old) {
    old.fPlayers.forEach((id, faction) -> this.fPlayers.put(id, new JSONFPlayer((MemoryFPlayer) faction)));
    forceSave();
    FPlayers.instance = this;
  }

  public void forceSave() {
    forceSave(true);
  }

  public void forceSave(boolean sync) {
    final Map<String, JSONFPlayer> entitiesThatShouldBeSaved = new HashMap<>();
    for (FPlayer entity : this.fPlayers.values()) {
      if (((MemoryFPlayer) entity).shouldBeSaved()) {
        entitiesThatShouldBeSaved.put(entity.getId(), (JSONFPlayer) entity);
      }
    }

    saveCore(file, entitiesThatShouldBeSaved, sync);
  }

  private boolean saveCore(File target, Map<String, JSONFPlayer> data, boolean sync) {
    return DiscUtil.writeCatch(target, FactionsPlugin.getInstance().getGson().toJson(data), sync);
  }

  public int load() {
    Map<String, JSONFPlayer> fplayers = this.loadCore();
    if (fplayers == null) {
      return 0;
    }
    this.fPlayers.clear();
    this.fPlayers.putAll(fplayers);
    return fPlayers.size();
  }

  private Map<String, JSONFPlayer> loadCore() {
    if (!this.file.exists()) {
      return new HashMap<>();
    }

    String content = DiscUtil.readCatch(this.file);
    if (content == null) {
      return null;
    }

    Map<String, JSONFPlayer> data =
        FactionsPlugin.getInstance().getGson().fromJson(content, new TypeToken<Map<String, JSONFPlayer>>() {
        }.getType());


    return data;
  }

  @Override
  public FPlayer generateFPlayer(String id) {
    FPlayer player = new JSONFPlayer(id);
    this.fPlayers.put(player.getId(), player);
    return player;
  }

}
