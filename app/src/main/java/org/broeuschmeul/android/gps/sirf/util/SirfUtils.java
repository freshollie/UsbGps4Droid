/*
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

package org.broeuschmeul.android.gps.sirf.util;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Formatter;
import java.util.Locale;

/**
 * Utilities for generation of SIRF III binary command.
 * <p>
 * It can be used to generate a array of byte corresponding
 * to a SIRF III binary command expressed in an hexadecimal string.
 * <p>
 * The input can be a complete SIRF III command or just the payload.
 * In this last case the utility will add the start and end parts of the command
 * (including the size and the checksum).
 *
 * @author Herbert von Broeuschmeul
 */
public class SirfUtils {

    private static final String start = "A0A2";
    private static final String end = "B0B3";

    public static byte[] genSirfCommand(String commandHexa) {
        int length = commandHexa.length() / 2;
        ByteBuffer command = ByteBuffer.allocate(length);
        command.put(new BigInteger(commandHexa, 16).toByteArray(), 1, length);
        return command.array();
    }

    public static byte[] genSirfCommandFromPayload(String payload) {
        String commandHexa = createSirfCommandFromPayload(payload);
        return genSirfCommand(commandHexa);
    }

    public static String createSirfCommandFromPayload(String payload) {
        int length = payload.length() / 2;
        String res;

        byte[] command = new BigInteger(payload, 16).toByteArray();

        int checkSum = 0;
        for (byte b : command) {
            checkSum += (b & 0xff);
        }
        checkSum &= 0x7FFF;

        res = String.format((Locale) null, "%s%04X%s%04X%s", start, length, payload, checkSum, end);
        return res;
    }

    public static String showSirfCommandFromPayload(String payload) {
        byte[] command = genSirfCommandFromPayload(payload);
        StringBuilder out = new StringBuilder(payload.length() + 16);
        Formatter fmt = new Formatter(out, null);
        for (byte b : command) {
            fmt.format("%02X", b);
        }
        return out.toString();
    }
}
