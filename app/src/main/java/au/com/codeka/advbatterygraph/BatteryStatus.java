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

/** The class manages and maintains the historical battery state. */
public class BatteryStatus {
    private Date mDate;
    private int mDevice;
    private float mChargeFraction;
    private float mBatteryTemp;

    private BatteryStatus() {
    }

    public int getDevice() { return mDevice; }
    public float getChargeFraction() {
        return mChargeFraction;
    }
    public float getBatteryTemp() {
        return mBatteryTemp;
    }
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
     * @throws IOException
     */
    public static void export(Context context, File csvFile) throws IOException {
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'");

        PrintWriter writer = new PrintWriter(new FileOutputStream(csvFile));
        writer.println("Timestamp,PhoneBatteryFraction,PhoneBatteryPercent,"
                + "PhoneTemperatureCelsius,WatchBatteryFraction,WatchBatteryPercent");
        for (BatteryStatus batteryStatus : new Store(context).export()) {
            writer.write(df.format(batteryStatus.getDate()));
            writer.write(",");
            if (batteryStatus.getDevice() == 0) {
                writer.print(batteryStatus.getChargeFraction());
                writer.write(",");
                writer.print((int)(batteryStatus.getChargeFraction() * 100.0));
                writer.write(",");
                writer.print(batteryStatus.getBatteryTemp());
                writer.write(",,");
            } else {
                writer.write(",,,");
                writer.print(batteryStatus.getChargeFraction());
                writer.write(",");
                writer.print((int)(batteryStatus.getChargeFraction() * 100.0));
            }
            writer.println();
        }
    }

    public static class Builder {
        private Date mDate;
        private int mDevice;
        private float mChargeFraction;
        private float mBatteryTemp;

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

        public Builder timestamp(long timestamp) {
            mDate = new Date(timestamp);
            return this;
        }

        public BatteryStatus build() {
            BatteryStatus status = new BatteryStatus();
            status.mDate = mDate == null ? new Date() : mDate;
            status.mDevice = mDevice;
            status.mChargeFraction = mChargeFraction;
            status.mBatteryTemp = mBatteryTemp;
            return status;
        }
    }

    private static class Store extends SQLiteOpenHelper {
        private static Object sLock = new Object();

        public Store(Context context) {
            super(context, "battery.db", null, 5);
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
                cursor = db.query("battery_history", new String[] {"timestamp", "charge_percent", "temperature"},
                        "device = "+device+" AND timestamp >= "+minTimestamp,
                        null, null, null, "timestamp DESC");

                ArrayList<BatteryStatus> statuses = new ArrayList<BatteryStatus>();
                while (cursor.moveToNext()) {
                    statuses.add(new BatteryStatus.Builder(device)
                                    .timestamp(cursor.getLong(0))
                                    .chargeFraction(cursor.getFloat(1))
                                    .batteryTemp(cursor.getFloat(2))
                                    .build());
                }

                return statuses;
            } catch (Exception e) {
                // todo: log errors
                return null;
            } finally {
                if (cursor != null) cursor.close();
                db.close();
            }
        }

        public List<BatteryStatus> export() {
            SQLiteDatabase db = getReadableDatabase();
            Cursor cursor = null;
            try {
                cursor = db.query("battery_history", new String[] {"timestamp", "device", "charge_percent", "temperature"},
                        null, null, null, null, "timestamp DESC");

                ArrayList<BatteryStatus> statuses = new ArrayList<BatteryStatus>();
                while (cursor.moveToNext()) {
                    statuses.add(new BatteryStatus.Builder(cursor.getInt(1))
                            .timestamp(cursor.getLong(0))
                            .chargeFraction(cursor.getFloat(2))
                            .batteryTemp(cursor.getFloat(3))
                            .build());
                }

                return statuses;
            } catch (Exception e) {
                // todo: log errors
                return null;
            } finally {
                if (cursor != null) cursor.close();
                db.close();
            }
        }
    }
}
