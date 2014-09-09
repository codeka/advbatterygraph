package au.com.codeka.advbatterygraph;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.util.Log;

/**
 * This {@link BroadcastReceiver} receives intent every now and then which we use to ensure the
 * {@StepSensorService} is still running and receiving events.
 */
public class AlarmReceiver extends BroadcastReceiver {
    private static final String TAG = AlarmReceiver.class.getSimpleName();

    public static void ensureRunning(Context context) {
        try {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            Intent intent = new Intent(context, AlarmReceiver.class);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0);
            alarmManager.setRepeating(AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis(), 1000 * 60 * 5, pendingIntent);
        } catch (Exception e) {
            Log.e(TAG, "Error starting alarm!");
        }
    }

    /** This is called by the system when an alarm is received. */
    @Override
    public void onReceive(Context context, Intent intent) {
        updateBattery(context);
    }

    private void updateBattery(Context context) {
        Log.i(TAG, "Got alarm!");
        // get the current battery status intent
        // TODO: this should be shared with the phone code better than just duplicating it...
        Intent batteryStatus = context.registerReceiver(null,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        float fraction = (float) level / scale;

        Log.i(TAG, "Battery percent: " + fraction);
        BatteryGraphSyncer syncer = new BatteryGraphSyncer(context);
        syncer.syncBatteryGraph(fraction, System.currentTimeMillis());
    }
}
