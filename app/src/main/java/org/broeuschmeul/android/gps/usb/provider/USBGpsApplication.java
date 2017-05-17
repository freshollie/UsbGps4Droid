package org.broeuschmeul.android.gps.usb.provider;

import android.app.Application;
import android.location.Location;
import android.os.Handler;
import android.os.SystemClock;

import java.util.ArrayList;

/**
 * Created by freshollie on 15/05/17.
 */

public class USBGpsApplication extends Application {
    private static boolean locationAsked = true;

    private int LOG_SIZE = 100;

    private final ArrayList<ServiceDataListener> serviceDataListeners = new ArrayList<>();
    private Location lastLocation;
    private ArrayList<String> sentenceLog = new ArrayList<>();

    private Handler mainHandler;

    public interface ServiceDataListener {
        void onNewSentence(String sentence);
        void onLocationNotified(Location location);
    }

    @Override
    public void onCreate() {
        locationAsked = false;
        mainHandler = new Handler(getMainLooper());
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

    public String[] getSentenceLog() {
        return sentenceLog.toArray(new String[sentenceLog.size()]);
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
        if (sentenceLog.size() > LOG_SIZE) {
            sentenceLog.remove(0);
        }

        final String newSentence = System.currentTimeMillis() + ": " + sentence;
        sentenceLog.add(newSentence);

        synchronized (serviceDataListeners) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    for (ServiceDataListener dataListener: serviceDataListeners) {
                        dataListener.onNewSentence(newSentence);
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
