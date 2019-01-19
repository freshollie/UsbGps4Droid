/*
 * Copyright (C) 2016 Oliver Bell
 * Copyright (C) 2010, 2011, 2012 Herbert von Broeuschmeul
 * Copyright (C) 2010, 2011, 2012 BluetoothGPS4Droid Project
 * Copyright (C) 2011, 2012 UsbGPS4Droid Project
 * 
 * This file is part of UsbGPS4Droid.
 *
 * UsbGPS4Droid is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * UsbGPS4Droid is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 *  You should have received a copy of the GNU General Public License
 *  along with UsbGPS4Droid. If not, see <http://www.gnu.org/licenses/>.
 */

package com.microntek.android.gps.usb.provider.driver;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.Toast;
import android.support.v4.app.NotificationCompat;

import com.microntek.android.gps.usb.provider.BuildConfig;
import com.microntek.android.gps.usb.provider.R;
import com.microntek.android.gps.usb.provider.ui.GpsInfoActivity;
import com.microntek.android.gps.usb.provider.ui.USBGpsSettingsFragment;

/**
 * A Service used to replace Android internal GPS with a USB GPS and/or write GPS NMEA data in a File.
 *
 * @author Herbert von Broeuschmeul &
 * @author Oliver Bell
 */
public class USBGpsProviderService extends Service implements USBGpsManager.UbxListener, LocationListener {

    public static final String ACTION_START_TRACK_RECORDING =
            "com.microntek.android.gps.usb.provider.action.START_TRACK_RECORDING";
    public static final String ACTION_STOP_TRACK_RECORDING =
            "com.microntek.android.gps.usb.provider.action.STOP_TRACK_RECORDING";
    public static final String ACTION_START_GPS_PROVIDER =
            "com.microntek.android.gps.usb.provider.action.START_GPS_PROVIDER";
    public static final String ACTION_STOP_GPS_PROVIDER =
            "com.microntek.android.gps.usb.provider.action.STOP_GPS_PROVIDER";
    public static final String ACTION_CONFIGURE_SIRF_GPS =
            "com.microntek.android.gps.usb.provider.action.CONFIGURE_SIRF_GPS";
    public static final String ACTION_ENABLE_SIRF_GPS =
            "com.microntek.android.gps.usb.provider.action.ENABLE_SIRF_GPS";

    public static final String PREF_START_GPS_PROVIDER = "startGps";
    public static final String PREF_START_ON_BOOT = "startOnBoot";
    public static final String PREF_START_ON_SCREEN_ON = "startOnScreenOn";
    public static final String PREF_START_DELAY = "startUpDelay";
    public static final String PREF_GPS_LOCATION_PROVIDER = "gpsLocationProviderKey";
    public static final String PREF_REPLACE_STD_GPS = "replaceStdtGps";
    public static final String PREF_FORCE_ENABLE_PROVIDER = "forceEnableProvider";
    public static final String PREF_MOCK_GPS_NAME = "mockGpsName";
    public static final String PREF_CONNECTION_RETRIES = "connectionRetries";
    public static final String PREF_TRACK_RECORDING = "trackRecording";
    public static final String PREF_TRACK_FILE_DIR = "trackFileDirectory";
    public static final String PREF_TRACK_FILE_PREFIX = "trackFilePrefix";
    public static final String PREF_GPS_DEVICE = "usbDevice";
    public static final String PREF_GPS_DEVICE_VENDOR_ID = "usbDeviceVendorId";
    public static final String PREF_GPS_DEVICE_PRODUCT_ID = "usbDeviceProductId";
    public static final String PREF_GPS_DEVICE_SPEED = "gpsDeviceSpeed";
    public static final String PREF_TOAST_LOGGING = "showToasts";
    public static final String PREF_SET_TIME = "setTime";
    public static final String PREF_ABOUT = "about";
    public static final String PREF_USE_HNR = "ubxHNR";
    public static final String PREF_USE_SPEED = "useSpeed";
    public static final String PREF_USE_ACCURACY = "useAccuracy";

    /**
     * Tag used for log messages
     */
    private static final String LOG_TAG = USBGpsProviderService.class.getSimpleName();

