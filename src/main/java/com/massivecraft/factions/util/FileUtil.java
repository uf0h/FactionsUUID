package com.massivecraft.factions.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import com.massivecraft.factions.FactionsPlugin;

public final class FileUtil {

    public static Set<File> getFilesInFolder(final String path) throws FileNotFoundException {
        final File folder = new File(path);
        if (!folder.exists()) {
            throw new FileNotFoundException(path);
        }

        final File[] filesInFolder = folder.listFiles();
        if (filesInFolder == null || filesInFolder.length == 0) {
            return new HashSet<>(0);
        }

        final Set<File> out = new HashSet<>();
        for (final File file : filesInFolder) {
            if (!file.isDirectory()) {
                if ("jar".equalsIgnoreCase(getFileExtension(file.getName()))) {
                    out.add(file);
                }
            }
        }

        return out;
    }

    private static String getFileExtension(final String fullName) {
        final String fileName = new File(fullName).getName();
        final int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex == -1) ? "" : fileName.substring(dotIndex + 1);
    }

    public static void saveResource(final ClassLoader classLoader, final String dataFolder, String resourcePath, final boolean replace) {
        if (resourcePath == null || resourcePath.equals("")) {
            throw new IllegalArgumentException("ResourcePath cannot be null or empty");
        }

        resourcePath = resourcePath.replace('\\', '/');
        final InputStream in = getResource(classLoader, resourcePath);
        if (in == null) {
            throw new IllegalArgumentException("The embedded resource '" + resourcePath + "' cannot be found.");
        }

        final File outFile = new File(dataFolder, resourcePath);
        final int lastIndex = resourcePath.lastIndexOf('/');
        final File outDir = new File(dataFolder, resourcePath.substring(0, Math.max(lastIndex, 0)));

        if (!outDir.exists()) {
            outDir.mkdirs();
        }

        try {
            if (!outFile.exists() || replace) {
                final OutputStream out = new FileOutputStream(outFile);
                final byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
                out.close();
                in.close();
            } else {
                FactionsPlugin.getInstance().getLogger().log(Level.WARNING, "Could not save " + outFile.getName() + " to " + outFile + " because " + outFile.getName() + " already exists.");
            }
        } catch (final IOException ex) {
            FactionsPlugin.getInstance().getLogger().log(Level.SEVERE, "Could not save " + outFile.getName() + " to " + outFile, ex);
        }
    }

    public static InputStream getResource(final ClassLoader classLoader, final String filename) {
        if (filename == null) {
            throw new IllegalArgumentException("Filename cannot be null");
        }

        try {
            final URL url = classLoader.getResource(filename);

            if (url == null) {
                return null;
            }

            final URLConnection connection = url.openConnection();
            connection.setUseCaches(false);
            return connection.getInputStream();
        } catch (final IOException ignored) {
            return null;
        }
    }

}
