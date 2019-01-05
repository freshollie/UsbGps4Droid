package com.microntek.android.gps.ubx.data;

import android.content.Context;
import android.location.Location;
import android.os.Bundle;

import com.microntek.android.gps.usb.provider.R;
import com.microntek.android.gps.usb.provider.USBGpsApplication;

import java.util.ArrayList;
import java.util.HashMap;

public class UbxNavSvInfo extends UbxData {

    private static final String LOG_TAG = UbxNavSvInfo.class.getSimpleName();
    Context context = null;

    public UbxNavSvInfo(byte[] data, Context context) {
        super(data);
        this.context = context;
    }

    @Override
    public boolean parse(Location fix) {
        int idx = IDX_LEN + 2 + 4; // numCh Offset=4
        long numCh = byte2hex(data, idx, 1);

        //StringBuilder sb = new StringBuilder();
        ArrayList<HashMap<String,String>> svInfoList = new ArrayList<HashMap<String,String>>();

        for (int i = 0; i < numCh; i++) {
            HashMap<String, String> svInfo = new HashMap<String, String>();
            svInfoList.add(svInfo);
            //if (i > 0)
            //    sb.append("/");

            idx = IDX_LEN + 2 + 9 + 12 * i; // svid Offset=9 + 12 * i
            long svid = byte2hex(data, idx, 1);
            svInfo.put("svId", String.valueOf(svid));
            svInfo.put("svName", svidToSvName((int) svid));
            svInfo.put("icon", svidToIcon((int) svid));

            idx = IDX_LEN + 2 + 10 + 12 * i; // cno flags=10 + 12 * i
            long flags = data[idx] & 0x01;
            svInfo.put("useFlag", String.valueOf(flags));

            idx = IDX_LEN + 2 + 12 + 12 * i; // cno Offset=12 + 12 * i
            long cno = byte2hex(data, idx, 1);
            svInfo.put("cno", String.valueOf(cno));

            //sb.append(svidToSvName((int) svid)).append("=").append(String.format("%02d", cno)).append("db").append(flags == 1 ? "[use]" : "");

        }
        // 各衛星の測位状態の表示
        ((USBGpsApplication) context).notifySvInfo(svInfoList);

        return true;
    }

    @Override
    public long getITow() {
        int idx = IDX_LEN + 2 + 0; // iTOW Offset=0
        long time = byte2hex(data, idx, 4);
        return time;
    }

    // UBX SVIDをSV名に変換
    private String svidToSvName(int svid) {
        String svName = "";
//        if(svid >= 1 && svid <= 32)
//            svName = "G" + svid;
//        else if(svid >= 120 && svid <= 158)
//            svName = "S" + svid;
//        else if(svid >= 211 && svid <= 246)
//            svName = "E" + (svid - 210);
//        else if(svid >= 159 && svid <= 163)
//            svName = "B" + (svid - 158);
//        else if(svid >= 33 && svid <= 64)
//            svName = "B" + (svid - 27);
//        else if(svid >= 193 && svid <= 197)
//            svName = "Q" + (svid - 192);
//        else if(svid >= 65 && svid <= 96)
//            svName = "R" + (svid - 64);
        if(svid >= 1 && svid <= 32)
            svName = String.valueOf(svid);
        else if(svid >= 120 && svid <= 158)
            svName = String.valueOf(svid);
        else if(svid >= 211 && svid <= 246)
            svName = String.valueOf(svid - 210);
        else if(svid >= 159 && svid <= 163)
            svName = String.valueOf(svid - 158);
        else if(svid >= 33 && svid <= 64)
            svName = String.valueOf(svid - 27);
        else if(svid >= 193 && svid <= 197)
            svName = String.valueOf(svid - 192);
        else if(svid >= 65 && svid <= 96)
            svName = String.valueOf(svid - 64);
        return svName;
    }

    // UBX SVIDをアイコンに変換
    private String svidToIcon(int svid) {
        String icon = "";
        if(svid >= 1 && svid <= 32)
            icon = String.valueOf(R.drawable.gps);
        else if(svid >= 120 && svid <= 158)
            icon = String.valueOf(R.drawable.gps); // SBASはとりあえずGPSと同じアイコンにしておく
        else if(svid >= 211 && svid <= 246)
            icon = String.valueOf(R.drawable.galileo);
        else if(svid >= 159 && svid <= 163)
            icon = String.valueOf(R.drawable.beidou);
        else if(svid >= 33 && svid <= 64)
            icon = String.valueOf(R.drawable.beidou);
        else if(svid >= 193 && svid <= 197)
            icon = String.valueOf(R.drawable.qzss);
        else if(svid >= 65 && svid <= 96)
            icon = String.valueOf(R.drawable.glonass);
        return icon;
    }
}
