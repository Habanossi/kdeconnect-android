package org.kde.kdeconnect.Plugins.PingPlugin;


import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Environment;
import android.util.Log;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;
import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.TimeInfo;
import org.kde.kdeconnect.Plugins.PingPlugin.Matrix;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import static java.lang.Math.PI;
import static java.lang.Math.abs;
import static java.lang.Math.floor;
import static java.lang.Math.log10;
import static java.lang.Math.sin;
import static org.apache.commons.math3.transform.DftNormalization.STANDARD;


public class AudioRec {

    //changeable parameters
    private static boolean rec = true; // true if live recording, false if prerecorded data is used
    private static boolean avg = true; // true if time averaging is used, false if not

    public static String message = "";
    public static final String TAG = "AudioFingerprint";

    //add a folder to your phone called "MediaRecorder", and there you should put the decorrelation matrices. Files should also be added called fingerprint.txt, window.txt, energybands.txt and fftp.txt
    //if prerecorded data is used, that file should also be in this folder, file called raw.dat
    private static File directory = new File(Environment.getExternalStorageDirectory().getAbsolutePath(), "MediaRecorder");
    private static String outputFile1;
    private static String outputFile2;
    private static String outputFile3;
    private static String outputFile4;

    //variables for prerecorded data
    private static File preRecFile = new File(directory, "/raw.dat");
    private static ArrayList<String> preRecData = new ArrayList<>();

    //variables for recorder
    private static final int SAMPLING_TIME = 2800; // recorded time is longer than needed because of the weak, variable signal from phones
    private static final int RECORDER_SAMPLERATE = 16000;
    private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
    private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private static int bufferSize;
    private static AudioRecord recorder = null;
    private static short[] audioBuffer = null;
    private static RecordThread RecThread = null;
    private static int frameLength =(int) (RECORDER_SAMPLERATE * 0.03);
    private static int hopSize = (int)(RECORDER_SAMPLERATE * 0.02);

    //arrays
    private static double[][] energy = null;
    private static double[][] avgEnergy = null;
    private static ArrayList<Double> windowingArray = new ArrayList<>();
    private static double[] array = null;
    private static byte[][] finger = null;

    //time-related variables
    public static long waitTime;
    private static long time;
    private static boolean calc = true;

    //decor matrices
    private static File optFile = new File(directory, "/ctx_opt.txt");
    private static File shapeFile= new File(directory, "/ctx_shape.txt");
    private static File TMatFile = new File(directory, "T_mat.txt");

    private static ArrayList<Integer> optData = new ArrayList<>();
    private static ArrayList<Integer> shapeData = new ArrayList<>();
    private static double[][][] tMatData = new double[30][9][9];


