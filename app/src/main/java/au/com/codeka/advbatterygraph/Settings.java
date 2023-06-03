package au.com.codeka.advbatterygraph;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import java.util.Locale;

public class Settings {
  @NonNull private final Context context;
  private boolean monitorWatch;

  // The previous version stored all preferences in a single shared preference with a weird prefix,
  // but that is kind of hard to maintain. So we want to migrate that to the new system.
  public static final String LEGACY_PREF_PREFIX = "au.com.codeka.advbatterygraph.";

  private Settings(@NonNull Context context) {
    this.context = context;
  }

  // TODO: remove me
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
      //preferences.edit().putBoolean(LEGACY_PREF_PREFIX + "MonitorWatch", monitor).apply();
    }
    return monitorWatch;
  }

  public GraphSettings getGraphSettings(int appWidgetId) {
    String legacy = String.format(Locale.ENGLISH, "%s(%d).", LEGACY_PREF_PREFIX, appWidgetId);
    SharedPreferences preferences =
        context.getSharedPreferences("Widget." + appWidgetId, Context.MODE_PRIVATE);
    SharedPreferences legacyPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    if (legacyPreferences.contains(legacy + "AutoGraph")) {
      // We have legacy preferences, time to migrate.
      GraphSettings gs = GraphSettings.get(legacyPreferences, legacy);
      gs.save(preferences);

      // "Delete" the old preferences. It's not worth it to actually delete delete them, just delete
      // enough so that we won't try to migrate again.
      legacyPreferences.edit().remove(legacy + "AutoGraph").apply();
    }
    if (!preferences.contains("AutoGraph")) {
      // if we don't have settings for this graph, copy over the 'default' preferences.
      GraphSettings gs = GraphSettings.get(preferences, "");
      gs.save(preferences);
    }
    return GraphSettings.get(preferences, "");
  }

  public static Settings get(@NonNull Context context) {
    return new Settings(context);
  }

  public static class GraphSettings {
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
      gs.tempCelsius = pref.getString(prefix + "TempUnits", "C").equalsIgnoreCase("c");
      gs.numHours = Integer.parseInt(pref.getString(prefix + "NumHours", Integer.toString(48)));
      gs.showTimeScale = pref.getBoolean(prefix + "ShowTime", false);
      gs.showTimeLines = pref.getBoolean(prefix + "ShowTimeLines", false);
      gs.showLastLevelLine = pref.getBoolean(prefix + "ShowLastLevelLine", false);
      return gs;
    }

    public void save(Context context, int appWidgetId) {
      save(context.getSharedPreferences("Widget." + appWidgetId, Context.MODE_PRIVATE));
    }

    public void save(SharedPreferences pref) {
      pref.edit()
          .putBoolean("AutoGraph", autoGraphSize)
          .putInt("GraphWidth", graphWidth)
          .putInt("GraphHeight", graphHeight)
          .putBoolean("IncludeWatchGraph", showWatchGraph)
          .putBoolean("IncludeBattery", showBatteryGraph)
          .putBoolean("IncludeBatteryCurrentInstant", showBatteryCurrentInstant)
          .putBoolean("IncludeBatteryCurrentAvg", showBatteryCurrentAvg)
          .putBoolean("IncludeBatteryEnergy", showBatteryEnergy)
          .putBoolean("IncludeTemp", showTempGraph)
          .putString("TempUnits", tempCelsius ? "C" : "F")
          .putString("NumHours", Integer.toString(numHours))
          .putBoolean("ShowTime", showTimeScale)
          .putBoolean("ShowTimeLines", showTimeLines)
          .putBoolean("ShowLastLevelLine", showLastLevelLine)
          .apply();
    }
  }
}
