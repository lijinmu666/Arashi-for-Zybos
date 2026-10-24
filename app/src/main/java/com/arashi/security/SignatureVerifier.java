package com.arashi.security;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

public final class SignatureVerifier {
    private static final String TAG = "Arashi-Signature";
    private static final String ORIGINAL_CERT_SHA256 =
            "b3557b3bf7f02d6d50d457a9864d91cdc8d2c8516884bb26851271ef5d6e11f3";

    private SignatureVerifier() {
    }

    public static boolean isMismatched(Context context) {
        try {
            return !context.getPackageManager().hasSigningCertificate(
                    context.getPackageName(), originalCertDigest(),
                    PackageManager.CERT_INPUT_SHA256);
        } catch (RuntimeException error) {
            Log.w(TAG, "Unable to verify application signature", error);
            return false;
        }
    }

    private static byte[] originalCertDigest() {
        byte[] digest = new byte[ORIGINAL_CERT_SHA256.length() / 2];
        for (int index = 0; index < digest.length; index++) {
            int offset = index * 2;
            digest[index] = (byte) ((Character.digit(ORIGINAL_CERT_SHA256.charAt(offset), 16) << 4)
                    | Character.digit(ORIGINAL_CERT_SHA256.charAt(offset + 1), 16));
        }
        return digest;
    }
}