    /*The Function To Rule Them All - does everything, apart from flushing the variables, and is called from PrivacyPlugin.java
      Init variables and decorrelation data, start recording, window, run calculations, print data to files if needed.
    */
    public static void Record() {
        initialize();
        initDecorData();
        Log.e(TAG, "decordata initialized");
        if(rec) {
            time = getCurrentNetworkTime();
            if(waitTime - time < 0 || waitTime - time > 3000){
                time = System.currentTimeMillis();
                Log.e(TAG, "NTP-server error in time, use device default time instead.");
            }
            Log.e(TAG, "Current time, AudioRec.Record, before RecThread.start(): " + time);
            RecThread = new RecordThread();
            RecThread.start();
            int count = 0;
            while (calc) {
                try {
                    Thread.sleep(100);
                    count++;
                } catch (InterruptedException e) {
                    Log.e(TAG, "Cannot sleep. Error");
                }
            }
            Log.e(TAG, "Slept for " + count*100 + " ms.");
        }else {
            Log.e(TAG, "initiating prerecdata");
            if (!preRecFile.exists())
                Log.e(TAG, "Can't find preRecData");
            try {
                String inputBuffer = "";
                BufferedReader reader1 = new BufferedReader(new FileReader(preRecFile));
                inputBuffer = reader1.readLine();
                while(inputBuffer != null){
                    preRecData.add(inputBuffer);
                    inputBuffer = reader1.readLine();
                }
                audioBuffer = new short[preRecData.size()];
                for(int i = 0; i < preRecData.size(); i++){
                    audioBuffer[i] = Short.parseShort(preRecData.get(i));
                }
                Log.e(TAG, "String from preRec: " + preRecData);
            } catch (Exception e) {Log.e(TAG, "can't init prereecdata");}
            Log.e(TAG, "Audiobuffer ready for windowing");
        }
        windowing();
        Log.e(TAG, "Windowing ready");
        calculate();

        Log.e(TAG, finger.length + " * " + finger[0].length);
        Log.e(TAG, "data array is long: " + array.length);
        try (PrintWriter p = new PrintWriter(new FileOutputStream(outputFile1, true))) {
            for(byte[] i : finger){
                for(int j = 0; j < finger[0].length; j++){
                    p.println(i[j]);
                    //Log.e(TAG,"value: " + String.valueOf(finger[i][j]));
                    message += String.valueOf(i[j]);
                }
            }

        } catch (FileNotFoundException e1) {
            e1.printStackTrace();
        }
        //input averaged energydata to textfile
        /*
        try (PrintWriter p = new PrintWriter(new FileOutputStream(outputFile2, true))) {
            for(int i = 0; i < 20; i++){
                for(int j = 0; j < 32; j++){
                    p.println(avgEnergy[j][i]);
                  //  Log.e(TAG, String.valueOf(energy[j][i]));
                }
            }


        } catch (FileNotFoundException e1) {
            e1.printStackTrace();
        }
        //input unaveraged energydata to textfile
        try (PrintWriter p = new PrintWriter(new FileOutputStream(outputFile4, true))) {
            for(int i = 0; i < 99; i++){
                for(int j = 0; j < 480; j++){
                    p.println(array[i*480 + j]);
                    //  Log.e(TAG, String.valueOf(energy[j][i]));
                }
            }


        } catch (FileNotFoundException e1) {
            e1.printStackTrace();
        }*/
        Log.e(TAG, "start time for recording: " + time);
    }

    //INIT THE DECORRELATION DATA FROM FILES TO ARRAYS
    public static void initDecorData() {
        FileInputStream in = null;
        InputStreamReader reader = null;
        if (!optFile.exists())
            Log.e(TAG, "Can't find OptFile");
        try {
            char[] inputBuffer = new char[9*30*2];
            in = new FileInputStream(optFile);
            reader = new InputStreamReader(in);
            reader.read(inputBuffer);
            for(char i : inputBuffer){
                if(i != 9 && i != 10) {
                    optData.add(Character.getNumericValue(i));
                }
            }
        } catch (Exception e){
            Log.e(TAG, "can't init optdata");
        }


        if (!shapeFile.exists())
            Log.e(TAG, "Can't find ShapeFile");
        try {
            String inputBuffer;
            in = new FileInputStream(shapeFile);
            reader = new InputStreamReader(in);
            BufferedReader reader1 = new BufferedReader(new FileReader(shapeFile));
            for(int i = 0; i < 9; i++) {
                inputBuffer = reader1.readLine();
                String[] textSplit = inputBuffer.split("\\s+");
                for (int j = 0; j < 2; j++) {
                    shapeData.add(Integer.parseInt(textSplit[j]));
                }
            }
        } catch (Exception e){
            Log.e(TAG, "can't init shapedata");
        }

        if (!TMatFile.exists())
            Log.e(TAG, "Can't find TMatFile");
        try {
            String inputBuffer = "";
            in = new FileInputStream(TMatFile);
            reader = new InputStreamReader(in);
            BufferedReader reader1 = new BufferedReader(new FileReader(TMatFile));
            for (int i = 0; i < 9; i++) {
                inputBuffer = reader1.readLine();
                String[] textSplit = inputBuffer.split("\\s+");
                for (int j = 0; j < 30; j++) {
                    for(int k = 0; k < 9; k++){
                        tMatData[j][i][k] = Double.parseDouble(textSplit[j*9 + k]);
                    }
                }
            }
        } catch (Exception e) {Log.e(TAG, "can't init tmatdata");}
        finally {
            try {
                if (reader != null)reader.close();
            } catch (IOException e){
                Log.e(TAG, "can't close inreader");
            }
            try {
                if (in != null)in.close();
            } catch (IOException e){
                Log.e(TAG, "can't close instream");
            }
        }
    }

