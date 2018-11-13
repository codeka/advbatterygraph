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
import java.util.List;
import java.util.Random;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

/** The class manages and maintains the historical battery state. */
public class BatteryStatus {
    private static final String TAG = "BatteryStatus";

    private Date mDate;
    private int mDevice;
    private float mChargeFraction;
    private float mBatteryTemp;
    private float mBatteryCurrentInstant;
    private float mBatteryCurrentAvg;
    private float mBatteryEnergy;

    private BatteryStatus() {
    }

    public int getDevice() { return mDevice; }
    public float getChargeFraction() {
        return mChargeFraction;
    }
    public float getBatteryTemp() {
        return mBatteryTemp;
    }
    public float getBatteryCurrentInstant() { return mBatteryCurrentInstant; }
    public float getBatteryCurrentAvg() { return mBatteryCurrentAvg; }
    public float getBatteryEnergy() { return mBatteryEnergy; }
    public Date getDate() {
        return mDate;
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
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm");

        PrintWriter writer = new PrintWriter(new FileOutputStream(csvFile));
        writer.println("Timestamp,PhoneBatteryFraction,PhoneBatteryCurrentInstantMilliAmperes,PhoneBatteryCurrentAvgMilliAmperes,PhoneEnergyMilliWattHours,PhoneTemperatureCelsius,WatchBatteryFraction");
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
                    BatteryStatus nextStatus = statuses.get(i+1);
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
        private Date mDate;
        private int mDevice;
        private float mChargeFraction;
        private float mBatteryTemp;
        private float mBatteryCurrentInstant;
        private float mBatteryCurrentAvg;
        private float mBatteryEnergy;

        public Builder(int device) {
            mDevice = device;
        }

        public Builder chargeFraction(float percent) {
            mChargeFraction = percent;
            return this;
        }

        public Builder batteryTemp(float temp) {
            mBatteryTemp = temp;
            return this;
        }

        public Builder batteryCurrentInstant(float milliAmperes) {
            mBatteryCurrentInstant = milliAmperes;
            return this;
        }

        public Builder batteryCurrentAvg(float milliAmperes) {
            mBatteryCurrentAvg = milliAmperes;
            return this;
        }

        public Builder batteryEnergy(float milliWattHours) {
            mBatteryEnergy = milliWattHours;
            return this;
        }

        public Builder timestamp(long timestamp) {
            mDate = new Date(timestamp);
            return this;
        }

        public BatteryStatus build() {
            BatteryStatus status = new BatteryStatus();
            status.mDate = mDate == null ? new Date() : mDate;
            status.mDevice = mDevice;
            status.mChargeFraction = mChargeFraction;
            status.mBatteryCurrentInstant = mBatteryCurrentInstant;
            status.mBatteryCurrentAvg = mBatteryCurrentAvg;
            status.mBatteryEnergy = mBatteryEnergy;
            status.mBatteryTemp = mBatteryTemp;
            return status;
        }
    }

    private static class Store extends SQLiteOpenHelper {
        private static Object sLock = new Object();

        public Store(Context context) {
            super(context, "battery.db", null, 7);
        }

        /**
         * This is called the first time we open the database, in order to create the required
         * tables, etc.
         */
        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE battery_history ("
                      +"  timestamp INTEGER,"
                      +"  device INTEGER,"
                      +"  charge_percent REAL,"
                      +"  current_instant_milliamperes REAL,"
                      +"  current_avg_milliamperes REAL,"
                      +"  energy_milliwatthours REAL,"
                      +"  temperature REAL)");
            db.execSQL("CREATE INDEX IX_device_timestamp ON battery_history (device, timestamp)");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion <= 1) {
                db.execSQL("ALTER TABLE battery_history ADD COLUMN temperature REAL");
            }
            if (oldVersion <= 2) {
                db.execSQL("UPDATE battery_history SET temperature=25.0 WHERE temperature=0");
            }
            if (oldVersion <= 3) {
                db.execSQL("ALTER TABLE battery_history ADD COLUMN device INT");
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
                        +" current_instant_milliamperes=current_instant_milliamperes / 1000,"
                        +" current_avg_milliamperes=current_avg_milliamperes / 1000,"
                        +" energy_milliwatthours = energy_milliwatthours / 1000");
            }
        }

        public void save(BatteryStatus status) {
            synchronized(sLock) {
                SQLiteDatabase db = getWritableDatabase();
                try {
                    // insert a new cached value
                    ContentValues values = new ContentValues();
                    values.put("timestamp", status.mDate.getTime());
                    values.put("device", status.mDevice);
                    values.put("charge_percent", status.mChargeFraction);
                    values.put("current_instant_milliamperes", status.mBatteryCurrentInstant);
                    values.put("current_avg_milliamperes", status.mBatteryCurrentAvg);
                    values.put("energy_milliwatthours", status.mBatteryEnergy);
                    values.put("temperature", status.mBatteryTemp);
                    db.insert("battery_history", null, values);
                } catch(Exception e) {
                    // ignore errors... todo: log them
                } finally {
                    db.close();
                }
            }
        }

        /**
         * Delete entries older than {@see dt}.
         */
        public void deleteOlderThan(long timestamp) {
            synchronized(sLock) {
                SQLiteDatabase db = getWritableDatabase();
                try {
                    db.delete("battery_history", "timestamp < "+timestamp, null);
                } catch(Exception e) {
                    // ignore errors... todo: log them?
                } finally {
                    db.close();
                }
            }
        }

        public List<BatteryStatus> getHistory(int device, long minTimestamp) {
            SQLiteDatabase db = getReadableDatabase();
            Cursor cursor = null;
            try {
                cursor = db.query("battery_history", new String[] {"timestamp", "charge_percent",
                                "current_instant_milliamperes", "current_avg_milliamperes",
                                "energy_milliwatthours", "temperature"},
                        "device = "+device+" AND timestamp >= "+minTimestamp,
                        null, null, null, "timestamp DESC");

                ArrayList<BatteryStatus> statuses = new ArrayList<BatteryStatus>();
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
                cursor = db.query("battery_history", new String[] {"timestamp", "device",
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
