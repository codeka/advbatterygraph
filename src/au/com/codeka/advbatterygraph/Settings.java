package au.com.codeka.advbatterygraph;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class Settings {
    private int mGraphWidth;
    private int mGraphHeight;
    private boolean mShowTempGraph;
    private int mNumHours;

    public static final String PREF_PREFIX = "au.com.codeka.advbatterygraph.";

    private Settings() {
    }

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

    public static Settings get(Context context) {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);

        Settings s = new Settings();
        s.mGraphWidth = pref.getInt(PREF_PREFIX+"GraphWidth", 40);
        s.mGraphHeight = pref.getInt(PREF_PREFIX+"GraphHeight", 40);
        s.mShowTempGraph = pref.getBoolean(PREF_PREFIX+"IncludeTemp", false);
        s.mNumHours = Integer.parseInt(pref.getString(PREF_PREFIX+"NumHours", Integer.toString(48)));
        return s;
    }

    public void save(Context context) {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        pref.edit().putInt(PREF_PREFIX+"GraphWidth", mGraphWidth)
                   .putInt(PREF_PREFIX+"GraphHeight", mGraphHeight)
                   .putBoolean(PREF_PREFIX+"IncludeTemp", mShowTempGraph)
                   .putString(PREF_PREFIX+"NumHours", Integer.toString(mNumHours))
                   .apply();
    }
}
