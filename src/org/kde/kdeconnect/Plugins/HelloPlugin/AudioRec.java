package org.kde.kdeconnect.Plugins.HelloPlugin;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.lang.Object;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;

import static java.lang.Math.PI;
import static java.lang.Math.floor;
import static java.lang.Math.sin;

public class AudioRec{
    public static String message = "";
    public static final String TAG = "AudioFingerprint";
    private static final int SAMPLING_TIME = 2000;
    private static final int RECORDER_SAMPLERATE = 16000;
    private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
    private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    static int BufferElements2Rec = 1024; // want to play 2048 (2K) since 2 bytes we use only 1024
    static int BytesPerElement = 2; // 2 bytes in 16bit format
    public static int bufferSize = AudioRecord.getMinBufferSize(SAMPLING_TIME, RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING);
    protected static AudioRecord recorder = null;
    private static Thread recordingThread = null;
    private static boolean isRecording = false;
    static short sData[] = new short[BufferElements2Rec];
    private static short[] audioBuffer = null;
    private static String outputFile;
    private static RecordThread RecThread = null;
    private static int calc = 1;

    private static int frameLength = 480;// RECORDER_SAMPLERATE * 0.03;
    private static int hopSize = 320; //RECORDER_SAMPLERATE * 0.02;
    private static ArrayList<Double> windowingArray = new ArrayList();
    private static double[][] array;

    //The Function To Rule Them All - does everything and is called from HelloPlugin.java
    public static void Record() {
        initialize();
        RecThread = new RecordThread();
        RecThread.start();
        while(calc == 1){
            try{
                Thread.sleep(1000);
            }catch(InterruptedException e){
                Log.e(TAG, "shit");
            }
        }
        windowing();
        Log.e(TAG, "data array is long: " + String.valueOf(array.length * array[0].length));
    }





    //FLATTOP
    private static void windowing(){
        double value;
        for(double i = 0.5; i < frameLength/2; i++){
            value = sin(PI*i / frameLength);
            windowingArray.add(value);
        }
        for(int i = 0; i < frameLength - hopSize; i++){
            windowingArray.add(1.0);
        }
        for(double i = (frameLength/2 - 1); i > 0.5; i--){
            value = sin(PI*i / frameLength);
            windowingArray.add(value);
        }
        int c = 0;
        int e = 0;
        if(audioBuffer!= null){
            int frameAmount = (int) (1 + floor((audioBuffer.length - frameLength) / hopSize));
            array = new double[frameAmount][frameLength];
            for(int i = 0; i < frameAmount; i++){
                for(int j = 0; j < frameLength; j++){
                    double dataPoint = audioBuffer[i*hopSize + j];
                    if(dataPoint != 0){
                        dataPoint *= windowingArray.get(j);
                        int d = c % 3;
                        int arrayJStart = j + (d/3)*frameLength;
                        int arrayIStart = i;

                        if(arrayJStart >= frameLength){
                            array[i - (e - 1)][arrayJStart - frameLength] = dataPoint;
                        }else{
                            array[i - e][arrayJStart] = dataPoint;

                        }

                    }
                }
                c += 2;
                if(c % 3 == 2){
                    e++;
                }
            }
            for(int i = 0; i < frameAmount; i++){
                for(int j = 0; j < frameLength; j++){
                    Log.e(TAG, "Data in array:" +  String.valueOf(array[i][j]));
                }
            }
        }
    }


    public static int getCalc() {
        return calc;
    }
    public static String getMessage(){
        return message;
    }
    public static void setCalc(int param){
        calc = param;
    }

    public static void initialize(){
        int minSize = AudioRecord.getMinBufferSize(SAMPLING_TIME, RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING);
        double nSample = RECORDER_SAMPLERATE * ((double) (SAMPLING_TIME + 2*100/*(redundant_time)*/))/1000f;
        int bufferSize = (int) Math.ceil(nSample);
        if (bufferSize < minSize)
            bufferSize = minSize;
        try {
            recorder = new AudioRecord( MediaRecorder.AudioSource.MIC, RECORDER_SAMPLERATE,
                    RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING, bufferSize*2 );
        } catch (Exception e) {
            Log.e(TAG, "Unable to initialize AudioRecord instance");
        }
        File file = new File(Environment.getExternalStorageDirectory().getAbsolutePath(), "MediaRecorderSample");
        if (!file.exists())
            file.mkdirs();
        outputFile = file.getAbsolutePath() + "/.txt";
    }



    private static class RecordThread extends Thread {

        public void run() {
            calc = 1;
            try {
                recorder.startRecording();
                isRecording = true;
                Log.e(TAG,"Started Recording");
            } catch (IllegalStateException e) {
                Log.e(".RecordThread", "cannot start Recording");
                return;
            }
            audioBuffer = new short[bufferSize];
            recorder.read(audioBuffer, 0, bufferSize);
            Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    if (null != recorder) {
                        isRecording = false;
                        recorder.stop();
                        recorder.release();
                        recorder = null;
                        recordingThread = null;
                        Log.e(TAG, "Stopped Recording");
                    }
                }
            }, 2000);
            try (PrintWriter p = new PrintWriter(new FileOutputStream(outputFile, true))) {
                for (int i = 0; i < bufferSize; i++) {
                   // p.println(audioBuffer[i]);
                     //Log.e("sdas",String.valueOf(audioBuffer[i]));
                    // message += String.valueOf(audioBuffer[i]);
                    // message += " ";
                }

            } catch (FileNotFoundException e1) {
                e1.printStackTrace();
            }
            Log.e("Audiorecording","amount of data in buffer:" + bufferSize);
            calc = 0;
        }
    }
}