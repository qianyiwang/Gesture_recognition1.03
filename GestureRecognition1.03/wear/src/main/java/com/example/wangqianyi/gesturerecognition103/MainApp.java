package com.example.wangqianyi.gesturerecognition103;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.ToggleButton;

public class MainApp extends Activity {

    ToggleButton trainingToggle;
    public static final String BROADCAST_ACTION = "net.qianyiw.broadcast.mainapp";
    Intent intent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.round_activity_main_app);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        intent = new Intent(BROADCAST_ACTION);
        trainingToggle = (ToggleButton)findViewById(R.id.togglebutton);
    }

    @Override
    protected void onResume() {
        super.onResume();
        startService(new Intent(getBaseContext(), MotionService.class));

        trainingToggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    intent.putExtra("training_toggle", true);
                    sendBroadcast(intent);
                } else {
                    intent.putExtra("training_toggle", false);
                    sendBroadcast(intent);
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopService(new Intent(getBaseContext(), MotionService.class));
    }

}
