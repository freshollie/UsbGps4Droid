/**
 * 
 */
package org.broeuschmeul.android.gps.nmea.util;

/**
 * @author Herbert von Broeuschmeul
 *
 */
public interface NmeaListener {
	abstract void onNmeaReceived(long timestamp, String nmea);
}
