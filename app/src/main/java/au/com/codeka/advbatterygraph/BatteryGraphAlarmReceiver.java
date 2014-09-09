package au.com.codeka.advbatterygraph;

import java.util.Date;
import java.util.List;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.RingtoneManager;
import android.os.BatteryManager;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;

public class BatteryGraphAlarmReceiver extends BroadcastReceiver {
    private static int sLastPercent;

    /** This is called by the system when an alarm is received. */
    @Override
    public void onReceive(Context context, Intent intent) {
        // get the current battery status intent
        Intent batteryStatus = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        float fraction = (float) level / scale;
        int percent = (int) (fraction * 100.0f);

        int temperature = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);

        BatteryStatus status = new BatteryStatus.Builder(0) // device 0 is the phone
                                                .chargeFraction(fraction)
                                                .batteryTemp(temperature / 10.0f)
                                                .build();
        BatteryStatus.save(context, status);

        // if the percent has actually changed from last time, tell the widget to update
        if (sLastPercent == 0 || sLastPercent != percent) {
            BatteryGraphWidgetProvider.notifyRefresh(context);
        }

        // if we have to display a notification, do it now
        handleNotifications(context, percent);

        // percent the last percent
        sLastPercent = percent;
    }

    private void handleNotifications(Context context, int percent) {
        // prefer to use this cached one, than fetching it from the DB every time...
        int lastPercent = sLastPercent;
        if (lastPercent == 0) {
            List<BatteryStatus> history = BatteryStatus.getHistory(context, 0, 1);
            if (history.size() > 1) {
                lastPercent = (int) (history.get(1).getChargeFraction() * 100.0f);
            } else {
                return;
            }
        }
        if (lastPercent == percent) {
            // if we haven't clicked over a percent, nothing to do.
            return;
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        for (int i = 1; ; i++) {
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
                notifyCharging ?  "charging" : "discharging");

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
