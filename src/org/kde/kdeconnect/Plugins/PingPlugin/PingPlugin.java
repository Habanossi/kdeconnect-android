/*
 * Copyright 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of
 * the License or (at your option) version 3 or any later version
 * accepted by the membership of KDE e.V. (or its successor approved
 * by the membership of KDE e.V.), which shall act as a proxy
 * defined in Section 14 of version 3 of the license.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.kde.kdeconnect.Plugins.PingPlugin;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Environment;
import android.util.Log;

import org.kde.kdeconnect.Helpers.NotificationHelper;
import org.kde.kdeconnect.NetworkPacket;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect.Plugins.PluginFactory;
import org.kde.kdeconnect.UserInterface.MainActivity;
import org.kde.kdeconnect_tp.R;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import java.io.File;

@PluginFactory.LoadablePlugin
public class PingPlugin extends Plugin {
    //variables
    private final static String PACKET_TYPE_PING = "kdeconnect.ping";
    private String messagePhone = "";
    private static String TAG = "FingerPrint";
    private int tick = 1;
    private String match= "N00%";
    private String thisFp = "";
    private int matchcount = 0;
    private int calc = 0;
    private boolean first = false;

    @Override
    public String getDisplayName() {
        return context.getResources().getString(R.string.pref_plugin_ping);
    }

    @Override
    public String getDescription() {
        return context.getResources().getString(R.string.pref_plugin_ping_desc);
    }



    /*onPacketReceived handles receiving packets of data from other devices.
      depending on what stage of the loop the program is, it will either start recording and send the fingerprint back,
      or compare the fingerprints, send the match info and continue the loop.
    */
    @Override
    public boolean onPacketReceived(NetworkPacket np) {
        if (!np.getType().equals(PACKET_TYPE_PING)) {
            Log.e("PingPlugin", "Ping plugin should not receive packets other than pings!");
            return false;
        }
        //Log.e("PingPacketReceiver", "was a ping!");
        PendingIntent resultPendingIntent = PendingIntent.getActivity(
                context,
                0,
                new Intent(context, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT
        );
        int id;
        String message;
        if (np.has("message")) {
            message = np.getString("message");
            Log.e("PingPacketSender", message + " is the message");
            id = (int) System.currentTimeMillis();
        } else {
            message = "Ping!";
            id = 42; //A unique id to create only one notification
        }

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if(message.equals("first")){

            Notification noti = new NotificationCompat.Builder(context, NotificationHelper.Channels.DEFAULT)
                    .setContentTitle(device.getName())
                    .setContentText("first")
                    .setContentIntent(resultPendingIntent)
                    .setTicker("first")
                    .setSmallIcon(R.drawable.ic_notification)
                    .setAutoCancel(true)
                    .setDefaults(Notification.DEFAULT_ALL)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText("first"))
                    .setLights(Color.WHITE, 3000, 1000)
                    .build();
            NotificationHelper.notifyCompat(notificationManager, id, noti);
            sendFunction();
            first = true;
        }
        else if(tick == 1){
            tick = 0;
            String wait = message.substring(4);
            Notification noti = new NotificationCompat.Builder(context, NotificationHelper.Channels.DEFAULT)
                    .setContentTitle(device.getName())
                    .setContentText(message.substring(0,4))
                    .setContentIntent(resultPendingIntent)
                    .setTicker("Tick 1")
                    .setSmallIcon(R.drawable.ic_notification)
                    .setAutoCancel(true)
                    .setDefaults(Notification.DEFAULT_ALL)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText("Tick 1"))
                    .setLights(Color.WHITE, 3000, 1000)
                    .build();
            NotificationHelper.notifyCompat(notificationManager, id, noti);
            AudioRec.waitTime = Long.parseLong(wait);
            Log.e(TAG,"Waittime: " + (AudioRec.waitTime));
            AudioRec.Record();
            messagePhone = AudioRec.getMessage();
            thisFp = messagePhone;
            AudioRec.flush();
            Log.e("hm","All done.");
            sendFunction();
        }else if(tick == 0){
            Notification noti = new NotificationCompat.Builder(context, NotificationHelper.Channels.DEFAULT)
                    .setContentTitle(device.getName())
                    .setContentText("Received fingerprint")
                    .setContentIntent(resultPendingIntent)
                    .setTicker("Tick 0")
                    .setSmallIcon(R.drawable.ic_notification)
                    .setAutoCancel(true)
                    .setDefaults(Notification.DEFAULT_ALL)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText("Tick 0"))
                    .setLights(Color.WHITE, 3000, 1000)
                    .build();
            NotificationHelper.notifyCompat(notificationManager, id, noti);
            while(calc == 1){
                try{
                    Thread.sleep(1000);
                    Log.e(TAG, "waiting for own calculations before receiving fingerprint");
                }catch(InterruptedException e){
                    Log.e(TAG, "can't sleep");
                }
            }
            Log.e(TAG, "this fingerprint: " + thisFp);
            Log.e(TAG, "other fingerprint: " + message);
            for(int i = 0; i < message.length(); i++){
                if(thisFp.charAt(i) == message.charAt(i)){
                    matchcount++;
                }
            }
            Log.e(TAG, "matchcount: " + matchcount);
            if(matchcount > 330){
                Log.e(TAG, "secure");
                match = "Y" + (int)((matchcount/540.0)*100) + "%";
            }else{
                Log.e(TAG, "not secure");
                match = "N" + (int)((matchcount/540.0)*100) + "%";
            }
            matchcount = 0;
            tick = 1;
            Log.e(TAG, "Cycle complete.");
            sendFunction();
        }else{
            Notification noti = new NotificationCompat.Builder(context, NotificationHelper.Channels.DEFAULT)
                    .setContentTitle(device.getName())
                    .setContentText("Other notification?")
                    .setContentIntent(resultPendingIntent)
                    .setTicker("Other notification?")
                    .setSmallIcon(R.drawable.ic_notification)
                    .setAutoCancel(true)
                    .setDefaults(Notification.DEFAULT_ALL)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText("Other notification?"))
                    .setLights(Color.WHITE, 3000, 1000)
                    .build();
            NotificationHelper.notifyCompat(notificationManager, id, noti);
        }
        return true;
    }

    public void sendFunction(){
        if(tick == 1){
            tick = 0;
            calc = 1;
            long time = AudioRec.getCurrentNetworkTime();
            Log.e(TAG, "Current time, sendFunction, tick 1: " + time);
            /*long time = NetworkTime.time;
            Log.e(TAG,"getting time");
            while(time == 0){
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Log.e(TAG, "nosleep");
                }
                time = NetworkTime.time;
            }*/
            time += 3000;
            Log.e(TAG,"sending with tick = 1");
            NetworkPacket npp = new NetworkPacket(PACKET_TYPE_PING);
            npp.set("message", match + time);
            device.sendPacket(npp);
            AudioRec.waitTime = time;
            Log.e(TAG,"Waittime: " + (AudioRec.waitTime));
            AudioRec.Record();
            thisFp = AudioRec.getMessage();
            AudioRec.flush();
            calc = 0;
        }else if(tick == 0){
            Log.e(TAG,"sending with tick = 0");
            NetworkPacket npp = new NetworkPacket(PACKET_TYPE_PING);
            npp.set("message", messagePhone);
            device.sendPacket(npp);
            tick = 1;
        }
    }

    @Override
    public String getActionName() {
        return context.getString(R.string.send_ping);
    }

    @Override
    public void startMainActivity(Activity activity) {
        if (device != null) {
            if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.RECORD_AUDIO},
                        10);
                Log.e("audiorecord", "permission for microphone questioned");
            } else if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        10);
                Log.e("audiorecord", "permission for writing in external storage questioned");
            } else {
                Log.e("AudioRecord", "Permission for microphone granted");
                File file = new File(Environment.getExternalStorageDirectory().getAbsolutePath(), "MediaRecorderSample");

                if (!file.exists())
                    file.mkdirs();
            }
            //new NetworkTime().execute();
            if(tick != 1 || first){
                tick = 1;
                first = false;
                Log.e(TAG, "fixing variables");
            }else{
                NetworkPacket npp = new NetworkPacket(PACKET_TYPE_PING);
                npp.set("message", "first");
                device.sendPacket(npp);
                first = true;
            }
        }
    }

    @Override
    public boolean hasMainActivity() {
        return true;
    }

    @Override
    public boolean displayInContextMenu() {
        return true;
    }

    @Override
    public String[] getSupportedPacketTypes() {
        return new String[]{PACKET_TYPE_PING};
    }

    @Override
    public String[] getOutgoingPacketTypes() {
        return new String[]{PACKET_TYPE_PING};
    }
}