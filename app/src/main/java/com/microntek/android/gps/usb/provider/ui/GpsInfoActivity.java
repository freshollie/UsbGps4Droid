package com.microntek.android.gps.usb.provider.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.widget.SwitchCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.microntek.android.gps.ubx.data.UbxData;
import com.microntek.android.gps.ubx.util.UbxParser;
import com.microntek.android.gps.usb.provider.BuildConfig;
import com.microntek.android.gps.usb.provider.USBGpsApplication;
import com.microntek.android.gps.usb.provider.R;
import com.microntek.android.gps.usb.provider.driver.USBGpsProviderService;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.TreeMap;

import static java.lang.Integer.parseInt;

/**
 * Created by Oliver Bell 5/12/15
 *
 * This activity displays a log, as well as the GPS info. If the users device is
 * large enough and in landscape, the settings fragment will be shown alongside
 */

public class GpsInfoActivity extends USBGpsBaseActivity implements
        USBGpsApplication.ServiceDataListener {

    private SharedPreferences sharedPreferences;
    private static final String TAG = GpsInfoActivity.class.getSimpleName();

    private USBGpsApplication application;

    private SwitchCompat startSwitch;
    private TextView numSatellites;
    private TextView accuracyText;
    private TextView locationText;
    private TextView elevationText;
    private TextView fixText;
    private TextView slasText;
    private TextView sensorText;
    private TextView courseText;
    private TextView odoText;

    //private TextView logText;
    private TextView timeText;
    // ScrollView logTextScroller;

    private SimpleDateFormat sdf = null;

    private LinearLayout svinfoLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (isDoublePanel()) {
            savedInstanceState = null;
        }
        super.onCreate(savedInstanceState);

        if (isDoublePanel()) {
            setContentView(R.layout.activity_info_double);
        } else {
            setContentView(R.layout.activity_info);
        }

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        application = (USBGpsApplication) getApplication();

        setupUI();

        if (isDoublePanel()) {
            showSettingsFragment(R.id.settings_holder, false);
        }
    }

    private void setupUI() {
        if (!isDoublePanel()) {
            startSwitch = (SwitchCompat) findViewById(R.id.service_start_switch);
            startSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    sharedPreferences
                            .edit()
                            .putBoolean(USBGpsProviderService.PREF_START_GPS_PROVIDER, isChecked)
                            .apply();
                }
            });
        }

        numSatellites = (TextView) findViewById(R.id.num_satellites_text);
        accuracyText = (TextView) findViewById(R.id.accuracy_text);
        locationText = (TextView) findViewById(R.id.location_text);
        elevationText = (TextView) findViewById(R.id.elevation_text);
        timeText = (TextView) findViewById(R.id.gps_time_text);

        fixText = (TextView) findViewById(R.id.fix_status_text);
        slasText = (TextView) findViewById(R.id.slas_text);
        sensorText = (TextView) findViewById(R.id.sensor_text);
        courseText = (TextView) findViewById(R.id.course_text);
        odoText = (TextView) findViewById(R.id.odo_text);

        svinfoLayout = (LinearLayout) findViewById(R.id.svinfo_layout);

        sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US);
//        logText = (TextView) findViewById(R.id.log_box);
//        logTextScroller = (ScrollView) findViewById(R.id.log_box_scroller);
    }

    private boolean isDoublePanel() {
        return false;
//        return (getResources().getConfiguration().screenLayout
//                & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE &&
//                getResources()
//                        .getConfiguration()
//                        .orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    private void updateData() {
        boolean running =
                sharedPreferences.getBoolean(USBGpsProviderService.PREF_START_GPS_PROVIDER, false);

        if (!isDoublePanel()) {
            startSwitch.setChecked(
                    running
            );
        }

        String accuracyValue = "N/A";
        String numSatellitesValue = "N/A";
        String slasStatus = "N/A";
        String fusionStatus = "N/A";
        String fixValue = "N/A";
        String odoValue1 = "N/A";
        String odoValue2 = "N/A";
        String lat = "N/A";
        String lon = "N/A";
        String elevation = "N/A";
        String course = "N/A";
        String gpsTime = "N/A";
        String systemTime = "N/A";

        Location location = application.getLastLocation();
        if (!running) {
            location = null;
        }

        if (location != null) {
            accuracyValue = String.format("%1$.3fm", location.getAccuracy());
            if (location.getExtras() != null) {
                numSatellitesValue = String.valueOf(location.getExtras().getInt(UbxData.SATELLITE_KEY));
                switch (location.getExtras().getInt(UbxData.FIX_STATUS_KEY)) {
                    case 1: fixValue = "DR only"; break;
                    case 2: fixValue = "2D-Fix"; break;
                    case 3: fixValue = "3D-Fix"; break;
                    case 4: fixValue = "3D-Fix+DR"; break;
                    case 5: fixValue = "Time only"; break;
                }
                int sbas = location.getExtras().getInt(UbxData.SBAS_STATUS_KEY);
                if(sbas > 0)
                    fixValue += "/DGNSS";
                int slas = location.getExtras().getInt(UbxData.SLAS_STATUS_KEY, -1);
                if(slas > 0)
                  slasStatus = "QS" + String.valueOf(slas);

                int fusion = location.getExtras().getInt(UbxData.FUSION_STATUS_KEY, -1);
                switch (fusion) {
                    case 0: fusionStatus = "Initialization"; break;
                    case 1: fusionStatus = "Fusion"; break;
                    case 2: fusionStatus = "Suspended"; break;
                    case 3: fusionStatus = "Disabled"; break;
                }

                int odo1 = location.getExtras().getInt(UbxData.DISTANCE1_STATUS_KEY);
                if(odo1 > 0) {
                    double val = (double) (odo1 / 1000.0);
                    odoValue1 = String.format("%1$.1f", val);
                }

                int odo2 = location.getExtras().getInt(UbxData.DISTANCE2_STATUS_KEY);
                if(odo2 > 0) {
                    double val = (double) (odo2 / 1000.0);
                    odoValue2 = String.format("%1$.1f", val);
                }

            }
            lat = String.format("%1$.5f", location.getLatitude());
            lon = String.format("%1$.5f", location.getLongitude());
            elevation = String.format("%1$.3fm", location.getAltitude());
            course = String.format("%1$.3f°", location.getBearing());

            gpsTime = sdf.format(new Date(location.getTime()));

            systemTime = sdf.format(new Date(location.getExtras().getLong(UbxParser.SYSTEM_TIME_FIX)));
        }

        numSatellites.setText(getString(R.string.number_of_satellites_placeholder, numSatellitesValue));
        accuracyText.setText(getString(R.string.accuracy_placeholder, accuracyValue));
        locationText.setText(getString(R.string.location_placeholder, lat, lon));
        elevationText.setText(getString(R.string.elevation_placeholder, elevation));
        timeText.setText(getString(R.string.gps_time_placeholder, gpsTime, systemTime));

        fixText.setText(getString(R.string.fix_status_placeholder, fixValue));
        slasText.setText(getString(R.string.slas_placeholder, slasStatus));
        sensorText.setText(getString(R.string.sensor_placeholder, fusionStatus));
        courseText.setText(getString(R.string.course_placeholder, course));
        odoText.setText(getString(R.string.odo_placeholder, odoValue1, odoValue2));
        //updateSvInfo();
    }

