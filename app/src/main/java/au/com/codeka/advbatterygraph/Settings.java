package au.com.codeka.advbatterygraph;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.util.Map;
import java.util.TreeMap;

public class Settings {
    private SharedPreferences mPreferences;
    private boolean mMonitorWatch;

    public static final String PREF_PREFIX = "au.com.codeka.advbatterygraph.";

    private Settings() {
    }

    public boolean monitorWatch() { return mMonitorWatch; }

    public GraphSettings getGraphSettings(int appWidgetId) {
        String prefix = String.format("%s(%d).", PREF_PREFIX, appWidgetId);
        if (!mPreferences.contains(prefix + "AutoGraph")) {
            // if we don't have settings for this graph, copy over the 'default' preferences.
            GraphSettings gs = GraphSettings.get(mPreferences, PREF_PREFIX);
            gs.save(mPreferences, prefix);
        }
        return GraphSettings.get(mPreferences, String.format("%s(%d).", PREF_PREFIX, appWidgetId));
    }

    public static Settings get(Context context) {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);

        Settings s = new Settings();
        s.mPreferences = pref;
        s.mMonitorWatch = pref.getBoolean(PREF_PREFIX+"MonitorWatch", true);
        return s;
    }

    public void save(Context context) {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        pref.edit()
            .putBoolean(PREF_PREFIX + "MonitorWatch", mMonitorWatch)
            .apply();
    }

    public static class GraphSettings {
        private String mPrefix;
        private int mGraphWidth;
        private int mGraphHeight;
        private boolean mShowTempGraph;
        private int mNumHours;
        private boolean mShowTimeScale;
        private boolean mShowTimeLines;
        private boolean mAutoGraphSize;

        public boolean autoGraphSize() { return mAutoGraphSize; }
        public int getGraphWidth() {
            return mGraphWidth;
        }
        public void setGraphWidth(int width) {
            mGraphWidth = width;
        }
        public int getGraphHeight() {
            return mGraphHeight;
        }
        public void setGraphHeight(int height) {
            mGraphHeight = height;
        }
        public boolean showTemperatureGraph() {
            return mShowTempGraph;
        }
        public int getNumHours() {
            return mNumHours;
        }
        public boolean showTimeScale() {
            return mShowTimeScale;
        }
        public boolean showTimeLines() {
            return mShowTimeLines;
        }

        public static GraphSettings get(SharedPreferences pref, String prefix) {
            GraphSettings gs = new GraphSettings();
            gs.mPrefix = prefix;
            gs.mAutoGraphSize = pref.getBoolean(prefix+"AutoGraph", true);
            gs.mGraphWidth = pref.getInt(prefix+"GraphWidth", 40);
            gs.mGraphHeight = pref.getInt(prefix+"GraphHeight", 40);
            gs.mShowTempGraph = pref.getBoolean(prefix+"IncludeTemp", false);
            gs.mNumHours = Integer.parseInt(pref.getString(prefix+"NumHours", Integer.toString(48)));
            gs.mShowTimeScale = pref.getBoolean(prefix+"ShowTime", false);
            gs.mShowTimeLines = pref.getBoolean(prefix+"ShowTimeLines", false);
            return gs;
        }

        public void save(Context context) {
            save(PreferenceManager.getDefaultSharedPreferences(context), mPrefix);
        }

        public void save(SharedPreferences pref, String prefix) {
            pref.edit()
                .putBoolean(prefix+"AutoGraph", mAutoGraphSize)
                .putInt(prefix+"GraphWidth", mGraphWidth)
                .putInt(prefix+"GraphHeight", mGraphHeight)
                .putBoolean(prefix+"IncludeTemp", mShowTempGraph)
                .putString(prefix+"NumHours", Integer.toString(mNumHours))
                .putBoolean(prefix+"ShowTime", mShowTimeScale)
                .putBoolean(prefix+"ShowTimeLines", mShowTimeLines)
                .apply();
        }
    }
}
