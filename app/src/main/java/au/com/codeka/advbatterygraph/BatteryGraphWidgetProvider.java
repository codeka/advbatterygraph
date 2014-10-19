package au.com.codeka.advbatterygraph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TreeMap;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.RemoteViews;

/**
 * This is the widget provider which renders the actual widget.
 */
public class BatteryGraphWidgetProvider extends AppWidgetProvider {
    private static final String TAG = "BatteryGraphWidgetProvider";
    private TreeMap<Integer, RemoteViews> mRemoteViews;
    private WatchConnection watchConnection = new WatchConnection();

    private float mPixelDensity;
    private Settings mSettings;

    public static final String CUSTOM_REFRESH_ACTION = "au.com.codeka.advbatterygraph.UpdateAction";

    /**
     * You can call this to send a notification to the graph widget to update
     * itself.
     */
    public static void notifyRefresh(Context context, @Nullable int[] appWidgetIds) {
        Intent i = new Intent(context, BatteryGraphWidgetProvider.class);
        i.setAction(BatteryGraphWidgetProvider.CUSTOM_REFRESH_ACTION);
        i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds);
        context.sendBroadcast(i);
    }

    /**
     * Called when we receive a notification, either from the widget subsystem
     * directly, or from our custom refresh code.
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            int[] appWidgetIds = null;
            Bundle extras = intent.getExtras();
            if (extras != null) {
                appWidgetIds = extras.getIntArray(AppWidgetManager.EXTRA_APPWIDGET_IDS);
            }
            if (appWidgetIds == null) {
                appWidgetIds = AppWidgetManager.getInstance(context).getAppWidgetIds(
                        new ComponentName(context, BatteryGraphWidgetProvider.class));
            }

            mSettings = Settings.get(context);
            if (mRemoteViews == null) {
                mPixelDensity = context.getResources().getDisplayMetrics().density;
                mRemoteViews = new TreeMap<Integer, RemoteViews>();
                for (int i = 0; i < appWidgetIds.length; i++) {
                    mRemoteViews.put(appWidgetIds[i], new RemoteViews(context.getPackageName(),
                            R.layout.widget));
                }
            }

            if (mSettings.monitorWatch()) {
                watchConnection.setup(context, null);
                watchConnection.start();
                watchConnection.sendMessage(
                        new WatchConnection.Message("/advbatterygraph/Start", null));
            }

            if (intent.getAction().equals(CUSTOM_REFRESH_ACTION)) {
                refreshGraph(context, appWidgetIds);
            } else {
                super.onReceive(context, intent);
            }

            if (appWidgetIds != null) {
                for (int i = 0; i < appWidgetIds.length; i++) {
                    AppWidgetManager.getInstance(context).updateAppWidget(
                            appWidgetIds[i], mRemoteViews.get(appWidgetIds[i]));
                }
            }
        } catch(Exception e) {
            Log.e(TAG, "Unhandled exception!", e);
        }
    }

    /**
     * This is called when the "options" of the widget change. In particular, we're
     * interested in when the dimensions change.
     *
     * Unfortunately, the "width" and "height" we receive can, in reality, be
     * incredibly inaccurate, we need to provide a setting that the user can
     * override :\
     */
    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager,
                                          int appWidgetId, Bundle newOptions) {
        Settings.GraphSettings gs = mSettings.getGraphSettings(appWidgetId);
        if (gs.autoGraphSize()) {
            gs.setGraphWidth(newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH));
            gs.setGraphHeight(newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT));
            gs.save(context);
        }

        refreshGraph(context, new int[] { appWidgetId });
    }

    /**
     * This is called when the widget is updated (usually when it starts up, but
     * also gets called ~every 30 minutes.
     */
    @Override
    public void onUpdate(Context context, AppWidgetManager mgr, int[] appWidgetIds) {
        super.onUpdate(context, mgr, appWidgetIds);

        // make sure the alarm is running
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, BatteryGraphAlarmReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0);
        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), 1000 * 60, pendingIntent);

        refreshGraph(context, appWidgetIds);
    }

    private void refreshGraph(Context context, int[] appWidgetIds) {
        for (int i = 0; i < appWidgetIds.length; i++) {
            int appWidgetId = appWidgetIds[i];

            Intent intent = new Intent(context, SettingsActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            PendingIntent pendingIntent = PendingIntent.getActivity(context, appWidgetId, intent,
                    PendingIntent.FLAG_CANCEL_CURRENT);
            mRemoteViews.get(appWidgetId).setOnClickPendingIntent(R.id.image, pendingIntent);

            Settings.GraphSettings graphSettings = mSettings.getGraphSettings(appWidgetId);
            int numMinutes = graphSettings.getNumHours() * 60;
            Bitmap bmp = renderGraph(context, graphSettings, numMinutes);
            mRemoteViews.get(appWidgetId).setImageViewBitmap(R.id.image, bmp);
        }
    }

    private Bitmap renderGraph(Context context, Settings.GraphSettings graphSettings,
                               int numMinutes) {
        final int width = (int)(graphSettings.getGraphWidth() * mPixelDensity);
        final int height = (int)(graphSettings.getGraphHeight() * mPixelDensity);
        if (width == 0 || height == 0) {
            return null;
        }

        Bitmap bmp = Bitmap.createBitmap(width, height, Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);

        int graphHeight = height;
        if (graphSettings.showTimeScale()) {
            graphHeight -= 10 * mPixelDensity;
        }

        List<BatteryStatus> batteryHistory = BatteryStatus.getHistory(
                context, 0, graphSettings.getNumHours());
        List<BatteryStatus> watchHistory;
        if (mSettings.monitorWatch()) {
            watchHistory = BatteryStatus.getHistory(context, 1, graphSettings.getNumHours());
        } else {
            watchHistory = new ArrayList<BatteryStatus>();
        }

        int numGraphsShowing = 0;
        List<GraphPoint> tempPoints = null;
        List<GraphPoint> batteryChargePoints = null;
        List<GraphPoint> batteryCurrentInstantPoints = null;
        List<GraphPoint> batteryCurrentAvgPoints = null;
        List<GraphPoint> batteryEnergyPoints = null;
        List<GraphPoint> watchChargePoints = renderChargeGraph(watchHistory, numMinutes, width,
                graphHeight, Color.argb(200, 0x00, 0xba, 0xff));

        if (graphSettings.showBatteryGraph()) {
            batteryChargePoints = renderChargeGraph(batteryHistory, numMinutes, width,
                    graphHeight, Color.GREEN);
            numGraphsShowing ++;
        }
        if (graphSettings.showTemperatureGraph()) {
            numGraphsShowing ++;
            tempPoints = renderTempGraph(batteryHistory, numMinutes, width, graphHeight);
        }
        if (graphSettings.showBatteryCurrentInstant()) {
            batteryCurrentInstantPoints = renderGraph(batteryHistory, numMinutes,  width, height,
                    new GraphCustomizer() {
                        @Override
                        public float getValue(BatteryStatus status) {
                            return status.getBatteryCurrentInstant();
                        }

                        @Override
                        public int getColor(float value, int height) {
                            return value >= 0.0f ? Color.GREEN : Color.RED;
                        }
                    }, height / 2, 0);
            numGraphsShowing ++;
        }
        if (graphSettings.showBatteryCurrentAvg()) {
            batteryCurrentAvgPoints = renderGraph(batteryHistory, numMinutes,  width, height,
                    new GraphCustomizer() {
                        @Override
                        public float getValue(BatteryStatus status) {
                            return status.getBatteryCurrentAvg();
                        }

                        @Override
                        public int getColor(float value, int height) {
                            return value >= 0.0f ? Color.GREEN : Color.RED;
                        }
                    }, height / 2, 0);
            numGraphsShowing ++;
        }
        if (graphSettings.showBatteryCurrentInstant()) {
            batteryEnergyPoints = renderGraph(batteryHistory, numMinutes,  width, height,
                    new GraphCustomizer() {
                        @Override
                        public float getValue(BatteryStatus status) {
                            return status.getBatteryEnergy();
                        }

                        @Override
                        public int getColor(float value, int thisHeight) {
                            return getColourForCharge((float) thisHeight / height, Color.GREEN);
                        }
                    }, 0, 0);
            numGraphsShowing ++;
        }
        if (watchChargePoints.size() > 0) {
            numGraphsShowing++;
        }
        if (graphSettings.showTimeScale()) {
            showTimeScale(canvas, width, height, numMinutes, numGraphsShowing,
                    graphSettings.showTimeLines());
        }

        if (tempPoints != null) {
            drawGraphBackground(tempPoints, canvas, width, graphHeight);
        }
        if (batteryCurrentInstantPoints != null) {
            drawGraphBackground(batteryCurrentInstantPoints, canvas, width, graphHeight);
        }
        if (batteryCurrentAvgPoints != null) {
            drawGraphBackground(batteryCurrentAvgPoints, canvas, width, graphHeight);
        }
        if (batteryEnergyPoints != null) {
            drawGraphBackground(batteryEnergyPoints, canvas, width, graphHeight);
        }
        if (watchChargePoints.size() > 0) {
            drawGraphBackground(watchChargePoints, canvas, width, graphHeight);
        }
        if (batteryChargePoints != null) {
            drawGraphBackground(batteryChargePoints, canvas, width, graphHeight);
        }

        if (tempPoints != null) {
            drawGraphLine(tempPoints, canvas, width, graphHeight);
        }
        if (batteryCurrentInstantPoints != null) {
            drawGraphLine(batteryCurrentInstantPoints, canvas, width, graphHeight);
        }
        if (batteryCurrentAvgPoints != null) {
            drawGraphLine(batteryCurrentAvgPoints, canvas, width, graphHeight);
        }
        if (batteryEnergyPoints != null) {
            drawGraphLine(batteryEnergyPoints, canvas, width, graphHeight);
        }
        if (watchChargePoints.size() > 0) {
            drawGraphLine(watchChargePoints, canvas, width, graphHeight);
        }
        if (batteryChargePoints != null) {
            drawGraphLine(batteryChargePoints, canvas, width, graphHeight);
        }

        String text = "";
        if (graphSettings.showBatteryGraph() && batteryHistory.size() > 0) {
            text = String.format("%d%%", (int) (batteryHistory.get(0).getChargeFraction() * 100.0f));
        }
        if (watchHistory.size() > 0) {
            if (text != "" && batteryChargePoints != null) {
                text = String.format("P:%s W:%d%%", text, (int) (watchHistory.get(0).getChargeFraction() * 100.0f));
            } else {
                text = String.format("%d%%", (int) (watchHistory.get(0).getChargeFraction() * 100.0f));
            }
        }

        if (graphSettings.showTemperatureGraph() && batteryHistory.size() > 0) {
            float celsius = batteryHistory.get(0).getBatteryTemp();
            String temp;
            if (graphSettings.tempInCelsius()) {
                temp = String.format("%.1f°", celsius);
            } else {
                temp = String.format("%.1f°", celsius * 9.0f / 5.0f + 32.0f) ;
            }
            if (text != "") {
                text = temp + " - " + text;
            } else {
                text = temp;
            }
        }

        if (graphSettings.showBatteryCurrentInstant() && batteryHistory.size() > 0) {
            String curr = String.format("%d mA", (int) batteryHistory.get(0).getBatteryCurrentInstant());
            if (text != "") {
                text += " - ";
            }
            text += curr;
        }

        if (graphSettings.showBatteryCurrentAvg() && batteryHistory.size() > 0) {
            String curr = String.format("%d mA", (int) batteryHistory.get(0).getBatteryCurrentAvg());
            if (text != "") {
                text += " - ";
            }
            text += curr;
        }

        if (graphSettings.showBatteryEnergy() && batteryHistory.size() > 0) {
            String curr = String.format("%d mWh", (int) batteryHistory.get(0).getBatteryEnergy());
            if (text != "") {
                text += " - ";
            }
            text += curr;
        }

        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setTextSize(20.0f * mPixelDensity);
        paint.setColor(Color.WHITE);
        paint.setStyle(Style.FILL);
        paint.setStrokeWidth(mPixelDensity);
        float textWidth = paint.measureText(text);
        canvas.drawText(text, width - textWidth - 4, graphHeight - 4, paint);

        return bmp;
    }

    private void showTimeScale(Canvas canvas, int width, int height, int numMinutes,
            int numGraphsShowing, boolean drawLines) {
        Rect r = new Rect(0, height - (int)(10 * mPixelDensity), width, height);
        Paint bgPaint = new Paint();
        bgPaint.setARGB(128, 0, 0, 0);
        bgPaint.setStyle(Style.FILL);
        for (int i = 0; i < numGraphsShowing; i++) {
            canvas.drawRect(r, bgPaint);
        }

        Paint fgPaint = new Paint();
        fgPaint.setAntiAlias(true);
        fgPaint.setTextSize(10 * mPixelDensity);
        fgPaint.setColor(Color.WHITE);
        fgPaint.setStyle(Style.FILL);
        fgPaint.setStrokeWidth(mPixelDensity);

        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());

        float x = width;
        float pixelsPerMinute = (float) width / numMinutes;

        int numDividers = 1;
        while ((numDividers * 60) * pixelsPerMinute < 50.0f) {
            if (numDividers == 1) {
                numDividers = 3;
            } else {
                numDividers += 3;
            }
        }

        for (int minute = 1; minute < numMinutes; minute++) {
            x -= pixelsPerMinute;
            cal.add(Calendar.MINUTE, -1);
            if (cal.get(Calendar.MINUTE) == 0 &&
                cal.get(Calendar.HOUR_OF_DAY) % numDividers == 0) {

                int hour = cal.get(Calendar.HOUR_OF_DAY);
                String ampm = "a";
                if (hour > 12) {
                    hour -= 12;
                    ampm = "p";
                } else if (hour == 12) {
                    ampm = "p";
                } else if (hour == 0) {
                    hour = 12;
                }
                String text = String.format("%d%s", hour, ampm);
                float textWidth = fgPaint.measureText(text);
                canvas.drawText(text, x - (textWidth / 2), height - 4, fgPaint);
                if (drawLines) {
                    canvas.drawLine(x, 0, x, height - (10.0f * mPixelDensity), fgPaint);
                }
            }
        }
    }

    private List<GraphPoint> renderChargeGraph(List<BatteryStatus> history, int numMinutes,
                                               int width, int height, int baseColour) {
        height -= 4;
        ArrayList<GraphPoint> points = new ArrayList<GraphPoint>();
        if (history.size() < 2) {
            points.add(new GraphPoint(width, height, Color.GREEN));
            points.add(new GraphPoint(0, height, Color.GREEN));
            return points;
        }

        float x = width;
        float pixelsPerMinute = (float) width / numMinutes;

        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());

        BatteryStatus status = history.get(0);
        float y = 2 + height - (height * status.getChargeFraction());
        if (y < 0 ) {
            y = 0;
        }
        if (y >= height) {
            y = height - 1;
        }
        points.add(new GraphPoint(x, y, getColourForCharge(status.getChargeFraction(), baseColour)));
        for (int minute = 1, j = 1; minute < numMinutes; minute++) {
            x -= pixelsPerMinute;
            cal.add(Calendar.MINUTE, -1);
            Date dt = cal.getTime();

            while (j < history.size() - 1 && history.get(j).getDate().after(dt)) {
                j++;
            }
            status = history.get(j);
            y = 2 + height - (height * status.getChargeFraction());
            if (y > 0 && y < height) {
              points.add(new GraphPoint(x, y, getColourForCharge(status.getChargeFraction(), baseColour)));
            }
        }

        return points;
    }

    private List<GraphPoint> renderGraph(List<BatteryStatus> history, int numMinutes,
                                         int width, int height, GraphCustomizer customizer,
                                         int zeroPoint, float maxValue) {
        height -= 4;
        ArrayList<GraphPoint> points = new ArrayList<GraphPoint>();
        if (history.size() < 2) {
            points.add(new GraphPoint(width, zeroPoint, customizer.getColor(0.0f, zeroPoint)));
            points.add(new GraphPoint(0, zeroPoint, customizer.getColor(0.0f, zeroPoint)));
            return points;
        }

        if (maxValue == 0) {
            for (BatteryStatus status : history) {
                float currValue = Math.abs(customizer.getValue(status));
                if (currValue > maxValue) {
                    maxValue = currValue;
                }
            }
        }

        float x = width;
        float pixelsPerMinute = (float) width / numMinutes;

        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());

        BatteryStatus status = history.get(0);
        float value = customizer.getValue(status);
        float y = getFractionHeight(value, maxValue, height);
        points.add(new GraphPoint(x, y, customizer.getColor(value, (int) y)));
        for (int minute = 1, j = 1; minute < numMinutes; minute++) {
            x -= pixelsPerMinute;
            cal.add(Calendar.MINUTE, -1);
            Date dt = cal.getTime();

            while (j < history.size() - 1 && history.get(j).getDate().after(dt)) {
                j++;
            }
            status = history.get(j);
            value = customizer.getValue(status);
            y = getFractionHeight(value, maxValue, height);
            points.add(new GraphPoint(x, y, customizer.getColor(value, (int) y)));
        }

        return points;
    }

    private float getFractionHeight(float value, float maxValue, float height) {
        float fractionHeight = (value / maxValue) * (height / 2.0f) + (height / 2.0f);
        if (fractionHeight < 0) {
            return 0;
        }
        if (fractionHeight >= height) {
            return height;
        }
        return fractionHeight;
    }

    private int getColourForCharge(float charge, int baseColour) {
        int colour = baseColour;
        if (charge < 0.14f) {
            colour = Color.RED;
        } else if (charge < 0.30f) {
            colour = Color.YELLOW;
        }

        // replace the alpha component in the colour with baseColour's alpha
        return Color.argb(Color.alpha(baseColour), Color.red(colour), Color.green(colour),
                Color.blue(colour));
    }

    private List<GraphPoint> renderTempGraph(List<BatteryStatus> history, int numMinutes, int width, int height) {
        height -= 4;
        ArrayList<GraphPoint> points = new ArrayList<GraphPoint>();
        if (history.size() < 2) {
            points.add(new GraphPoint(width, height, Color.BLUE));
            points.add(new GraphPoint(0, height, Color.BLUE));
            return points;
        }

        float maxTemp = 0.0f;
        float minTemp = 1000.0f;
        float avgTemp = 0.0f;
        for (BatteryStatus status : history) {
            if (maxTemp < status.getBatteryTemp()) {
                maxTemp = status.getBatteryTemp();
            }
            if (minTemp > status.getBatteryTemp()) {
                minTemp = status.getBatteryTemp();
            }
            avgTemp += status.getBatteryTemp();
        }
        avgTemp /= history.size();


        // if the range is small (< 8 degrees) we'll expand it a bit
        if (maxTemp - minTemp < 8.0f) {
            minTemp = avgTemp - 4.0f;
            maxTemp = avgTemp + 4.0f;
        }

        float x = width;
        float pixelsPerMinute = (float) width / numMinutes;

        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());

        BatteryStatus status = history.get(0);
        points.add(new GraphPoint(x, 2 + height - (height * getTempFraction(status.getBatteryTemp(), minTemp, maxTemp)), Color.BLUE));
        for (int minute = 1, j = 1; minute < numMinutes; minute++) {
            x -= pixelsPerMinute;
            cal.add(Calendar.MINUTE, -1);
            Date dt = cal.getTime();

            while (j < history.size() - 1 && history.get(j).getDate().after(dt)) {
                j++;
            }
            status = history.get(j);
            points.add(new GraphPoint(x, 2 + height - (height * getTempFraction(status.getBatteryTemp(), minTemp, maxTemp)), Color.BLUE));
        }

        return points;
    }

    private static float getTempFraction(float temp, float min, float max) {
        float range = max - min;
        if (range < 0.01) {
            return 0.0f;
        }

        return (temp - min) / range;
    }

    private void drawGraphBackground(List<GraphPoint> points, Canvas canvas, int width, int height) {
        Path path = new Path();
        path.moveTo(width, height);
        for (GraphPoint pt : points) {
            path.lineTo(pt.x, pt.y);
        }
        path.lineTo(0, height);
        path.lineTo(width, height);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setARGB(128, 0, 0, 0);
        paint.setStyle(Style.FILL);
        canvas.drawPath(path, paint);
    }

    private void drawGraphLine(List<GraphPoint> points, Canvas canvas, int width, int height) {
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setStyle(Style.STROKE);
        paint.setAlpha(255);
        paint.setStrokeWidth(4.0f);
        Path path = null;
        int colour = Color.BLACK;
        for (GraphPoint pt : points) {
            if (pt.colour != colour || path == null) {
                if (path != null) {
                    path.lineTo(pt.x, pt.y);
                    paint.setColor(colour);
                    canvas.drawPath(path, paint);
                    colour = pt.colour;
                }
                path = new Path();
                path.moveTo(pt.x, pt.y);
            } else {
                path.lineTo(pt.x, pt.y);
            }
        }
        paint.setColor(colour);
        canvas.drawPath(path, paint);
    }

    private interface GraphCustomizer {
        float getValue(BatteryStatus status);
        int getColor(float value, int height);
    }

    private static class GraphPoint {
        public float x;
        public float y;
        public int colour;

        public GraphPoint(float x, float y, int colour) {
            this.x = x;
            this.y = y;
            this.colour = colour;
        }
    }
}
