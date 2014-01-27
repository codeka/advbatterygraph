package au.com.codeka.advbatterygraph;

import java.util.List;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

public class SettingsActivity extends PreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        Intent i = new Intent(this, BatteryGraphWidgetProvider.class);
        i.setAction(BatteryGraphWidgetProvider.CUSTOM_REFRESH_ACTION);
        sendBroadcast(i);
    }

    public static class GraphSettingsFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.graph_settings);
        }
    }

    public static class TempSettingsFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.temp_settings);
        }
    }

    public static class NotificationSettingsFragment extends PreferenceFragment
                                                     implements SharedPreferences.OnSharedPreferenceChangeListener {

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.notification_settings);
            refreshNotificationPrefs();

            getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
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
            switch (item.getItemId()) {
                case R.id.delete:
                    int position = ((AdapterView.AdapterContextMenuInfo) item.getMenuInfo()).position;
                    deleteNotificationSetting(position + 1);
                    break;
                case R.id.edit:
                    // TODO
                    break;
            }

            return super.onOptionsItemSelected(item);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (key.startsWith("notification:")) {
                refreshNotificationPrefs();
            }
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
}
