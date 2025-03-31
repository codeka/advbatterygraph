package au.com.codeka.advbatterygraph;

import android.Manifest;
import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.BatteryManager;
import android.os.Build;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.preference.CheckBoxPreference;

import java.lang.reflect.Method;
import java.util.HashMap;

public class BatteryGraphAlarmReceiver extends BroadcastReceiver {
  private static final String TAG = "advbattery.Alarm";

  /** This is called by the system when an alarm is received. */
  @Override
  public void onReceive(Context context, Intent intent) {
    // get the current battery status intent
    Intent batteryStatus =
        context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    if (batteryStatus == null) {
      Log.w(TAG, "BatteryStatus is null!");
      return;
    }

    long currentInstantMicroAmperes = 0;
    long currentAvgMicroAmperes = 0;
    long energyMicroWattHours = 0;
    BatteryManager batteryManager =
        (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
    currentInstantMicroAmperes =
        getSpecialValue(batteryManager, BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
    currentAvgMicroAmperes =
        getSpecialValue(batteryManager, BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE);
    energyMicroWattHours =
        getSpecialValue(batteryManager, BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER);

    int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
    int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
    float fraction = (float) level / scale;
    int percent = (int) (fraction * 100.0f);

    int temperature = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);

    BatteryStatus status = new BatteryStatus.Builder(0) // device 0 is the phone
        .chargeFraction(fraction)
        .batteryTemp(temperature / 10.0f)
        .batteryCurrentInstant((float) currentInstantMicroAmperes / 1000.0f)
        .batteryCurrentAvg((float) currentAvgMicroAmperes / 1000.0f)
        .batteryEnergy((float) energyMicroWattHours / 1000000.0f)
        .build();
    BatteryStatus.save(context, status);
    Log.d(TAG, String.format(
        "Battery status: %.2f%% %.1f°", fraction * 100.0f, temperature / 10.0f));

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        ActivityCompat.checkSelfPermission(
            context, Manifest.permission.BLUETOOTH_CONNECT)
            == PackageManager.PERMISSION_GRANTED) {
      // We have bluetooth permission, so can fetch status for them.
      BatteryStatus.getBluetoothDeviceIds(context);

      HashMap<String, Integer> knownDevices = BatteryStatus.getBluetoothDeviceIds(context);
      for (BluetoothDevice device : BluetoothAdapter.getDefaultAdapter().getBondedDevices()) {
        try {
          Integer id = knownDevices.get(device.getName());
          if (id == null) {
            id = BatteryStatus.addBluetoothDevice(context, device.getName());
          }

          Method method = device.getClass().getMethod("getBatteryLevel");
          Object deviceLevel = method.invoke(device);
          if (deviceLevel == null) {
            continue;
          }
          status = new BatteryStatus.Builder(id)
              .chargeFraction((float)((int) deviceLevel) / 100.0f)
              .build();
          BatteryStatus.save(context, status);
        } catch (Exception e) {
          Log.i(TAG, "error getting battery level: " + e);
        }
      }
    }

    // Notify the widget to update itself
    BatteryGraphWidgetProvider.notifyRefresh(context, null);

    // if we have to display a notification, do it now
    new Notifier().handleNotifications(context, 0, percent);
  }

  private int getSpecialValue(BatteryManager batteryManager, int key) {
    int value = batteryManager.getIntProperty(key);
    if (value == Integer.MIN_VALUE) {
      return 0;
    }
    return value;
  }
}
