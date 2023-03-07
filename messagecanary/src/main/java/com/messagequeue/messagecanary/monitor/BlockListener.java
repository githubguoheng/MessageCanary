package com.messagequeue.messagecanary.monitor;

import java.util.ArrayList;

public interface BlockListener {
    public void onBlockEvent(long startTime, long endTime, long startThreadTime, long endThreadTime, ArrayList<AbstractSampler> samplers);
}
