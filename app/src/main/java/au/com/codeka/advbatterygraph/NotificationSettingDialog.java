package au.com.codeka.advbatterygraph;

import java.util.Locale;
import java.util.Map;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

public class NotificationSettingDialog extends DialogPreference {
    private SeekBar mPercentBar;
    private Spinner mDirectionSpinner;
    private Spinner mDeviceSpinner;

    public NotificationSettingDialog(Context context) {
        this(context, null);
    }

    public NotificationSettingDialog(Context context, AttributeSet attrs) {
        super(context, attrs);
        setPersistent(false);
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);

        mPercentBar = (SeekBar) view.findViewById(R.id.percent_bar);
        final TextView percentText = (TextView) view.findViewById(R.id.percent_text);
        mDirectionSpinner = (Spinner) view.findViewById(R.id.direction_spinner);
        mDeviceSpinner = (Spinner) view.findViewById(R.id.device_spinner);

        mPercentBar.setMax(20);
        mPercentBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int percent = progress * 5;
                percentText.setText(String.format(Locale.ENGLISH, "%d %%", percent));
            }
        });

        ArrayAdapter<CharSequence> deviceAdapter = ArrayAdapter.createFromResource(getContext(),
                R.array.devices, android.R.layout.simple_spinner_item);
        deviceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mDeviceSpinner.setAdapter(deviceAdapter);

        ArrayAdapter<CharSequence> directionAdapter = ArrayAdapter.createFromResource(getContext(),
                R.array.charge_directions, android.R.layout.simple_spinner_item);
        directionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mDirectionSpinner.setAdapter(directionAdapter);

        if (!getKey().equals("notification:new")) {
            SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
            int percent = prefs.getInt(getKey()+":percent", 0);
            String direction = prefs.getString(getKey()+":direction", "");
            String device = prefs.getString(getKey()+":device", "");

            mPercentBar.setProgress(percent / 5);
            for (int i = 0; i < directionAdapter.getCount(); i++) {
                if (directionAdapter.getItem(i).equals(direction)) {
                    mDirectionSpinner.setSelection(i);
                }
            }
            for (int i = 0; i < deviceAdapter.getCount(); i++) {
                if (deviceAdapter.getItem(i).equals(device)) {
                    mDeviceSpinner.setSelection(i);
                }
            }
        }
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);
        if (positiveResult) {
            if (getKey().equals("notification:new")) {
                generateNewKey();
            }

            int percent = mPercentBar.getProgress() * 5;
            String direction = (String) mDirectionSpinner.getSelectedItem();
            String device = (String) mDeviceSpinner.getSelectedItem();

            getSharedPreferences().edit()
                .putInt(getKey()+":percent", percent)
                .putString(getKey()+":direction", direction)
                .putString(getKey()+":device", device)
                .commit();
        }
    }

    /**
     * Called when we're persisting a new preference, we need to come up with a new key.
     */
    private void generateNewKey() {
        Map<String, ?> prefs = getSharedPreferences().getAll();
        int maxKey = 0;
        for (String key : prefs.keySet()) {
            if (key.startsWith("notification:")) {
                String[] parts = key.split(":");
                try {
                    int thisKey = Integer.parseInt(parts[1]);
                    if (thisKey > maxKey) {
                        maxKey = thisKey;
                    }
                } catch (NumberFormatException e) {
                    // ignore, not a valid pref
                }
            }
        }

        setKey(String.format("notification:%d", maxKey+1));
    }
}
