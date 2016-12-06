package org.broeuschmeul.android.gps.usb.provider;

import android.app.Activity;
import android.os.Bundle;

/**
 * Preferences activity was deprecated and so now we make a preferences
 * fragment and make an activity to display that fragment.
 * <p>
 * All this activity does is display the fragment, the fragment handles
 * everything else.
 * <p>
 * Created by Oliver Bell 5/12/15
 */

public class USBGpsActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Display the fragment as the main content.
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new USBGpsSettingsFragment())
                .commit();
    }
}
