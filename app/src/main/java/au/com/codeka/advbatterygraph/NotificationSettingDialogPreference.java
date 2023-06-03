package au.com.codeka.advbatterygraph;

import java.util.Locale;
import java.util.Map;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AttributeSet;

import androidx.preference.DialogPreference;

public class NotificationSettingDialogPreference extends DialogPreference {
  public static class Value {
    public int percent;
    public String direction; // TODO: make this an enum
    public String device;
  }

  public NotificationSettingDialogPreference(Context context) {
    this(context, null);
  }

  public NotificationSettingDialogPreference(Context context, AttributeSet attrs) {
    super(context, attrs);
    setPersistent(false);
    setDialogLayoutResource(R.layout.notification_pref);
  }

  public Value getValue() {
    SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
    Value v = new Value();
    v.percent = prefs.getInt(getKey() + ":percent", 0);
    v.direction = prefs.getString(getKey() + ":direction", "");
    v.device = prefs.getString(getKey() + ":device", "");
    return v;
  }

  public void setValue(Value value) {
    if (getKey().equals("notification:new")) {
      generateNewKey();
    }

    getSharedPreferences().edit()
        .putInt(getKey() + ":percent", value.percent)
        .putString(getKey() + ":direction", value.direction)
        .putString(getKey() + ":device", value.device)
        .apply();
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

    setKey(String.format(Locale.ENGLISH, "notification:%d", maxKey + 1));
  }
}
