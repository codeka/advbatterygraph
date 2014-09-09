package au.com.codeka.advbatterygraph;

import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.util.Log;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

/**
 * This is the {@link WearableListenerService} that listens for messages from the phone app.
 */
public class PhoneListenerService extends WearableListenerService {
    private static final String TAG = "PhoneListenerService";

    @Override
    public void onMessageReceived(MessageEvent msgEvent) {
        Log.i(TAG, msgEvent.getPath());
        if (msgEvent.getPath().equals("/advbatterygraph/Start")) {
            Log.i(TAG, "Got notification to fetch battery status!");
            // get the current battery status intent
            // TODO: this should be shared with the phone code better than just duplicating it...
            Intent batteryStatus = this.registerReceiver(null,
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            float fraction = (float) level / scale;

            Log.i(TAG, "Battery percent: " + fraction);
            BatteryGraphSyncer syncer = new BatteryGraphSyncer(this);
            syncer.syncBatteryGraph(fraction, System.currentTimeMillis());
        }
    }
}
