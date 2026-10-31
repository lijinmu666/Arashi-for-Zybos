// Adapted from Yuehong (MPL-2.0); see third_party/dirtyfrag/NOTICE.md.
package com.lijinmu.arashi.jailbreak;

/** Receives line-oriented output from the DirtyFrag VR native chain. */
public interface DirtyFragVrReporter {
    void report(String message);
}
