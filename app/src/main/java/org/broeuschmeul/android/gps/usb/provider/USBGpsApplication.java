package org.broeuschmeul.android.gps.usb.provider;

import android.app.Application;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatDelegate;

import java.util.ArrayList;

/**
 * Created by freshollie on 15/05/17.
 */

public class USBGpsApplication extends Application {
    private static boolean locationAsked = true;

    private int LOG_SIZE = 100;

    private final ArrayList<ServiceDataListener> serviceDataListeners = new ArrayList<>();
    private Location lastLocation;
    private ArrayList<String> logLines = new ArrayList<>();

    private Handler mainHandler;

    static {

    }

    public interface ServiceDataListener {
        void onNewSentence(String sentence);
        void onLocationNotified(Location location);
    }

    private void setupDaynightMode() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean on = preferences.getBoolean(getString(R.string.pref_daynight_theme_key), false);

        AppCompatDelegate.setDefaultNightMode(on ?
                        AppCompatDelegate.MODE_NIGHT_AUTO:
                        AppCompatDelegate.MODE_NIGHT_YES
        );
    }

    @Override
    public void onCreate() {
        setupDaynightMode();
        locationAsked = false;
        mainHandler = new Handler(getMainLooper());
        for (int i = 0; i < LOG_SIZE; i++) {
            logLines.add("");
        }
        super.onCreate();
    }


    public static void setLocationAsked() {
        locationAsked = true;
    }

    public static boolean wasLocationAsked() {
        return locationAsked;
    }

    public static void setLocationNotAsked() {
        locationAsked = false;
    }

    public String[] getLogLines() {
        return logLines.toArray(new String[logLines.size()]);
    }

    public Location getLastLocation() {
        return lastLocation;
    }

    public void registerServiceDataListener(ServiceDataListener listener) {
        serviceDataListeners.add(listener);
    }

    public void unregisterServiceDataListener(ServiceDataListener listener) {
        serviceDataListeners.remove(listener);
    }

    public void notifyNewSentence(final String sentence) {
        if (logLines.size() > LOG_SIZE) {
            logLines.remove(0);
        }

        logLines.add(sentence);

        synchronized (serviceDataListeners) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    for (ServiceDataListener dataListener: serviceDataListeners) {
                        dataListener.onNewSentence(sentence);
                    }
                }
            });

        }
    }

    public void notifyNewLocation(final Location location) {
        lastLocation = location;
        synchronized (serviceDataListeners) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    for (ServiceDataListener dataListener: serviceDataListeners) {
                        dataListener.onLocationNotified(location);
                    }
                }
            });

        }
    }
}
