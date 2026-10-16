package com.nexbytes.bolap3.data;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Process-wide executors. Use these instead of creating executors per screen or per adapter:
 * {@link #db()} is single-threaded so database writes keep their order, {@link #io()} is for
 * file reads (album art, tags, lyrics).
 */
public final class AppExecutors {

    private static final ExecutorService DB = Executors.newSingleThreadExecutor(named("bolap3-db"));
    private static final ExecutorService IO = Executors.newFixedThreadPool(3, named("bolap3-io"));
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private AppExecutors() {
    }

    public static ExecutorService db() {
        return DB;
    }

    public static ExecutorService io() {
        return IO;
    }

    public static void main(Runnable runnable) {
        MAIN.post(runnable);
    }

    private static ThreadFactory named(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + counter.incrementAndGet());
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            return thread;
        };
    }
}
