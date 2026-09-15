package com.zoeykl.simpleboard;

import android.os.Bundle;

public final class PrivacySettingsActivity extends SettingsPageActivity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        initPage("Privacy & behavior", "SimpleBoard is intentionally local-first.");
        section("Network");
        text("SimpleBoard itself does not request android.permission.INTERNET. Keyboard input, learned words, explicit correction pairs, shortcuts and themes remain on-device.");
        section("Voice typing");
        text("Voice recognition is delegated to the recognition service you select. That separate provider may use its own network connection and privacy policy; SimpleBoard only passes the recognition request through Android's speech APIs.");
        section("Backups");
        text("Android application backup is disabled for SimpleBoard, so its local keyboard data is not silently copied into the platform backup service.");
    }
}
