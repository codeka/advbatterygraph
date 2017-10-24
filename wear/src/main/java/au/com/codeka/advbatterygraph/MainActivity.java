package au.com.codeka.advbatterygraph;

import android.app.Activity;
import android.os.Bundle;
import android.support.wearable.view.WatchViewStub;
import android.widget.TextView;

import java.util.Locale;

public class MainActivity extends Activity {
    private TextView textView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final WatchViewStub stub = findViewById(R.id.watch_view_stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                textView = stub.findViewById(R.id.text);
                textView.setText(String.format(
                        Locale.US,
                        "Last percent: %.2f",
                        BatteryGraphSyncer.lastPercent));
            }
        });
    }
}
