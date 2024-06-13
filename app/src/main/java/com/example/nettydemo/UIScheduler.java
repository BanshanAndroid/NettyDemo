package com.example.nettydemo;

import android.os.Handler;
import android.os.Looper;

public class UIScheduler {

    private UIScheduler() {}

    private static final UIScheduler sUIScheduler = new UIScheduler();

    private final Handler handler = new Handler(Looper.getMainLooper());

    public static UIScheduler getUIScheduler() {
        return sUIScheduler;
    }

    public void postRunnable(Runnable runnable) {
        handler.post(runnable);
    }


    public void postRunnableDelayed(Runnable runnable, long delay) {
        handler.postDelayed(runnable, delay);
    }

    public void removeRunnable(Runnable runnable) {
        handler.removeCallbacks(runnable);
    }
}
