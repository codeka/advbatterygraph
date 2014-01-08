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
import android.os.BatteryManager;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;

public class BatteryGraphAlarmReceiver extends BroadcastReceiver {
    /** This is called by the system when an alarm is received. */
    @Override
    public void onReceive(Context context, Intent intent) {
        // get the current battery status intent
        Intent batteryStatus = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        float percent = (float) level / scale;

        BatteryStatus status = new BatteryStatus.Builder()
                                                .chargePercent(percent)
                                                .build();
        BatteryStatus.save(context, status);

        int extraStatus = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, 0);
        boolean isCharging = ((extraStatus & BatteryManager.BATTERY_STATUS_FULL) != 0 ||
                              (extraStatus & BatteryManager.BATTERY_STATUS_CHARGING) != 0);
        handleNotifications(context, percent, isCharging);
    }

    private void handleNotifications(Context context, float percent, boolean isCharging) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        // TODO: this is horribly inefficient!
        List<BatteryStatus> history = BatteryStatus.getHistory(context, 1);

        for (int i = 1; ; i++) {
            String key = String.format("notification:%d:percent", i);
            if (prefs.getInt(key, -1) < 0) {
                break;
            }

            int notifyPercent = prefs.getInt(key, -1);
            String notifyDirection = prefs.getString(String.format("notification:%d:direction", i), "");
            boolean notifyCharging = notifyDirection.toLowerCase().equals("charging");

            if (needNotification(notifyPercent, notifyCharging,
                    history.get(1).getChargePercent(), history.get(0).getChargePercent(), isCharging)) {
                displayNotification(context, notifyPercent, isCharging);
            }
        }
    }

    private boolean needNotification(float notifyPercent, boolean notifyCharging,
            float lastPercent, float currPercent, boolean currCharging) {
        if (notifyCharging != currCharging) {
            return false;
        }

        if (notifyCharging) {
            return (lastPercent < notifyPercent && currPercent >= notifyPercent);
        } else {
            return (lastPercent >= notifyPercent && currPercent < notifyPercent);
        }
    }

    private void displayNotification(Context context, float notifyPercent, boolean isCharging) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
        builder.setSmallIcon(R.drawable.ic_launcher); // TODO: better icon
        builder.setAutoCancel(true);

        NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();

        String title = String.format("Battery at %d%% and %s", (int) notifyPercent,
                isCharging ?  "charging" : "discharging");

        builder.setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_launcher)); // TODO: better icon
        builder.setContentTitle(title);
        builder.setWhen(new Date().getTime());

        inboxStyle.setBigContentTitle(title);
        builder.setStyle(inboxStyle);
//        builder.setLights(options.getLedColour(), 1000, 5000);

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(1, builder.build());

    }
}
