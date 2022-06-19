package au.com.codeka.advbatterygraph;

import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.RingtoneManager;
import android.preference.PreferenceManager;
import androidx.core.app.NotificationCompat;

import java.util.Date;
import java.util.List;

/**
 * Helper class for determining whether a new notification needs to be posted.
 */
public class Notifier {
  /**
   * Checks the last percent of this device, and displays a notification if required.
   */
  public void handleNotifications(Context context, int device, int percent) {
    int lastPercent;
    List<BatteryStatus> history = BatteryStatus.getHistory(context, device, 1);
    if (history.size() > 1) {
      lastPercent = (int) (history.get(1).getChargeFraction() * 100.0f);
    } else {
      return;
    }
    if (lastPercent == percent) {
      // if we haven't clicked over a percent, nothing to do.
      return;
    }

    String deviceName = context.getResources().getStringArray(R.array.devices)[device];
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    for (int i = 1; ; i++) {
      if (!prefs.getString(String.format("notification:%d:device", i), "Phone").equals(deviceName)) {
        continue;
      }

      String key = String.format("notification:%d:percent", i);
      if (prefs.getInt(key, -1) < 0) {
        break;
      }

      int notifyPercent = prefs.getInt(key, -1);
      String notifyDirection = prefs.getString(String.format("notification:%d:direction", i), "");
      boolean notifyCharging = notifyDirection.toLowerCase().equals("charging");

      if (needNotification((int) notifyPercent, notifyCharging, lastPercent, percent)) {
        displayNotification(context, notifyPercent, notifyCharging);
      }
    }
  }

  private boolean needNotification(int notifyPercent, boolean notifyCharging,
                                   int lastPercent, int currPercent) {
    if (lastPercent == currPercent) {
      return false;
    }

    if (notifyCharging) {
      return (lastPercent < notifyPercent && currPercent >= notifyPercent);
    } else {
      return (lastPercent > notifyPercent && currPercent <= notifyPercent);
    }
  }

  private void displayNotification(Context context, int notifyPercent, boolean notifyCharging) {
    NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
    builder.setSmallIcon(R.drawable.ic_launcher); // TODO: better icon
    builder.setAutoCancel(true);

    String title = String.format("Battery at %d%% and %s", notifyPercent,
        notifyCharging ? "charging" : "discharging");

    builder.setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_launcher)); // TODO: better icon
    builder.setContentTitle(title);
    builder.setWhen(new Date().getTime());

    // TODO: allow these to be configured
    builder.setLights(Color.WHITE, 1000, 5000);
    builder.setSound(RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_NOTIFICATION));

    NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    nm.notify(1, builder.build());
  }
}
