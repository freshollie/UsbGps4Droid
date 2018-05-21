package org.broeuschmeul.android.gps.usb.provider;

import android.app.Application;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Handler;
import org.broeuschmeul.android.gps.nmea.util.USBGpsSatellite;

import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatDelegate;

import java.util.ArrayList;

/**
 * Created by freshollie on 15/05/17.
 */

public class USBGpsApplication extends Application {
    private static boolean locationAsked = true;

    private int MAX_LOG_SIZE = 100;

    private final ArrayList<UsbGpsDataListener> dataListeners = new ArrayList<>();
    private Location lastLocation;
    private ArrayList<String> nmeaSentenceLog = new ArrayList<>();

    private USBGpsSatellite[] lastSatelliteList = new USBGpsSatellite[0];

    private Handler mainHandler;

    public interface UsbGpsDataListener {
        void onNmeaReceived(String sentence);
        void onLocationNotified(Location location);
        void onSatellitesUpdated(USBGpsSatellite[] satellites);
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
        for (int i = 0; i < MAX_LOG_SIZE; i++) {
            nmeaSentenceLog.add("");
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

    public String[] getNmeaSentenceLog() {
        return nmeaSentenceLog.toArray(new String[nmeaSentenceLog.size()]);
    }

    public USBGpsSatellite[] getLastSatelliteList() {
        return lastSatelliteList;
    }

    public Location getLastLocation() {
        return lastLocation;
    }

    public void registerServiceDataListener(UsbGpsDataListener listener) {
        dataListeners.add(listener);
    }

    public void unregisterServiceDataListener(UsbGpsDataListener listener) {
        dataListeners.remove(listener);
    }

    public void notifyNewSentence(final String sentence) {
        if (nmeaSentenceLog.size() > MAX_LOG_SIZE) {
            nmeaSentenceLog.remove(0);
        }

        nmeaSentenceLog.add(sentence);

        synchronized (dataListeners) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    for (UsbGpsDataListener dataListener: dataListeners) {
                        dataListener.onNmeaReceived(sentence);
                    }
                }
            });
        }
    }

    public void notifyNewLocation(final Location location) {
        lastLocation = location;
        synchronized (dataListeners) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    for (UsbGpsDataListener dataListener: dataListeners) {
                        dataListener.onLocationNotified(location);
                    }
                }
            });

        }
    }

    public void notifySatellitesUpdated(final USBGpsSatellite[] satellites) {
        lastSatelliteList = satellites;
        synchronized (dataListeners) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    for (UsbGpsDataListener dataListener: dataListeners) {
                        dataListener.onSatellitesUpdated(satellites.clone());
                    }
                }
            });
        }
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
    }
}
