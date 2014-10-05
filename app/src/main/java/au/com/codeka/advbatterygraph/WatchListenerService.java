package au.com.codeka.advbatterygraph;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcel;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.common.data.FreezableUtils;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

import java.util.List;

/**
 * This service listens for events from the watch.
 */
public class WatchListenerService extends WearableListenerService {
    private static final String TAG = "WatchListenerService";

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        if (messageEvent.getPath().equals("/advbatterygraph/Status")) {
            Parcel parcel = Parcel.obtain();
            parcel.unmarshall(messageEvent.getData(), 0, messageEvent.getData().length);
            parcel.setDataPosition(0);
            long timestamp = parcel.readLong();
            float percent = parcel.readFloat();

            Log.i(TAG, "Got new percent from watch: "+percent);
            BatteryStatus status = new BatteryStatus.Builder(1)
                    .chargeFraction(percent)
                    .timestamp(timestamp)
                    .build();
            BatteryStatus.save(this, status);
        }
    }
}
