package com.gsajith.sonosvolume;

import android.app.Activity;
import android.os.Bundle;

/**
 * Invisible entry point. Saves the Beam's IP if one is passed, then starts the service:
 *   adb shell am start -n com.gsajith.sonosvolume/.MainActivity --es ip 192.168.1.50
 * Opening it from the launcher just restarts the service with the saved IP.
 */
public class MainActivity extends Activity {
    static final String PREFS = "settings";
    static final String KEY_IP = "sonos_ip";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String ip = getIntent().getStringExtra("ip");
        if (ip != null) {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_IP, ip.trim()).apply();
        }
        VolumeOverlayService.start(this);
        finish();
    }
}
