package au.com.codeka.advbatterygraph;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

/**
 * The class manages and maintains the historical battery state.
 */
public class BatteryStatus {
  private static final String TAG = "BatteryStatus";

  private Date date;
  private int device;
  private float chargeFraction;
  private float batteryTemp;
  private float batteryCurrentInstant;
  private float batteryCurrentAvg;
  private float batteryEnergy;

  private BatteryStatus() {
  }

  public int getDevice() {
    return device;
  }

  public float getChargeFraction() {
    return chargeFraction;
  }

  public float getBatteryTemp() {
    return batteryTemp;
  }

  public float getBatteryCurrentInstant() {
    return batteryCurrentInstant;
  }

  public float getBatteryCurrentAvg() {
    return batteryCurrentAvg;
  }

  public float getBatteryEnergy() {
    return batteryEnergy;
  }

  public Date getDate() {
    return date;
  }

  public static void save(Context context, BatteryStatus status) {
    new Store(context).save(status);

    if (new Random().nextDouble() < 0.01) {
      // every now and then, we need to clear out the old data, so delete
      // everything older than 1 week
      Calendar cal = Calendar.getInstance();
      cal.setTime(new Date());
      cal.add(Calendar.DAY_OF_MONTH, -7);
      Date dt = cal.getTime(); // 7 days ago

      new Store(context).deleteOlderThan(dt.getTime());
    }
  }

  /**
   * Returns the last {@see numHours} worth of battery status.
   *
   * @param numHours The number of hours of data to return.
   */
  public static List<BatteryStatus> getHistory(Context context, int device, int numHours) {
    Calendar cal = Calendar.getInstance();
    cal.setTime(new Date());
    cal.add(Calendar.HOUR_OF_DAY, -numHours);
    Date dt = cal.getTime();

    return new Store(context).getHistory(device, dt.getTime());
  }

  /**
   * Exports the complete history of battery data to the given file.
   *
   * @param context A {@link Context}.
   * @param csvFile {@link File} to write the exported data (as a .csv file).
   * @return The number of records exported.
   * @throws IOException
   */
  public static long export(Context context, File csvFile) throws IOException {
    DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH);

