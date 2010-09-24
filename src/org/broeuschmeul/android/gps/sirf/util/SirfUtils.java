package org.broeuschmeul.android.gps.sirf.util;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Formatter;
import java.util.Locale;

public class SirfUtils {
	
	private static final String start ="A0A2";
	private static final String end ="B0B3";

	public static byte[] genSirfCommand(String commandHexa){
		int length = commandHexa.length()/2;		
		ByteBuffer command = ByteBuffer.allocate(length);
		command.put(new BigInteger(commandHexa,16).toByteArray(), 1,length);
		return command.array();
	}
	
	public static byte[] genSirfCommandFromPayload(String payload){
		String commandHexa = createSirfCommandFromPayload(payload);
		return genSirfCommand(commandHexa);
	}
	
	public static String createSirfCommandFromPayload(String payload){
		int length = payload.length()/2;
		String res;
		
		byte[] command = new BigInteger(payload,16).toByteArray();
		
		int checkSum = 0;
		for (byte b : command){
			checkSum += (b & 0xff);
		}
		checkSum &= 0x7FFF;
		
		res = String.format((Locale)null, "%s%04X%s%04X%s", start, length, payload, checkSum, end);
		return res;
	}
	
	public static String showSirfCommandFromPayload(String payload){
		byte[] command = genSirfCommandFromPayload(payload);
		StringBuilder out = new StringBuilder(payload.length()+16);
		Formatter fmt = new Formatter(out, null);
		for (byte b : command){
			fmt.format("%02X", b);
		}
		return out.toString();
	}
}
