package au.com.codeka.advbatterygraph;

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
    private float mChargeFraction;
    private float mBatteryTemp;

    private BatteryStatus() {
    }

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
    public static List<BatteryStatus> getHistory(Context context, int numHours) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        cal.add(Calendar.HOUR_OF_DAY, -numHours);
        Date dt = cal.getTime();

        return new Store(context).getHistory(dt.getTime());
    }

    public static class Builder {
        private Date mDate;
        private float mChargeFraction;
        private float mBatteryTemp;

        public Builder() {
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
            status.mChargeFraction = mChargeFraction;
            status.mBatteryTemp = mBatteryTemp;
            return status;
        }
    }

    private static class Store extends SQLiteOpenHelper {
        private static Object sLock = new Object();

        public Store(Context context) {
            super(context, "battery.db", null, 3);
        }

        /**
         * This is called the first time we open the database, in order to create the required
         * tables, etc.
         */
        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE battery_history ("
                      +"  timestamp INTEGER,"
                      +"  charge_percent REAL,"
                      +"  temperature REAL)");
            db.execSQL("CREATE INDEX IX_timestamp ON battery_history (timestamp)");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion <= 1) {
                db.execSQL("ALTER TABLE battery_history ADD COLUMN temperature REAL");
            }
            if (oldVersion <= 2) {
                db.execSQL("UPDATE battery_history SET temperature=25.0 WHERE temperature=0");
            }
        }

        public void save(BatteryStatus status) {
            synchronized(sLock) {
                SQLiteDatabase db = getWritableDatabase();
                try {
                    // insert a new cached value
                    ContentValues values = new ContentValues();
                    values.put("timestamp", status.mDate.getTime());
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

        public List<BatteryStatus> getHistory(long minTimestamp) {
            SQLiteDatabase db = getReadableDatabase();
            Cursor cursor = null;
            try {
                cursor = db.query("battery_history", new String[] {"timestamp", "charge_percent", "temperature"},
                        "timestamp >= "+minTimestamp,
                        null, null, null, "timestamp DESC");

                ArrayList<BatteryStatus> statuses = new ArrayList<BatteryStatus>();
                while (cursor.moveToNext()) {
                    statuses.add(new BatteryStatus.Builder()
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
    }
}
