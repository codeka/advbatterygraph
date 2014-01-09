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
import android.graphics.PointF;
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

        List<PointF> points = renderGraph(history, numMinutes, width, height);

        // draw the background first
        Path path = new Path();
        path.moveTo(width, height);
        for (PointF pt : points) {
            path.lineTo(pt.x, pt.y);
        }
        path.lineTo(0, height);
        path.lineTo(width, height);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setARGB(128, 0, 0, 0);
        paint.setStyle(Style.FILL);
        canvas.drawPath(path, paint);

        // then draw the line
        paint.setStyle(Style.STROKE);
        paint.setAlpha(255);
        paint.setStrokeWidth(4.0f);
        path = null;
        int colour = Color.BLACK;
        for (PointF pt : points) {
            float pct = 1.0f - pt.y / height;
            int thisColour;
            if (pct < 0.14f) {
                thisColour = Color.RED;
            } else if (pct < 0.30f) {
                thisColour = Color.YELLOW;
            } else {
                thisColour = Color.GREEN;
            }
            if (thisColour != colour || path == null) {
                if (path != null) {
                    path.lineTo(pt.x, pt.y);
                    paint.setColor(colour);
                    canvas.drawPath(path, paint);
                    colour = thisColour;
                }
                path = new Path();
                path.moveTo(pt.x, pt.y);
            } else {
                path.lineTo(pt.x, pt.y);
            }
        }
        paint.setColor(colour);
        canvas.drawPath(path, paint);

        String text = String.format("%d%%", (int) (history.get(0).getChargeFraction() * 100.0f));
        paint.setTextSize(30.0f);
        paint.setColor(Color.WHITE);
        paint.setStyle(Style.FILL);
        paint.setStrokeWidth(mPixelDensity);
        float textWidth = paint.measureText(text);
        canvas.drawText(text, width - textWidth - 4, height - 4, paint);

        return bmp;
    }

    List<PointF> renderGraph(List<BatteryStatus> history, int numMinutes, int width, int height) {
        height -= 4;
        ArrayList<PointF> points = new ArrayList<PointF>();
        if (history.size() == 0) {
            points.add(new PointF(width, height));
            points.add(new PointF(0, height));
            return points;
        }

        float x = width;
        float pixelsPerMinute = (float) width / numMinutes;

        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());

        BatteryStatus status = history.get(0);
        points.add(new PointF(x, 2 + height - (height * status.getChargeFraction())));
        for (int minute = 1, j = 1; minute < numMinutes; minute++) {
            x -= pixelsPerMinute;
            cal.add(Calendar.MINUTE, -1);
            Date dt = cal.getTime();

            while (j < history.size() - 1 && history.get(j).getDate().after(dt)) {
                j++;
            }
            status = history.get(j);
            points.add(new PointF(x, 2 + height - (height * status.getChargeFraction())));
        }

        return points;
    }
}