//    public void updateLog() {
//
//        boolean atBottom = (
//                logText.getBottom() - (
//                        logTextScroller.getHeight() +
//                                logTextScroller.getScrollY()
//                )
//        ) == 0;
//
//        logText.setText(TextUtils.join("\n", application.getLogLines()));
//
//        if (atBottom) {
//            logText.post(new Runnable() {
//                @Override
//                public void run() {
//                    logTextScroller.fullScroll(View.FOCUS_DOWN);
//                }
//            });
//        }
//    }

    // 衛星の情報を反映する
    public void updateSvInfo() {

        TreeMap<Integer, HashMap<String, String>> svInfo = application.getSvInfo();
        View prevView = null;

        for (Integer key : svInfo.keySet()) {
            HashMap<String, String> rec = svInfo.get(key);

            int cno = Integer.parseInt(rec.get("cno"));
            boolean hidden = Integer.parseInt(rec.get("disableCnt")) > 9;
            View view = svinfoLayout.findViewById(key);

            // 一定期間受信できなかったものは非表示
            if(hidden) {
                if(view != null) {
                    //svinfoLayout.removeView(view);
                    view.setVisibility(View.GONE);
                    prevView = view;
                }
                continue;
            }
            // 初回のみ生成
            if(view == null) {
                // 受信レベル0の場合は生成しない
                if(cno == 0)
                    continue;
                view = getLayoutInflater().inflate(R.layout.svinfo_row, null);
                view.setId(key);

                // SvNo順に表示するので途中に挿入
                if(prevView != null) {
                    int prevIndex = svinfoLayout.indexOfChild(prevView);
                    svinfoLayout.addView(view, prevIndex + 1);
                }
                else
                    svinfoLayout.addView(view, 0);

                ImageView icon = (ImageView) view.findViewById(R.id.icon);
                icon.setImageResource(Integer.parseInt(rec.get("icon")));
                TextView svName = (TextView) view.findViewById(R.id.svName);
                svName.setText(rec.get("svName"));
                ProgressBar bar = (ProgressBar) view.findViewById(R.id.bar);
                //bar.setMax(50);
            }
            view.setVisibility(View.VISIBLE);
            TextView cnoView = (TextView) view.findViewById(R.id.cno);
            cnoView.setText(rec.get("cno"));
            ProgressBar bar = (ProgressBar) view.findViewById(R.id.bar);
            bar.setProgress(cno);

            if(rec.get("useFlag").equals("1"))
                bar.getProgressDrawable().setColorFilter(Color.GREEN, android.graphics.PorterDuff.Mode.SRC_IN);
            else
                bar.getProgressDrawable().setColorFilter(Color.BLUE, android.graphics.PorterDuff.Mode.SRC_IN);
            prevView = view;
        }
    }

    @Override
    public void onResume() {
        updateData();
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);
        ((USBGpsApplication) getApplication()).registerServiceDataListener(this);
        super.onResume();
    }

    @Override
    public void onPause() {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
        ((USBGpsApplication) getApplication()).unregisterServiceDataListener(this);
        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (!isDoublePanel()) {
            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.menu_main, menu);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onNewSentence(String sentence) {
//        updateLog();
        updateSvInfo();
    }

    @Override
    public void onNewSvInfo() {
        updateSvInfo();
    }

    @Override
    public void onLocationNotified(Location location) {
        updateData();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(USBGpsProviderService.PREF_START_GPS_PROVIDER)) {
            updateData();
        }

        super.onSharedPreferenceChanged(sharedPreferences, key);
    }
}
