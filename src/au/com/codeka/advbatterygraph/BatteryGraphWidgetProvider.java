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
import android.os.Bundle;
import android.util.Log;
import android.widget.RemoteViews;

public class BatteryGraphWidgetProvider extends AppWidgetProvider {
    private RemoteViews mRemoteViews;
    private ComponentName mComponentName;

    private float mPixelDensity;
    private Settings mSettings;

    public static final String CUSTOM_REFRESH_ACTION = "au.com.codeka.advbatterygraph.UpdateAction";

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            mSettings = Settings.get(context);
            mPixelDensity = context.getResources().getDisplayMetrics().density;
            mRemoteViews = new RemoteViews(context.getPackageName(), R.layout.widget);
            mComponentName = new ComponentName(context, BatteryGraphWidgetProvider.class);

            if (intent.getAction().equals(CUSTOM_REFRESH_ACTION)) {
                refreshGraph(context, BatteryStatus.getHistory(context, 48), 48 * 60);
            } else {
                super.onReceive(context, intent);
            }

            AppWidgetManager.getInstance(context).updateAppWidget(mComponentName, mRemoteViews); 
        } catch(Exception e) {
            Log.e("Battery Graph", "Unhandled exception!", e);
        }
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager, int appWidgetId, Bundle newOptions) {
        mSettings.setGraphWidth(newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH));
        mSettings.setGraphHeight(newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT));
        mSettings.save(context);

        refreshGraph(context, BatteryStatus.getHistory(context, 48), 48 * 60);
    }

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

        refreshGraph(context, BatteryStatus.getHistory(context, 48), 48 * 60);
    }

    private void refreshGraph(Context context, List<BatteryStatus> history, int numMinutes) {
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

        List<GraphPoint> tempPoints = null;
        List<GraphPoint> chargePoints = renderChargeGraph(history, numMinutes, width, height);

        if (mSettings.showTemperatureGraph()) {
            tempPoints = renderTempGraph(history, numMinutes, width, height);
        }

        if (tempPoints != null) {
            drawGraphBackground(tempPoints, canvas, width, height);
        }
        drawGraphBackground(chargePoints, canvas, width, height);

        if (tempPoints != null) {
            drawGraphLine(tempPoints, canvas, width, height);
        }
        drawGraphLine(chargePoints, canvas, width, height);

        String text = String.format("%d%%", (int) (history.get(0).getChargeFraction() * 100.0f));
        if (mSettings.showTemperatureGraph()) {
            text = String.format("%.1f° - ", history.get(0).getBatteryTemp()) + text;
        }
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setTextSize(30.0f);
        paint.setColor(Color.WHITE);
        paint.setStyle(Style.FILL);
        paint.setStrokeWidth(mPixelDensity);
        float textWidth = paint.measureText(text);
        canvas.drawText(text, width - textWidth - 4, height - 4, paint);

        return bmp;
    }

    List<GraphPoint> renderChargeGraph(List<BatteryStatus> history, int numMinutes, int width, int height) {
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

    List<GraphPoint> renderTempGraph(List<BatteryStatus> history, int numMinutes, int width, int height) {
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

    void drawGraphBackground(List<GraphPoint> points, Canvas canvas, int width, int height) {
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

    void drawGraphLine(List<GraphPoint> points, Canvas canvas, int width, int height) {
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
