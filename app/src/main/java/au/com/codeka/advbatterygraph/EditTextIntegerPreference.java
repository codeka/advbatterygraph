package au.com.codeka.advbatterygraph;

import android.content.Context;
import android.text.InputType;
import android.util.AttributeSet;

import androidx.preference.EditTextPreference;

/** This is a version of {@see EditTextPreference} which works with integers instead. */
public class EditTextIntegerPreference extends EditTextPreference {
  private Integer integer;

  public EditTextIntegerPreference(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
//    getEditText().setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
  }

  public EditTextIntegerPreference(Context context, AttributeSet attrs) {
    super(context, attrs);
//    getEditText().setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
  }

  public EditTextIntegerPreference(Context context) {
    super(context);
//    getEditText().setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
  }

  @Override
  public void setText(String text) {
    final boolean wasBlocking = shouldDisableDependents();
    integer = parseInteger(text);
    persistString(integer != null ? integer.toString() : null);
    final boolean isBlocking = shouldDisableDependents();
    if (isBlocking != wasBlocking) {
      notifyDependencyChange(isBlocking);
    }
  }

  @Override
  public String getText() {
    return integer != null ? integer.toString() : null;
  }

  public Integer getInteger() {
    return integer;
  }

  @Override
  protected String getPersistedString(String defaultReturnValue) {
    return String.valueOf(getPersistedInt(-1));
  }

  @Override
  protected boolean persistString(String value) {
    return persistInt(Integer.valueOf(value));
  }

  private static Integer parseInteger(String text) {
    try {
      return Integer.parseInt(text);
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
