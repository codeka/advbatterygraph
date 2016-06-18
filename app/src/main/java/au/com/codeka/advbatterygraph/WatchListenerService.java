package au.com.codeka.advbatterygraph;

import android.os.Parcel;
import android.util.Log;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

import java.util.List;

/** This service listens for events from an Android Wear watch. */
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
      parcel.recycle();

      Log.i(TAG, "Got new percent from watch: " + percent);
      BatteryStatus status = new BatteryStatus.Builder(1)
          .chargeFraction(percent)
          .timestamp(timestamp)
          .build();
      BatteryStatus.save(this, status);
    }
  }
}
