package com.microntek.android.gps.ubx.data;

import android.location.Location;
import android.util.Log;

import com.microntek.android.gps.usb.provider.BuildConfig;

// UBXバイナリの抽象クラス
public abstract class UbxData {
    private static final String LOG_TAG = UbxData.class.getSimpleName();
    protected byte[] data = null;

    public static final int IDX_HEADER = 0; // 2byte
    public static final int IDX_CLASS = 2;  // 1byte
    public static final int IDX_ID = 3;  // 1byte
    public static final int IDX_LEN = 4;  // 2byte

    public static final String SATELLITE_KEY = "satellites";
    public static final String SYSTEM_TIME_FIX = "system_time_fix";
    public static final String FIX_STATUS_KEY = "fix_status";
    public static final String SBAS_STATUS_KEY = "sbas_status";
    public static final String SLAS_STATUS_KEY = "slas_status";
    public static final String FUSION_STATUS_KEY = "fusion_status";
    public static final String DISTANCE1_STATUS_KEY = "distance1_status";
    public static final String DISTANCE2_STATUS_KEY = "distance2_status";

    UbxData() {

    }
    UbxData(byte[] data) {
        this.data = data;
    }

    public abstract long getITow();

    public boolean parse(Location fix) {
        return false;
    }

    // byteは符号付なので16進数のリテラルと比較しやすいように0～255に変換する
    public static long byte2hex(byte[] ubx, int start, int len) {
        long ret = 0;
        int mul = 1;
        for(int i = 0; i < len; i++) {
            if(i > 0)
                mul *= 256;
            ret += (ubx[start+i] & 0xFF) * mul;
        }
        return ret;
    }

    // チェックサムを計算
    public static int[] calcCheckSum(byte[] ubx, int start) {
        int ck_a = 0;
        int ck_b = 0;

        // ペイロード部分のサイズを計算
        long len = UbxData.byte2hex(ubx, start + 2, 2);

        // CLASS ~ ペイロードまでを対象にチェックサムを算出
        for(int i = start; i < 6 + len; i++){
            int hex2 = ubx[i];
            hex2 &= 0xFF;
            ck_a += hex2;
            ck_a &= 0xFF;
            ck_b += ck_a;
            ck_b &= 0xFF;
        }
        return new int[] {ck_a, ck_b};
    }

    public byte[] getData() {
        return this.data;
    }

    public void logMsg() {
        log("UbxData class:" + data[IDX_CLASS] + " id:" + data[IDX_ID]);
    }

    protected void log(String message) {
        if (BuildConfig.DEBUG) Log.d(LOG_TAG, message);
    }

    protected void logError(String message, Exception e) {
        if (BuildConfig.DEBUG) Log.e(LOG_TAG, message, e);
    }
}
