package com.massivecraft.factions.util;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import me.ufo.shaded.com.google.gson.Gson;
import me.ufo.shaded.com.google.gson.TypeAdapter;
import me.ufo.shaded.com.google.gson.TypeAdapterFactory;
import me.ufo.shaded.com.google.gson.annotations.SerializedName;
import me.ufo.shaded.com.google.gson.reflect.TypeToken;
import me.ufo.shaded.com.google.gson.stream.JsonReader;
import me.ufo.shaded.com.google.gson.stream.JsonToken;
import me.ufo.shaded.com.google.gson.stream.JsonWriter;

public final class EnumTypeAdapter<T extends Enum<T>> extends TypeAdapter<T> {

  public static final TypeAdapterFactory ENUM_FACTORY = newEnumTypeHierarchyFactory();
  private final Map<String, T> nameToConstant = new HashMap<>();
  private final Map<T, String> constantToName = new HashMap<>();

  public EnumTypeAdapter(Class<T> classOfT) {
    try {
      for (T constant : classOfT.getEnumConstants()) {
        String name = constant.name();
        SerializedName annotation = classOfT.getField(name).getAnnotation(SerializedName.class);
        if (annotation != null) {
          name = annotation.value();
        }
        nameToConstant.put(name, constant);
        constantToName.put(constant, name);
      }
    } catch (NoSuchFieldException e) {
      // ignore since it could be a modified enum
    }
  }

  public T read(JsonReader in) throws IOException {
    if (in.peek() == JsonToken.NULL) {
      in.nextNull();
      return null;
    }
    return nameToConstant.get(in.nextString());
  }

  public void write(JsonWriter out, T value) throws IOException {
    out.value(value == null ? null : constantToName.get(value));
  }

  public static <TT> TypeAdapterFactory newEnumTypeHierarchyFactory() {
    return new TypeAdapterFactory() {
      @SuppressWarnings({"unchecked"})
      public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
        Class<? super T> rawType = typeToken.getRawType();
        if (!Enum.class.isAssignableFrom(rawType) || rawType == Enum.class) {
          return null;
        }
        if (!rawType.isEnum()) {
          rawType = rawType.getSuperclass(); // handle anonymous subclasses
        }
        return (TypeAdapter<T>) new EnumTypeAdapter(rawType);
      }
    };
  }

}