    public static final String PREF_SIRF_GPS = "sirfGps";
    public static final String PREF_SIRF_ENABLE_GGA = "enableGGA";
    public static final String PREF_SIRF_ENABLE_RMC = "enableRMC";
    public static final String PREF_SIRF_ENABLE_GLL = "enableGLL";
    public static final String PREF_SIRF_ENABLE_VTG = "enableVTG";
    public static final String PREF_SIRF_ENABLE_GSA = "enableGSA";
    public static final String PREF_SIRF_ENABLE_GSV = "enableGSV";
    public static final String PREF_SIRF_ENABLE_ZDA = "enableZDA";
    public static final String PREF_SIRF_ENABLE_SBAS = "enableSBAS";
    public static final String PREF_SIRF_ENABLE_NMEA = "enableNMEA";
    public static final String PREF_SIRF_ENABLE_STATIC_NAVIGATION = "enableStaticNavigation";

    public static final String PREF_UBX_HNR = "highNavRate";
    public static final String PREF_UBX_RESETODO = "resetOdo";

    private USBGpsManager gpsManager = null;
//    private PrintWriter writer;
    private FileOutputStream writer;
    private File trackFile;
    private boolean preludeWritten = false;

    private boolean debugToasts = false;

    //private NotificationManager mNoticeManager;
    //private Notification.Builder mNoticeBuilder;

    /**
     * Will start the service if set so in settings when the device boots
     */
    public static class BootReceiver extends BroadcastReceiver {
        static private long lastProcTime = 0;
        static private Object lock = new Object();

