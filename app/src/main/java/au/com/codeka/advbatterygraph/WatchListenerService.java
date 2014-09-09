package au.com.codeka.advbatterygraph;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.common.data.FreezableUtils;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.WearableListenerService;

import java.util.List;

/**
 * This service listens for events from the watch.
 */
public class WatchListenerService extends WearableListenerService {
    private static final String TAG = "WatchListenerService";

    private Handler handler;

    private static final long AUTO_SYNC_INTERVAL_MS = 6 * 60 * 60 * 1000; // 6 hours

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler();
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        final List<DataEvent> events = FreezableUtils.freezeIterable(dataEvents);
        for (DataEvent event : events) {
            DataMapItem dataItem = DataMapItem.fromDataItem(event.getDataItem());
            if (dataItem.getUri().getPath().equals("/advbatterygraph/battery")) {
                float percent = dataItem.getDataMap().getFloat("percent");
                long timestamp = dataItem.getDataMap().getLong("timestamp");

                Log.i(TAG, "Got new percent from watch: "+percent);
                BatteryStatus status = new BatteryStatus.Builder(1)
                        .chargeFraction(percent)
                        .timestamp(timestamp)
                        .build();
                BatteryStatus.save(this, status);
            }
        }
    }
}
