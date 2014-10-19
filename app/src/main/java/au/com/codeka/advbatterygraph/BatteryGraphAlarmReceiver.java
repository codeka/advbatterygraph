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

        // Notify the widget to update itself
        BatteryGraphWidgetProvider.notifyRefresh(context, null);

        // if we have to display a notification, do it now
        new Notifier().handleNotifications(context, 0, percent);

        // percent the last percent
        sLastPercent = percent;
    }

}
