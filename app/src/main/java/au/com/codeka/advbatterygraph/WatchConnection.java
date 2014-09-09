package au.com.codeka.advbatterygraph;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import java.util.ArrayList;

/**
 * Class that represents our connection to the watch.
 */
public class WatchConnection {
    private static final String TAG = WatchConnection.class.getSimpleName();
    private GoogleApiClient googleApiClient;
    private boolean isConnected;
    private ArrayList<Message> pendingMessages = new ArrayList<Message>();
    private ArrayList<String> watchNodes = new ArrayList<String>();

    public void setup(Context context) {
        googleApiClient = new GoogleApiClient.Builder(context)
                .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                    @Override
                    public void onConnected(Bundle connectionHint) {
                        Log.d(TAG, "onConnected: " + connectionHint);
                        Wearable.NodeApi.getConnectedNodes(googleApiClient).setResultCallback(
                                new ResultCallback<NodeApi.GetConnectedNodesResult>() {
                                    @Override
                                    public void onResult(NodeApi.GetConnectedNodesResult getConnectedNodesResult) {
                                        for (Node node : getConnectedNodesResult.getNodes()) {
                                            watchNodes.add(node.getId());
                                        }
                                        isConnected = true;
                                        for (Message msg : pendingMessages) {
                                            sendMessage(msg);
                                        }
                                        pendingMessages.clear();
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
                    public void onConnectionFailed(ConnectionResult result) {
                        Log.d(TAG, "onConnectionFailed: " + result);
                        isConnected = false;
                    }
                })
                .addApi(Wearable.API)
                .build();
    }

    public void start() {
        googleApiClient.connect();
    }

    public void stop() {
        googleApiClient.disconnect();
    }

    public void sendMessage(Message msg) {
        Log.i(TAG, "Sending message: "+msg.getPath());
        if (!isConnected) {
            Log.i(TAG, "Adding to pending messages.");
            pendingMessages.add(msg);
        } else {
            Log.i(TAG, "Actually sending!");
            for (String watchNode : watchNodes) {
                Wearable.MessageApi.sendMessage(googleApiClient, watchNode, msg.getPath(),
                        msg.getPayload());
            }
        }
    }

    public static class Message {
        private final String path;
        private final byte[] payload;

        public Message(String path) {
            this.path = path;
            this.payload = null;
        }

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
