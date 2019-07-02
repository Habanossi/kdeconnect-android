package org.kde.kdeconnect.Plugins.HelloPlugin;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Random;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;

public class AudioFingerprint{

    // Debugging
    public static final String TAG = "AudioFingerprint";
    public static final boolean D = false;

    // Constants that indicate the current recording state
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_RECORDING = 1;

    // Constant for PairingManager to access
    public static int fingerprintBits = 0;

    // Default arguments
    public static final int DEFAULT_SAMPLING_TIME = 2125;//6375; //6375; //millisecond
    public static final int DEFAULT_DELAYED_TIME = 2000; //millisecond, waiting time before sampling
    public static final int DEFAULT_REDUNDANT_TIME = 100; //for each forward and backward
    public static final int DEFAULT_SAMPLING_RATE = 44100; //Hz
    public static final int DEFAULT_FRAME_LENGTH = 8192;//16384; //2^12, must be a power of 2
    public static final int DEFAULT_BAND_LENGTH = 700;//497; //Frequency band, result of dividing frame
    public static final int DEFAULT_MAX_FREQUENCY = 10000; //max frequency
    public static final int DEFAULT_MIN_FREQUENCY = 70; //min frequency

    // Constant for pattern sync
    public static final int NUMBER_OF_MATCHING_POSITIONS = 10;

    // Arguments for sampling audio
    private int sampling_time; //millisecond
    private int delayed_time; //millisecond, waiting time before sampling
    private int redundant_time; //for each forward and backward
    private int sampling_rate; //Hz
    private int nFrame; //number of time frames
    private int nBand; //number of frequency bands
    private int frame_length; //must be a power of 2 in order to be able to FFT
    private int band_length; //number of samples in a frequency band
    private int sample_start; // beginning position of the main sample
    private int sample_end; // end position (plus 1) of the main sample

    // Member fields
    private int mState;
    private int bufferSize; // in shorts
    private short[] audioBuffer = null;
    private long recordingTime = 0;
    private byte[] fingerprint = null; // the result of pattern-sync
    private int[] pattern_matching_pos = new int[NUMBER_OF_MATCHING_POSITIONS]; // millisecond
    private Random mRandom = new Random();
    private int[] random_matching_pos = new int[NUMBER_OF_MATCHING_POSITIONS];
    // Tool object
    private Context mContext;
    private Handler mHandler;
    private org.kde.kdeconnect.Plugins.HelloPlugin.Timer mTimer;
    private AudioRecord mAudioRecord = null;
    private RecordThread mRecordThread = null;
    private ComputingThread mComputingThread = null;
    //private MatchingPatternThread mMatchingPatternThread = null;
    private HashMap<String, byte[][]> mHashMap = null;

    // for Sync Pattern
    /*int[] pattern = {-17,-51,-102,-141,-158,-141,-96,-40,5,11,
               -12,-57,-113,-164,-237,-315,-377,-400,-383,-327,
               -282,-304,-372,-445,-462,-400,-299,-192,-147,-152,
               -175,-169,-124,-62,-29,-45,-85,-119,-119,-74,
               -34,-34,-85,-164,-225,-237,-192,-119,-51,-6,
                -12,-51,-113,-169,-214,-231,-203,-152,-96,-62,
                -74,-147,-242,-338,-400,-383,-293,-152,5,129,
                174,140,44,-85,-197,-265,-265,-214,-135,-57,
                -12,-12,-51,-119,-175,-220,-214,-158,-68,33,
                123,174,185,157,101,44,-6,-74,-130,-175};
        int pattern_length = 100;
*/
     public AudioFingerprint(Context context,/* Handler handler, */org.kde.kdeconnect.Plugins.HelloPlugin.Timer timer) {
         //mContext = context;
         //mHandler = handler;
         mTimer = timer;
         mState = STATE_NONE;
     }

     public long startSampling(long uptimeMillis) {
         if (uptimeMillis == 0)
             uptimeMillis = System.currentTimeMillis() + delayed_time - redundant_time;

         mRecordThread = new RecordThread(uptimeMillis);
         mRecordThread.start();
         return uptimeMillis;
     }

     public void cancel() {
         audioBuffer = null;
         mAudioRecord.release();
         mAudioRecord = null;
         setState(STATE_NONE);
         if (mRecordThread != null)
             mRecordThread = null;
     }

     public int getState() {
         return mState;
     }




