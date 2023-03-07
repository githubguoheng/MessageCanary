package com.messagequeue.messagecanary.monitor;

import com.messagequeue.messagecanary.utils.BlockInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;

/**
 * Dumps thread stack.
 */
public class StackSampler extends AbstractSampler<ArrayList<String>, Long> {

    private static final int DEFAULT_MAX_ENTRY_COUNT = 100;
    private static final LinkedHashMap<Long, String> sStackMap = new LinkedHashMap<>();

    private int mMaxEntryCount = DEFAULT_MAX_ENTRY_COUNT;
    private Thread mCurrentThread;

    public StackSampler(Thread thread, long sampleIntervalMillis) {
        this(thread, DEFAULT_MAX_ENTRY_COUNT, sampleIntervalMillis);
    }

    public StackSampler(Thread thread, int maxEntryCount, long sampleIntervalMillis) {
        super(sampleIntervalMillis);
        mCurrentThread = thread;
        mMaxEntryCount = maxEntryCount;
    }

    @Override
    protected void doSample() {
        StringBuilder stringBuilder = new StringBuilder();

        for (StackTraceElement stackTraceElement : mCurrentThread.getStackTrace()) {
            stringBuilder
                    .append(stackTraceElement.toString())
                    .append(BlockInfo.SEPARATOR);
        }

        synchronized (sStackMap) {
            if (sStackMap.size() == mMaxEntryCount && mMaxEntryCount > 0) {
                sStackMap.remove(sStackMap.keySet().iterator().next());
            }
            sStackMap.put(System.currentTimeMillis(), stringBuilder.toString());
        }
    }

    /**
     * 获取从当前时间往前 durationMillis 毫秒时间内主线程发生超时的线程堆栈
     * @param key
     * @return
     */
    @Override
    public ArrayList<String> getResult(Long key) {

        ArrayList<String> result = new ArrayList<>();

        long currentTimeMillis = System.currentTimeMillis();

        synchronized (sStackMap) {
            for (Long entryTime : sStackMap.keySet()) {
                if (currentTimeMillis - key.longValue() < entryTime && entryTime < currentTimeMillis) {
                    result.add(BlockInfo.TIME_FORMATTER.format(entryTime)
                            + BlockInfo.SEPARATOR
                            + BlockInfo.SEPARATOR
                            + sStackMap.get(entryTime));
                }
            }
        }
        return result;
    }

}