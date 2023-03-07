package com.messagequeue.messagecanary.messages;

import android.os.Looper;
import android.text.TextUtils;

import org.jetbrains.annotations.Nullable;

/**
 * 循环列表实现的环形队列
 *
 * 指定容量的消息缓存队列，先进先出
 * 当前数量小于容量时，入队时创建新实例，当大于等于容量时，按照先进先出的原则替换头部列表元素内容，不再创建新实例
 */
public class MessageCacheQueue {

    private static final int DEFAULT_CAPACITY = 1000;
    private static final int DEFAULT_TIME_INTERVAL = 500;

    private int capacity = DEFAULT_CAPACITY;
    private int timeInterval = DEFAULT_TIME_INTERVAL;

    private MainLooperMessagePack first;
    private MainLooperMessagePack last;
    private MainLooperMessagePack dispatchingMsg;

    private int size = 0;

    private MessageMergeStrategy messageMergeStrategy;


    /**
     * 设置消息间隔，超过该间隔，消息做合并处理
     * @param timeInterval
     */
    public void setTimeInterval(int timeInterval) {
        this.timeInterval = timeInterval;
    }


    /**
     * 设置消息队列的容量
     *
     * @param capacity
     */
    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    /**
     * 自定义消息聚合策略
     * @param messageMergeStrategy
     */
    public void setMessageMergeStrategy(MessageMergeStrategy messageMergeStrategy) {
        this.messageMergeStrategy = messageMergeStrategy;
    }

    /**
     * 当前正在分发的消息
     * @param msg
     * @param cost
     */
    public void setDispatchingMsg(String msg, long cost) {
        if (dispatchingMsg == null) {
            dispatchingMsg = new MainLooperMessagePack();
        }

        dispatchingMsg.lastMsg = msg;
        dispatchingMsg.duration = cost;
        dispatchingMsg.count = 1;
        dispatchingMsg.msgType = MsgType.DISPATCHING_MSG;
        dispatchingMsg.next = null;
    }


    public MainLooperMessagePack getFirst() {
        return first;
    }

    public MainLooperMessagePack getLast() {
        return last;
    }

    public MainLooperMessagePack getDispatchingMsg() {
        return dispatchingMsg;
    }

    public int getSize() {
        return size;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getTimeInterval() {
        return timeInterval;
    }

    /**
     * 只能在主线程使用
     * 缓存消息，使用环形队列，队列没满时，创建新实例入队，队列满时，不创建新实例，替换队列原有元素
     *
     * 默认消息聚合策略
     * 1、如果累计的消息分发耗时超过 {@link #timeInterval}, 前面的消息聚合成一个pack
     * 2、如果单条消息分发耗时超过 {@link #timeInterval}, 单独作为一个pack，前面累积的消息聚合成一个pack
     * @param msg
     * @param duration
     * @param messageMergeStrategy  自定义聚合策略
     */
    public void add(String msg, long duration,  @Nullable MessageMergeStrategy messageMergeStrategy, @Nullable MessageFilter msgFilter) throws RuntimeException {
        if (Looper.myLooper() !=  Looper.getMainLooper()) {
            throw new RuntimeException("Message Should be added in main looper");
        }

        if (TextUtils.isEmpty(msg) || (msgFilter != null && msgFilter.filter(msg, duration))) {
            return;
        }

        if ((last != null && duration <= timeInterval && last.duration <= timeInterval) ||
                (messageMergeStrategy != null && messageMergeStrategy.merge(this, msg, duration))) {
            last.count ++;
            last.duration += duration;

            last.lastMsg = msg;
            last.msgType = MsgType.DISPATCHED_MSG;
            return;
        }


        if (size < capacity) {

            if (size == 0) {
                MainLooperMessagePack node = new MainLooperMessagePack();

                node.next = node;

                first = node;
                last = node;

                last.count = 1;
                size++;

            } else {
                MainLooperMessagePack node = new MainLooperMessagePack();

                node.next = first;

                last.next = node;
                last = node;

                last.count = 1;

                size++;
            }
        } else {

            last = last.next;
            first = first.next;

            last.count = 1;
        }

        last.lastMsg = msg;
        last.duration = duration;
        last.msgType = MsgType.DISPATCHED_MSG;
    }
    /**
     * 消息体，记录消息的分发耗时、合并次数、最新消息的内容，消息被合并后只记录最后一条消息的具体信息
     */
    public static class MainLooperMessagePack {
        /**
         * 链表下一条数据
         */
        public MainLooperMessagePack next = null;
        /**
         * 消息被合并的次数
         */
        public int count = 0;
        /**
         * 消息分发耗时
         */
        public long duration = 0;
        /**
         * 消息体最新一条消息的内容
         */
        public String lastMsg;

        /**
         * 消息类型
         */
        public MsgType msgType;

        public String toString() {
            StringBuilder sb = new StringBuilder();

            if (lastMsg != null) {
                sb.append(lastMsg);
            }

            sb.append(" msgType: " + msgType.getType() +  " count: " + count + " duration: " + duration );

            return sb.toString();
        }
    }

    public enum MsgType {
        DISPATCHED_MSG(-1), DISPATCHING_MSG(0),PENDING_MSG(1);

        private int type;
        private MsgType(int type) {
            this.type = type;
        }

        public int getType() {
            return type;
        }
    }
}
