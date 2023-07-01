package au.com.codeka.advbatterygraph;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.DialogPreference;

public class ColorPreference extends DialogPreference {
  private int selectedColor;

  private Bitmap iconBitmap;
  private int bitmapColor;

  public ColorPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
    init();
  }

  public ColorPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init();
  }

  public ColorPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  public ColorPreference(@NonNull Context context) {
    super(context);
    init();
  }

  public void setColor(int color) {
    this.selectedColor = color;
    persistInt(color);
    refresh(true);
  }

  private void init() {
    setDialogLayoutResource(R.layout.color_dialog);
  }

  @Override
  public void onAttached() {
    super.onAttached();

    // TODO: get the correct default?
    selectedColor = getPersistedInt(Color.argb(200, 0x00, 0xba, 0xff));
    refresh(false);
  }

  private void refresh(boolean notify) {
    if (iconBitmap == null || bitmapColor != selectedColor) {
      bitmapColor = selectedColor;
      iconBitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888);
      Canvas canvas = new Canvas(iconBitmap);
      Paint paint = new Paint();
      paint.setColor(bitmapColor);
      canvas.drawRect(0.0f, 0.0f, 256.0f, 256.0f, paint);

      setIcon(new BitmapDrawable(getContext().getResources(), iconBitmap));
    }

    if (notify) {
      notifyChanged();
    }
  }
}
