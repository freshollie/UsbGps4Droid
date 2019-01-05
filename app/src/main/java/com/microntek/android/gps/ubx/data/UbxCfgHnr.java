package com.microntek.android.gps.ubx.data;

import android.location.Location;
import android.os.Bundle;

public class UbxCfgHnr extends UbxData {

    private static final String LOG_TAG = UbxCfgHnr.class.getSimpleName();

    private static final byte[] base =
            {(byte) 0xB5, (byte) 0x62, (byte) 0x06, (byte) 0x5C // header
                    , (byte) 0x04, (byte) 0x00 // len
                    , (byte) 0xFF, (byte) 0x00, (byte) 0x00, (byte) 0x00 // payload
                    , (byte) 0xFF, (byte) 0xFF}; // checksum

    public UbxCfgHnr(byte[] data) {
        super(data);
    }

    public UbxCfgHnr(int rate) {
        byte[] data = (byte[])base.clone();
        data[6] = (byte)(rate & 0xFF); // highNavRate

        int[] chk = calcCheckSum(data, 2);
        data[10] = (byte)chk[0];
        data[11] = (byte)chk[1];
        this.data = data;
    }

    @Override
    public boolean parse(Location fix) {
        return false;
    }

    @Override
    public long getITow() {
        return -1;
    }
}
