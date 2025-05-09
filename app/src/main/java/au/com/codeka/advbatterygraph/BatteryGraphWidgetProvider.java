package au.com.codeka.advbatterygraph;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import android.util.Log;
import android.widget.RemoteViews;

/**
 * This is the widget provider which renders the actual widget.
 */
public class BatteryGraphWidgetProvider extends AppWidgetProvider {
  private static final String TAG = "BatteryGraphWidget";

  // If there are gaps in the graph that are bigger than this, don't render a line, just draw the
  // gap.
  private static final float MAX_GAP_PX = 50f;

  // The status message on the bottom-right of the graph contains a mixture of text and icons, so
  // we represent it using an array of StatusMessageEntry objects.
  private static class StatusMessageEntry {
    public String text;
    public Drawable icon;

    public static StatusMessageEntry text(String text) {
      StatusMessageEntry entry = new StatusMessageEntry();
      entry.text = text;
      return entry;
    }

    public static StatusMessageEntry icon(Context context, int resId) {
      StatusMessageEntry entry = new StatusMessageEntry();
      entry.icon = context.getDrawable(resId);
      return entry;
    }
  }

  private TreeMap<Integer, RemoteViews> remoteViews;

  private float pixelDensity;
  private Settings settings;

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

      settings = Settings.get(context);
      if (remoteViews == null) {
        pixelDensity = context.getResources().getDisplayMetrics().density;
        remoteViews = new TreeMap<>();
        for (int appWidgetId : appWidgetIds) {
          remoteViews.put(appWidgetId, new RemoteViews(context.getPackageName(),
              R.layout.widget));
        }
      }

      if (CUSTOM_REFRESH_ACTION.equals(intent.getAction())) {
        refreshGraph(context, appWidgetIds);
      } else {
        super.onReceive(context, intent);
      }

