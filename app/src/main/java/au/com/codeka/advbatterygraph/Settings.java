package au.com.codeka.advbatterygraph;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;

import java.util.Locale;

public class Settings {
  private SharedPreferences preferences;
  private boolean monitorWatch;

  public static final String PREF_PREFIX = "au.com.codeka.advbatterygraph.";

  private Settings() {
  }

  public boolean monitorWatch(int[] appWidgetIds) {
    if (appWidgetIds == null) {
      return monitorWatch;
    }

    boolean monitor = false;
    for (int appWidgetId : appWidgetIds) {
      GraphSettings gs = getGraphSettings(appWidgetId);
      if (gs.showWatchGraph()) {
        monitor = true;
        break;
      }
    }
    if (monitorWatch != monitor) {
      monitorWatch = monitor;
      preferences.edit().putBoolean(PREF_PREFIX + "MonitorWatch", monitor).apply();
    }
    return monitorWatch;
  }

  public GraphSettings getGraphSettings(int appWidgetId) {
    String prefix = String.format(Locale.ENGLISH, "%s(%d).", PREF_PREFIX, appWidgetId);
    if (!preferences.contains(prefix + "AutoGraph")) {
      // if we don't have settings for this graph, copy over the 'default' preferences.
      GraphSettings gs = GraphSettings.get(preferences, PREF_PREFIX);
      gs.save(preferences, prefix);
    }
    return GraphSettings.get(
        preferences, String.format(Locale.ENGLISH, "%s(%d).", PREF_PREFIX, appWidgetId));
  }

  public static Settings get(Context context) {
    SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);

    Settings s = new Settings();
    s.preferences = pref;
    s.monitorWatch = pref.getBoolean(PREF_PREFIX + "MonitorWatch", true);
    return s;
  }

  public void save(Context context) {
    SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
    pref.edit()
        .putBoolean(PREF_PREFIX + "MonitorWatch", monitorWatch)
        .apply();
  }

  public static class GraphSettings {
    private String prefix;
    private int graphWidth;
    private int graphHeight;
    private boolean showWatchGraph;
    private boolean showBatteryGraph;
    private boolean showBatteryCurrentInstant;
    private boolean smoothBatteryCurrentInstant;
    private boolean invertBatteryCurrentInstant;
    private boolean showBatteryCurrentAvg;
    private boolean showBatteryEnergy;
    private boolean showTempGraph;
    private boolean smoothTemp;
    private boolean tempCelsius;
    private int numHours;
    private boolean showTimeScale;
    private boolean showTimeLines;
    private boolean showLastLevelLine;
    private boolean autoGraphSize;

    public boolean autoGraphSize() {
      return autoGraphSize;
    }

    public int getGraphWidth() {
      return graphWidth;
    }

    public void setGraphWidth(int width) {
      graphWidth = width;
    }

    public int getGraphHeight() {
      return graphHeight;
    }

    public void setGraphHeight(int height) {
      graphHeight = height;
    }

    public boolean showWatchGraph() {
      return showWatchGraph;
    }

    public boolean showBatteryGraph() {
      return showBatteryGraph;
    }

    public boolean showBatteryCurrentInstant() {
      return showBatteryCurrentInstant;
    }

    public boolean smoothBatteryCurrentInstant() {
      return smoothBatteryCurrentInstant;
    }

    public boolean invertBatteryCurrentInstant() {
      return invertBatteryCurrentInstant;
    }

    public boolean showBatteryCurrentAvg() {
      return showBatteryCurrentAvg;
    }

    public boolean showBatteryEnergy() {
      return showBatteryEnergy;
    }

    public boolean showTemperatureGraph() {
      return showTempGraph;
    }

    public boolean tempInCelsius() {
      return tempCelsius;
    }

    public boolean smoothTemp() {
      return smoothTemp;
    }

    public int getNumHours() {
      return numHours;
    }

    public boolean showLastLevelLine() {
      return showLastLevelLine;
    }

    public boolean showTimeScale() {
      return showTimeScale;
    }

    public boolean showTimeLines() {
      return showTimeLines;
    }

    public static GraphSettings get(SharedPreferences pref, String prefix) {
      GraphSettings gs = new GraphSettings();
      gs.prefix = prefix;
      gs.autoGraphSize = pref.getBoolean(prefix + "AutoGraph", true);
      gs.graphWidth = pref.getInt(prefix + "GraphWidth", 40);
      gs.graphHeight = pref.getInt(prefix + "GraphHeight", 40);
      gs.showWatchGraph = pref.getBoolean(prefix + "IncludeWatchGraph", true);
      gs.showBatteryGraph = pref.getBoolean(prefix + "IncludeBattery", true);
      gs.showBatteryCurrentInstant = pref.getBoolean(prefix + "IncludeBatteryCurrentInstant", false);
      gs.smoothBatteryCurrentInstant = pref.getBoolean(prefix + "SmoothBatteryCurrentInstant", false);
      gs.invertBatteryCurrentInstant = pref.getBoolean(prefix + "InvertBatteryCurrentInstant", false);
      gs.showBatteryCurrentAvg = pref.getBoolean(prefix + "IncludeBatteryCurrentAvg", false);
      gs.showBatteryEnergy = pref.getBoolean(prefix + "IncludeBatteryEnergy", false);
      gs.showTempGraph = pref.getBoolean(prefix + "IncludeTemp", false);
      gs.smoothTemp = pref.getBoolean(prefix + "SmoothTemp", false);
      gs.tempCelsius = pref.getString(prefix + "TempUnits", "C").toLowerCase().equals("c");
      gs.numHours = Integer.parseInt(pref.getString(prefix + "NumHours", Integer.toString(48)));
      gs.showTimeScale = pref.getBoolean(prefix + "ShowTime", false);
      gs.showTimeLines = pref.getBoolean(prefix + "ShowTimeLines", false);
      gs.showLastLevelLine = pref.getBoolean(prefix + "ShowLastLevelLine", false);
      return gs;
    }

    public void save(Context context) {
      save(PreferenceManager.getDefaultSharedPreferences(context), prefix);
    }

    public void save(SharedPreferences pref, String prefix) {
      pref.edit()
          .putBoolean(prefix + "AutoGraph", autoGraphSize)
          .putInt(prefix + "GraphWidth", graphWidth)
          .putInt(prefix + "GraphHeight", graphHeight)
          .putBoolean(prefix + "IncludeWatchGraph", showWatchGraph)
          .putBoolean(pref + "IncludeBattery", showBatteryGraph)
          .putBoolean(prefix + "IncludeBatteryCurrentInstant", showBatteryCurrentInstant)
          .putBoolean(prefix + "IncludeBatteryCurrentAvg", showBatteryCurrentAvg)
          .putBoolean(prefix + "IncludeBatteryEnergy", showBatteryEnergy)
          .putBoolean(prefix + "IncludeTemp", showTempGraph)
          .putString(prefix + "TempUnits", tempCelsius ? "C" : "F")
          .putString(prefix + "NumHours", Integer.toString(numHours))
          .putBoolean(prefix + "ShowTime", showTimeScale)
          .putBoolean(prefix + "ShowTimeLines", showTimeLines)
          .putBoolean(prefix + "ShowLastLevelLine", showLastLevelLine)
          .apply();
    }
  }
}
