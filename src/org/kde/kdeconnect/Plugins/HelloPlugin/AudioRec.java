package org.kde.kdeconnect.Plugins.HelloPlugin;


import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Environment;
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

    public static int bufferSize; // = AudioRecord.getMinBufferSize(SAMPLING_TIME, RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING);
    protected static AudioRecord recorder = null;
    private static boolean isRecording = false;
    private static short[] audioBuffer = null;
    private static String outputFile;
    private static RecordThread RecThread = null;
    private static int calc = 1;

    private static int frameLength =(int) (RECORDER_SAMPLERATE * 0.03);
    private static int hopSize = (int)(RECORDER_SAMPLERATE * 0.02);
    private static ArrayList<Double> windowingArray = new ArrayList();
    private static double[][] array;
    private static byte[][] finger;

    public static void getValidSampleRates() {
        for (int rate : new int[] {8000, 11025, 16000, 22050, 44100}) {  // add the rates you wish to check against
            int bufferSize = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_CONFIGURATION_DEFAULT, AudioFormat.ENCODING_PCM_16BIT);
            if (bufferSize > 0) {
                Log.e(TAG, rate + " is supported");// buffer size is valid, Sample rate supported

            }
        }
    }

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
        CalculateFFT();
        for(int i = 0; i < finger.length; i++){
            for(int j = 0; j < finger[0].length; j++){
                //Log.e(TAG,"value: " + String.valueOf(finger[i][j]));
                message += String.valueOf(finger [i][j]);
                message += "\n";
            }
        }
        Log.e(TAG, String.valueOf(finger.length) + " * " + String.valueOf(finger[0].length));
        Log.e(TAG, "data array is long: " + String.valueOf(array.length * array[0].length));
        getValidSampleRates();

    }

    public static void initialize(){
        int minSize = AudioRecord.getMinBufferSize(SAMPLING_TIME, RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING);
        Log.e(TAG, "minsize: " + String.valueOf(minSize));
        int nSample = (int) (RECORDER_SAMPLERATE * ((double) (SAMPLING_TIME/* + 2*100/*(redundant_time)*/))/1000f);
        Log.e(TAG, "nSample: " + String.valueOf(nSample));
        bufferSize = (int) Math.ceil(nSample);
        /*if (bufferSize < minSize)
            bufferSize = minSize;*/
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
            Log.e(TAG, String.valueOf(frameAmount));
            array = new double[66][frameLength];
            for(int i = 0; i < frameAmount; i++){
                for(int j = 0; j < frameLength; j++){
                    short dataPoint = audioBuffer[i*hopSize + j];
                    if(dataPoint != 0){
                        dataPoint *= windowingArray.get(j);
                        audioBuffer[i*hopSize + j] = dataPoint;
                        int d = c % 3;
                        int arrayJStart = j + (d/3)*frameLength;
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
          /*  for(int i = 0; i < frameAmount; i++){
                for(int j = 0; j < frameLength; j++){
                    Log.e(TAG, "Data in array:" +  String.valueOf(array[i][j]));
                }
            }*/
        }
    }

    //FFT, Energybands
    private static void CalculateFFT(){
        double[][] energy = new double[array.length][32];
        int bandL = frameLength / 32;
        // FFT the frame i-th
        int sdf = 0;
        Complex[] complex = new Complex[1024];
        for(int i = 0; i < (int)(floor(32000/1024)); i++){
            for (int j=0; j<1024; j++)
                complex[j] = new Complex(audioBuffer[i*hopSize + j], 0f);
            sdf++;
            Log.e(TAG, "count: " + String.valueOf(sdf));
            complex = FFT.fft(complex);

            double e = 0;
            for (int j=0; j<frameLength; j++) {
                if (j > 0 && j % bandL == 0) {
                    energy[i][j / bandL - 1] = e;
                    e = complex[j].abs();
                    if (j == frameLength - 1)
                        energy[i][j / bandL] = e;
                } else if (j == frameLength - 1) {
                    e += complex[j].abs();
                    energy[i][j / bandL] = e;
                } else
                    e += complex[j].abs();
            }
        }
        // calculate finger-print matrix
        finger = new byte[array.length/2-1][32-1];
        for (int i=1; i<array.length/2 ; i++)
            for (int j=0; j<32-1; j++)
                if ( (energy[i][j]-energy[i][j+1]) -
                        (energy[i-1][j]-energy[i-1][j+1]) > 0 )
                    finger[i-1][j] = 1;
                else
                    finger[i-1][j] = 0;
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
            Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    if (null != recorder) {
                        Log.e(TAG, "used samplerate: " + String.valueOf(recorder.getSampleRate()));
                        isRecording = false;
                        recorder.stop();
                        recorder.release();
                        recorder = null;
                        Log.e(TAG, "Stopped Recording");
                    }
                }
            }, 2000);
            audioBuffer = new short[bufferSize];
            recorder.read(audioBuffer, 0, bufferSize);
            int count = 0;
            try (PrintWriter p = new PrintWriter(new FileOutputStream(outputFile, true))) {
                for (int i = 0; i < bufferSize; i++) {
                    p.println(audioBuffer[i]);
                    if(audioBuffer[i] != 0) {
                        count++;
                       // Log.e(TAG, String.valueOf(audioBuffer[i]));
                    }
                    // message += String.valueOf(audioBuffer[i]);
                    // message += " ";
                }
                Log.e(TAG,"amount of non-zeros in buffer: " + String.valueOf(count));

            } catch (FileNotFoundException e1) {
                e1.printStackTrace();
            }
            Log.e("Audiorecording","amount of data in buffer:" + bufferSize);

            calc = 0;
        }
    }
}