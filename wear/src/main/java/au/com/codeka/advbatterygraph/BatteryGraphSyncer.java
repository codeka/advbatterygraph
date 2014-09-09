package au.com.codeka.advbatterygraph;

import android.content.Context;
import android.os.Bundle;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

/**
 * This class syncs the battery graph details with the phone.
 */
public class BatteryGraphSyncer {
    private GoogleApiClient googleApiClient;

    public static float lastPercent;

    public BatteryGraphSyncer(Context context) {
        googleApiClient = new GoogleApiClient.Builder(context)
                .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                    @Override
                    public void onConnected(Bundle connectionHint) {
                    }
                    @Override
                    public void onConnectionSuspended(int cause) {
                    }
                })
                .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(ConnectionResult result) {
                    }
                })
                .addApi(Wearable.API)
                .build();
        googleApiClient.connect();
    }

    public void syncBatteryGraph(float percent, long timestamp) {
        PutDataMapRequest dataMap = PutDataMapRequest.create("/advbatterygraph/battery");
        dataMap.getDataMap().putFloat("percent", percent);
        dataMap.getDataMap().putLong("timestamp", timestamp);
        PutDataRequest request = dataMap.asPutDataRequest();
        Wearable.DataApi.putDataItem(googleApiClient, request);

        lastPercent = percent;
    }
}
