package org.kde.kdeconnect.Plugins.PingPlugin;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import java.io.IOException;

public class InitTrueTimeAsyncTask extends AsyncTask<Void, Void, Void> {
    private Context ctx;

    public InitTrueTimeAsyncTask (Context context){
        ctx = context;
    }

    protected Void doInBackground(Void... params) {
        try {
            TrueTime.build()
                    .withSharedPreferences(ctx)
                    .withNtpHost("fi.pool.ntp.org")
                    .withLoggingEnabled(false)
                    .withConnectionTimeout(31_428)
                    .initialize();
        } catch (IOException e) {
            e.printStackTrace();
            Log.d("truetime", "Exception when trying to get TrueTime", e);
        }
        return null;
    }
}