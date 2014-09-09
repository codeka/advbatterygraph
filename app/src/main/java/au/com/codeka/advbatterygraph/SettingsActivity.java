package au.com.codeka.advbatterygraph;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Locale;

import android.app.Fragment;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.support.v4.content.FileProvider;
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
    private WatchConnection watchConnection = new WatchConnection();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);
        watchConnection.setup(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        watchConnection.start();
        watchConnection.sendMessage(new WatchConnection.Message("/advbatterygraph/Start", null));
    }

    @Override
    protected void onPause() {
        super.onPause();
        watchConnection.stop();
    }

    @Override
    public void onBuildHeaders(List<PreferenceActivity.Header> target) {
        loadHeadersFromResource(R.xml.settings_headers, target);
    }

    /**
     * When preferences change, notify the graph to update itself.
     */
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        BatteryGraphWidgetProvider.notifyRefresh(this);
    }

    /**
     * Base class for our various preference fragments.
     */
    public static abstract class BasePreferenceFragment extends PreferenceFragment
            implements SharedPreferences.OnSharedPreferenceChangeListener {
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
            EditTextIntegerPreference intpref = (EditTextIntegerPreference) findPreference(Settings.PREF_PREFIX+"GraphWidth");
            intpref.setSummary(String.format(Locale.ENGLISH, "%d px", intpref.getInteger()));

            intpref = (EditTextIntegerPreference) findPreference(Settings.PREF_PREFIX+"GraphHeight");
            intpref.setSummary(String.format(Locale.ENGLISH, "%d px", intpref.getInteger()));

            ListPreference listpref = (ListPreference) findPreference(Settings.PREF_PREFIX+"NumHours");
            listpref.setSummary(listpref.getEntry());
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
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){
            final View v = super.onCreateView(inflater, container, savedInstanceState);

            final ListView lv = (ListView) v.findViewById(android.R.id.list);
            registerForContextMenu(lv);

            return v;
        }

        @Override
        public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
            super.onCreateContextMenu(menu, v, menuInfo);
            new MenuInflater(getActivity()).inflate (R.menu.notification_settings_menu, menu);
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
                String key = String.format("notification:%d:percent", j);
                if (prefs.getInt(key, -1) < 0) {
                    break;
                }

                int percent = prefs.getInt(key, -1);
                String dir = prefs.getString(String.format("notification:%d:direction", j), "");

                editor.putInt(String.format("notification:%d:percent", j - 1), percent);
                editor.putString(String.format("notification:%d:direction", j - 1), dir);
                last = j;
            }

            editor.remove(String.format("notification:%d:percent", last));
            editor.remove(String.format("notification:%d:direction", last));
            editor.commit();
        }

        private void refreshNotificationPrefs() {
            PreferenceScreen screen = (PreferenceScreen) getPreferenceScreen();
            screen.removeAll();

            SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
            for (int i = 1; ; i++) {
                String key = String.format("notification:%d:percent", i);
                if (prefs.getInt(key, -1) < 0) {
                    break;
                }

                int percent = prefs.getInt(key, -1);
                String dir = prefs.getString(String.format("notification:%d:direction", i), "");

                NotificationSettingDialog pref = new NotificationSettingDialog(getActivity());
                pref.setKey(String.format("notification:%d", i));
                pref.setTitle(String.format("%d%% and %s", percent, dir.toLowerCase()));
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
        private View mView;

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle args) {
            mView = inflater.inflate(R.layout.export_fragment, parent, false);
            return mView;
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            doExport();
        }

        private void doExport() {
            new AsyncTask<Void, Void, Uri> () {
                @Override
                protected Uri doInBackground(Void... voids) {
                    FileProvider fileProvider = new FileProvider();
                    File dir = new File(getActivity().getCacheDir(), "exports");
                    if (!dir.exists()) {
                        dir.mkdir();
                    }
                    File exportFile = new File(dir, "battery-graph.csv");
                    try {
                        exportFile.createNewFile();
                        BatteryStatus.export(getActivity(), exportFile);
                    } catch (IOException e) {
                        // TODO: handle error
                    }
                    Uri contentUri = fileProvider.getUriForFile(getActivity(),
                            "au.com.codeka.advbatterygraph.exportprovider", exportFile);
                    return contentUri;
                }

                @Override
                protected void onPostExecute(Uri contentUri) {
                    mView.findViewById(R.id.progress).setVisibility(View.GONE);
                    ((TextView) mView.findViewById(R.id.label)).setText("Export complete!");

                    Intent shareIntent = new Intent();
                    shareIntent.setAction(Intent.ACTION_SEND);
                    shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
                    shareIntent.setType("text/csv");
                    shareIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(shareIntent, "Share exported data"));
                }
            }.execute();
        }
    }
}
