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

import static java.lang.System.exit;

@PluginFactory.LoadablePlugin
public class PingPlugin extends Plugin {

    private final static String PACKET_TYPE_PING = "kdeconnect.ping";
    int i = 1;
    String messageLapTop = "";
    String messagePhone = "";

    @Override
    public String getDisplayName() {
        return context.getResources().getString(R.string.pref_plugin_ping);
    }

    @Override
    public String getDescription() {
        return context.getResources().getString(R.string.pref_plugin_ping_desc);
    }

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
            Log.e("HelloPacketSender", message + " is the message");
            id = (int) System.currentTimeMillis();
            messageLapTop = message;


        } else {
            message = "Ping!";
            id = 42; //A unique id to create only one notification
        }
        if(i == 1) {
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
            NetworkPacket npp = new NetworkPacket(PACKET_TYPE_PING);
            npp.set("message", String.valueOf(System.currentTimeMillis()));
            device.sendPacket(npp);
            AudioRec.waitTime = System.currentTimeMillis() + 2000;
    /*
            if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.RECORD_AUDIO},
                        10);
                Log.e("audiorecord", "permission for microphone questioned");
            } else if(ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
                ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        10);
                Log.e("audiorecord", "permission for writing in external storage questioned");
            } else {
                Log.e("AudioRecord","Permission for microphone granted");*/

            AudioRec.Record();
            // message = AudioRec.getMessage();
            messagePhone = AudioRec.getMessage();
            AudioRec.flush();
        }
        /*else {
            int matchCount = 0;
            for (int i = 0; i < messageLapTop.length() - 1; i++) {
                //Log.e("hm", messageLapTop.charAt(i) + " vs " + messagePhone.charAt(i));
                if (messageLapTop.charAt(i) == messagePhone.charAt(i)) {
                    matchCount++;
                }
            }
            Log.e("hm", "Amount of matching points in fingerprints: " + String.valueOf(matchCount));
        }*/
        Log.e("hm","All done.");
        if(i == 1) i = 0;
       // else i = 1;
      //  messagePhone = "";
        return true;

    }

    @Override
    public String getActionName() {
        return context.getString(R.string.send_ping);
    }

    @Override
    public void startMainActivity(Activity activity) {
        if (device != null) {
            messagePhone += "x";
            if(i == 10) {
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
                    AudioRec.Record();
                }
            }
            //messagePhone += AudioRec.getMessage();
            NetworkPacket np = new NetworkPacket(PACKET_TYPE_PING);
            np.set("message", messagePhone);
            device.sendPacket(np);
            Log.e("HelloPacketSender", "message sent");
            AudioRec.flush();
            i = 1;
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
