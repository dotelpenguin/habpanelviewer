package de.vier_bier.habpanelviewer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import de.vier_bier.habpanelviewer.ssl.ConnectionUtil;

public class NetworkTracker extends BroadcastReceiver {
    private static final String TAG = "HPV-NetworkTracker";

    private final Context mCtx;
    private final ArrayList<INetworkListener> mListeners = new ArrayList<>();
    private boolean mConnected;
    private AtomicBoolean sslInitialized = new AtomicBoolean();

    public NetworkTracker(Context context) {
        mCtx = context;

        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                try {
                    ConnectionUtil.getInstance().setContext(mCtx);
                    Log.d(TAG, "SSL context initialized");
                } catch (GeneralSecurityException | IOException e) {
                    Log.e(TAG, "failed to initialize ConnectionUtil", e);
                    Toast.makeText(mCtx, R.string.sslFailed, Toast.LENGTH_LONG).show();
                } finally {
                    sslInitialized.set(true);
                    updateStatus();
                }

                return null;
            }
        }.execute();

        Log.d(TAG, "registering network receiver...");
        mCtx.registerReceiver(this, intentFilter);
    }

    public void addListener(INetworkListener l) {
        synchronized (mListeners) {
            mListeners.add(l);
        }
    }

    public void removeListener(INetworkListener l) {
        synchronized (mListeners) {
            mListeners.remove(l);
        }
    }

    public boolean isConnected() {
        return mConnected;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "network broadcast received");
        updateStatus();
    }

    private void updateStatus() {
        if (sslInitialized.get()) {
            ConnectivityManager cm = (ConnectivityManager) mCtx.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo activeNetwork = cm == null ? null : cm.getActiveNetworkInfo();

            if (activeNetwork != null && activeNetwork.isConnected()) {
                Log.d(TAG, "network is active: " + activeNetwork);

                if (!mConnected) {
                    synchronized (mListeners) {
                        mConnected = true;
                        Log.d(TAG, "notifying listeners of active network...");
                        for (INetworkListener listener : mListeners) {
                            listener.connected();
                        }
                    }
                }
            } else {
                Log.d(TAG, "network is NOT active: " + activeNetwork);

                if (mConnected) {
                    synchronized (mListeners) {
                        mConnected = false;
                        Log.d(TAG, "notifying listeners of inactive network...");
                        for (INetworkListener listener : mListeners) {
                            listener.disconnected();
                        }
                    }
                }
            }
        } else {
            Log.d(TAG, "Network state changed before initialization of SSL context!");
        }
    }

    public void terminate() {
        Log.d(TAG, "unregistering network receiver...");
        try {
            mCtx.unregisterReceiver(this);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "unregistering network receiver failed", e);
        }

        synchronized (mListeners) {
            mListeners.clear();
        }
    }

    public interface INetworkListener {
        void disconnected();

        void connected();
    }
}
