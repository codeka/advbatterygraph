package au.com.codeka.advbatterygraph;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceDialogFragmentCompat;

import com.skydoves.colorpickerview.AlphaTileView;
import com.skydoves.colorpickerview.ColorPickerView;
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener;
import com.skydoves.colorpickerview.sliders.AlphaSlideBar;
import com.skydoves.colorpickerview.sliders.BrightnessSlideBar;

public class ColorDialogFragment extends PreferenceDialogFragmentCompat {
  public static ColorDialogFragment newInstance(String preferenceKey) {
    ColorDialogFragment fragment = new ColorDialogFragment();

    Bundle args = new Bundle();
    args.putString(ARG_KEY, preferenceKey);
    fragment.setArguments(args);

    return fragment;
  }

  private ColorPickerView colorPickerView;

  @Override
  protected void onBindDialogView(@NonNull View view) {
    super.onBindDialogView(view);

    AlphaTileView alphaTileView = view.findViewById(R.id.alpha_tile_view);
    TextView colorValueView = view.findViewById(R.id.color_value);

    colorPickerView = view.findViewById(R.id.color_picker);
    colorPickerView.setColorListener((ColorEnvelopeListener) (envelope, fromUser) -> {
      alphaTileView.setPaintColor(envelope.getColor());
      colorValueView.setText("#" + envelope.getHexCode());
    });

    AlphaSlideBar alphaSlideBar = view.findViewById(R.id.alpha_slider);
    colorPickerView.attachAlphaSlider(alphaSlideBar);

    BrightnessSlideBar brightnessSlideBar = view.findViewById(R.id.brightness_slider);
    colorPickerView.attachBrightnessSlider(brightnessSlideBar);
  }

  private ColorPreference getColorPreference() {
    return (ColorPreference) getPreference();
  }

  @Override
  public void onDialogClosed(boolean positiveResult) {
    if (positiveResult) {
      int color = colorPickerView.getColor();
      if (getColorPreference().callChangeListener(color)) {
        getColorPreference().setColor(color);
      }
    }
  }
}
