package au.com.codeka.advbatterygraph;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.Objects;

import android.Manifest;
import android.annotation.SuppressLint;
import android.appwidget.AppWidgetManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.fragment.app.Fragment;
import androidx.preference.CheckBoxPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

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

public class SettingsActivity extends AppCompatActivity
    implements SharedPreferences.OnSharedPreferenceChangeListener,
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback,
    ActivityCompat.OnRequestPermissionsResultCallback {
  private static final String TAG = "SettingsActivity";

  private static final int BLUETOOTH_PERMISSION_TAG = 694724;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.settings_activity);

    PreferenceManager.getDefaultSharedPreferences(this)
            .registerOnSharedPreferenceChangeListener(this);

    HeadersSettingsFragment fragment = new HeadersSettingsFragment();
    Bundle args = new Bundle();
    args.putInt(
        AppWidgetManager.EXTRA_APPWIDGET_ID,
        getIntent().getExtras().getInt(AppWidgetManager.EXTRA_APPWIDGET_ID));
    fragment.setArguments(args);

    getSupportFragmentManager()
        .beginTransaction()
        .replace(R.id.settings_container, fragment)
        .commit();

    ActionBar actionBar = getSupportActionBar();
    if (actionBar != null) {
      actionBar.setDisplayHomeAsUpEnabled(true);
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
  }

  @Override
  protected void onPause() {
    super.onPause();

    notifyWidgetRefresh();
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item){
    // TODO: check if it's the back button?
    if (getSupportFragmentManager().getBackStackEntryCount() == 0) {
      finish();
    } else {
      getSupportFragmentManager().popBackStack();
    }
    return true;
  }

  @Override
  public boolean onPreferenceStartFragment(@NonNull PreferenceFragmentCompat caller, Preference pref) {
    // Instantiate the new Fragment
    final Bundle args = pref.getExtras();
    final String fragmentName = pref.getFragment();
    if (fragmentName == null) {
      return false;
    }
    final Fragment fragment =
        getSupportFragmentManager().getFragmentFactory().instantiate(
            getClassLoader(), fragmentName);
    // Add the widget ID so it shows the right settings.
    args.putInt(
        AppWidgetManager.EXTRA_APPWIDGET_ID,
        getIntent().getExtras().getInt(AppWidgetManager.EXTRA_APPWIDGET_ID));
    fragment.setArguments(args);

    getSupportFragmentManager().beginTransaction()
        .replace(R.id.settings_container, fragment)
        .addToBackStack(null)
        .commit();
    return true;
  }

  @Override
  public void onRequestPermissionsResult(
      int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);

    if (requestCode == BLUETOOTH_PERMISSION_TAG) {
      // TODO: jump back to the bluetooth settings fragment
    }
  }

  /**
   * When preferences change, notify the graph to update itself.
   */
  @Override
  public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
    notifyWidgetRefresh();
  }

  private void notifyWidgetRefresh() {
    BatteryGraphWidgetProvider.notifyRefresh(this, new int[]{
            getIntent().getExtras().getInt(AppWidgetManager.EXTRA_APPWIDGET_ID)
    });
  }

  /**
   * Base class for our various preference fragments.
   */
  public static abstract class BasePreferenceFragment extends PreferenceFragmentCompat
      implements SharedPreferences.OnSharedPreferenceChangeListener {
    protected int appWidgetId;

    @Override
    public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      if (getArguments() != null) {
        appWidgetId = getArguments().getInt(AppWidgetManager.EXTRA_APPWIDGET_ID);
        getPreferenceManager().setSharedPreferencesName("Widget." + appWidgetId);
        getPreferenceManager().setSharedPreferencesMode(Context.MODE_PRIVATE);
      }
    }

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
    }

    @Override
    public void onDisplayPreferenceDialog(@NonNull Preference preference) {
      if (getParentFragmentManager().findFragmentByTag(TAG) != null) {
        return;
      }

      if (preference instanceof NotificationSettingDialogPreference) {
        final NotificationSettingDialogFragment f = NotificationSettingDialogFragment.newInstance(preference.getKey());
        f.setTargetFragment(this, 0);
        f.show(getParentFragmentManager(), TAG);
      } else if (preference instanceof IconPreference) {
        final IconDialogFragment f = IconDialogFragment.newInstance(preference.getKey());
        f.setTargetFragment(this, 0);
        f.show(getParentFragmentManager(), TAG);
      } else if (preference instanceof ColorPreference) {
        final ColorDialogFragment f = ColorDialogFragment.newInstance(preference.getKey());
        f.setTargetFragment(this, 0);
        f.show(getParentFragmentManager(), TAG);
      } else {
        super.onDisplayPreferenceDialog(preference);
      }
    }

    @Override
    public void onStart() {
      super.onStart();
      refreshSummaries();
      SharedPreferences sharedPreferences = getPreferenceManager().getSharedPreferences();
      if (sharedPreferences != null) {
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);
      }
    }

    @Override
    public void onStop() {
      super.onStop();
      SharedPreferences sharedPreferences = getPreferenceManager().getSharedPreferences();
      if (sharedPreferences != null) {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
      }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
      refreshSummaries();
    }

    protected abstract void refreshSummaries();
  }

  public static class HeadersSettingsFragment extends BasePreferenceFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      addPreferencesFromResource(R.xml.settings_headers);
    }

    @Override
    protected void refreshSummaries() {

    }
  }

  public static class GraphSettingsFragment extends BasePreferenceFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      addPreferencesFromResource(R.xml.graph_settings);
    }

    @Override
    protected void refreshSummaries() {
      EditTextIntegerPreference intpref = findPreference("GraphWidth");
      if (intpref != null) {
        intpref.setSummary(String.format(Locale.ENGLISH, "%d px", intpref.getInteger()));
      }

      intpref = findPreference("GraphHeight");
      if (intpref != null) {
        intpref.setSummary(String.format(Locale.ENGLISH, "%d px", intpref.getInteger()));
      }

      ListPreference listpref = findPreference("NumHours");
      if (listpref != null) {
        listpref.setSummary(listpref.getEntry());
      }
    }
  }

  /** Preference fragment for a single bluetooth device. */
  @SuppressLint("MissingPermission") // We wouldn't get here if we don't have permission.
  public static class BluetoothDeviceSettingsFragment extends BasePreferenceFragment {
    @Nullable
    private BluetoothDevice getDevice(int deviceId) {
      HashMap<String, Integer> deviceIds = BatteryStatus.getBluetoothDeviceIds(requireContext());
      String deviceName = null;
      for (String name : deviceIds.keySet()) {
        if (Objects.equals(deviceIds.get(name), deviceId)) {
          deviceName = name;
        }
      }
      if (deviceName == null) {
        return null;
      }
      for (BluetoothDevice device : BluetoothAdapter.getDefaultAdapter().getBondedDevices()) {
        if (deviceName.equals(device.getName())) {
          return device;
        }
      }

      return null;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      setPreferenceScreen(getPreferenceManager().createPreferenceScreen(requireContext()));

      Bundle args = getArguments();
      BluetoothDevice device = null;
      if (args != null) {
        device = getDevice(args.getInt("deviceId"));
      }
      if (device == null) {
        // This shouldn't happen, we've already checked everything.
        Log.e(TAG, "Device is unexpectedly null");
        getParentFragmentManager().popBackStack();
        return;
      }

      PreferenceCategory category = new PreferenceCategory(requireContext());
      category.setTitle(device.getName());
      getPreferenceScreen().addPreference(category);

      CheckBoxPreference enabledPref = new CheckBoxPreference(requireContext());
      enabledPref.setKey(device.getName());
      enabledPref.setTitle("Show graph");
      getPreferenceScreen().addPreference(enabledPref);

      IconPreference iconPref = new IconPreference(requireContext());
      iconPref.setKey(device.getName() + ".Icon");
      iconPref.setTitle("Icon");
      getPreferenceScreen().addPreference(iconPref);

      ColorPreference colorPref = new ColorPreference(requireContext());
      colorPref.setKey(device.getName() + ".Color");
      colorPref.setTitle("Color");
      getPreferenceScreen().addPreference(colorPref);
    }

    @Override
    protected void refreshSummaries() {
    }
  }

  public static class BluetoothSettingsFragment extends BasePreferenceFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      setPreferenceScreen(getPreferenceManager().createPreferenceScreen(requireContext()));

      PreferenceCategory category = new PreferenceCategory(requireActivity());
      category.setTitle("Bluetooth Devices");
      getPreferenceScreen().addPreference(category);

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
          ActivityCompat.checkSelfPermission(
              requireActivity(), Manifest.permission.BLUETOOTH_CONNECT)
              != PackageManager.PERMISSION_GRANTED) {
          ActivityCompat.requestPermissions(
              requireActivity(),
              new String[] { Manifest.permission.BLUETOOTH_CONNECT },
              BLUETOOTH_PERMISSION_TAG);
        return;
      }

      SharedPreferences prefs = getPreferenceManager().getSharedPreferences();

      HashMap<String, Integer> deviceIds = BatteryStatus.getBluetoothDeviceIds(requireContext());
      for (BluetoothDevice device : BluetoothAdapter.getDefaultAdapter().getBondedDevices()) {
        Integer deviceId = deviceIds.get(device.getName());
        if (deviceId == null) {
          deviceId = BatteryStatus.addBluetoothDevice(requireActivity(), device.getName());
        }

        Preference pref = new Preference(requireActivity());
        pref.setFragment(BluetoothDeviceSettingsFragment.class.getName());
        pref.getExtras().putInt("deviceId", deviceId);
        pref.setTitle(device.getName());
        if (prefs != null) {
          String iconName = prefs.getString(device.getName() + ".Icon", "");
          int color = prefs.getInt(device.getName() + ".Color", Color.argb(200, 0x00, 0xba, 0xff));
          Integer resId = IconPreference.icons.get(iconName);
          if (resId != null) {
            Drawable iconDrawable = DrawableCompat.wrap(requireContext().getDrawable(resId));
            DrawableCompat.setTint(iconDrawable, color);
            pref.setIcon(iconDrawable);
          }

          if (prefs.getBoolean(device.getName(), false)) {
            pref.setSummary("Enabled");
          }
        }
        getPreferenceScreen().addPreference(pref);
      }
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
          findPreference("IncludeBatteryCurrentInstant");
      Preference batteryCurrentAvgPref = findPreference("IncludeBatteryCurrentAvg");
      Preference batteryEnergyPref = findPreference("IncludeBatteryEnergy");
      if (batteryCurrentInstantPref == null || batteryCurrentAvgPref == null ||
          batteryEnergyPref == null) {
        return;
      }

      BatteryManager batteryManager = (BatteryManager) requireActivity()
          .getSystemService(Context.BATTERY_SERVICE);
      batteryCurrentInstantPref.setEnabled(batteryManager.getIntProperty(
          BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) != Integer.MIN_VALUE);
      batteryCurrentAvgPref.setEnabled(batteryManager.getIntProperty(
          BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE) != Integer.MIN_VALUE);
      batteryEnergyPref.setEnabled(batteryManager.getIntProperty(
          BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER) != Integer.MIN_VALUE);
      if (!batteryCurrentInstantPref.isEnabled()) {
        SharedPreferences sharedPreferences = batteryCurrentInstantPref.getSharedPreferences();
        if (sharedPreferences != null) {
          sharedPreferences.edit()
              .putBoolean(batteryCurrentInstantPref.getKey(), false)
              .apply();
        }
      }
      if (!batteryCurrentAvgPref.isEnabled()) {
        SharedPreferences sharedPreferences = batteryCurrentAvgPref.getSharedPreferences();
        if (sharedPreferences != null) {
          sharedPreferences.edit()
              .putBoolean(batteryCurrentAvgPref.getKey(), false)
              .apply();
        }
      }
      if (!batteryEnergyPref.isEnabled()) {
        SharedPreferences sharedPreferences = batteryEnergyPref.getSharedPreferences();
        if (sharedPreferences != null) {
          sharedPreferences.edit()
              .putBoolean(batteryEnergyPref.getKey(), false)
              .apply();
        }
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
      ListPreference listpref = findPreference("TempUnits");
      if (listpref != null) {
        listpref.setSummary(listpref.getEntry());
      }
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

    @NonNull
    @Override
    public View onCreateView(
        @NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
      final View v = super.onCreateView(inflater, container, savedInstanceState);

      registerForContextMenu(getListView());

      return v;
    }

    @Override
    public void onCreateContextMenu(
        @NonNull ContextMenu menu, @NonNull View v, ContextMenuInfo menuInfo) {
      new MenuInflater(getActivity()).inflate(R.menu.notification_settings_menu, menu);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
      int position = ((AdapterView.AdapterContextMenuInfo) item.getMenuInfo()).position;
      if (item.getItemId() == R.id.delete) {
        deleteNotificationSetting(position + 1);
      } else if (item.getItemId() == R.id.edit) {
          //showNotificationSetting(position + 1);
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
      if (prefs == null) return;
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
      if (prefs == null) {
        return;
      }

      for (int i = 1; ; i++) {
        String key = String.format(Locale.US, "notification:%d:percent", i);
        if (prefs.getInt(key, -1) < 0) {
          break;
        }

        int percent = prefs.getInt(key, -1);
        String dir = prefs.getString(String.format(Locale.US, "notification:%d:direction", i), "");
        String device =
            prefs.getString(String.format(Locale.US, "notification:%d:device", i), "Phone");

        NotificationSettingDialogPreference pref =
            new NotificationSettingDialogPreference(getActivity());
        pref.setKey(String.format(Locale.US, "notification:%d", i));
        pref.setTitle(
            String.format(Locale.US, "%s %d%% and %s", device, percent, dir.toLowerCase()));
        pref.setPositiveButtonText("Save");
        pref.setNegativeButtonText("Cancel");
        screen.addPreference(pref);
      }

      NotificationSettingDialogPreference pref =
          new NotificationSettingDialogPreference(getActivity());
      pref.setKey("notification:new");
      pref.setTitle("Add Notification");
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