    PrintWriter writer = new PrintWriter(new FileOutputStream(csvFile));
    writer.println("Timestamp,PhoneBatteryFraction,PhoneBatteryCurrentInstantMilliAmperes," +
                   "PhoneBatteryCurrentAvgMilliAmperes,PhoneEnergyMilliWattHours," +
                   "PhoneTemperatureCelsius,WatchBatteryFraction");
    List<BatteryStatus> statuses = new Store(context).export();
    Log.i(TAG, "Writing " + statuses.size() + " records to export file.");
    for (int i = 0; i < statuses.size(); i++) {
      BatteryStatus batteryStatus = statuses.get(i);
      writer.write(df.format(batteryStatus.getDate()));
      writer.write(",");
      if (batteryStatus.getDevice() == 0) {
        writer.print(batteryStatus.getChargeFraction());
        writer.write(",");
        writer.print(batteryStatus.getBatteryCurrentInstant());
        writer.write(",");
        writer.print(batteryStatus.getBatteryCurrentAvg());
        writer.write(",");
        writer.print(batteryStatus.getBatteryEnergy());
        writer.write(",");
        writer.print(batteryStatus.getBatteryTemp());
        writer.write(",");

        if (i < statuses.size() - 1) {
          BatteryStatus nextStatus = statuses.get(i + 1);
          // if they're the same time to within 1 minute, and the next one is a watch, then
          // add it to this row as well.
          if (nextStatus.getDate().getTime() / 60000 == batteryStatus.getDate().getTime() / 60000
              && nextStatus.getDevice() > 0) {
            writer.print(nextStatus.getChargeFraction());
          }
          i++;
        }
      } else {
        writer.write(",,,,,");
        writer.print(batteryStatus.getChargeFraction());
      }
      writer.println();
    }
    return statuses.size();
  }

  public static class Builder {
    private Date date;
    private int device;
    private float chargeFraction;
    private float batteryTemp;
    private float batteryCurrentInstant;
    private float batteryCurrentAvg;
    private float batteryEnergy;

    public Builder(int device) {
      this.device = device;
    }

    public Builder chargeFraction(float percent) {
      chargeFraction = percent;
      return this;
    }

    public Builder batteryTemp(float temp) {
      batteryTemp = temp;
      return this;
    }

    public Builder batteryCurrentInstant(float milliAmperes) {
      batteryCurrentInstant = milliAmperes;
      return this;
    }

    public Builder batteryCurrentAvg(float milliAmperes) {
      batteryCurrentAvg = milliAmperes;
      return this;
    }

    public Builder batteryEnergy(float milliWattHours) {
      batteryEnergy = milliWattHours;
      return this;
    }

    public Builder timestamp(long timestamp) {
      date = new Date(timestamp);
      return this;
    }

    public BatteryStatus build() {
      BatteryStatus status = new BatteryStatus();
      status.date = date == null ? new Date() : date;
      status.device = device;
      status.chargeFraction = chargeFraction;
      status.batteryCurrentInstant = batteryCurrentInstant;
      status.batteryCurrentAvg = batteryCurrentAvg;
      status.batteryEnergy = batteryEnergy;
      status.batteryTemp = batteryTemp;
      return status;
    }
  }

  public static HashMap<String, Integer> getBluetoothDeviceIds(Context context) {
    return new Store(context).loadBluetoothDevices();
  }

  public static HashMap<Integer, String> getBluetoothDeviceNames(Context context) {
    HashMap<String, Integer> nameToDevice = new Store(context).loadBluetoothDevices();
    HashMap<Integer, String> deviceIdToName = new HashMap<>();
    for (String name : nameToDevice.keySet()) {
      deviceIdToName.put(nameToDevice.get(name), name);
    }
    return deviceIdToName;
  }

  public static int addBluetoothDevice(Context context, String deviceName) {
    return new Store(context).addBluetoothDevice(deviceName);
  }

  private static class Store extends SQLiteOpenHelper {
    private static final Object LOCK = new Object();

    public Store(Context context) {
      super(context, "battery.db", null, 8);
    }

    /**
     * This is called the first time we open the database, in order to create the required
     * tables, etc.
     */
    @Override
    public void onCreate(SQLiteDatabase db) {
      Log.i(TAG, "onCreate");
      db.execSQL("CREATE TABLE battery_history ("
          + "  timestamp INTEGER,"
          + "  charge_percent REAL)");
      db.execSQL("CREATE INDEX IX_timestamp ON battery_history (timestamp)");

      // We call onUpgrade so we don't need to support two paths, a "create" and "upgrade",
      // essentially, everything is an upgrade.
      onUpgrade(db, 0, 8);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
      Log.i(TAG, "onUpgrade oldVersion=" + oldVersion + ", newVersion=" + newVersion);
      if (oldVersion <= 1) {
        db.execSQL("ALTER TABLE battery_history ADD COLUMN temperature REAL");
      }
      if (oldVersion <= 2) {
        db.execSQL("UPDATE battery_history SET temperature=25.0 WHERE temperature=0");
      }
      if (oldVersion <= 3) {
        db.execSQL("ALTER TABLE battery_history ADD COLUMN device INTEGER");
        db.execSQL("UPDATE battery_history SET device=0");
        db.execSQL("DROP INDEX IX_timestamp");
        db.execSQL("CREATE INDEX IX_device_timestamp ON battery_history (device, timestamp)");
      }
      if (oldVersion <= 4) {
        db.execSQL("UPDATE battery_history SET timestamp = timestamp / 1000 WHERE device > 0");
      }
      if (oldVersion <= 5) {
        db.execSQL("ALTER TABLE battery_history ADD COLUMN current_instant_milliamperes REAL");
        db.execSQL("ALTER TABLE battery_history ADD COLUMN current_avg_milliamperes REAL");
        db.execSQL("ALTER TABLE battery_history ADD COLUMN energy_milliwatthours REAL");
        db.execSQL("UPDATE battery_history SET current_instant_milliamperes=0,"
            + " current_avg_milliamperes=0, energy_milliwatthours = 0");
      }
      if (oldVersion <= 6) {
        db.execSQL("UPDATE battery_history SET"
            + " current_instant_milliamperes=current_instant_milliamperes / 1000,"
            + " current_avg_milliamperes=current_avg_milliamperes / 1000,"
            + " energy_milliwatthours = energy_milliwatthours / 1000");
      }
      if (oldVersion <= 7) {
        db.execSQL("CREATE TABLE bluetooth_devices ("
            + " name TEXT,"
            + " device_id INTEGER)");
      }
    }

    public HashMap<String, Integer> loadBluetoothDevices() {
      synchronized (LOCK) {
        Cursor cursor = null;
        try (SQLiteDatabase db = getReadableDatabase()) {
          cursor = db.query(
              "bluetooth_devices", new String[]{"name", "device_id"},
              null, null, null, null, null);

          HashMap<String, Integer> devices = new HashMap<>();
          while (cursor.moveToNext()) {
            devices.put(cursor.getString(0), cursor.getInt(1));
          }

          return devices;
        } catch (Exception e) {
          Log.e(TAG, "Exception", e);
          return new HashMap<>();
        } finally {
          if (cursor != null) cursor.close();
        }
      }
    }

    public int addBluetoothDevice(String deviceName) {
      synchronized (LOCK) {
        int nextId = 1;
        Cursor cursor = null;
        try (SQLiteDatabase db = getReadableDatabase()) {
          cursor = db.rawQuery("SELECT MAX(device_id) FROM bluetooth_devices", new String[0]);

          if (cursor.moveToNext()) {
            nextId = cursor.getInt(0) + 1;
          }
        } catch (Exception e) {
          Log.e(TAG, "Exception", e);
          return -1;
        } finally {
          if (cursor != null) cursor.close();
        }

        try (SQLiteDatabase db = getWritableDatabase()) {
          // insert a new cached value
          ContentValues values = new ContentValues();
          values.put("name", deviceName);
          values.put("device_id", nextId);
          db.insert("bluetooth_devices", null, values);
        } catch (Exception e) {
          // ignore errors... todo: log them
        }

        return nextId;
      }
    }

    public void save(BatteryStatus status) {
      synchronized (LOCK) {
        try (SQLiteDatabase db = getWritableDatabase()) {
          // insert a new cached value
          ContentValues values = new ContentValues();
          values.put("timestamp", status.date.getTime());
          values.put("device", status.device);
          values.put("charge_percent", status.chargeFraction);
          values.put("current_instant_milliamperes", status.batteryCurrentInstant);
          values.put("current_avg_milliamperes", status.batteryCurrentAvg);
          values.put("energy_milliwatthours", status.batteryEnergy);
          values.put("temperature", status.batteryTemp);
          db.insert("battery_history", null, values);
        } catch (Exception e) {
          // ignore errors... todo: log them
        }
      }
    }

    /**
     * Delete entries older than {@see dt}.
     */
    public void deleteOlderThan(long timestamp) {
      synchronized (LOCK) {
        try (SQLiteDatabase db = getWritableDatabase()) {
          db.delete("battery_history", "timestamp < " + timestamp, null);
        } catch (Exception e) {
          // ignore errors... todo: log them?
        }
      }
    }

    public List<BatteryStatus> getHistory(int device, long minTimestamp) {
      SQLiteDatabase db = getReadableDatabase();
      Cursor cursor = null;
      try {
        cursor = db.query("battery_history", new String[]{"timestamp", "charge_percent",
                "current_instant_milliamperes", "current_avg_milliamperes",
                "energy_milliwatthours", "temperature"},
            "device = " + device + " AND timestamp >= " + minTimestamp,
            null, null, null, "timestamp DESC");

        ArrayList<BatteryStatus> statuses = new ArrayList<>();
        while (cursor.moveToNext()) {
          statuses.add(new BatteryStatus.Builder(device)
              .timestamp(cursor.getLong(0))
              .chargeFraction(cursor.getFloat(1))
              .batteryCurrentInstant(cursor.getFloat(2))
              .batteryCurrentAvg(cursor.getFloat(3))
              .batteryEnergy(cursor.getFloat(4))
              .batteryTemp(cursor.getFloat(5))
              .build());
        }

        return statuses;
      } catch (Exception e) {
        Log.e(TAG, "Exception", e);
        return new ArrayList<>();
      } finally {
        if (cursor != null) cursor.close();
        db.close();
      }
    }

    public List<BatteryStatus> export() {
      SQLiteDatabase db = getReadableDatabase();
      Cursor cursor = null;
      try {
        cursor = db.query("battery_history", new String[]{"timestamp", "device",
                "charge_percent", "current_instant_milliamperes",
                "current_avg_milliamperes", "energy_milliwatthours", "temperature"},
            null, null, null, null, "timestamp DESC, device ASC");

        ArrayList<BatteryStatus> statuses = new ArrayList<>();
        while (cursor.moveToNext()) {
          statuses.add(new BatteryStatus.Builder(cursor.getInt(1))
              .timestamp(cursor.getLong(0))
              .chargeFraction(cursor.getFloat(2))
              .batteryCurrentInstant(cursor.getFloat(3))
              .batteryCurrentAvg(cursor.getFloat(4))
              .batteryEnergy(cursor.getFloat(5))
              .batteryTemp(cursor.getFloat(6))
              .build());
        }

        Log.i(TAG, "Successfully fetched " + statuses.size() + " events from data store.");
        return statuses;
      } catch (Exception e) {
        Log.e(TAG, "Exception", e);
        return new ArrayList<>();
      } finally {
        if (cursor != null) cursor.close();
        db.close();
      }
    }
  }
}
