package au.com.codeka.advbatterygraph;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

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
import android.util.Log;
import android.widget.RemoteViews;

/**
 * This is the widget provider which renders the actual widget.
 */
public class BatteryGraphWidgetProvider extends AppWidgetProvider {
    private RemoteViews mRemoteViews;
    private ComponentName mComponentName;

    private float mPixelDensity;
    private Settings mSettings;

    public static final String CUSTOM_REFRESH_ACTION = "au.com.codeka.advbatterygraph.UpdateAction";

    /**
     * You can call this to send a notification to the graph widget to update
     * itself.
     */
    public static void notifyRefresh(Context context) {
        Intent i = new Intent(context, BatteryGraphWidgetProvider.class);
        i.setAction(BatteryGraphWidgetProvider.CUSTOM_REFRESH_ACTION);
        context.sendBroadcast(i);

    }

    /**
     * Called when we receive a notification, either from the widget subsystem
     * directly, or from our customer refresh code.
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            mSettings = Settings.get(context);
            mPixelDensity = context.getResources().getDisplayMetrics().density;
            mRemoteViews = new RemoteViews(context.getPackageName(), R.layout.widget);
            mComponentName = new ComponentName(context, BatteryGraphWidgetProvider.class);

            if (intent.getAction().equals(CUSTOM_REFRESH_ACTION)) {
                refreshGraph(context);
            } else {
                super.onReceive(context, intent);
            }

            AppWidgetManager.getInstance(context).updateAppWidget(mComponentName, mRemoteViews); 
        } catch(Exception e) {
            Log.e("Battery Graph", "Unhandled exception!", e);
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
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager, int appWidgetId, Bundle newOptions) {
        mSettings.setGraphWidth(newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH));
        mSettings.setGraphHeight(newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT));
        mSettings.save(context);

        refreshGraph(context);
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

        intent = new Intent(context, SettingsActivity.class);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds);
        pendingIntent = PendingIntent.getActivity(context, 0 , intent, PendingIntent.FLAG_UPDATE_CURRENT);
        mRemoteViews.setOnClickPendingIntent(R.id.image, pendingIntent);

        refreshGraph(context);
    }

    private void refreshGraph(Context context) {
        List<BatteryStatus> history = BatteryStatus.getHistory(context, mSettings.getNumHours());
        int numMinutes = mSettings.getNumHours() * 60;
        Bitmap bmp = renderGraph(history, numMinutes);
        mRemoteViews.setImageViewBitmap(R.id.image, bmp);
    }

    private Bitmap renderGraph(List<BatteryStatus> history, int numMinutes) {
        int width = (int)(mSettings.getGraphWidth() * mPixelDensity);
        int height = (int)(mSettings.getGraphHeight() * mPixelDensity);
        if (width == 0 || height == 0) {
            return null;
        }

        Bitmap bmp = Bitmap.createBitmap(width, height, Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);

        int graphHeight = height;
        if (mSettings.showTimeScale()) {
            graphHeight -= 26;
        }

        int numGraphsShowing = 1;
        List<GraphPoint> tempPoints = null;
        List<GraphPoint> chargePoints = renderChargeGraph(history, numMinutes, width, graphHeight);

        if (mSettings.showTemperatureGraph()) {
            numGraphsShowing ++;
            tempPoints = renderTempGraph(history, numMinutes, width, graphHeight);
        }

        if (mSettings.showTimeScale()) {
            showTimeScale(canvas, width, height, numMinutes, numGraphsShowing, mSettings.showTimeLines());
        }

        if (tempPoints != null) {
            drawGraphBackground(tempPoints, canvas, width, graphHeight);
        }
        drawGraphBackground(chargePoints, canvas, width, graphHeight);

        if (tempPoints != null) {
            drawGraphLine(tempPoints, canvas, width, graphHeight);
        }
        drawGraphLine(chargePoints, canvas, width, graphHeight);

        String text = String.format("%d%%", (int) (history.get(0).getChargeFraction() * 100.0f));
        if (mSettings.showTemperatureGraph()) {
            text = String.format("%.1f° - ", history.get(0).getBatteryTemp()) + text;
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
        Rect r = new Rect(0, (int)(height - 20 * mPixelDensity), width, height);
        Paint bgPaint = new Paint();
        bgPaint.setARGB(128, 0, 0, 0);
        bgPaint.setStyle(Style.FILL);
        for (int i = 0; i < numGraphsShowing; i++) {
            canvas.drawRect(r, bgPaint);
        }

        Paint fgPaint = new Paint();
        fgPaint.setAntiAlias(true);
        fgPaint.setTextSize(22.0f);
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
                    canvas.drawLine(x, 0, x, height - 26.0f, fgPaint);
                }
            }
        }
    }

    private List<GraphPoint> renderChargeGraph(List<BatteryStatus> history, int numMinutes, int width, int height) {
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
        points.add(new GraphPoint(x, 2 + height - (height * status.getChargeFraction()),
                getColourForCharge(status.getChargeFraction())));
        for (int minute = 1, j = 1; minute < numMinutes; minute++) {
            x -= pixelsPerMinute;
            cal.add(Calendar.MINUTE, -1);
            Date dt = cal.getTime();

            while (j < history.size() - 1 && history.get(j).getDate().after(dt)) {
                j++;
            }
            status = history.get(j);
            points.add(new GraphPoint(x, 2 + height - (height * status.getChargeFraction()),
                    getColourForCharge(status.getChargeFraction())));
        }

        return points;
    }

    private int getColourForCharge(float charge) {
        if (charge < 0.14f) {
            return Color.RED;
        } else if (charge < 0.30f) {
            return Color.YELLOW;
        } else {
            return Color.GREEN;
        }
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
        Path path = new Path();
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setStyle(Style.STROKE);
        paint.setAlpha(255);
        paint.setStrokeWidth(4.0f);
        path = null;
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
