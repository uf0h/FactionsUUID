package com.massivecraft.factions.util;

import java.io.File;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.jar.JarFile;
import com.massivecraft.factions.FactionsPlugin;

public final class ClassUtil {

  public static <C> C loadClass(final File file, final Class<C> parent) throws Exception {
    final String classPath = new JarFile(file).getManifest().getMainAttributes().getValue("Main-Class");

    if (classPath == null || classPath.isEmpty()) {
      throw new NullPointerException("The jar file does not have a main classpath.");
    }

    final URLClassLoader loader =
      URLClassLoader.newInstance(new URL[] {file.toURI().toURL()}, FactionsPlugin.class.getClassLoader());

    final Class<?> clazz = Class.forName(classPath, true, loader);
    final Class<? extends C> newClass = clazz.asSubclass(parent);
    final Constructor<? extends C> constructor = newClass.getConstructor();

    return constructor.newInstance();
  }

}
