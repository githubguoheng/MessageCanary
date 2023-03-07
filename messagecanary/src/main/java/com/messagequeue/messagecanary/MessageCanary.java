package com.messagequeue.messagecanary;

import android.content.Context;
import android.os.Looper;

import com.messagequeue.messagecanary.messages.MessageCacheQueue;
import com.messagequeue.messagecanary.messages.MessageFilter;
import com.messagequeue.messagecanary.messages.MessageManager;
import com.messagequeue.messagecanary.messages.MessageMergeStrategy;
import com.messagequeue.messagecanary.monitor.AbstractSampler;
import com.messagequeue.messagecanary.monitor.BlockListener;
import com.messagequeue.messagecanary.monitor.LooperMonitor;

import java.util.LinkedList;

public class MessageCanary {
    private static MessageCanary sInstance;
    private LooperMonitor monitor;

    public void setContext(Context context) {
        MessageCanaryContext.setContext(context);
    }

    public static MessageCanary getInstance() {
        if (sInstance == null) {
            synchronized (MessageCanary.class) {
                if (sInstance == null) {
                    sInstance = new MessageCanary();
                }
            }
        }

        return sInstance;
    }

    private MessageCanary() {
        monitor = new LooperMonitor();
    }

    /**
     * 卡顿检测
     * @param blockListener
     */
    public void setBlockListener(BlockListener blockListener) {
        if (monitor != null) {
            monitor.setBlockListener(blockListener);
        }
    }

    /**
     * 消息过滤器，满足条件时消息将被过滤掉
     * @param filter
     */
    public void setMessageFilter(MessageFilter filter) {
        if (monitor != null) {
            this.monitor.setFilter(filter);
        }
    }

    /**
     * 自定义消息合并策略，满足条件时消息将被合并
     * @param strategy
     */
    public void setMessageMergeStrategy(MessageMergeStrategy strategy) {
        if (monitor != null) {
            this.monitor.setMessageMergeStrategy(strategy);
        }
    }

    /**
     * 添加数据采集器，用于处理在消息分发过程中的一些数据采集，采集起始时间为主线程消息开始分发，结束时间为消息分发结束
     * @param sampler
     */
    public void addSampler(AbstractSampler sampler) {
        if (sampler != null) {
            if (monitor != null) {
                monitor.addSampler(sampler);
            }
        }
    }


    /**
     * 只能在主线程使用，一般在Application的oncreate中调用
     */
    public void startMonitor() {

        if (Looper.myLooper() == Looper.getMainLooper()) {
            Looper.getMainLooper().setMessageLogging(monitor);
        }
    }

    /**
     * 在程序结束&退出前调用
     */
    public void stopMonitor() {
        if (monitor != null) {
            monitor.stopDump();
            monitor = null;
        }
        Looper.getMainLooper().setMessageLogging(null);
    }


    /**
     * 当发生ANR时开始dump历史消息
     */
    private void startDump() {
        if (monitor != null) {
            monitor.startDump();
        }
    }

    public LinkedList<MessageCacheQueue.MainLooperMessagePack> getDumpedMessage() {
        startDump();

        LinkedList<MessageCacheQueue.MainLooperMessagePack> ret = new LinkedList<>();

        MessageCacheQueue cacheQueue = mainLooperMessageCacheQueue();

        MessageCacheQueue.MainLooperMessagePack first = cacheQueue.getFirst();
        MessageCacheQueue.MainLooperMessagePack last = cacheQueue.getLast();

        if (first != null ) {
            MessageCacheQueue.MainLooperMessagePack current = first;

            while (current != last) {
                ret.add(current);
                current = current.next;
            }

            ret.add(current);
        }

        ret.add(cacheQueue.getDispatchingMsg());
        ret.addAll(getPendingMessages());

        return ret;
    }


    /**
     * 通过消息队列获取历史调度消息，包括历史调度消息、当前调度消息以及Pending的消息
     *
     * @return
     */
    private MessageCacheQueue mainLooperMessageCacheQueue() {
        return MessageManager.getInstance().getMessageCacheQueue();
    }


    /**
     * 获取阻塞的消息
     * @return
     */
    private LinkedList<MessageCacheQueue.MainLooperMessagePack> getPendingMessages() {
        if (monitor != null) {
            return monitor.getPendingMessages();
        } else {
            return null;
        }
    }

}
