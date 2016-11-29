package com.example.wangqianyi.gesturerecognition103;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ToggleButton;

import java.util.Locale;

public class MainApp extends Activity {

    ToggleButton trainingToggle;
    Button clearButton;
    public static final String BROADCAST_ACTION = "net.qianyiw.broadcast.mainapp";
    Intent intent;
    TextToSpeech t1;
//    SharedPreferences.Editor training_editor;
//    public static final String TRAINING_FILE = "TrainingFile";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.round_activity_main_app);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        intent = new Intent(BROADCAST_ACTION);
        trainingToggle = (ToggleButton)findViewById(R.id.togglebutton);
        clearButton = (Button)findViewById(R.id.clearButton);
        t1=new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if(status != TextToSpeech.ERROR) {
                    t1.setLanguage(Locale.US);
                }
            }
        });
//        training_editor = getSharedPreferences(TRAINING_FILE, MODE_PRIVATE).edit();
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
                    t1.speak("please do single inside to train", TextToSpeech.QUEUE_FLUSH, null);
                } else {
                    intent.putExtra("training_toggle", false);
                    sendBroadcast(intent);
                }
            }
        });

        clearButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                training_editor.clear().commit();
//                t1.speak("you have cleared all training result", TextToSpeech.QUEUE_FLUSH, null);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopService(new Intent(getBaseContext(), MotionService.class));
    }

}
