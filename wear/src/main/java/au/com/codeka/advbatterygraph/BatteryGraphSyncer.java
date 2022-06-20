package au.com.codeka.advbatterygraph;

import android.content.Context;
import android.os.Bundle;
import android.os.Parcel;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.util.ArrayList;

/**
 * This class syncs the battery graph details with the phone.
 */
public class BatteryGraphSyncer {
  private static final String TAG = "BatteryGraphSyncer";
  private GoogleApiClient googleApiClient;
  private boolean isConnected;
  private ArrayList<Message> pendingMessages = new ArrayList<Message>();
  private ArrayList<String> phoneNodes = new ArrayList<String>();

  public static float lastPercent;

  public BatteryGraphSyncer(Context context) {
    googleApiClient = new GoogleApiClient.Builder(context)
        .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
          @Override
          public void onConnected(Bundle connectionHint) {
            Wearable.NodeApi.getConnectedNodes(googleApiClient).setResultCallback(
                getConnectedNodesResult -> {
                  for (Node node : getConnectedNodesResult.getNodes()) {
                    phoneNodes.add(node.getId());
                  }
                  isConnected = true;
                  for (Message msg : pendingMessages) {
                    sendMessage(msg);
                  }
                  pendingMessages.clear();
                });
          }

          @Override
          public void onConnectionSuspended(int cause) {
            isConnected = false;
          }
        })
        .addOnConnectionFailedListener(result -> isConnected = false)
        .addApi(Wearable.API)
        .build();
    googleApiClient.connect();
  }

  public void syncBatteryGraph(float percent, long timestamp) {
    Parcel parcel = Parcel.obtain();
    parcel.writeLong(timestamp);
    parcel.writeFloat(percent);
    parcel.setDataPosition(0);
    byte[] payload = parcel.marshall();

    Message msg = new Message("/advbatterygraph/Status", payload);
    sendMessage(msg);

    lastPercent = percent;
  }

  public void sendMessage(Message msg) {
    if (!isConnected) {
      Log.d(TAG, "Messages " + msg.getPath() + " added to pending messages.");
      pendingMessages.add(msg);
    } else {
      Log.d(TAG, "Sending message: " + msg.getPath());
      for (String watchNode : phoneNodes) {
        Wearable.MessageApi.sendMessage(googleApiClient, watchNode, msg.getPath(),
            msg.getPayload());
      }
    }
  }

  public static class Message {
    private final String path;
    private final byte[] payload;

    public Message(String path, byte[] payload) {
      this.path = path;
      this.payload = payload;
    }

    public String getPath() {
      return path;
    }

    public byte[] getPayload() {
      return payload;
    }
  }
}
