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

package org.kde.kdeconnect.Plugins.HelloPlugin;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import org.kde.kdeconnect.Helpers.NotificationHelper;
import org.kde.kdeconnect.NetworkPacket;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect.Plugins.PluginFactory;
import org.kde.kdeconnect.UserInterface.MainActivity;
import org.kde.kdeconnect_tp.R;

import java.io.File;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

@PluginFactory.LoadablePlugin
public class HelloPlugin extends Plugin {

    private final static String PACKET_TYPE_HELLO = "kdeconnect.hello";
/*
    private org.kde.kdeconnect.Plugins.HelloPlugin.Timer mTimer = null;
    public AudioFingerprint mAudioFingerprint = null;
*/


    @Override
    public String getDisplayName() {
        return "Hello";//context.getResources().getString(R.string.pref_plugin_ping);
    }

    @Override
    public String getDescription() {
        return "Send hello to someone.";//context.getResources().getString(R.string.pref_plugin_ping_desc);
    }

    @Override
    public boolean onPacketReceived(NetworkPacket np) {

        if (!np.getType().equals(PACKET_TYPE_HELLO)) {
            Log.e("HelloPlugin", "Hello plugin should not receive packets other than Hello!");
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
            id = (int) System.currentTimeMillis();
        } else {
            message = "No fingerprint!";
            id = 42; //A unique id to create only one notification
        }

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        Notification noti = new NotificationCompat.Builder(context, NotificationHelper.Channels.DEFAULT)
                .setContentTitle(device.getName())
                .setContentText(message)
                .setContentIntent(resultPendingIntent)
                .setTicker(message)
                .setSmallIcon(R.drawable.ic_notification)
                .setAutoCancel(true)
                .setDefaults(Notification.DEFAULT_ALL)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .build();

        NotificationHelper.notifyCompat(notificationManager, id, noti);

        return true;

    }

    @Override
    public String getActionName() {
        return "Hello";
    }

    @Override
    public void startMainActivity(Activity activity) {
        if (device != null) {
            String message = "";

            if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.RECORD_AUDIO},
                        10);
                Log.e("audiorecord", "permission for microphone questioned");
            } else if(ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
                ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        10);
                Log.e("audiorecord", "permission for writing in external storage questioned");
            } else {
                Log.e("AudioRecord","Permission for microphone granted");
                File file = new File(Environment.getExternalStorageDirectory().getAbsolutePath(), "MediaRecorderSample");

               if (!file.exists())
                    file.mkdirs();

               //AudioRec.startRecording();
              // message = AudioRec.stopRecording();
                AudioRec.Record();
               // int s = 0;
               // AudioRec.setCalc(1);
                /*while(AudioRec.getCalc() == 1){
                    s++;
                    try {
                        Thread.sleep(1000);
                        Log.e("Hello", "sleep " + s);
                    }catch(InterruptedException e ){
                        Log.e("Hello","No Sleep");
                    }
                }*/

/*
                final MediaRecorder myAudioRecorder = new MediaRecorder();
                String outputFile;
                outputFile = file.getAbsolutePath() + "/.mp4";


                myAudioRecorder.setOutputFile(outputFile);
                AudioRec.record(myAudioRecorder);*/

               /* MediaPlayer mediaPlayer = new MediaPlayer();
                try {
                    mediaPlayer.setDataSource(outputFile);
                    mediaPlayer.prepare();
                    mediaPlayer.start();
                } catch (Exception e) {
                    // make something
                }*/
            }
            message = AudioRec.getMessage();
            NetworkPacket np = new NetworkPacket(PACKET_TYPE_HELLO);
            np.set("message", message);
            device.sendPacket(np);
            Log.e("HelloPacketSender", "message sent");


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
        return new String[]{PACKET_TYPE_HELLO};
    }

    @Override
    public String[] getOutgoingPacketTypes() {
        return new String[]{PACKET_TYPE_HELLO};
    }

}
