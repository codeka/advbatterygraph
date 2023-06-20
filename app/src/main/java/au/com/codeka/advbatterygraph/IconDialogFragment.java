package au.com.codeka.advbatterygraph;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceDialogFragmentCompat;

public class IconDialogFragment extends PreferenceDialogFragmentCompat  {
  @Nullable
  private String selectedIcon = null;

  public static IconDialogFragment newInstance(String preferenceKey) {
    IconDialogFragment fragment = new IconDialogFragment();

    Bundle args = new Bundle();
    args.putString(ARG_KEY, preferenceKey);
    fragment.setArguments(args);

    return fragment;
  }

  @Override
  protected void onBindDialogView(@NonNull View view) {
    super.onBindDialogView(view);

    LayoutInflater inflater = LayoutInflater.from(requireContext());
    LinearLayout layout = view.findViewById(R.id.icon_dialog_layout);
    for (String iconName : IconPreference.icons.keySet()) {
      View row = inflater.inflate(R.layout.icon_dialog_row, layout, /*attachToRoot=*/false);
      ImageView img = row.findViewById(R.id.icon);
      Integer resId = IconPreference.icons.get(iconName);
      if (resId != null) {
        img.setImageResource(resId);
      }
      TextView txt = row.findViewById(R.id.icon_name);
      txt.setText(iconName);

      row.setTag(iconName);
      row.setOnClickListener(v -> {
        selectedIcon = (String) v.getTag();

        // Simulate pressing the positive button
        Dialog dialog = getDialog();
        if (dialog != null) {
          onClick(dialog, DialogInterface.BUTTON_POSITIVE);
        }
        dismiss();
      });

      layout.addView(row);
    }
  }

  private IconPreference getIconPreference() {
    return (IconPreference) getPreference();
  }

  @Override
  public void onDialogClosed(boolean positiveResult) {
    if (positiveResult) {
      if (getIconPreference().callChangeListener(selectedIcon)) {
        getIconPreference().setIconName(selectedIcon);
      }
    }
  }
}
