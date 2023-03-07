package com.messagequeue.messagecanary.monitor;

import android.os.Looper;
import android.os.Message;
import android.os.MessageQueue;
import android.os.SystemClock;
import android.util.Printer;

import com.messagequeue.messagecanary.messages.MessageCacheQueue;
import com.messagequeue.messagecanary.messages.MessageFilter;
import com.messagequeue.messagecanary.messages.MessageManager;
import com.messagequeue.messagecanary.messages.MessageMergeStrategy;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedList;

public class LooperMonitor implements Printer {

    private static final int DEFAULT_BLOCK_THRESHOLD_MILLIS = 500;

    private long mBlockThresholdMillis = DEFAULT_BLOCK_THRESHOLD_MILLIS;
    private long mStartTimestamp = 0;
    private long mStartThreadTimestamp = 0;
    private BlockListener mBlockListener = null;
    private boolean mDispatchingStarted = false;

    private String mDispatchingMsg;

    private Field MESSAGE_QUEUE_MESSAGES_FIELD;
    private Field MESSAGE_NEXT_FIELD;

    LinkedList<MessageCacheQueue.MainLooperMessagePack> pendingMessages = new LinkedList<>();

    private ArrayList<AbstractSampler> samplers = new ArrayList<>();
    private MessageMergeStrategy messageMergeStrategy;
    private MessageFilter filter;

    public LooperMonitor() {

        try {
            MESSAGE_QUEUE_MESSAGES_FIELD = MessageQueue.class.getDeclaredField("mMessages");
            MESSAGE_QUEUE_MESSAGES_FIELD.setAccessible(true);

            MESSAGE_NEXT_FIELD = Message.class.getDeclaredField("next");
            MESSAGE_NEXT_FIELD.setAccessible(true);

        } catch (NoSuchFieldException e) {
            throw new RuntimeException("Failed to initialize TestLooper", e);
        }
    }

    public void setBlockThresholdMillis(long mBlockThresholdMillis) {
        this.mBlockThresholdMillis = mBlockThresholdMillis;
    }

    public void setBlockListener(BlockListener blockListener) {
        this.mBlockListener = blockListener;
    }

    public void setMessageMergeStrategy(MessageMergeStrategy messageMergeStrategy) {
        this.messageMergeStrategy = messageMergeStrategy;
    }

    public void setFilter(MessageFilter filter) {
        this.filter = filter;
    }

    public void addSampler(AbstractSampler sampler) {
        if (sampler != null && !samplers.contains(sampler)) {
            samplers.add(sampler);
        }
    }

    public void removeSampler(AbstractSampler sampler) {
        if (sampler != null && samplers.contains(sampler)) {
            samplers.remove(sampler);
        }
    }

    @Override
    public void println(String msg) {
        if (!mDispatchingStarted) {
            mStartTimestamp = SystemClock.uptimeMillis();
            mStartThreadTimestamp = SystemClock.currentThreadTimeMillis();
            mDispatchingStarted = true;

            mDispatchingMsg = msg;

            if (samplers != null && samplers.size() > 0) {
                for (AbstractSampler sampler : samplers) {
                    sampler.start();
                }
            }

        } else {
            final long endTimestamp = SystemClock.uptimeMillis();

            mDispatchingStarted = false;
            mDispatchingMsg = null;

            // 单条消息的分发耗时
            long cost = duration(endTimestamp);

            MessageManager.getInstance().add(msg, cost, messageMergeStrategy, filter);

            if (cost > mBlockThresholdMillis) {
                notifyBlockEvent(endTimestamp);
            }

            if (samplers != null && samplers.size() > 0) {
                for (AbstractSampler sampler : samplers) {
                    sampler.stop();
                }
            }
        }
    }

    public LinkedList<MessageCacheQueue.MainLooperMessagePack> getPendingMessages() {
        return pendingMessages;
    }


    private void dumpPendingMessages() {
        if (pendingMessages != null && !pendingMessages.isEmpty()) {
            pendingMessages.clear();
        }

        Message currentMsg = getMessageLinkedList();

        while (currentMsg != null) {
            MessageCacheQueue.MainLooperMessagePack msg = new MessageCacheQueue.MainLooperMessagePack();
            StringBuilder sb = new StringBuilder();
            sb.append(" arg1: ");
            sb.append(currentMsg.arg1);
            sb.append(" arg2: ");
            sb.append(currentMsg.arg2);
            sb.append(" callback: ");
            sb.append(currentMsg.getCallback());
            sb.append(" target: ");
            sb.append(currentMsg.getTarget());
            sb.append(" what: ");
            sb.append(currentMsg.what);
            long current = SystemClock.uptimeMillis();
            sb.append(" when: ");
            sb.append(current - currentMsg.getWhen());
            msg.msgType = MessageCacheQueue.MsgType.PENDING_MSG;
            msg.lastMsg = sb.toString();
            pendingMessages.add(msg);

            currentMsg = getMessageQueueNext(currentMsg);
        }
    }


    public boolean isDispatchingStarted() {
        return mDispatchingStarted;
    }


    private Message getMessageLinkedList() {
        try {
            MessageQueue queue = Looper.getMainLooper().getQueue();
            return (Message) MESSAGE_QUEUE_MESSAGES_FIELD.get(queue);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Access failed in TestLooper: get - MessageQueue.mMessages",
                    e);
        }
    }

    private Message getMessageQueueNext(Message msg) {
        try {
            if (msg != null) {
                msg = (Message) MESSAGE_NEXT_FIELD.get(msg);
            }

            return msg;
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Access failed in TestLooper", e);
        }
    }


    private long duration(long endTime) {
        return endTime - mStartTimestamp;
    }

    private void notifyBlockEvent(final long endTime) {
        final long startTime = mStartTimestamp;
        final long startThreadTime = mStartThreadTimestamp;
        final long endThreadTime = SystemClock.currentThreadTimeMillis();

        if (mBlockListener != null) {
            mBlockListener.onBlockEvent(startTime, endTime, startThreadTime, endThreadTime, samplers);
        }
    }

    public void startDump() {
        if (mDispatchingMsg != null) {
            long cost = SystemClock.uptimeMillis() - mStartTimestamp;
            MessageManager.getInstance().setDispatchingMsg(mDispatchingMsg, cost);
        }
        dumpPendingMessages();
    }

    public void stopDump() {
        if (samplers != null && samplers.size() > 0) {
            for (AbstractSampler sampler : samplers) {
                if (sampler instanceof StackSampler) {
                    sampler.destroy();
                }
            }
        }

    }
}