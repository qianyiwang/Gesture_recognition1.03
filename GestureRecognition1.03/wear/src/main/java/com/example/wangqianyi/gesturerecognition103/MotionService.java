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

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;

import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.classifiers.trees.J48;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.FastVector;
import weka.core.Instance;
import weka.core.Instances;

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
//    SharedPreferences.Editor training_editor;
//    SharedPreferences training_prefs;
//    public static final String TRAINING_FILE = "TrainingFile";

    BroadcastReceiver broadcastReceiver;
    boolean training_toggle = false;

    // weka variable
    Attribute peakNum, maxAccY, classAttribute;
    FastVector fvClassVal, fvWekaAttributes;
    Instances trainingSet;
    int trainIdx = 0;
    String path;
    File training_file;

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
                    t1.speak ("Hello World", TextToSpeech.QUEUE_FLUSH, null);
                }
            }
        });

//        training_editor = getSharedPreferences(TRAINING_FILE, MODE_PRIVATE).edit();
//        training_prefs = getSharedPreferences(TRAINING_FILE, MODE_PRIVATE);
//        training_editor.clear().commit();

        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                training_toggle = intent.getBooleanExtra("training_toggle",false);
                Log.v("training_toggle", String.valueOf(training_toggle));
            }
        };
        registerReceiver(broadcastReceiver, new IntentFilter(MainApp.BROADCAST_ACTION));

        initialWekaVariable();
        // declare the training set
        trainingSet = new Instances("Rel", fvWekaAttributes, 10);
        trainingSet.setClassIndex(trainingSet.numAttributes()-1);

        training_file = new File(getApplicationContext().getExternalFilesDir(null) + File.separator + "training_file");
        if( !training_file.exists() )
            training_file.mkdirs();
        else if( !training_file.isDirectory() && training_file.canWrite() ){
            training_file.delete();
            training_file.mkdirs();
        }

        path = training_file.getPath()+ File.separator+"training_model";
        Log.v("path", path);
    }

    private void initialWekaVariable(){
        // declare attributes
        peakNum = new Attribute("peak number");
        maxAccY = new Attribute("max accY");
        // declare class attribute
        fvClassVal = new FastVector(4);
        fvClassVal.addElement("single outside");
        fvClassVal.addElement("single inside");
        fvClassVal.addElement("double outside");
        fvClassVal.addElement("double inside");
        classAttribute = new Attribute("theClass", fvClassVal);
        // declare the feature vector
        fvWekaAttributes = new FastVector(3);
        fvWekaAttributes.addElement(peakNum);
        fvWekaAttributes.addElement(maxAccY);
        fvWekaAttributes.addElement(classAttribute);
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

            if(mGry>=8) {
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

    private int findPeaks(ArrayList<Float> dataArr){
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
        return bigVal.size();
    }

    private float findMax(ArrayList<Float> dataArr){
        float bigVal = dataArr.get(0);
        for(float f: dataArr){
            if(f>bigVal){
                bigVal = f;
            }
        }

        return bigVal;
    }


    private void train() throws Exception{

        // create the instance
        Instance tempInstance = new DenseInstance(3);
        tempInstance.setValue((Attribute)fvWekaAttributes.elementAt(0),findPeaks(dataGry));
        tempInstance.setValue((Attribute)fvWekaAttributes.elementAt(1),findMax(dataArray_acc_y));
        switch (trainIdx/5){
            case 0:
                tempInstance.setValue((Attribute)fvWekaAttributes.elementAt(2),"single outside");
                t1.speak("complete "+(trainIdx+1)+" single outside training", TextToSpeech.QUEUE_FLUSH, null);
                break;
            case 1:
                tempInstance.setValue((Attribute)fvWekaAttributes.elementAt(2),"single inside");
                t1.speak("complete "+(trainIdx+1)+" single inside training", TextToSpeech.QUEUE_FLUSH, null);
                break;
            case 2:
                tempInstance.setValue((Attribute)fvWekaAttributes.elementAt(2),"double outside");
                t1.speak("complete "+(trainIdx+1)+" double outside training", TextToSpeech.QUEUE_FLUSH, null);
                break;
            case 3:
                tempInstance.setValue((Attribute)fvWekaAttributes.elementAt(2),"double inside");
                t1.speak("complete "+(trainIdx+1)+" double inside training", TextToSpeech.QUEUE_FLUSH, null);
                break;
        }

        trainingSet.add(tempInstance);
//        Log.v("trainingSet: ", String.valueOf(trainingSet));

        if(trainIdx<19){
            trainIdx++;
        }
        else{
            trainIdx=0;
            // create a classifier
//            Classifier model = new J48();
            J48 model = new J48();
            model.setUnpruned(true);
            Log.v("trainingSet:", String.valueOf(trainingSet));
            model.buildClassifier(trainingSet);
            Log.v("training model:", String.valueOf(model));
            weka.core.SerializationHelper.write(path, model);
            t1.speak("training complete", TextToSpeech.QUEUE_FLUSH, null);
            trainingSet.clear();
        }

    }

    private void test() throws Exception{
        initialWekaVariable();
        J48 model = (J48) weka.core.SerializationHelper.read(path);
        Instances testSet = new Instances("Rel", fvWekaAttributes, 10);
        testSet.setClassIndex(testSet.numAttributes()-1);
        // create the instance
        Instance tempInstance = new DenseInstance(3);
        tempInstance.setValue((Attribute)fvWekaAttributes.elementAt(0),findPeaks(dataGry));
        tempInstance.setValue((Attribute)fvWekaAttributes.elementAt(1),findMax(dataArray_acc_y));
        testSet.add(tempInstance);
        Log.v("testingSet", String.valueOf(testSet));
        double idx = model.classifyInstance(testSet.instance(0));
        String className = trainingSet.attribute(trainingSet.numAttributes()-1).value((int)idx);
        t1.speak(className, TextToSpeech.QUEUE_FLUSH, null);
        Log.v("pred: ", String.valueOf(className));
    }

    public void excute(){
        // Execute some code after 2 seconds have passed
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                trigger = false;
                if(training_toggle){
                    try {
                        train();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                else{
                    try {
                        test();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                dataArray_acc_y.clear();
                dataGry.clear();
            }
        }, 1000);
    }
}
