package com.messagequeue.messagecanary.messages;

import org.jetbrains.annotations.Nullable;

/**
 * 只在主线程调用
 */
public class MessageManager{
    private MessageCacheQueue messageCacheQueue;

    private static class LazyHolder {

        private static MessageManager instance = new MessageManager();
    }
    private MessageManager() {
        messageCacheQueue = new MessageCacheQueue();
    }

    public static MessageManager getInstance() {
        return LazyHolder.instance;
    }

    /**
     * 只能在主线程调用
     * @param msg
     * @param duration
     * @param messageMergeStrategy
     * @throws RuntimeException
     */
    public void add(String msg, long duration, @Nullable MessageMergeStrategy messageMergeStrategy, @Nullable MessageFilter filter) throws RuntimeException {
        messageCacheQueue.add(msg, duration, messageMergeStrategy, filter);
    }

    public void setDispatchingMsg(String msg, long cost) {
        messageCacheQueue.setDispatchingMsg(msg, cost);
    }

    public MessageCacheQueue getMessageCacheQueue() {
        return messageCacheQueue;
    }
}
