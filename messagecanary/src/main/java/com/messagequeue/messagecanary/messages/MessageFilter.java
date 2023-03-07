package com.messagequeue.messagecanary.messages;

public interface MessageFilter {
    public boolean filter(String msg, long cost);
}
