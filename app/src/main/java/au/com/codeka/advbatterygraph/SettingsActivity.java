package au.com.codeka.advbatterygraph;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;

import android.Manifest;
import android.app.Fragment;
import android.appwidget.AppWidgetManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;

import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;

import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

public class SettingsActivity extends PreferenceActivity
        implements SharedPreferences.OnSharedPreferenceChangeListener {
  private static final String TAG = "SettingsActivity";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    PreferenceManager.getDefaultSharedPreferences(this)
            .registerOnSharedPreferenceChangeListener(this);
    WatchConnection.i.setup(this, watchConnectedRunnable);

    Log.i("DEANH", "Settings fragment onCreate");
    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
      ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.BLUETOOTH_CONNECT }, 975364);

      return;
    }
    for (BluetoothDevice device : BluetoothAdapter.getDefaultAdapter().getBondedDevices()) {
      Log.i("DEANH", "got device " + device.getName() + " - " + device);
      try {
        Method method = device.getClass().getMethod("getBatteryLevel");
        Object level = method.invoke(device);
        Log.i("DEANH", "    battery level: " + level);
      } catch (Exception e) {
        Log.i("DEANH", "error getting battery level: " + e.toString());
      }
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    WatchConnection.i.start();
    WatchConnection.i.sendMessage(new WatchConnection.Message("/advbatterygraph/Start", null));

    notifyWidgetRefresh();
  }

  @Override
  protected void onPause() {
    super.onPause();
  }

  @Override
  public void onBuildHeaders(List<PreferenceActivity.Header> target) {
    loadHeadersFromResource(R.xml.settings_headers, target);
    for (int i = 0; i < target.size(); i++) {
      PreferenceActivity.Header header = target.get(i);
      if (header.fragmentArguments == null) {
        header.fragmentArguments = new Bundle();
      }
      header.fragmentArguments.putInt(AppWidgetManager.EXTRA_APPWIDGET_ID,
          getIntent().getExtras().getInt(AppWidgetManager.EXTRA_APPWIDGET_ID));
    }

    if (!WatchConnection.i.isConnected()) {
      // if we're not connected to a watch, don't show the watch headings
      for (PreferenceActivity.Header header : target) {
        if (header.id == R.id.watch_settings_header) {
          target.remove(header);
          break;
        }
      }
    }
  }

  /** Only allow fragments from within our package. */
  @Override
  protected boolean isValidFragment(String fragmentName) {
    return fragmentName.contains("au.com.codeka.advbatterygraph");
  }

  // Just invalidate the headers, the onBuildHeaders logic will recreate the headers with the watch
  // enabled again.
  private Runnable watchConnectedRunnable = this::invalidateHeaders;

  /**
   * When preferences change, notify the graph to update itself.
   */
  @Override
  public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
    notifyWidgetRefresh();
  }

  private void notifyWidgetRefresh() {
    Log.i("DEANH", "onSharedPreferenceChanged");
    BatteryGraphWidgetProvider.notifyRefresh(this, new int[]{
            getIntent().getExtras().getInt(AppWidgetManager.EXTRA_APPWIDGET_ID)
    });
  }

  /**
   * Base class for our various preference fragments.
   */
  public static abstract class BasePreferenceFragment extends PreferenceFragment
      implements SharedPreferences.OnSharedPreferenceChangeListener {
    protected int mAppWidgetId;

    @Override
    public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      mAppWidgetId = getArguments().getInt(AppWidgetManager.EXTRA_APPWIDGET_ID);
    }

    protected String getPrefix() {
      if (mAppWidgetId == 0) {
        return Settings.PREF_PREFIX;
      } else {
        return Settings.PREF_PREFIX + "(" + mAppWidgetId + ").";
      }
    }

    @Override
    public void addPreferencesFromResource(int resourceId) {
      super.addPreferencesFromResource(resourceId);
      updateKeys(getPreferenceScreen());
    }

    private void updateKeys(PreferenceGroup parent) {
      for (int i = 0; i < parent.getPreferenceCount(); i++) {
        Preference pref = parent.getPreference(i);
        boolean changed = false;
        if (pref.getKey() != null && pref.getKey().contains("%d")) {
          pref.setKey(String.format(pref.getKey(), mAppWidgetId));
          changed = true;
        }
        if (pref.getDependency() != null && pref.getDependency().contains("%d")) {
          pref.setDependency(String.format(pref.getDependency(), mAppWidgetId));
          changed = true;
        }
        if (changed) {
          reloadPreference(pref);
        }
        if (pref instanceof PreferenceGroup) {
          updateKeys((PreferenceGroup) pref);
        }
      }
    }

    /**
     * This is kind of a dumb way to cause a preference to reload itself after changing one of
     * its properties (such as 'Key').
     * <p/>
     * The "correct" solution to this problem would be to dynamically generate all my preference
     * instances with the correct key to begin with, but that's ridiculously tedious.
     */
    @SuppressWarnings("unchecked")
    private void reloadPreference(Preference pref) {
      Class cls = pref.getClass();
      while (cls != Preference.class) {
        try {
          Method m = cls.getDeclaredMethod("onSetInitialValue", boolean.class, Object.class);
          m.setAccessible(true);
          m.invoke(pref, true, null);
          break;
        } catch (Exception e) {
          // Ignore.
        }
        cls = cls.getSuperclass();
      }
    }

    @Override
    public void onStart() {
      super.onStart();
      refreshSummaries();
      getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onStop() {
      super.onStop();
      getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
      refreshSummaries();
    }

    protected abstract void refreshSummaries();
  }

  public static class GraphSettingsFragment extends BasePreferenceFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      addPreferencesFromResource(R.xml.graph_settings);
    }

    @Override
    protected void refreshSummaries() {
      EditTextIntegerPreference intpref =
          (EditTextIntegerPreference) findPreference(getPrefix() + "GraphWidth");
      intpref.setSummary(String.format(Locale.ENGLISH, "%d px", intpref.getInteger()));

      intpref = (EditTextIntegerPreference) findPreference(getPrefix() + "GraphHeight");
      intpref.setSummary(String.format(Locale.ENGLISH, "%d px", intpref.getInteger()));

      ListPreference listpref = (ListPreference) findPreference(getPrefix() + "NumHours");
      listpref.setSummary(listpref.getEntry());
    }
  }

  public static class WatchSettingsFragment extends BasePreferenceFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      addPreferencesFromResource(R.xml.watch_settings);
    }

    @Override
    protected void refreshSummaries() {
    }
  }

  public static class BatterySettingsFragment extends BasePreferenceFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      addPreferencesFromResource(R.xml.battery_settings);

      // battery charge is only available on Android L (API level 21)+.
      Preference batteryCurrentInstantPref =
          findPreference(getPrefix() + "IncludeBatteryCurrentInstant");
      Preference batteryCurrentAvgPref = findPreference(getPrefix() + "IncludeBatteryCurrentAvg");
      Preference batteryEnergyPref = findPreference(getPrefix() + "IncludeBatteryEnergy");
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        BatteryManager batteryManager = (BatteryManager) getActivity()
            .getSystemService(Context.BATTERY_SERVICE);
        batteryCurrentInstantPref.setEnabled(batteryManager.getIntProperty(
            BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) != Integer.MIN_VALUE);
        batteryCurrentAvgPref.setEnabled(batteryManager.getIntProperty(
            BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE) != Integer.MIN_VALUE);
        batteryEnergyPref.setEnabled(batteryManager.getIntProperty(
            BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER) != Integer.MIN_VALUE);
      } else {
        Log.w(TAG, "Not Lollipop, no battery properties available.");
        batteryCurrentInstantPref.setEnabled(false);
        batteryCurrentAvgPref.setEnabled(false);
        batteryEnergyPref.setEnabled(false);
      }
      if (!batteryCurrentInstantPref.isEnabled()) {
        batteryCurrentInstantPref.getSharedPreferences().edit()
            .putBoolean(batteryCurrentInstantPref.getKey(), false)
            .apply();
      }
      if (!batteryCurrentAvgPref.isEnabled()) {
        batteryCurrentAvgPref.getSharedPreferences().edit()
            .putBoolean(batteryCurrentAvgPref.getKey(), false)
            .apply();
      }
      if (!batteryEnergyPref.isEnabled()) {
        batteryEnergyPref.getSharedPreferences().edit()
            .putBoolean(batteryEnergyPref.getKey(), false)
            .apply();
      }
    }

    @Override
    protected void refreshSummaries() {
    }
  }

  public static class TempSettingsFragment extends BasePreferenceFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      addPreferencesFromResource(R.xml.temp_settings);
    }

    @Override
    protected void refreshSummaries() {
      ListPreference listpref = (ListPreference) findPreference(getPrefix() + "TempUnits");
      listpref.setSummary(listpref.getEntry());
    }
  }

  public static class NotificationSettingsFragment extends BasePreferenceFragment
      implements SharedPreferences.OnSharedPreferenceChangeListener {

    @Override
    public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      addPreferencesFromResource(R.xml.notification_settings);
      refreshNotificationPrefs();
    }

    @Override
    public View onCreateView(
        LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
      final View v = super.onCreateView(inflater, container, savedInstanceState);

      final ListView lv = v.findViewById(android.R.id.list);
      registerForContextMenu(lv);

      return v;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
      super.onCreateContextMenu(menu, v, menuInfo);
      new MenuInflater(getActivity()).inflate(R.menu.notification_settings_menu, menu);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
      int position = ((AdapterView.AdapterContextMenuInfo) item.getMenuInfo()).position;
      switch (item.getItemId()) {
        case R.id.delete:
          deleteNotificationSetting(position + 1);
          break;
        case R.id.edit:
          //showNotificationSetting(position + 1);
          break;
      }

      return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
      super.onSharedPreferenceChanged(sharedPreferences, key);

      if (key.startsWith("notification:")) {
        refreshNotificationPrefs();
      }
    }

    @Override
    protected void refreshSummaries() {
    }

    /**
     * Deletes the notification at the specified index. Basically, we shuffle all the
     * later notifications down one, and then delete the last one.
     */
    private void deleteNotificationSetting(int i) {
      SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
      SharedPreferences.Editor editor = prefs.edit();

      int last = i;
      for (int j = i + 1; ; j++) {
        String key = String.format(Locale.US, "notification:%d:percent", j);
        if (prefs.getInt(key, -1) < 0) {
          break;
        }

        int percent = prefs.getInt(key, -1);
        String dir = prefs.getString(String.format(Locale.US, "notification:%d:direction", j), "");
        String device = prefs.getString(String.format(Locale.US, "notification:%d:device", j), "");

        editor.putInt(String.format(Locale.US, "notification:%d:percent", j - 1), percent);
        editor.putString(String.format(Locale.US, "notification:%d:direction", j - 1), dir);
        editor.putString(String.format(Locale.US, "notification:%d:device", i - 1), device);
        last = j;
      }

      editor.remove(String.format(Locale.US, "notification:%d:percent", last));
      editor.remove(String.format(Locale.US, "notification:%d:direction", last));
      editor.remove(String.format(Locale.US, "notification:%d:device", last));
      editor.apply();
    }

    private void refreshNotificationPrefs() {
      PreferenceScreen screen = getPreferenceScreen();
      screen.removeAll();

      SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
      for (int i = 1; ; i++) {
        String key = String.format(Locale.US, "notification:%d:percent", i);
        if (prefs.getInt(key, -1) < 0) {
          break;
        }

        int percent = prefs.getInt(key, -1);
        String dir = prefs.getString(String.format(Locale.US, "notification:%d:direction", i), "");
        String device =
            prefs.getString(String.format(Locale.US, "notification:%d:device", i), "Phone");

        NotificationSettingDialog pref = new NotificationSettingDialog(getActivity());
        pref.setKey(String.format(Locale.US, "notification:%d", i));
        pref.setTitle(
            String.format(Locale.US, "%s %d%% and %s", device, percent, dir.toLowerCase()));
        pref.setDialogLayoutResource(R.layout.notification_pref);
        pref.setPositiveButtonText("Save");
        pref.setNegativeButtonText("Cancel");
        screen.addPreference(pref);
      }

      NotificationSettingDialog pref = new NotificationSettingDialog(getActivity());
      pref.setKey("notification:new");
      pref.setTitle("Add Notification");
      pref.setDialogLayoutResource(R.layout.notification_pref);
      pref.setPositiveButtonText("Add");
      pref.setNegativeButtonText("Cancel");
      screen.addPreference(pref);
    }
  }

  public static class ExportFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle args) {
      View view = inflater.inflate(R.layout.export_fragment, parent, false);
      new ExportTask(getActivity(), view).execute();
      return view;
    }
  }

  private static class ExportResult {
    Exception error;
    Uri contentUri;
    long numRecords;
    long timeMs;

    public ExportResult(Exception e) {
      this.error = e;
    }

    public ExportResult(Uri contentUri, long numRecords, long timeMs) {
      this.contentUri = contentUri;
      this.numRecords = numRecords;
      this.timeMs = timeMs;
    }
  }

  private static class ExportTask extends AsyncTask<Void, Void, ExportResult> {
    private final Context context;
    private final View view;


    public ExportTask(Context context, View view) {
      this.context = context;
      this.view = view;
    }

    @Override
    protected ExportResult doInBackground(Void... voids) {
      Log.i(TAG, "Export started.");
      long startTimeNs = System.nanoTime();
      File dir = new File(context.getCacheDir(), "exports");
      if (!dir.exists()) {
        dir.mkdir();
      }
      File exportFile = new File(dir, "battery-graph.csv");
      Log.i(TAG, "Writing to: " + exportFile.getAbsolutePath());
      long numResults = 0;
      try {
        exportFile.createNewFile();
        numResults = BatteryStatus.export(context, exportFile);
      } catch (IOException e) {
        Log.e(TAG, "Error exporting!", e);
        return new ExportResult(e);
      }
      long endTimeNs = System.nanoTime();
      Uri uri = FileProvider.getUriForFile(context,
          "au.com.codeka.advbatterygraph.exportprovider", exportFile);
      Log.i(TAG, "Returning share URI: " + uri);
      return new ExportResult(uri, numResults, (endTimeNs - startTimeNs) / 1000000L);
    }

    @Override
    protected void onPostExecute(ExportResult result) {
      view.findViewById(R.id.progress).setVisibility(View.GONE);
      TextView msgView = view.findViewById(R.id.label);
      if (result.error != null) {
        msgView.setText(String.format(Locale.US,
            "Error occurred during export\n\n%s",
            result.error.getMessage()));

      } else {
        msgView.setText(String.format(Locale.US,
            "Export complete, %d records in %d ms",
            result.numRecords,
            result.timeMs));

        Log.i(TAG, "Firing share intent with: " + result.contentUri);
        Intent shareIntent = new Intent();
        shareIntent.setAction(Intent.ACTION_SEND);
        shareIntent.putExtra(Intent.EXTRA_STREAM, result.contentUri);
        shareIntent.setType("text/csv");
        shareIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(Intent.createChooser(shareIntent, "Share exported data"));
      }
    }
  }
}
