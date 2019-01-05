package com.microntek.android.gps.usb.provider;

import android.app.Application;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatDelegate;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;

/**
 * Created by freshollie on 15/05/17.
 */

public class USBGpsApplication extends Application {
    private static boolean locationAsked = true;

    private int LOG_SIZE = 100;

    private final ArrayList<ServiceDataListener> serviceDataListeners = new ArrayList<>();
    private Location lastLocation;
    private ArrayList<String> logLines = new ArrayList<>();
    private TreeMap<Integer, HashMap<String, String>> svInfo = new TreeMap<Integer, HashMap<String, String>>();

    private Handler mainHandler;

    static {

    }

    public interface ServiceDataListener {
        void onNewSentence(String sentence);
        void onNewSvInfo();
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


    public TreeMap<Integer, HashMap<String, String>> getSvInfo() {
        // TODO:非効率な気がする
        synchronized(this.svInfo) {
            TreeMap<Integer, HashMap<String, String>> ret = new TreeMap<Integer, HashMap<String, String>>();
            for(Integer key : this.svInfo.keySet())
              ret.put(key, (HashMap<String, String>)this.svInfo.get(key).clone());
            return ret;
        }
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

    public void notifySvInfo(final ArrayList<HashMap<String, String>> svInfo) {

         synchronized (this.svInfo) {
            Set<Integer> disableSv = new HashSet<Integer>(this.svInfo.keySet());

            for(HashMap<String, String> rec : svInfo) {
                Integer key = Integer.parseInt(rec.get("svId"));
                HashMap<String, String> val = null;

                if(this.svInfo.containsKey(key))
                    val = this.svInfo.get(key);
                else {
                    val = new HashMap<String, String>();
                    this.svInfo.put(key, val);
                    val.put("svName", rec.get("svName"));
                    val.put("icon", rec.get("icon"));
                    val.put("disableCnt", "0");
                }
                val.put("useFlag", rec.get("useFlag"));
                val.put("cno", rec.get("cno"));

                if(Integer.parseInt(rec.get("cno")) > 0) {
                    disableSv.remove(key);
                    val.put("disableCnt", "0");
                }
            }

            // 受信データに含まれていなかった物はクリアする
            for(Integer key : disableSv) {
                HashMap<String, String> val = this.svInfo.get(key);

                int i = Integer.parseInt(val.get("disableCnt"));

                val.put("useFlag", "");
                val.put("cno", "0");
                val.put("disableCnt", String.valueOf(++i));
            }

        }

        synchronized (serviceDataListeners) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    for (ServiceDataListener dataListener: serviceDataListeners) {
                        dataListener.onNewSvInfo();
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