     public void initialize() {
         // initialize arguments
         SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(mContext);
         sampling_time = Integer.parseInt(pref.getString("sampling_time", Integer.toString(DEFAULT_SAMPLING_TIME)));
         delayed_time = Integer.parseInt(pref.getString("delayed_time", Integer.toString(DEFAULT_DELAYED_TIME)));
         redundant_time = Integer.parseInt(pref.getString("redundant_time", Integer.toString(DEFAULT_REDUNDANT_TIME)));
         sampling_rate = Integer.parseInt(pref.getString("sampling_rate", Integer.toString(DEFAULT_SAMPLING_RATE)));
         frame_length = Integer.parseInt(pref.getString("frame_length", Integer.toString(DEFAULT_FRAME_LENGTH)));
         band_length = Integer.parseInt(pref.getString("band_length", Integer.toString(DEFAULT_BAND_LENGTH)));

         // initialize bufferSize
         int minSize = AudioRecord.getMinBufferSize(sampling_rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
         double nSample = sampling_rate * ((double) (sampling_time + 2 * redundant_time)) / 1000f;
         bufferSize = (int) Math.ceil(nSample);
         if (bufferSize < minSize)
             bufferSize = minSize;

         // nFrame, nBand
         nFrame = (int) Math.ceil(sampling_rate * sampling_time / 1000f / frame_length);
         nBand = (int) Math.ceil(((double) frame_length) / band_length);
         AudioFingerprint.fingerprintBits = (nFrame - 1) * (nBand - 1);

         // sample_start, sample_end
         sample_start = Math.round(sampling_rate * redundant_time / 1000f);
         sample_end = Math.round(sampling_rate * (redundant_time + sampling_time) / 1000f);
     }
     public int calculateHammingDistance(byte[] f, int shiftTime) {
         if (f.length != fingerprintBits) {
             Log.e(TAG, "Illegal number of bits in the finger-print");
             // postStatus(TAG + ": ERROR f.length != fingerprintBits");
             return -1;
         }
         byte[][] fp = getFingerprint(shiftTime);
         if (fp == null)
             return -1;
         int count = 0;
         for (int i=0; i<nFrame-1; i++)
             for (int j=0; j<nBand-1; j++)//
                 if (fp[i][j] != f[i*(nBand-1) + j]) count++;
                 return count;
     }
     public byte[][] getFingerprint(int shiftTime){
         return mHashMap.get(Integer.toString(shiftTime));
     }

     public void calculateFingerprint(int shiftTime) {
         if (mComputingThread != null) return;
         mComputingThread = new ComputingThread(shiftTime, shiftTime);
         mComputingThread.start();
     }

     public void calculateFPwithAllPossibleShiftTime() {
         int min = getMinShiftTime();
         int max = getMaxShiftTime();
         if (mComputingThread != null) return;
         mComputingThread = new ComputingThread(min, max);
         mComputingThread.start();
     }

    private void setState(int state) {
        mState = state;
        //setbackTitle(null);
    }

    // Return the minimum of possible shift time (millisecond)
    public int getMinShiftTime() {
        int shiftTime = 0;
        int shiftSample = Math.round(sampling_rate * ((float) shiftTime) / 1000f);
        int start = sample_start + shiftSample;
        while (start >= 0) {
            shiftTime--;
            shiftSample = Math.round(sampling_rate * ((float) shiftTime) / 1000f);
            start = sample_start + shiftSample;
        }
        return shiftTime + 1;
    }

    // Return the maximum of possible shift time (millisecond)
    public int getMaxShiftTime() {
        int shiftTime = 0;
        int shiftSample = Math.round(sampling_rate * ((float) shiftTime) / 1000f);
        int end = sample_end + shiftSample;
        while (end <= bufferSize) {
            shiftTime++;
            shiftSample = Math.round(sampling_rate * ((float) shiftTime) / 1000f);
            end = sample_end + shiftSample;
        }
        return shiftTime - 1;
    }
    public void saveRecordedData() {
        if (audioBuffer == null) {
            //postStatus("There are no recorded data to save.");
            Log.e(TAG,"There are no recorded data to save.");
            return ;
        }
        // Create file name
        String device_name = "testname";//((AdhocPairingActivity) mContext).mBluetoothAdapter.getName();
        device_name = device_name.replace(' ', '-');
        Date date = new Date(recordingTime);
        SimpleDateFormat sdf = new SimpleDateFormat("MMddHHmmss");
        String time_string = sdf.format(date);
        String args_string = Integer.toString(sampling_rate) + "_"
                + Integer.toString(sampling_time) + "_"
                + Integer.toString(redundant_time) + "_"
                + Integer.toString(frame_length) + "_"
                + Integer.toString(band_length);
        String filename = time_string + "_" + args_string + "_" + device_name + ".raw";
        String path = Environment.getExternalStorageDirectory().getAbsolutePath();
        // create folder if not exist!
        String path_filename = path + "/AdhocPairing/" + filename;

        try {

            // Using FileWriter
			/*FileWriter f = new FileWriter(path_filename);
			for (int i=0; i<bufferSize; i++) {
				//if (i%10 == 0)
					//f.write("\n");
				f.write(Short.toString(audioBuffer[i]) + ",");
			}
			f.flush();
			f.close();*/

            // Using FileOutputStream
            File file = new File(path_filename);
            FileOutputStream fos = new FileOutputStream(file);
            BufferedOutputStream bos = new BufferedOutputStream(fos);
            DataOutputStream dos = new DataOutputStream(bos);
            for (int i=0; i<bufferSize; i++) {
                dos.writeShort(audioBuffer[i]);
            }
            dos.flush();
            dos.close();

        } catch(IOException e) {
           // postStatus("Cannot open file to write.");
            return ;
        }
        //postStatus("Data saved successfully.\n" + filename);
    }

    public void setAtomicRecordingTime(long time) {
        this.recordingTime = time;
    }

    private class RecordThread extends Thread {
        private long uptimeMillis;

        public RecordThread(long uptimeMillis) {
            this.uptimeMillis = uptimeMillis;
        }

        public void run() {
            try {
                long sleepTime = uptimeMillis - System.currentTimeMillis();
                if (sleepTime < 0) {
                   // postStatus(TAG + ": sleep time < 0 -> continue without sleeping");
                    sleepTime = 0;
                }

                //setbackTitle("waiting...");
                Thread.sleep(sleepTime);

                setState(STATE_RECORDING);
                mAudioRecord.startRecording();
            } catch (InterruptedException e) {
                Log.e(TAG + ".Record", "sleep() interrupted");
               // postStatus(TAG + ".RecordThread: sleep() interrupted");
                setState(STATE_NONE);
                return;
            } catch (IllegalStateException e) {
                Log.e(TAG + ".Record", "cannot start Recording");
               // postStatus(TAG + ".RecordThread: cannot start Recording");
                setState(STATE_NONE);
                return;
            }
            audioBuffer = new short[bufferSize];
            int nShort = mAudioRecord.read(audioBuffer, 0, bufferSize);
           // postStatus(TAG + ".RecordThread: recorded " +
           //         Integer.toString(nShort) + " shorts out of desired " +
           //        Integer.toString(bufferSize) + " shorts");
            mAudioRecord.stop();
            setState(STATE_NONE);
        }
    }


    private class ComputingThread extends Thread {
         private int minShiftTime;
         private int maxShiftTime;
         private int shiftTime;

         public ComputingThread(int minShiftTime, int maxShiftTime) {
             this.minShiftTime = minShiftTime;
             this.maxShiftTime = maxShiftTime;
             this.shiftTime = minShiftTime;
         }

         public void run() {
             while (shiftTime <= maxShiftTime && getFingerprint(shiftTime)==null) {
                 // determine the start and end point in the audioBuffer array
                 int shiftSample = Math.round(sampling_rate * ((float) shiftTime) / 1000f);
                 int start = sample_start + shiftSample;
                 int end = sample_end + shiftSample;
                 if (start<0 || end>bufferSize) {
                     // postStatus(TAG + ": access out of recorded range, shiftTime=" + Integer.toString(shiftTime));
                     mComputingThread = null;
                     return ;
                 }

                 // copy the sampled audio sequence
                 int n = end - start;
                 short[] audioSequence = new short[n];
                 for (int i=0; i<n; i++)
                     audioSequence[i] = audioBuffer[i+start];

                 // for each frame, calculate FFT, then calculate energy
                 double[][] energy = new double[nFrame][nBand];
                 short[] frame = new short[frame_length];
                 for (int i=0; i<nFrame; i++) {
                     // Report the process
                     String audio_status = Integer.toString(i) + "/" + Integer.toString(nFrame);
                     if (maxShiftTime > minShiftTime)
                         audio_status += ", " + Integer.toString(shiftTime-minShiftTime+1) + "/" + Integer.toString(maxShiftTime-minShiftTime+1);
                     //  setbackTitle("FFT " + audio_status);

                     // pick the frame i-th
                     for (int j=0; j<frame_length; j++)
                         if (i*frame_length+j < n)
                             frame[j] = audioSequence[i*frame_length + j];
                         else
                             frame[j] = 0;

                         // apply windown function
                     for (int j=0; j<frame_length; j++)
                         frame[j] = (short) (FFT.window_hanning(j, frame_length) * (double) frame[j]);

                     // FFT the frame i-th
                     Complex[] complex = new Complex[frame_length];
                     for (int j=0; j<frame_length; j++)
                         complex[j] = new Complex(frame[j], 0f);
                     complex = FFT.fft(complex);

                     // calculate energy of each band
                     double e = 0;
                     for (int j=0; j<frame_length; j++)
                         if ( j>0 && j%band_length==0 ) {
                             energy[i][j/band_length-1] = e;
                             e = complex[j].abs();
                             if (j == frame_length-1)
                                 energy[i][j/band_length] = e;
                         } else if (j == frame_length-1) {
                             e += complex[j].abs();
                             energy[i][j/band_length] = e;
                         } else
                             e += complex[j].abs();
                 }
                 // calculate finger-print matrix
                 byte[][] finger = new byte[nFrame-1][nBand-1];
                 for (int i=1; i<nFrame; i++)
                     for (int j=0; j<nBand-1; j++)
                         if ( (energy[i][j]-energy[i][j+1]) -
                                 (energy[i-1][j]-energy[i-1][j+1]) > 0 )
                             finger[i-1][j] = 1;
                         else
                             finger[i-1][j] = 0;

                         // Ending the computing
                 mHashMap.put(Integer.toString(shiftTime), finger);
                 shiftTime++;
             }
             // setbackTitle(null);
             mComputingThread = null;
             //  finishCalculating(minShiftTime, maxShiftTime);
         }
     }
}