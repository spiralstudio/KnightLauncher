package com.lucasallegri.bootstrap;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.zip.ZipFile;

/**
 * Bootstraps instead of {@literal com.threerings.projectx.client.ProjectXApp} for loading mods and language packs.
 * <p>
 * Makes sure the "META-INF/MANIFEST.MF" is included in each code mod,
 * and the main class must be specified.
 *
 * @author Leego Yih
 */
public class ProjectXBootstrap {
  private static final String USER_DIR = System.getProperty("user.dir");
  private static final String CODE_MODS_DIR = USER_DIR + "/code-mods/";
  private static final String MANIFEST_PATH = "META-INF/MANIFEST.MF";
  private static final String MAIN_CLASS_KEY = "Main-Class:";
  private static final String NAME_KEY = "Name:";
  private static final Properties configs = new Properties();
  private static final Logger logger = Logger.getLogger(ProjectXBootstrap.class.getName());

  public static void main(String[] args) throws Exception {
    initLogger("bootstrap.log");

    loadConfigs();

    loadJarMods();

    com.threerings.projectx.client.ProjectXApp.main(args);
  }

  static void initLogger(String filename) throws Exception {
    String oldLogPath = USER_DIR + File.separator + "old-" + filename;
    String newLogPath = USER_DIR + File.separator + filename;

    File oldLogFile = new File(oldLogPath);
    if (oldLogFile.exists()) {
      oldLogFile.delete();
    }

    File newLogFile = new File(newLogPath);
    if (newLogFile.exists()) {
      newLogFile.renameTo(oldLogFile);
    }

    FileHandler fileHandler = new FileHandler(newLogPath, true);
    fileHandler.setFormatter(new LogFormatter());

    logger.addHandler(fileHandler);
    logger.setLevel(Level.ALL);

    Function<PrintStream, PrintStream> wrapper = (out) -> new PrintStream(out) {
      public void println(String x) {logger.info(x);}

      public void print(String x) {logger.info(x);}
    };

    System.setOut(wrapper.apply(System.out));
    System.setErr(wrapper.apply(System.err));
  }

  static void loadConfigs() throws Exception {
    InputStream is = Files.newInputStream(Paths.get(USER_DIR + File.separator + "KnightLauncher.properties"));
    configs.load(is);
    is.close();
  }

  static void loadJarMods() {
    // Read disabled mods from `KnightLauncher.properties`
    Set<String> disabledJarMods = new HashSet<>();
    String disabledJarModsString = configs.getProperty("modloader.disabledMods");
    if (disabledJarModsString != null && disabledJarModsString.length() > 0) {
      for (String disabledJarMod : disabledJarModsString.split(",")) {
        disabledJarMod = disabledJarMod.trim();
        if (disabledJarMod.length() > 0) {
          disabledJarMods.add(disabledJarMod);
        }
      }
    }
    // Obtain the mod files in the "/code-mods/" directory
    File codeModsDir = new File(CODE_MODS_DIR);
    if (!codeModsDir.exists()) {
      return;
    }
    File[] files = codeModsDir.listFiles();
    if (files == null || files.length == 0) {
      return;
    }
    List<File> jars = new ArrayList<File>(files.length);
    for (File file : files) {
      String filename = file.getName();
      if (filename.endsWith(".jar")
          && !disabledJarMods.contains(filename)) {
        jars.add(file);
      }
    }
    if (jars.isEmpty()) {
      return;
    }
    loadJars(jars);
    loadClasses(jars);
  }

  static void loadJars(List<File> jars) {
    // TODO Compatible with more versions of the JDK
    Method method;
    try {
      method = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
    } catch (Exception e) {
      e.printStackTrace();
      return;
    }
    URLClassLoader classLoader = (URLClassLoader) ClassLoader.getSystemClassLoader();
    boolean accessible = method.isAccessible();
    method.setAccessible(true);
    for (File jar : jars) {
      try {
        method.invoke(classLoader, jar.toURI().toURL());
        logger.info("Loaded jar '" + jar.getName() + "'");
      } catch (Exception e) {
        logger.warning("Failed to load jar '" + jar.getName() + "'");
        e.printStackTrace();
      }
    }
    method.setAccessible(accessible);
  }

