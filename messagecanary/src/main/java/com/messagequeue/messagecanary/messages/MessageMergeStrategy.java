package com.messagequeue.messagecanary.messages;

public interface MessageMergeStrategy {
    /**
     * @param messageCacheQueue  消息队列的要被聚合的消息
     * @param msgInfo      消息体的内容
     * @param duration     消息耗时
     * @return
     */
    public boolean merge(MessageCacheQueue messageCacheQueue, String msgInfo, long duration);
}