        @Override
        public void onReceive(final Context context, Intent intent) {
            SharedPreferences sharedPreferences =
                    PreferenceManager.getDefaultSharedPreferences(context);
            if (BuildConfig.DEBUG) Log.d(LOG_TAG, intent.getAction());

            if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED) &&
                    sharedPreferences.getBoolean(PREF_START_ON_BOOT, false) ||
                intent.getAction().equals(Intent.ACTION_SCREEN_ON) &&
                    sharedPreferences.getBoolean(PREF_START_ON_SCREEN_ON, false))  {

                // 直近60秒以内に処理していない場合のみ実行
                synchronized (lock) {
                    long time = System.currentTimeMillis();

                    if (time - lastProcTime > 60000) {

                        lastProcTime = time;
                        int delay = 0;
                        try {
                            delay = Integer.parseInt(sharedPreferences.getString(PREF_START_DELAY, "10000"));
                        }
                        catch (Exception e) {
                            delay = 10000;
                        }

//                        new Handler(context.getMainLooper()).postDelayed(new Runnable() {
//                            @Override
//                            public void run() {
//                                if (BuildConfig.DEBUG) Log.d(LOG_TAG, "Boot start");
//                                Intent intent2 = new Intent(context, USBGpsProviderService.class);
//                                intent2.setAction(ACTION_START_GPS_PROVIDER);
//                                context.startService(intent2);
//                            }
//                        }, delay);
                        // postDelayedはディレイ時間が長いと動作しない？
                        try {
                            Thread.sleep(delay);
                        } catch (InterruptedException e) {

                        }
                        if (BuildConfig.DEBUG) Log.d(LOG_TAG, "Boot start");
                        Intent intent2 = new Intent(context, USBGpsProviderService.class);
                        intent2.setAction(ACTION_START_GPS_PROVIDER);
                        context.startService(intent2);
                    }
                }
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor edit = sharedPreferences.edit();

        debugToasts = sharedPreferences.getBoolean(PREF_TOAST_LOGGING, false);

        int vendorId = sharedPreferences.getInt(PREF_GPS_DEVICE_VENDOR_ID,
                USBGpsSettingsFragment.DEFAULT_GPS_VENDOR_ID);
        int productId = sharedPreferences.getInt(PREF_GPS_DEVICE_PRODUCT_ID,
                USBGpsSettingsFragment.DEFAULT_GPS_PRODUCT_ID);

        int maxConRetries = Integer.parseInt(
                sharedPreferences.getString(
                        PREF_CONNECTION_RETRIES,
                        this.getString(R.string.defaultConnectionRetries)
                )
        );

        log("prefs device addr: " + vendorId + " - " + productId);

        if (ACTION_START_GPS_PROVIDER.equals(intent.getAction())) {
            if (gpsManager == null) {
                String mockProvider = LocationManager.GPS_PROVIDER;
                if (!sharedPreferences.getBoolean(PREF_REPLACE_STD_GPS, true)) {
                    mockProvider = sharedPreferences.getString(PREF_MOCK_GPS_NAME, getString(R.string.defaultMockGpsName));
                }
                gpsManager = new USBGpsManager(this, vendorId, productId, maxConRetries);
                boolean enabled = gpsManager.enable();

                if (sharedPreferences.getBoolean(PREF_START_GPS_PROVIDER, false) != enabled) {
                    edit.putBoolean(PREF_START_GPS_PROVIDER, enabled);
                    edit.apply();
                }

                if (enabled) {
                    gpsManager.enableMockLocationProvider(mockProvider);

                    PendingIntent launchIntent =
                            PendingIntent.getActivity(
                                    this,
                                    0,
                                    new Intent(this, GpsInfoActivity.class),
                                    PendingIntent.FLAG_CANCEL_CURRENT
                            );

                    sharedPreferences
                            .edit()
                            .putInt(
                                    getString(R.string.pref_disable_reason_key), 0
                            )
                            .apply();

                    Notification notification = new NotificationCompat.Builder(this)
                            .setContentIntent(launchIntent)
                            .setSmallIcon(R.drawable.ic_stat_notify)
                            .setAutoCancel(true)
                            .setContentTitle(getString(R.string.foreground_service_started_notification_title))
                            .setContentText(getString(R.string.foreground_gps_provider_started_notification))
                            .build();
//                    mNoticeBuilder = new Notification.Builder(this);
//
//                    mNoticeBuilder.setOngoing(true);
//                    mNoticeBuilder.setContentIntent(launchIntent);
//                    mNoticeBuilder.setSmallIcon(R.drawable.ic_stat_notify);
//                    mNoticeBuilder.setContentTitle(getString(R.string.foreground_service_started_notification_title));
//                    mNoticeBuilder.setContentText(getString(R.string.foreground_gps_provider_started_notification));
//
//                    mNoticeManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
//                    mNoticeManager.notify(0, mNoticeBuilder.build());

                    startForeground(R.string.foreground_gps_provider_started_notification, notification);

                    showToast(R.string.msg_gps_provider_started);

                    if (sharedPreferences.getBoolean(PREF_TRACK_RECORDING, false)) {
                        startTracking();
                    }
                } else {
                    stopSelf();
                }

//            } else {
//                // We received a start intent even though it's already running so restart
//                stopSelf();
//                startService(new Intent(this, USBGpsProviderService.class)
//                        .setAction(intent.getAction()));
            }

        } else if (ACTION_START_TRACK_RECORDING.equals(intent.getAction())) {
            startTracking();

        } else if (ACTION_STOP_TRACK_RECORDING.equals(intent.getAction())) {
            if (gpsManager != null) {
                gpsManager.removeNmeaListener(this);
                endTrack();
                showToast(this.getString(R.string.msg_nmea_recording_stopped));
            }
            if (sharedPreferences.getBoolean(PREF_TRACK_RECORDING, true)) {
                edit.putBoolean(PREF_TRACK_RECORDING, false);
                edit.commit();
            }

        } else if (ACTION_STOP_GPS_PROVIDER.equals(intent.getAction())) {
            if (sharedPreferences.getBoolean(PREF_START_GPS_PROVIDER, true)) {
                edit.putBoolean(PREF_START_GPS_PROVIDER, false);
                edit.commit();
            }
            stopSelf();

        } else if (ACTION_CONFIGURE_SIRF_GPS.equals(intent.getAction()) ||
                ACTION_ENABLE_SIRF_GPS.equals(intent.getAction())) {
            if (gpsManager != null) {
                Bundle extras = intent.getExtras();
                if (extras != null) {
                    gpsManager.enableSirfConfig(extras);
                } else {
                    gpsManager.enableSirfConfig(sharedPreferences);
                }
            }
        }
        return Service.START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        USBGpsManager manager = gpsManager;
        gpsManager = null;
        if (manager != null) {
            if (manager.getDisableReason() != 0) {
                showToast(getString(R.string.msg_gps_provider_stopped_by_problem, getString(manager.getDisableReason())));
            } else {
                showToast(R.string.msg_gps_provider_stopped);
            }
            manager.removeNmeaListener(this);
            manager.disableMockLocationProvider();
            manager.disable();
        }
        endTrack();
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor edit = sharedPreferences.edit();

        if (sharedPreferences.getBoolean(PREF_START_GPS_PROVIDER, true)) {
            edit.putBoolean(PREF_START_GPS_PROVIDER, false);
            edit.apply();
        }

        super.onDestroy();
    }

