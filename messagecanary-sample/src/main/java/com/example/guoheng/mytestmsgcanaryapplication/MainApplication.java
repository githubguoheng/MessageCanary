package com.example.guoheng.mytestmsgcanaryapplication;

import android.app.Application;
import android.os.Looper;
import android.util.Log;

import com.github.anrwatchdog.ANRError;
import com.github.anrwatchdog.ANRWatchDog;
import com.messagequeue.messagecanary.MessageCanary;
import com.messagequeue.messagecanary.MessageCanaryContext;
import com.messagequeue.messagecanary.messages.MessageCacheQueue;
import com.messagequeue.messagecanary.messages.MessageFilter;
import com.messagequeue.messagecanary.monitor.AbstractSampler;
import com.messagequeue.messagecanary.monitor.BlockListener;
import com.messagequeue.messagecanary.monitor.StackSampler;

import java.util.ArrayList;
import java.util.LinkedList;

public class MainApplication extends Application {
    private static final String TAG = "MSG_MONITER";

    @Override
    public void onCreate() {
        super.onCreate();

        MessageCanary.getInstance().setContext(getApplicationContext());

        MessageCanary.getInstance().addSampler(new StackSampler(Looper.getMainLooper().getThread(), MessageCanaryContext.getSampleDelay()));

        MessageCanary.getInstance().setBlockListener(new BlockListener() {
            @Override
            public void onBlockEvent(long startTime, long endTime, long startThreadTime, long endThreadTime, ArrayList<AbstractSampler> samplers) {

                for (AbstractSampler sampler : samplers) {
                    if (sampler instanceof StackSampler) {
                        Log.e(TAG, "onBlockEvent " + ((StackSampler) sampler).getResult((long) 10000));

                    }
                }
            }

        });

        MessageCanary.getInstance().setMessageFilter(new MessageFilter() {
            @Override
            public boolean filter(String msg, long cost) {
                if (msg.contains("com.github.anrwatchdog.ANRWatchDog")) {
                    return true;
                }
                return false;
            }
        });

        MessageCanary.getInstance().startMonitor();

        new ANRWatchDog().setANRListener(new ANRWatchDog.ANRListener() {
            @Override
            public void onAppNotResponding(ANRError error) {

                Log.e(TAG,"ANR Happened #########");

                LinkedList<MessageCacheQueue.MainLooperMessagePack> msgs =  MessageCanary.getInstance().getDumpedMessage();

                Log.e(TAG,"==================================");
                for (MessageCacheQueue.MainLooperMessagePack msg : msgs) {
                    Log.e(TAG, msg.toString());
                }
            }



        }).start();
    }
}