    //method for checking valid sample rates
    /*public static void getValidSampleRates() {
        for (int rate : new int[] {8000, 11025, 16000, 22050, 44100}) {  // add the rates you wish to check against
            int bufferSize = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_CONFIGURATION_DEFAULT, AudioFormat.ENCODING_PCM_16BIT);
            if (bufferSize > 0) {
                Log.e(TAG, rate + " is supported");// buffer size is valid, Sample rate supported

            }
        }
    }*/

    //Gets the time from an NTP-server, which will ideally sync both devices times
    public static long getCurrentNetworkTime() {
        String TIME_SERVER = ".fi.pool.ntp.org";
        NTPUDPClient timeClient = new NTPUDPClient();
        timeClient.setDefaultTimeout(100);
        long string = 0;
        int e = 0;
        int f = 0;
        int k = 0;
        while(e == 0 && f < 5) {
            f += 1;
            try {
                String g = k + TIME_SERVER;
                InetAddress inetAddress = InetAddress.getByName(g);
                k += 1;
                try {
                    timeClient.open();
                    TimeInfo timeInfo = timeClient.getTime(inetAddress);
                    long returnTime = timeInfo.getMessage().getTransmitTimeStamp().getTime();   //server time
                    Date time = new Date(returnTime);
                    Log.d(TAG, "Time from " + g + ": " + time);
                    string = returnTime;
                    e = 1;
                } catch (IOException I1) {
                    Log.e(TAG, "Can't get response from server.");
                    string = System.currentTimeMillis();
                }
            } catch (UnknownHostException u1) {
                Log.e(TAG, "Unknown Host");
                string = System.currentTimeMillis();
            }
        }
        //long returnTime = timeInfo.getReturnTime();   //local device time
        timeClient.close();
        return string;
    }

    //time function that's used only when NetworkTime.java is used
    /*
    public static long getTime(){
        new NetworkTime().execute();
        long lTime = NetworkTime.time;
        Log.e(TAG,"getting time");
        while(lTime == 0){
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Log.e(TAG, "nosleep");
            }
            lTime = NetworkTime.time;
        }
        Log.e(TAG, "this time: " + lTime);
        return lTime;
    }*/




    private static void initialize(){
        int minSize = AudioRecord.getMinBufferSize(RECORDER_SAMPLERATE, RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING);
        Log.e(TAG, "minsize: " + minSize);
        int nSample = (int) (RECORDER_SAMPLERATE * ((double) (SAMPLING_TIME))/1000f);
        Log.e(TAG, "nSample: " + nSample);
        bufferSize = (int) Math.ceil(nSample);
        if (bufferSize < minSize)
            bufferSize = minSize;
        try {
            recorder = new AudioRecord( MediaRecorder.AudioSource.MIC, RECORDER_SAMPLERATE,
                    RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING, bufferSize);
        } catch (Exception e) {
            Log.e(TAG, "Unable to initialize AudioRecord instance");
        }

        //File file = new File(Environment.getExternalStorageDirectory().getAbsolutePath(), "MediaRecorderSample");
        //if (!file.exists())
       //     file.mkdirs();
        outputFile1 = directory.getAbsolutePath() + "/fingerprint.txt";
        try (PrintWriter p = new PrintWriter(new FileOutputStream(outputFile1, false))) {
            p.write("");
        } catch (FileNotFoundException e1) {
            e1.printStackTrace();
        }
        outputFile2 = directory.getAbsolutePath() + "/energybands.txt";
        try (PrintWriter p = new PrintWriter(new FileOutputStream(outputFile2, false))) {
            p.write("");
        } catch (FileNotFoundException e1) {
            e1.printStackTrace();
        }
        outputFile3 = directory + "/fftp.txt";
        try (PrintWriter p = new PrintWriter(new FileOutputStream(outputFile3, false))) {
            p.write("");
        } catch (FileNotFoundException e1) {
            e1.printStackTrace();
            Log.e(TAG, "can't find fftpfile");
        }
        outputFile4 = directory.getAbsolutePath() + "/window.txt";
        try (PrintWriter p = new PrintWriter(new FileOutputStream(outputFile4, false))) {
            p.write("");
        } catch (FileNotFoundException e1) {
            e1.printStackTrace();
        }
        Log.e(TAG, "initialized.");
    }

    //resets variables
    public static void flush(){
        message = "";
        audioBuffer = null;
        RecThread = null;
        energy = null;
        windowingArray = new ArrayList<>();
        array = null;
        finger = null;
        calc = true;
        shapeData = new ArrayList<>();
        optData = new ArrayList<>();
        tMatData = new double[30][9][9];
        time = 0;
    }

