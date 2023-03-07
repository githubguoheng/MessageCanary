package com.messagequeue.messagecanary.monitor;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@link AbstractSampler} sampler defines sampler work flow.
 */
public abstract class AbstractSampler<T,K>{

    private static final int DEFAULT_SAMPLE_INTERVAL = 300;

    protected AtomicBoolean mShouldSample = new AtomicBoolean(false);
    protected long mSampleInterval;

    private Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            doSample();

            if (mShouldSample.get()) {
                HandlerThreadFactory.getTimerThreadHandler()
                        .postDelayed(mRunnable, mSampleInterval);
            }
        }
    };

    public AbstractSampler(long sampleInterval) {
        if (0 == sampleInterval) {
            sampleInterval = DEFAULT_SAMPLE_INTERVAL;
        }
        mSampleInterval = sampleInterval;
    }

    public void start() {
        if (mShouldSample.get()) {
            return;
        }
        mShouldSample.set(true);

        HandlerThreadFactory.getTimerThreadHandler().removeCallbacks(mRunnable);
        HandlerThreadFactory.getTimerThreadHandler().postDelayed(mRunnable, 0);
    }

    public void stop() {
        if (!mShouldSample.get()) {
            return;
        }
        mShouldSample.set(false);
        HandlerThreadFactory.getTimerThreadHandler().removeCallbacks(mRunnable);
    }

    public void destroy() {
        HandlerThreadFactory.getTimerThreadHandler().removeCallbacks(mRunnable);
        HandlerThreadFactory.getTimerHandlerThread().quitSafely();
    }

    protected abstract void doSample();

    /**
     * 结果返回
     * @return
     */
    public abstract T getResult(K key);
}
