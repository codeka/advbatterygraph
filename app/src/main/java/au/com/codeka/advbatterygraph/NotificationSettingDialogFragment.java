package au.com.codeka.advbatterygraph;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.preference.DialogPreference;
import androidx.preference.PreferenceDialogFragmentCompat;

import java.util.Locale;

public class NotificationSettingDialogFragment extends PreferenceDialogFragmentCompat {
  public static final String TAG = NotificationSettingDialogFragment.class.getSimpleName();

  private SeekBar percentBar;
  private Spinner directionSpinner;
  private Spinner deviceSpinner;

  public static NotificationSettingDialogFragment newInstance(String key) {
    NotificationSettingDialogFragment fragment = new NotificationSettingDialogFragment();
    final Bundle args = new Bundle(1);
    args.putString(ARG_KEY, key);
    fragment.setArguments(args);
    return fragment;
  }

  @Override
  protected void onBindDialogView(@NonNull View view) {
    super.onBindDialogView(view);

    percentBar = view.findViewById(R.id.percent_bar);
    final TextView percentText = view.findViewById(R.id.percent_text);
    directionSpinner = view.findViewById(R.id.direction_spinner);
    deviceSpinner = view.findViewById(R.id.device_spinner);
    percentBar = view.findViewById(R.id.percent_bar);

    percentBar.setMax(20);
    percentBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
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
    deviceSpinner.setAdapter(deviceAdapter);

    ArrayAdapter<CharSequence> directionAdapter = ArrayAdapter.createFromResource(getContext(),
        R.array.charge_directions, android.R.layout.simple_spinner_item);
    directionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    directionSpinner.setAdapter(directionAdapter);

    DialogPreference dialogPreference = getPreference();
    if (!(dialogPreference instanceof NotificationSettingDialogPreference)) {
      return;
    }
    NotificationSettingDialogPreference pref = (NotificationSettingDialogPreference) dialogPreference;
    if (!pref.getKey().equals("notification:new")) {
      NotificationSettingDialogPreference.Value v = pref.getValue();
      percentBar.setProgress(v.percent / 5);
      for (int i = 0; i < directionAdapter.getCount(); i++) {
        if (directionAdapter.getItem(i).equals(v.direction)) {
          directionSpinner.setSelection(i);
        }
      }
      for (int i = 0; i < deviceAdapter.getCount(); i++) {
        if (deviceAdapter.getItem(i).equals(v.device)) {
          deviceSpinner.setSelection(i);
        }
      }
    }
  }

  @Override
  public void onDialogClosed(boolean positiveResult) {
    if (positiveResult) {

      DialogPreference dialogPreference = getPreference();
      if (!(dialogPreference instanceof NotificationSettingDialogPreference)) {
        return;
      }
      NotificationSettingDialogPreference pref =
          (NotificationSettingDialogPreference) dialogPreference;

      NotificationSettingDialogPreference.Value v = new NotificationSettingDialogPreference.Value();
      v.percent = percentBar.getProgress() * 5;
      v.direction = (String) directionSpinner.getSelectedItem();
      v.device = (String) deviceSpinner.getSelectedItem();
      if (pref.callChangeListener(v)) {
        pref.setValue(v);
      }
    }
  }
}
