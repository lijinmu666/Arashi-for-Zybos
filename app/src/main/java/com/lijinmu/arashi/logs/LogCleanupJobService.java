package com.lijinmu.arashi.logs;

import android.app.job.JobParameters;
import android.app.job.JobService;

/** Persisted periodic maintenance also runs when the UI has been closed. */
public final class LogCleanupJobService extends JobService {
    public static final int JOB_ID = 0x41524c;

    @Override public boolean onStartJob(JobParameters params) {
        Thread worker = new Thread(() -> {
            try { RuntimeLogStore.prune(getApplicationContext()); }
            catch (RuntimeException ignored) { /* Retry on the next read/write or scheduled run. */ }
            finally { jobFinished(params, false); }
        }, "Arashi-LogCleanup");
        worker.start();
        return true;
    }

    @Override public boolean onStopJob(JobParameters params) { return true; }
}