    /**
     * Checks if the applications has the given runtime permission
     */
    private boolean hasPermission(String perm) {
        return (
                PackageManager.PERMISSION_GRANTED ==
                        ContextCompat.checkSelfPermission(this, perm)
        );
    }

    private void startTracking() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor edit = sharedPreferences.edit();
        if (trackFile == null) {
            if (gpsManager != null) {
                if (hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    beginTrack();
                    gpsManager.addNmeaListener(this);
                    if (!sharedPreferences.getBoolean(PREF_TRACK_RECORDING, false)) {
                        edit.putBoolean(PREF_TRACK_RECORDING, true);
                        edit.apply();
                    }
                    showToast(R.string.msg_nmea_recording_started);

                } else {
                    Toast.makeText(this, "UsbGps logger - No storage permission", Toast.LENGTH_SHORT)
                            .show();

                    edit.putBoolean(PREF_TRACK_RECORDING, false)
                            .apply();
                }

            } else {
                endTrack();
            }

        } else {
            showToast(R.string.msg_nmea_recording_already_started);
        }
    }

    private void showToast(int messageId) {
        if (debugToasts) {
            Toast.makeText(this, messageId, Toast.LENGTH_SHORT).show();
        }
    }

    private void showToast(String message) {
        if (debugToasts) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        }
    }

    private void beginTrack() {
        @SuppressLint("SimpleDateFormat")
        SimpleDateFormat fmt = new SimpleDateFormat("_yyyy-MM-dd_HH-mm-ss'.ubx'");

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String trackDirName = sharedPreferences.getString(PREF_TRACK_FILE_DIR, this.getString(R.string.defaultTrackFileDirectory));
        String trackFilePrefix = sharedPreferences.getString(PREF_TRACK_FILE_PREFIX, this.getString(R.string.defaultTrackFilePrefix));
        trackFile = new File(trackDirName, trackFilePrefix + fmt.format(new Date()));
        log("Writing the prelude of the NMEA file: " + trackFile.getAbsolutePath());
        File trackDir = trackFile.getParentFile();
        try {
            if ((!trackDir.mkdirs()) && (!trackDir.isDirectory())) {
                Log.e(LOG_TAG, "Error while creating parent dir of NMEA file: " + trackDir.getAbsolutePath());
            }
//            writer = new PrintWriter(new BufferedWriter(new FileWriter(trackFile)));
            writer = new FileOutputStream(trackFile);
            preludeWritten = true;
        } catch (IOException e) {
            Log.e(LOG_TAG, "Error while writing the prelude of the NMEA file: " + trackFile.getAbsolutePath(), e);
            // there was an error while writing the prelude of the NMEA file, stopping the service...
            stopSelf();
        }
    }

    private void endTrack() {
        if (trackFile != null && writer != null) {
            log("Ending the NMEA file: " + trackFile.getAbsolutePath());
            preludeWritten = false;
            try {
                writer.close();
            }catch (IOException e) {
                log("IOException: " + trackFile.getAbsolutePath());
            }
            trackFile = null;
        }
    }

    private void addUbxLog(byte[] data) {
        if (!preludeWritten) {
            beginTrack();
        }
        //log("Adding data in the NMEA file: " + data);
        if (trackFile != null && writer != null) {
            try {
                writer.write(data);
            }catch (IOException e) {
                log("IOException: " + data);
            }
        }
    }

    /* (non-Javadoc)
     * @see android.app.Service#onBind(android.content.Intent)
     */
    @Override
    public IBinder onBind(Intent intent) {
        log("trying access IBinder");
        return null;
    }

    @Override
    public void onLocationChanged(Location location) {

    }

    @Override
    public void onProviderDisabled(String provider) {
        log("The GPS has been disabled.....stopping the NMEA tracker service.");
        stopSelf();
    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onUbxReceived(byte[] data) {
        addUbxLog(data);
    }

    private void log(String message) {
        if (BuildConfig.DEBUG) Log.d(LOG_TAG, message);
    }
}
