package com.example.wangqianyi.gesturerecognition103;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Created by wangqianyi on 2016-11-21.
 */
public class MotionService extends Service implements SensorEventListener{

    //Sensor variable
    Sensor senAccelerometer, senGyroscope;
    SensorManager mSensorManager;
    float acc_x, acc_y, acc_z, gry_x, gry_y, gry_z, acc_y_lowpass;
    private float mGry; // acceleration apart from gravity
    private float mGryCurrent; // current acceleration including gravity
    private float mGryLast; // last acceleration including gravity
    private float yAcc; // acceleration apart from gravity
    private float yAccCurrent; // current acceleration including gravity
    private float yAccLast; // last acceleration including gravity
    boolean trigger = false;
    ArrayList<Float> dataArray_acc_y = new ArrayList();
    ArrayList<Float> dataGry = new ArrayList();

    TextToSpeech t1;
    SharedPreferences.Editor training_editor;
    SharedPreferences training_prefs;
    public static final String TRAINING_FILE = "TrainingFile";

    BroadcastReceiver broadcastReceiver;
    boolean training_toggle = false;

    @Override
    public void onCreate() {
        super.onCreate();

        mSensorManager = ((SensorManager) getSystemService(SENSOR_SERVICE));
        senAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        senGyroscope = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        mSensorManager.registerListener(this, senAccelerometer , SensorManager.SENSOR_DELAY_FASTEST);//adjust the frequency
        mSensorManager.registerListener(this, senGyroscope , SensorManager.SENSOR_DELAY_FASTEST);//adjust the frequency

        t1=new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if(status != TextToSpeech.ERROR) {
                    t1.setLanguage(Locale.US);
                }
            }
        });

        training_editor = getSharedPreferences(TRAINING_FILE, MODE_PRIVATE).edit();
        training_prefs = getSharedPreferences(TRAINING_FILE, MODE_PRIVATE);

        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                training_toggle = intent.getBooleanExtra("training_toggle",false);
                Log.v("training_toggle", String.valueOf(training_toggle));
            }
        };
        registerReceiver(broadcastReceiver, new IntentFilter(MainApp.BROADCAST_ACTION));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mSensorManager.unregisterListener(this);
        unregisterReceiver(broadcastReceiver);
        Toast.makeText(this,"stop motion service",0).show();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            gry_x = event.values[0];
            gry_y = event.values[1];
            gry_z = event.values[2];
            mGryLast = mGryCurrent;
            float omegaMagnitude = (float) Math.sqrt(gry_x * gry_x + gry_y * gry_y + gry_z * gry_z);
            mGryCurrent = omegaMagnitude;
            float delta = mGryCurrent - mGryLast;
            mGry = mGry * 0.9f + delta; // perform low-cut filter

            if(mGry>=10) {
                if (!trigger) {
                    trigger = true;
                    excute();
                }
            }

            if(trigger){
                dataGry.add(mGry);
            }
        }

        if(event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION){
            acc_x = event.values[0];
            acc_y = event.values[1];
            acc_z= event.values[2];
//            acc_y_lowpass = acc_y_lowpass * 0.8f + (1 - 0.8f) * event.values[1];
            yAccLast = yAccCurrent;
            yAccCurrent = acc_y;//(float) Math.sqrt(acc_x * acc_x + acc_y * acc_y + acc_z * acc_z);
            float delta = yAccCurrent - yAccLast;
            yAcc = yAcc * 0.9f + delta; // perform low-cut filter

            if(trigger){
                dataArray_acc_y.add(yAcc);
            }

        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        Toast.makeText(this,"start motion service",0).show();

        return super.onStartCommand(intent, flags, startId);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private ArrayList findPeaks(ArrayList<Float> dataArr){
        ArrayList<Float> bigVal = new ArrayList();
        ArrayList<Float> peakNum = new ArrayList();

        for (int i=1; i<dataArr.size(); i++){

            if(i<dataArr.size()-1){
                if((float)dataArr.get(i)>(float)dataArr.get(i-1)&&(float)dataArr.get(i)>(float)dataArr.get(i+1)){
                    peakNum.add((float)dataArr.get(i));
                }
            }
        }

        for(float f: peakNum){
            if(f>10){
                bigVal.add(f);
            }
        }
        return bigVal;
    }

    private boolean checkIfOutside(ArrayList<Float> dataArr){
        for(float f: dataArr){
            if(f>20){
                return false;
            }
        }

        return true;
    }

    private float findInsideMax(ArrayList<Float> dataArr){
        float maxVal = dataArr.get(0);
        for(float f: dataArr){
            if(f>=maxVal){
                maxVal = f;
            }
        }
        return maxVal;
    }

    private void train(int peakNum, float maxAcc){
        float training_maxAcc = training_prefs.getFloat("maxAcc", maxAcc);
        int training_peakNum = training_prefs.getInt("peakNum", peakNum);
        training_maxAcc = (training_maxAcc+maxAcc)/2;
        training_peakNum = (training_peakNum+peakNum)/2;
        training_editor.putFloat("maxAcc", training_maxAcc).commit();
        training_editor.putInt("peakNum", training_peakNum).commit();
    }

    public void excute(){
        // Execute some code after 2 seconds have passed
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                trigger = false;
                ArrayList peakNum_gry = new ArrayList();
                peakNum_gry = findPeaks(dataGry);

                float maxVal = findInsideMax(dataArray_acc_y);
                if(training_toggle){
                    train(peakNum_gry.size(), maxVal);
                }
                else{
                    if(peakNum_gry.size()>=2){
                        if(checkIfOutside(dataArray_acc_y)){
                            t1.speak("double outside", TextToSpeech.QUEUE_FLUSH, null);
                        }
                        else{
                            t1.speak("double inside", TextToSpeech.QUEUE_FLUSH, null);
                        }
                    }
                    else{
                        if(checkIfOutside(dataArray_acc_y)){
                            t1.speak("single outside", TextToSpeech.QUEUE_FLUSH, null);
                        }
                        else{
                            t1.speak("single inside", TextToSpeech.QUEUE_FLUSH, null);
                        }
                    }
                }

//                for(float f: dataGry){
//                    Log.v("dataGry", String.valueOf(f));
//                }
//                for(float f: dataArray_acc_y){
//                    Log.v("dataArray_acc_y", String.valueOf(f));
//                }

                dataArray_acc_y.clear();
                dataGry.clear();
            }
        }, 1000);
    }
}
