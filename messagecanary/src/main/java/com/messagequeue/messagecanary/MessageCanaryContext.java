package com.messagequeue.messagecanary;

import android.content.Context;

public class MessageCanaryContext {
    private static final int DEFAULT_SAMPLE_DELAY = 5000;

    private static Context sContext;
    private static int sampleyDelay = -1;


    public static void setContext(Context context) {
        sContext = context;
    }

    public static Context getContext() {
        return sContext;
    }


    public static void setSampleDelay(int delay) {
        if (delay > 0) {
            sampleyDelay = delay;
        }
    }

    public static long getSampleDelay() {
        if (sampleyDelay > 0) {
            return sampleyDelay;
        } else {
            return DEFAULT_SAMPLE_DELAY;
        }
    }
}