    //return fingerprint
    public static String getMessage(){
        return message;
    }


    //FLATTOP
    private static void windowing(){
        double value;
        for(double i = 0.5; i < frameLength; i+=3){
            value = sin(PI*i / frameLength);
            windowingArray.add(value);
        }
        for(double i = 0; i < frameLength - hopSize; i++){
            windowingArray.add(1.0);
        }
        for(double i = (frameLength); i > 0.5; i-=3){
            value = sin(PI*i / frameLength);
            windowingArray.add(value);
        }
        Log.e(TAG, "length of windowingArray: " + windowingArray.size());
        if(audioBuffer!= null){
            int frameAmount = (int) (1 + floor((audioBuffer.length - frameLength) / (double)(hopSize)));
            Log.e(TAG, String.valueOf(frameAmount));
            frameAmount = 99;
            array = new double[frameAmount * frameLength];
            for(int i = 24; i < frameAmount +24; i++) {//24 frames wait, because of weak signals in beginning of recording of some phones
              //  Log.e(TAG, "i: " + String.valueOf(i));
                for (int j = 0; j < frameLength; j++) {
                    //Log.e(TAG, "j: " + String.valueOf(j));
                    //Log.e(TAG, String.valueOf((audioBuffer[i*hopSize + j])));
                   double dataP = audioBuffer[i*hopSize + j];
                   if(dataP != 0) {
                      // Log.e(TAG, "before windowfunction: " + String.valueOf(dataP));
                       dataP *= windowingArray.get(j);
                      // Log.e(TAG, "after windowfunction: " + String.valueOf(dataP));
                       array[(i-24) * frameLength + j] = dataP;
                   }
                }
            }
        }
    }

    //FFT, Energybands, Fingerprint
    private static void calculate(){
        energy = new double[32][99];
        double[][] fftvalues = new double[99][513];
        FastFourierTransformer s = new FastFourierTransformer(STANDARD);
        Complex[] fftcomplex;
        int bandL = 512 / 32;
        // FFT the frame i-th
        double[] frame = new double[1024];
        Log.e(TAG, "calculating FFTs...");
        for (int i = 0; i < 99; i++) {
            for (int j = 0; j < 1024; j++) {
                if (j < frameLength) {
                    //Log.e(TAG, String.valueOf(array[i * frameLength + j]));
                    frame[j] = array[i * frameLength + j];
                }else frame[j] = 0;
            }
            fftcomplex = s.transform(frame, TransformType.FORWARD);
            try (PrintWriter p = new PrintWriter(new FileOutputStream(outputFile3, true))) {
                for(int j = 513; j > 0; j--){
                    double value = 20*log10(fftcomplex[j].abs()+ Math.ulp(1.0));
                    p.println(value);
                    fftvalues[i][513 - j] = value;
                    if(String.valueOf(fftvalues[i][513-j]).equals("-Infinity")){
                        Log.e(TAG, "i: " + i + ", j: " + j);
                        Log.e(TAG, String.valueOf(fftvalues[i][513-j]));
                    }
                }
            } catch (FileNotFoundException e1) {
                e1.printStackTrace();
            }
        }
        Log.e(TAG, "calculating energybands...");
        int count = 0;
        for(int i = 512; i > 0; i--){
            if(count % bandL == 0){
                double mean = 0;
                for(int j = 0; j < 99; j++){
                    double sum = 0;
                    for(int k = 0; k < bandL; k++){
                        if(i-k >= 0) sum += fftvalues[j][i-k];
                    }
                    sum = log10(abs(sum));
                    //Log.e(TAG, String.valueOf(sum));
                    mean += sum;
                    energy[count/bandL][j] = sum;
                }
                mean /= 99;
                //Log.e(TAG, "mean: " + String.valueOf(mean));
                for(int k = 0; k < 99; k++){
                    energy[count/bandL][k] -= mean;
                }
            }
            count++;
        }

        //AVGENER, reduce frames to ~20
        Log.e(TAG, "calculating average energy...");
        avgEnergy = new double[32][20];
        if(avg){
            int avgLen = 5;
            for(int i = 0; i < 32; i++){
                count = 0;
                for(int j = 98; j > 0; j--){
                    if(count % avgLen == 0){
                        double sum = 0;
                        for(int k = 0; k < avgLen; k++){
                            if(j-k >= 0) sum += energy[i][j-k];
                        }
                        sum /= avgLen;
                        avgEnergy[i][count/avgLen] = sum;
                    }
                    count++;
                }
            }
        }
        Log.e(TAG, "calculating decorrelation...");
        int nEnerBandsUsed = energy.length - 2;
        int nFramesUsed = avgEnergy[0].length - 2;
        Log.e(TAG, "size of shapedata/2: " + shapeData.size()/2);
        double[][][] ctxBands = new double[nFramesUsed][shapeData.size()/2][nEnerBandsUsed];
        double[][][] multAux = new double[nFramesUsed][shapeData.size()/2][nEnerBandsUsed];
        double[][] decorMat = new double[nFramesUsed][nEnerBandsUsed];
        double[] v = new double[9];
        for(int i= 0; i < nEnerBandsUsed; i++){
            for(int j= 0; j < nFramesUsed; j++){
                int mean = 0;
                for(int k = 0; k < shapeData.size()/2; k++) {
                    int ctxIdx = optData.get(k * nEnerBandsUsed + i) - 1;
                    int idy = j + 1 + shapeData.get(ctxIdx * 2 + 1);
                    int idx = i + 1 + shapeData.get(ctxIdx * 2);
                    ctxBands[j][k][i] = avgEnergy[idx][idy];
                    v[k] = ctxBands[j][k][i];
                    mean += v[k];
                }
                mean /= 9;
                for(int k = 0; k < 9; k++){
                    v[k] -= mean;

                }
                double[] x = Matrix.multiply(Matrix.transpose(tMatData[i]), v);
                for(int k= 0; k < shapeData.size()/2; k++){
                    multAux[j][k][i] = x[k];
                }
                decorMat[j][i] = multAux[j][0][i];
            }
        }
        Log.e(TAG, "calculating fingerprint...");
        finger = new byte[30][nFramesUsed];
        for(int i = 0; i < 30; i++){
            for(int j = 0; j < nFramesUsed; j++){
               // Log.e(TAG, "decorMat: " + String.valueOf(decorMat[j][i]));
                if(decorMat[j][i] > 0){
                    finger[i][j] = 1;
                }else finger[i][j] = 0;
               // Log.e(TAG, String.valueOf(finger[i][j]));
            }
        }
    }