      if (appWidgetIds != null) {
        for (int appWidgetId : appWidgetIds) {
          AppWidgetManager.getInstance(context).updateAppWidget(
              appWidgetId, remoteViews.get(appWidgetId));
        }
      }
    } catch (Exception e) {
      Log.e(TAG, "Unhandled exception!", e);
    }
  }

  /**
   * This is called when the "options" of the widget change. In particular, we're
   * interested in when the dimensions change.
   * <p/>
   * Unfortunately, the "width" and "height" we receive can, in reality, be
   * incredibly inaccurate, we need to provide a setting that the user can
   * override :\
   */
  @Override
  public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager,
      int appWidgetId, Bundle newOptions) {
    Settings.GraphSettings gs = settings.getGraphSettings(appWidgetId);
    if (gs.autoGraphSize()) {
      gs.setGraphWidth(newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH));
      gs.setGraphHeight(newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT));
      gs.save(context, appWidgetId);
    }

    refreshGraph(context, new int[]{appWidgetId});
  }

  /**
   * This is called when the widget is updated (usually when it starts up, but
   * also gets called ~every 30 minutes.
   */
  @Override
  public void onUpdate(Context context, AppWidgetManager mgr, int[] appWidgetIds) {
    super.onUpdate(context, mgr, appWidgetIds);

    refreshGraph(context, appWidgetIds);
  }

  private void refreshGraph(Context context, int[] appWidgetIds) {
    // make sure the alarm is running
    AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    Intent alarmIntent = new Intent(context, BatteryGraphAlarmReceiver.class);
    PendingIntent pendingAlarmIntent = PendingIntent.getBroadcast(context, 0, alarmIntent, PendingIntent.FLAG_MUTABLE);
    alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), 1000 * 60, pendingAlarmIntent);

    for (int appWidgetId : appWidgetIds) {
      Intent intent = new Intent(context, SettingsActivity.class);
      intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
      intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
      PendingIntent pendingIntent = PendingIntent.getActivity(context, appWidgetId, intent,
          PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
      remoteViews.get(appWidgetId).setOnClickPendingIntent(R.id.image, pendingIntent);

      Settings.GraphSettings graphSettings = settings.getGraphSettings(appWidgetId);
      int numMinutes = graphSettings.getNumHours() * 60;
      Bitmap bmp = renderGraph(context, graphSettings, numMinutes);
      remoteViews.get(appWidgetId).setImageViewBitmap(R.id.image, bmp);
    }
  }

  private HashMap<Integer, List<BatteryStatus>> getBluetoothDeviceBatteryHistory(
          Context context, Settings.GraphSettings graphSettings) {
    HashMap<Integer, List<BatteryStatus>> history = new HashMap<>();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        ActivityCompat.checkSelfPermission(
            context, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED) {
      // Don't have permission, cannot return anything.
      return history;
    }

    HashMap<String, Integer> knownDevices = BatteryStatus.getBluetoothDeviceIds(context);
    for (BluetoothDevice device : BluetoothAdapter.getDefaultAdapter().getBondedDevices()) {
      Settings.BluetoothDeviceSettings deviceSettings =
              graphSettings.getBluetoothDeviceSettings(device.getName());
      if (deviceSettings != null && deviceSettings.showGraph()) {
        Integer id = knownDevices.get(device.getName());
        if (id == null) {
          continue;
        }
        history.put(id, BatteryStatus.getHistory(context, id, graphSettings.getNumHours()));
      }
    }

    return history;
  }

  private Bitmap renderGraph(Context context, Settings.GraphSettings graphSettings,
      int numMinutes) {
    final int width = (int) (graphSettings.getGraphWidth() * pixelDensity);
    final int height = (int) (graphSettings.getGraphHeight() * pixelDensity);
    if (width == 0 || height == 0) {
      return null;
    }

    Bitmap bmp = Bitmap.createBitmap(width, height, Config.ARGB_8888);
    Canvas canvas = new Canvas(bmp);

    int graphHeight = height;
    if (graphSettings.showTimeScale()) {
      graphHeight -= 10 * pixelDensity;
    }

    List<BatteryStatus> batteryHistory = BatteryStatus.getHistory(
        context, 0, graphSettings.getNumHours());
    HashMap<Integer, List<BatteryStatus>> bluetoothDeviceHistory =
        getBluetoothDeviceBatteryHistory(context, graphSettings);
    HashMap<Integer, String> bluetoothDeviceNames = BatteryStatus.getBluetoothDeviceNames(context);

    int numGraphsShowing = 0;
    List<GraphPoint> tempPoints = null;
    List<GraphPoint> batteryChargePoints = null;
    List<GraphPoint> batteryCurrentInstantPoints = null;
    List<GraphPoint> batteryCurrentAvgPoints = null;
    List<GraphPoint> batteryEnergyPoints = null;
    HashMap<Integer, List<GraphPoint>> bluetoothGraphs = new HashMap<>();
    for (Integer id : bluetoothDeviceHistory.keySet()) {
      List<BatteryStatus> history = bluetoothDeviceHistory.get(id);
      if (history == null) continue;
      int color = graphSettings.getBluetoothDeviceSettings(bluetoothDeviceNames.get(id)).getColor();
      bluetoothGraphs.put(
          id,
          renderChargeGraph(
              history, numMinutes, width, graphHeight, color));
    }

    if (graphSettings.showBatteryGraph()) {
      batteryChargePoints = renderChargeGraph(batteryHistory, numMinutes, width,
          graphHeight, Color.GREEN);
      numGraphsShowing++;
    }
    if (graphSettings.showTemperatureGraph()) {
      numGraphsShowing++;
      tempPoints = renderTempGraph(batteryHistory, numMinutes, width, graphHeight,
          graphSettings.smoothTemp() ? 20 : 0);
    }
    if (graphSettings.showBatteryCurrentInstant()) {
      final float scale = graphSettings.invertBatteryCurrentInstant() ? -1.0f : 1.0f;
      final int smoothness = graphSettings.smoothBatteryCurrentInstant() ? 20 : 0;
      batteryCurrentInstantPoints = renderGraph(batteryHistory, numMinutes, width, height,
          new GraphCustomizer() {
            @Override
            public float getValue(BatteryStatus status) {
              return scale * status.getBatteryCurrentInstant();
            }

            @Override
            public int getColor(float value, int height) {
              return value >= 0.0f ? Color.GREEN : Color.RED;
            }
          }, height / 2, 0, smoothness);
      numGraphsShowing++;
    }
    if (graphSettings.showBatteryCurrentAvg()) {
      batteryCurrentAvgPoints = renderGraph(batteryHistory, numMinutes, width, height,
          new GraphCustomizer() {
            @Override
            public float getValue(BatteryStatus status) {
              return status.getBatteryCurrentAvg();
            }

            @Override
            public int getColor(float value, int height) {
              return value >= 0.0f ? Color.GREEN : Color.RED;
            }
          }, height / 2, 0, 0);
      numGraphsShowing++;
    }
    if (graphSettings.showBatteryEnergy()) {
      batteryEnergyPoints = renderGraph(batteryHistory, numMinutes, width, height,
          new GraphCustomizer() {
            @Override
            public float getValue(BatteryStatus status) {
              return status.getBatteryEnergy();
            }

            @Override
            public int getColor(float value, int thisHeight) {
              return getColourForCharge((float) thisHeight / height, Color.GREEN);
            }
          }, 0, 0, 0);
      numGraphsShowing++;
    }
    numGraphsShowing += bluetoothGraphs.size();
    if (graphSettings.showTimeScale()) {
      showTimeScale(canvas, width, height, numMinutes, numGraphsShowing,
          graphSettings.showTimeLines());
    }

    if (tempPoints != null) {
      drawGraphBackground(tempPoints, canvas, width, graphHeight);
    }
    if (batteryCurrentInstantPoints != null) {
      drawGraphBackground(batteryCurrentInstantPoints, canvas, width, graphHeight / 2);
    }
    if (batteryCurrentAvgPoints != null) {
      drawGraphBackground(batteryCurrentAvgPoints, canvas, width, graphHeight / 2);
    }
    if (batteryEnergyPoints != null) {
      drawGraphBackground(batteryEnergyPoints, canvas, width, graphHeight);
    }
    for (Integer id : bluetoothGraphs.keySet()) {
      List<GraphPoint> points = bluetoothGraphs.get(id);
      drawGraphBackground(points, canvas, width, graphHeight);
    }
    if (batteryChargePoints != null) {
      drawGraphBackground(batteryChargePoints, canvas, width, graphHeight);
    }

    if (tempPoints != null) {
      drawGraphLine(tempPoints, canvas);
    }
    if (batteryCurrentInstantPoints != null) {
      drawGraphLine(batteryCurrentInstantPoints, canvas);
    }
    if (batteryCurrentAvgPoints != null) {
      drawGraphLine(batteryCurrentAvgPoints, canvas);
    }
    if (batteryEnergyPoints != null) {
      drawGraphLine(batteryEnergyPoints, canvas);
    }
    for (Integer id : bluetoothGraphs.keySet()) {
      List<GraphPoint> graphPoints = bluetoothGraphs.get(id);
      if (graphSettings.showLastLevelLine()) {
        drawHorizontalLine(graphPoints, canvas);
      }
      drawGraphLine(graphPoints, canvas);
    }
    if (batteryChargePoints != null) {
      if (graphSettings.showLastLevelLine()) {
        drawHorizontalLine(batteryChargePoints, canvas);
      }
      drawGraphLine(batteryChargePoints, canvas);
    }

    ArrayList<StatusMessageEntry> statusMessage = new ArrayList<>();
    if (graphSettings.showTemperatureGraph() && batteryHistory.size() > 0) {
      float celsius = batteryHistory.get(0).getBatteryTemp();
      String temp;
      if (graphSettings.tempInCelsius()) {
        temp = String.format(Locale.US, "%.1f°", celsius);
      } else {
        temp = String.format(Locale.US, "%.1f°", celsius * 9.0f / 5.0f + 32.0f);
      }
      statusMessage.add(StatusMessageEntry.icon(context, R.drawable.ic_temp));
      statusMessage.add(StatusMessageEntry.text(temp));
    }

    boolean hasCurrentInstant = false;
    if (graphSettings.showBatteryCurrentInstant() && batteryHistory.size() > 0) {
      String curr = String.format(Locale.US,
          "%.2f mA (inst)", batteryHistory.get(0).getBatteryCurrentInstant());
      if (statusMessage.size() > 0) {
        statusMessage.add(StatusMessageEntry.text(" - "));
      }
      statusMessage.add(StatusMessageEntry.icon(context, R.drawable.ic_current_inst));
      statusMessage.add(StatusMessageEntry.text(curr));
      hasCurrentInstant = true;
    }

    if (graphSettings.showBatteryCurrentAvg() && batteryHistory.size() > 0) {
      String curr = String.format(Locale.US, "%d mA (avg)",
          (int) batteryHistory.get(0).getBatteryCurrentAvg());
      if (!hasCurrentInstant) {
        statusMessage.add(StatusMessageEntry.icon(context, R.drawable.ic_current_inst));
      }
      statusMessage.add(StatusMessageEntry.text(curr));
    }

    if (graphSettings.showBatteryEnergy() && batteryHistory.size() > 0) {
      String curr = String.format(Locale.US, "%d mWh",
          (int) batteryHistory.get(0).getBatteryEnergy());
      statusMessage.add(StatusMessageEntry.icon(context, R.drawable.ic_battery_energy));
      statusMessage.add(StatusMessageEntry.text(curr));
    }

    if (graphSettings.showBatteryGraph() && batteryHistory.size() > 0) {
      statusMessage.add(StatusMessageEntry.icon(context, R.drawable.ic_device_phone));
      statusMessage.add(
          StatusMessageEntry.text(
              String.format(
                  Locale.US,
                  "%d%%",
                  (int) (batteryHistory.get(0).getChargeFraction() * 100.0f))));
    }

    HashMap<Integer, String> deviceNames = BatteryStatus.getBluetoothDeviceNames(context);
    for (Integer deviceId : bluetoothDeviceHistory.keySet()) {
      List<BatteryStatus> deviceHistory = bluetoothDeviceHistory.get(deviceId);
      if (deviceHistory == null || deviceHistory.size() == 0) continue;
      if (deviceHistory.get(0).getChargeFraction() < 0.0) continue;

      String deviceName = deviceNames.get(deviceId);
      if (deviceName == null) continue;

      Settings.BluetoothDeviceSettings deviceSettings =
          graphSettings.getBluetoothDeviceSettings(deviceName);
      if (deviceSettings == null) continue;
      statusMessage.add(StatusMessageEntry.icon(context, deviceSettings.getIconResId()));
      statusMessage.add(
          StatusMessageEntry.text(
              String.format(
                  Locale.US, "%d%%",
                  (int) (deviceHistory.get(0).getChargeFraction() * 100.0f))));
    }

    Paint paint = new Paint();
    paint.setAntiAlias(true);
    paint.setTextSize(20.0f * pixelDensity);
    paint.setColor(Color.WHITE);
    paint.setStyle(Style.FILL);
    paint.setStrokeWidth(pixelDensity);
    int iconSize = 64;
    float x = width - 4;
    float y = graphHeight - 4;
    for (int i = statusMessage.size() - 1; i >= 0; i--) {
      StatusMessageEntry entry = statusMessage.get(i);
      if (entry.text != null) {
        float textWidth = paint.measureText(entry.text);
        x -= textWidth;
        canvas.drawText(entry.text, x, y, paint);
        x -= 8;
      } else if (entry.icon != null) {
        x -= iconSize;
        entry.icon.setBounds((int) x, (int) y - iconSize, (int) x + iconSize, (int) y);
        entry.icon.draw(canvas);
        x -= 16;
      }
    }

    return bmp;
  }

  private void showTimeScale(Canvas canvas, int width, int height, int numMinutes,
      int numGraphsShowing, boolean drawLines) {
    Rect r = new Rect(0, height - (int) (10 * pixelDensity), width, height);
    Paint bgPaint = new Paint();
    bgPaint.setARGB(128, 0, 0, 0);
    bgPaint.setStyle(Style.FILL);
    for (int i = 0; i < numGraphsShowing; i++) {
      canvas.drawRect(r, bgPaint);
    }

    Paint fgPaint = new Paint();
    fgPaint.setAntiAlias(true);
    fgPaint.setTextSize(10 * pixelDensity);
    fgPaint.setColor(Color.WHITE);
    fgPaint.setStyle(Style.FILL);
    fgPaint.setStrokeWidth(pixelDensity);

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
        String text = String.format(Locale.US, "%d%s", hour, ampm);
        float textWidth = fgPaint.measureText(text);
        canvas.drawText(text, x - (textWidth / 2), height - 4, fgPaint);
        if (drawLines) {
          canvas.drawLine(x, 0, x, height - (10.0f * pixelDensity), fgPaint);
        }
      }
    }
  }

  private List<GraphPoint> renderChargeGraph(List<BatteryStatus> history, int numMinutes,
      int width, int height, int baseColour) {
    height -= 4;
    ArrayList<GraphPoint> points = new ArrayList<>();
    if (history.size() < 2) {
      points.add(new GraphPoint(width, height, Color.GREEN));
      points.add(new GraphPoint(0, height, Color.GREEN));
      return points;
    }

    float x = width;
    float pixelsPerMinute = (float) width / numMinutes;

    Calendar cal = Calendar.getInstance();
    cal.setTime(new Date());

    for (int minute = 0, j = 0; minute < numMinutes; minute++) {
      Date dt = cal.getTime();
      while (j < history.size() - 1 && history.get(j).getDate().after(dt)) {
        j++;
      }
      BatteryStatus status = history.get(j);
      float y = 2 + height - (height * status.getChargeFraction());
      if (y > 0 && y < height) {
        points.add(new GraphPoint(x, y, getColourForCharge(status.getChargeFraction(), baseColour)));
      }

      x -= pixelsPerMinute;
      cal.add(Calendar.MINUTE, -1);
    }

    return points;
  }

  private List<GraphPoint> renderGraph(List<BatteryStatus> history, int numMinutes,
      int width, int height, GraphCustomizer customizer,
      int zeroPoint, float maxValue, int smoothness) {
    height -= 4;
    ArrayList<GraphPoint> points = new ArrayList<>();
    if (history.size() < 2) {
      points.add(new GraphPoint(width, zeroPoint, customizer.getColor(0.0f, zeroPoint)));
      points.add(new GraphPoint(0, zeroPoint, customizer.getColor(0.0f, zeroPoint)));
      return points;
    }

    if (maxValue == 0) {
      ArrayList<Integer> lastIndices = new ArrayList<>();
      for (int i = 0; i < history.size(); i++) {
        float currValue = Math.abs(getSmoothedValue(history, i, lastIndices, 1.0f,
            smoothness, customizer));
        lastIndices.add(i);
        if (currValue > maxValue) {
          maxValue = currValue;
        }
      }
    }

    float x = width;
    float pixelsPerMinute = (float) width / numMinutes;

    Calendar cal = Calendar.getInstance();
    cal.setTime(new Date());

    ArrayList<Integer> lastIndices = new ArrayList<>();
    float value = getSmoothedValue(history, 0, lastIndices, pixelsPerMinute, smoothness, customizer);
    lastIndices.add(0);
    float y = getFractionHeight(value, maxValue, height);
    points.add(new GraphPoint(x, y, customizer.getColor(value, (int) y)));
    for (int minute = 1, j = 1; minute < numMinutes; minute++) {
      x -= pixelsPerMinute;
      cal.add(Calendar.MINUTE, -1);
      Date dt = cal.getTime();

      while (j < history.size() - 1 && history.get(j).getDate().after(dt)) {
        j++;
      }
      value = getSmoothedValue(history, j, lastIndices, 1.0f, smoothness, customizer);
      lastIndices.add(j);
      y = getFractionHeight(value, maxValue, height);
      points.add(new GraphPoint(x, y, customizer.getColor(value, (int) y)));
    }

    return points;
  }

  private float getSmoothedValue(List<BatteryStatus> history, int position,
      ArrayList<Integer> lastIndices, float pixelsPerMinute,
      int smoothness, GraphCustomizer customizer) {
    float value = customizer.getValue(history.get(position));
    if (smoothness > 0) {
      int numValues = 1;
      for (int i = 0; i < smoothness; i++) {
        int index = lastIndices.size() - (int) (i / pixelsPerMinute) - 1;
        if (index < 0) {
          break;
        }
        float thisValue = customizer.getValue(history.get(lastIndices.get(index)));
        value += thisValue;
        numValues++;
      }
      value /= numValues;
    }
    return value;
  }

  private float getFractionHeight(float value, float maxValue, float height) {
    float fractionHeight = (height / 2.0f) - (value / maxValue) * (height / 2.0f);
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

  private List<GraphPoint> renderTempGraph(List<BatteryStatus> history, int numMinutes,
      int width, int height, int smoothness) {
    height -= 4;
    ArrayList<GraphPoint> points = new ArrayList<>();
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

    ArrayList<Integer> lastIndices = new ArrayList<>();
    float temp = getSmoothedTemp(history, 0, lastIndices, pixelsPerMinute, smoothness);
    lastIndices.add(0);
    float y = 2 + height - (height * getTempFraction(temp, minTemp, maxTemp));
    points.add(new GraphPoint(x, y, Color.BLUE));
    for (int minute = 1, j = 1; minute < numMinutes; minute++) {
      x -= pixelsPerMinute;
      cal.add(Calendar.MINUTE, -1);
      Date dt = cal.getTime();

      while (j < history.size() - 1 && history.get(j).getDate().after(dt)) {
        j++;
      }
      temp = getSmoothedTemp(history, j, lastIndices, pixelsPerMinute, smoothness);
      lastIndices.add(j);
      y = 2 + height - (height * getTempFraction(temp, minTemp, maxTemp));
      points.add(new GraphPoint(x, y, Color.BLUE));
    }

    return points;
  }

  private float getSmoothedTemp(List<BatteryStatus> history, int position,
      ArrayList<Integer> lastIndices, float pixelsPerMinute,
      int smoothness) {
    float value = history.get(position).getBatteryTemp();
    if (smoothness > 0) {
      int numValues = 1;
      for (int i = 0; i < smoothness; i++) {
        int index = lastIndices.size() - (int) (i / pixelsPerMinute) - 1;
        if (index < 0) {
          break;
        }
        float thisValue = history.get(lastIndices.get(index)).getBatteryTemp();
        value += thisValue;
        numValues++;
      }
      value /= numValues;
    }
    return value;
  }

  private static float getTempFraction(float temp, float min, float max) {
    float range = max - min;
    if (range < 0.01) {
      return 0.0f;
    }

    return (temp - min) / range;
  }

  private void drawGraphBackground(
      List<GraphPoint> points, Canvas canvas, int width, int zeroValue) {
    Path path = new Path();
    boolean first = true;
    float lastX = width;
    for (GraphPoint pt : points) {
      if (first) {
        path.moveTo(pt.x, zeroValue);
      } else {
        if (lastX - pt.x > MAX_GAP_PX) {
          path.lineTo(lastX, zeroValue);
          path.lineTo(pt.x, zeroValue);
        }
      }
      path.lineTo(pt.x, pt.y);
      first = false;
      lastX = pt.x;
    }
    if (lastX < MAX_GAP_PX) {
      path.lineTo(0, zeroValue);
    } else {
      path.lineTo(lastX, zeroValue);
    }

    path.lineTo(width, zeroValue);
    Paint paint = new Paint();
    paint.setAntiAlias(true);
    paint.setARGB(128, 0, 0, 0);
    paint.setStyle(Style.FILL);
    canvas.drawPath(path, paint);
  }

  private void drawGraphLine(List<GraphPoint> points, Canvas canvas) {
    Paint paint = new Paint();
    paint.setAntiAlias(true);
    paint.setStyle(Style.STROKE);
    paint.setAlpha(255);
    paint.setStrokeWidth(4.0f);
    Path path = null;
    if (points.isEmpty()) {
      return;
    }

    float lastX = points.get(0).x;
    int colour = points.get(0).colour;
    for (GraphPoint pt : points) {
      if (lastX - pt.x <=  MAX_GAP_PX && (pt.colour != colour || path == null)) {
        if (path != null) {
          path.lineTo(pt.x, pt.y);
          paint.setColor(colour);
          canvas.drawPath(path, paint);
        }
        path = new Path();
        path.moveTo(pt.x, pt.y);
        colour = pt.colour;
      } else if (path != null) {
        if (lastX - pt.x > MAX_GAP_PX) {
          path.moveTo(pt.x, pt.y);
        } else {
          path.lineTo(pt.x, pt.y);
        }
      }
      lastX = pt.x;
    }

    paint.setColor(colour);
    canvas.drawPath(path, paint);
  }

  private void drawHorizontalLine(List<GraphPoint> points, Canvas canvas) {
    GraphPoint pt = points.get(0);

    Paint paint = new Paint();
    paint.setStyle(Style.STROKE);
    paint.setStrokeWidth(4.0f);
    paint.setColor(pt.colour);
    paint.setAlpha(80);
    canvas.drawLine(0, pt.y, pt.x, pt.y, paint);
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
