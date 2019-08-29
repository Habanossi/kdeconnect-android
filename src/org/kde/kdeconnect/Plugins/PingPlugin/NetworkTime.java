/*
THIS CLASS IS NOT USED FOR NOW
FUNCTIONALITY: GETS THE TIME FROM NTPSERVER (SAME AS AUDIOREC.GETCURRENTNETWORKTIME()), BUT WORKS ON ANOTHER THREAD.
*/
package org.kde.kdeconnect.Plugins.PingPlugin;

import android.os.AsyncTask;
import android.util.Log;

import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.TimeInfo;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;

import static org.kde.kdeconnect.Plugins.PingPlugin.AudioRec.TAG;

public class NetworkTime extends AsyncTask<String, Void, Long> {
    public static long time = 0;
    public static boolean isOn = true;
    protected Long doInBackground(String... params) {
        String TIME_SERVER = ".fi.pool.ntp.org";
        NTPUDPClient timeClient = new NTPUDPClient();
        timeClient.setDefaultTimeout(100);
        int e = 0;
        int f = 0;
        int k = 0;
        while(isOn) {
            while (e == 0 && f < 5) {
                f += 1;
                try {
                    String g = k + TIME_SERVER;
                    InetAddress inetAddress = InetAddress.getByName(g);
                    k += 1;
                    try {
                        timeClient.open();
                        TimeInfo timeInfo = timeClient.getTime(inetAddress);
                        long returnTime = timeInfo.getMessage().getTransmitTimeStamp().getTime();   //server time
                        Date date = new Date(returnTime);
                        Log.d(TAG, "Time from " + g + ": " + date);
                        time = returnTime;
                        e = 1;
                    } catch (IOException I1) {
                        Log.e(TAG, "Can't get response from server.");
                        time = System.currentTimeMillis();
                    }
                } catch (UnknownHostException u1) {
                    Log.e(TAG, "Unknown Host");
                    time = System.currentTimeMillis();
                }
            }
        }
        timeClient.close();
        return time;
    }
    protected void onProgressUpdate(){

    }
    protected void onPostExecute(Long val) {
        // TODO: check this.exception
        // TODO: do something with the feed
    }
}

