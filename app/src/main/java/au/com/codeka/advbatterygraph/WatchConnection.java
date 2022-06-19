package au.com.codeka.advbatterygraph;

import android.content.Context;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import java.util.ArrayList;

/** Class that represents our connection to an Android Wear watch. */
public class WatchConnection {
  private static final String TAG = WatchConnection.class.getSimpleName();
  public static WatchConnection i = new WatchConnection();

  private GoogleApiClient googleApiClient;
  private boolean isConnected;
  private final ArrayList<Message> pendingMessages;
  private final ArrayList<String> watchNodes;

  private WatchConnection() {
    isConnected = false;
    pendingMessages = new ArrayList<>();
    watchNodes = new ArrayList<>();
  }

  void setup(Context context, @Nullable final Runnable onConnectedRunnable) {
    if (googleApiClient != null) {
      // only need to do this once.
      if (isConnected && onConnectedRunnable != null) {
        onConnectedRunnable.run();
      }
      return;
    }

    googleApiClient = new GoogleApiClient.Builder(context)
        .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
          @Override
          public void onConnected(Bundle connectionHint) {
            Log.d(TAG, "onConnected: " + connectionHint);
            Wearable.NodeApi.getConnectedNodes(googleApiClient).setResultCallback(
                new ResultCallback<NodeApi.GetConnectedNodesResult>() {
                  @Override
                  public void onResult(@NonNull NodeApi.GetConnectedNodesResult getConnectedNodesResult) {
                    for (Node node : getConnectedNodesResult.getNodes()) {
                      watchNodes.add(node.getId());
                    }
                    isConnected = true;
                    for (Message msg : pendingMessages) {
                      sendMessage(msg);
                    }
                    pendingMessages.clear();
                    if (onConnectedRunnable != null) {
                      onConnectedRunnable.run();
                    }
                  }
                });
          }

          @Override
          public void onConnectionSuspended(int cause) {
            Log.d(TAG, "onConnectionSuspended: " + cause);
            isConnected = false;
          }
        })
        .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
          @Override
          public void onConnectionFailed(@NonNull ConnectionResult result) {
            Log.d(TAG, "onConnectionFailed: " + result);
            isConnected = false;
          }
        })
        .addApi(Wearable.API)
        .build();
  }

  void start() {
    googleApiClient.connect();
  }

  void stop() {
    googleApiClient.disconnect();
  }

  boolean isConnected() {
    return this.isConnected && watchNodes.size() > 0;
  }

  void sendMessage(Message msg) {
    if (!isConnected) {
      Log.d(TAG, "Messages " + msg.getPath() + " added to pending queue.");
      pendingMessages.add(msg);
    } else {
      Log.d(TAG, "Sending message: " + msg.getPath());
      for (String watchNode : watchNodes) {
        Wearable.MessageApi.sendMessage(googleApiClient, watchNode, msg.getPath(),
            msg.getPayload());
      }
    }
  }

  static class Message {
    private final String path;
    private final byte[] payload;

    Message(String path, byte[] payload) {
      this.path = path;
      this.payload = payload;
    }

    String getPath() {
      return path;
    }

    byte[] getPayload() {
      return payload;
    }
  }
}
