package au.com.codeka.advbatterygraph;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.DialogPreference;

import java.util.HashMap;

public class IconPreference extends DialogPreference {
  public static HashMap<String, Integer> icons = new HashMap<>();

  static {
    icons.put("car", R.drawable.ic_device_car);
    icons.put("headphones", R.drawable.ic_device_headphones);
    icons.put("heart", R.drawable.ic_device_heart);
    icons.put("keyboard", R.drawable.ic_device_keyboard);
    icons.put("mouse", R.drawable.ic_device_mouse);
    icons.put("speaker", R.drawable.ic_device_speaker);
    icons.put("watch", R.drawable.ic_device_watch);
  }

  @Nullable
  private String selectedIcon = null;

  public IconPreference(@NonNull Context context) {
    super(context);
    init();
  }

  public IconPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  public IconPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init();
  }

  public void setIconName(String iconName) {
    this.selectedIcon = iconName;
    persistString(iconName);
    refresh(true);
  }

  @Nullable
  public String getIconName() {
    return selectedIcon;
  }

  @Nullable
  public Integer getIconResourceId() {
    return icons.get(selectedIcon);
  }

  @Override
  public void onAttached() {
    super.onAttached();

    selectedIcon = getPersistedString(null);
    refresh(false);
  }

  private void init() {
    setDialogLayoutResource(R.layout.icon_dialog);
    setPositiveButtonText(null);
  }

  private void refresh(boolean notify) {
    Integer resId = icons.get(selectedIcon);
    if (resId == null) {
      setIcon(null);
    } else {
      setIcon(resId);
    }

    if (notify) {
      notifyChanged();
    }
  }
}