  static void loadClasses(List<File> jars) {
    Map<String, Class<?>> classes = new LinkedHashMap<>();
    for (File jar : jars) {
      String manifest = readZip(jar, MANIFEST_PATH);
      if (manifest == null || manifest.length() == 0) {
        logger.warning("Failed to read '" + MANIFEST_PATH + "' from '" + jar.getName() + "'");
        continue;
      }
      String className = null;
      String modName = null;
      for (String item : manifest.split("\n")) {
        if (item.startsWith(MAIN_CLASS_KEY)) {
          className = item.replace(MAIN_CLASS_KEY, "").trim();
        } else if (item.startsWith(NAME_KEY)) {
          modName = item.replace(NAME_KEY, "").trim();
        }
      }
      if (className == null || className.length() == 0) {
        logger.warning("Failed to read 'Main-Class' from '" + jar.getName() + "'");
        continue;
      }
      if (modName == null) {
        modName = jar.getName();
      }
      try {
        Class<?> clazz = Class.forName(className);
        classes.put(modName, clazz);
        logger.info("Loaded class '" + className + "' from '" + jar.getName() + "'");
      } catch (Exception e) {
        logger.warning("Failed to load class '" + className + "' from '" + jar.getName() + "'");
        e.printStackTrace();
      }
    }
    if (!classes.isEmpty()) {
      mountMods(classes);
    }
  }

  static void mountMods(Map<String, Class<?>> classes) {
    for (Map.Entry<String, Class<?>> entry : classes.entrySet()) {
      String modName = entry.getKey();
      Class<?> clazz = entry.getValue();
      try {
        logger.info("Mounting mod '" + modName + "'");
        Method method = clazz.getDeclaredMethod("mount");
        method.setAccessible(true);
        method.invoke(null);
        logger.info("Mounted mod '" + modName + "'");
      } catch (NoSuchMethodException e) {
        logger.warning("Failed to mount mod '" + modName + "', it does not define `mount` method");
      } catch (IllegalAccessException | InvocationTargetException e) {
        logger.warning("Failed to mount mod '" + modName + "': " + e.getMessage());
        e.printStackTrace();
      }
    }
  }

  static String readZip(File file, String entry) {
    StringBuilder sb = new StringBuilder();
    try {
      ZipFile zip = new ZipFile(file);
      InputStream is = zip.getInputStream(zip.getEntry(entry));
      BufferedReader reader = new BufferedReader(new InputStreamReader(is));
      String s;
      while ((s = reader.readLine()) != null) {
        sb.append(s).append("\n");
      }
      reader.close();
      zip.close();
      return sb.toString();
    } catch (Exception e) {
      logger.warning("Failed to read '" + file.getName() + "'");
      e.printStackTrace();
      return null;
    }
  }

  static class LogFormatter extends Formatter {
    private static final String format = "%1$tY/%1$tm/%1$td/%1$tH:%1$tM:%1$tS %2$s[%4$s]\t%5$s%6$s%n";

    private final Date dat = new Date();

    public synchronized String format(LogRecord record) {
      dat.setTime(record.getMillis());
      String source;
      if (record.getSourceClassName() != null) {
        source = record.getSourceClassName();
        if (record.getSourceMethodName() != null) {
          source += " " + record.getSourceMethodName();
        }
      } else {
        source = record.getLoggerName();
      }
      String message = formatMessage(record);
      String throwable = "";
      if (record.getThrown() != null) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        pw.println();
        record.getThrown().printStackTrace(pw);
        pw.close();
        throwable = sw.toString();
      }
      return String.format(format, dat, source, record.getLoggerName(), record.getLevel().getName(), message, throwable);
    }
  }

}
