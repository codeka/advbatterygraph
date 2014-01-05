package au.com.codeka.advbatterygraph;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

public class BatteryGraphAlarmReceiver extends BroadcastReceiver {
    /** This is called by the system when an alarm is received. */
    @Override
    public void onReceive(Context context, Intent intent) 
    {
        // get the current battery status intent
        Intent batteryStatus = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        float percent = (float) level / scale;

        BatteryStatus status = new BatteryStatus.Builder()
                                                .chargePercent(percent)
                                                .build();
        BatteryStatus.save(context, status);
    }
}
