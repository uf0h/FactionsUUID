package com.massivecraft.factions.config.transition.oldclass.v0;

import java.io.IOException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.bukkit.Material;

public class MaterialAdapter extends TypeAdapter<Material> {

    @Override
    public void write(JsonWriter out, Material value) throws IOException {
        out.value(value.name());
    }

    @Override
    public Material read(JsonReader in) throws IOException {
        return Material.getMaterial(in.nextString());
    }

}
