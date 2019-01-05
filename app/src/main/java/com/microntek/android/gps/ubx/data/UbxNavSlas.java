package com.microntek.android.gps.ubx.data;

import android.location.Location;
import android.os.Bundle;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

public class UbxNavSlas extends UbxData {

    private static final String LOG_TAG = UbxNavSlas.class.getSimpleName();

    public UbxNavSlas(byte[] data) {
        super(data);
    }

    @Override
    public boolean parse(Location fix) {
        int idx = IDX_LEN + 2 + 17; // qzssSvId Offset=17
        long qzssSatNo = byte2hex(data, idx, 1);

        idx = IDX_LEN + 2 + 18; // serviceFlags Offset=18
        long serviceFlg = byte2hex(data, idx, 1); // 3の場合に補正に使用

        idx = IDX_LEN + 2 + 19; // cnt Offset=19
        long correctCnt = byte2hex(data, idx, 1); // 補正対した衛星数

        Bundle bundle = fix.getExtras();

        // 1つも補正してない場合は0
        if(!(qzssSatNo > 0 && serviceFlg == 3 && correctCnt > 0))
            qzssSatNo = 0;

        bundle.putInt(SLAS_STATUS_KEY, (int) qzssSatNo);
        fix.setExtras(bundle);

        return true;
    }

    @Override
    public long getITow() {
        int idx = IDX_LEN + 2 + 0; // iTOW Offset=0
        long time = byte2hex(data, idx, 4);
        return time;
    }
}
