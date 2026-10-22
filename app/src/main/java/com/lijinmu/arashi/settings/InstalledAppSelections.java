package com.lijinmu.arashi.settings;

import android.content.pm.PackageManager;
import java.util.HashSet;
import java.util.Set;

/** Only a confirmed package lookup miss may discard a stored app selection. */
public final class InstalledAppSelections {
    private InstalledAppSelections() {}

    public static Set<String> findMissing(PackageManager manager, Set<String> selected,
                                          boolean prefixed) {
        Set<String> missing = new HashSet<>();
        for (String key : selected) {
            if (prefixed && !key.startsWith("pkg:")) continue; // UID entries are not packages.
            String packageName = prefixed ? key.substring(4) : key;
            try {
                manager.getApplicationInfo(packageName, 0);
            } catch (PackageManager.NameNotFoundException absent) {
                missing.add(key);
            } catch (RuntimeException unavailable) {
                // A permission or transient Binder failure is not an uninstall.
            }
        }
        return missing;
    }
}
