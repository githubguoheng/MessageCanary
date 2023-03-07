package com.messagequeue.messagecanary.monitor;

import android.os.Handler;
import android.os.HandlerThread;

final class HandlerThreadFactory {

    private static HandlerThreadWrapper sLoopThread = new HandlerThreadWrapper("loop");
    private static HandlerThreadWrapper sWriteLogThread = new HandlerThreadWrapper("writer");

    private HandlerThreadFactory() {
        throw new InstantiationError("Must not instantiate this class");
    }

    public static Handler getTimerThreadHandler() {
        return sLoopThread.getHandler();
    }

    public static HandlerThread getTimerHandlerThread() {
        return sLoopThread.getHandlerThread();
    }

    public static Handler getWriteLogThreadHandler() {
        return sWriteLogThread.getHandler();
    }

    private static class HandlerThreadWrapper {
        private Handler handler = null;
        private HandlerThread handlerThread = null;

        public HandlerThreadWrapper(String threadName) {
            handlerThread = new HandlerThread("MessagePlaback-" + threadName);
            handlerThread.start();
            handler = new Handler(handlerThread.getLooper());
        }

        public HandlerThread getHandlerThread() {
            return handlerThread;
        }

        public Handler getHandler() {
            return handler;
        }
    }
}
