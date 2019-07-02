/*
 lisence template here
 */

package org.kde.kdeconnect.Plugins.HelloWorldPlugin;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.kde.kdeconnect.Helpers.NotificationHelper;
import org.kde.kdeconnect.NetworkPacket;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect.Plugins.PluginFactory;
import org.kde.kdeconnect.UserInterface.MainActivity;
import org.kde.kdeconnect_tp.R;

import androidx.core.app.NotificationCompat;

@PluginFactory.LoadablePlugin
public class HelloWorldPlugin extends Plugin {

    private final static String PACKET_TYPE_HELLOWORLD = "kdeconnect.helloworld";

    @Override
    public String getDisplayName() {
        return context.getResources().getString(R.string.pref_plugin_helloworld);
    }

    @Override
    public String getDescription() {
        return context.getResources().getString(R.string.pref_plugin_helloworld_desc);
    }

    @Override
    public boolean onPacketReceived(NetworkPacket np) {

        if (!np.getType().equals(PACKET_TYPE_HELLOWORLD)) {
            Log.e("HelloWorldPlugin", "HelloWorld plugin should not receive packets other than helloworlds!");
            return false;
        }

        //Log.e("PingPacketReceiver", "was a helloworld!");

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
            message = "Hello World!";
            id = 420; //A unique id to create only one notification
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
        return context.getString(R.string.send_ping);
    }

    @Override
    public void startMainActivity(Activity activity) {
        if (device != null) {
            device.sendPacket(new NetworkPacket(PACKET_TYPE_HELLOWORLD));
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
        return new String[]{PACKET_TYPE_HELLOWORLD};
    }

    @Override
    public String[] getOutgoingPacketTypes() {
        return new String[]{PACKET_TYPE_HELLOWORLD};
    }

}