    //START THREAD THAT HANDLES THE AUDIO RECORDING
    private static class RecordThread extends Thread {
        public void run() {
            calc = true;
            Timer timer = new Timer();
            long timeLeft = waitTime - time; //need to adjust if connected with laptop, reason unknown. Seems to depend on the connected phone, too
            time += timeLeft;
            Log.e(TAG, "Time left: " + timeLeft);
            if(timeLeft > 0 ) {
                try{
                    Log.e("REC", "Sleeping...");
                    Thread.sleep(timeLeft);
                }catch(InterruptedException e){
                    Log.e(TAG, "Can't sleep, error.");
                }
                try {
                    recorder.startRecording();
                    Log.e(TAG, "Started Recording");
                } catch (IllegalStateException e) {
                    Log.e(".RecordThread", "cannot start Recording");
                    return;
                }
            }
            else{
                try {
                    recorder.startRecording();
                    Log.e(TAG, "Started Recording");
                } catch (IllegalStateException e) {
                    Log.e(".RecordThread", "cannot start Recording");
                    return;
                }
            }
            timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                    if (null != recorder) {
                        Log.e(TAG, "Used samplerate: " + recorder.getSampleRate());
                        recorder.stop();
                        recorder.release();
                        recorder = null;
                        Log.e(TAG, "Stopped Recording");
                    }
                }
            }, SAMPLING_TIME);
            audioBuffer = new short[bufferSize];
            recorder.read(audioBuffer, 0, bufferSize);
            int count = 0;
            for (short i : audioBuffer) {//  p.println(audioBuffer[i]);
                if(i != 0) {
                    count++;
                }
                //    Log.e(TAG, String.valueOf(audioBuffer[i]));
            }
            Log.e(TAG,"Amount of non-zeros in buffer: " + count);
            Log.e("Audiorecording","Amount of data in buffer:" + bufferSize);
            calc = false;
        }
    }
}