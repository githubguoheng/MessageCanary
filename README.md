# 简介
Android的线上ANR问题比较复杂，因为ANR产生机制的原因，通常通过anrtrace的信息，不足以协助分析定位复杂问题发生原因。发生ANR时通常伴随着
主线程消息队列中的消息的分发耗时延长或者阻塞，所以通过获取ANR发生前一段时间内，程序运行环境中主线程消息队列中的消息分发以及运行状态，能够
极大的提升问题定位能力。

Android主线程队列里面特定的消息，在等待了一个超时时间后，仍然还没有得到消费，就会触发系统的ANR机制，原因主要有2个：

- 主线程太忙，来不及处理：主线程队列里面有很多待处理的消息并且其中可能夹杂着非常耗时的消息。具体又可以分为以下几种情况：
    - a. ANR发生时，当前正在处理的消息非常耗时
    - b. ANR发生时，有个一个或者多个历史消息非常耗时
    - c. ANR发生时，队列有异常多的排队消息 
- cpu太忙，主线程没有cpu资源，没机会处理：其他线程严重占用cpu资源

基于以上原因，设计了一个高效的主线程消息录制回放的工具，用来对主线程调度的历史、当前以及未来（pendding）的消息进行监控，发
生ANR时通过观察这些消息的调度耗时情况来辅助定位分析ANR问题，同时也提供了一个卡顿检测的方式，用来辅助分析卡顿问题，提升性能和稳定性

# 检测原理
## Looper loop()源码

### 关键代码

frameworks/base/core/java/android/os/Looper.java

无论是通过反射替换Looper的mLogging还是通过setMessageLogging设置printer，我们只需要替换主线程Looper的printer对象，  
通过计算执行dispatchMessage方法之后和之前打印字符串的时间的差值，就可以拿到到dispatchMessage方法执行的时间。而主线程
的操作最终都会执行到这个dispatchMessage方法中，通过记录Printer的内容可以保存主线程消息队列的历史消息，当发生ANR时，
通过回放主线程的消息状态来辅助定位ANR问题

设计思路：监控主线程的每一个消息的调度，对消息进行聚合、拆分、关键消息、IDLE消息识别归类形成特定的MessagePack，  
放入一个环形消息队列，再新启动一个耗时消息检测线程，采集耗时消息处理时的主线程堆栈，最后待ANR发生时，暂停主线程，  
获取被阻塞的所有待处理的消息，最后整理输出完整的采集日志。  

```
      /**
       * Run the message queue in this thread. Be sure to call
       * {@link #quit()} to end the loop.
       */
     public static void loop() {
  
 
         for (;;) {
             Message msg = queue.next(); // might block
             if (msg == null) {
                 // No message indicates that the message queue is quitting.
                 return;
             }
 
             // This must be in a local variable, in case a UI event sets the logger
             final Printer logging = me.mLogging;
             
             // 关键代码，通过 Printer 来记录 消息分发前后的消息情况 msg.target.dispatchMessage(msg) 
             if (logging != null) {
                 logging.println(">>>>> Dispatching to " + msg.target + " " +
                         msg.callback + ": " + msg.what);
             }
             // Make sure the observer won't change while processing a transaction.
             final Observer observer = sObserver;
 
             final long traceTag = me.mTraceTag;
             long slowDispatchThresholdMs = me.mSlowDispatchThresholdMs;
             long slowDeliveryThresholdMs = me.mSlowDeliveryThresholdMs;
             if (thresholdOverride > 0) {
                 slowDispatchThresholdMs = thresholdOverride;
                 slowDeliveryThresholdMs = thresholdOverride;
             }
             final boolean logSlowDelivery = (slowDeliveryThresholdMs > 0) && (msg.when > 0);
             final boolean logSlowDispatch = (slowDispatchThresholdMs > 0);
 
             final boolean needStartTime = logSlowDelivery || logSlowDispatch;
             final boolean needEndTime = logSlowDispatch;
 
             if (traceTag != 0 && Trace.isTagEnabled(traceTag)) {
                 Trace.traceBegin(traceTag, msg.target.getTraceName(msg));
             }
 
             final long dispatchStart = needStartTime ? SystemClock.uptimeMillis() : 0;
             final long dispatchEnd;
             Object token = null;
             if (observer != null) {
                 token = observer.messageDispatchStarting();
             }
             long origWorkSource = ThreadLocalWorkSource.setUid(msg.workSourceUid);
             try {
                 msg.target.dispatchMessage(msg);
                 if (observer != null) {
                     observer.messageDispatched(token, msg);
                 }
                 dispatchEnd = needEndTime ? SystemClock.uptimeMillis() : 0;
             } catch (Exception exception) {
                 if (observer != null) {
                     observer.dispatchingThrewException(token, msg, exception);
                 }
                 throw exception;
             } finally {
                 ThreadLocalWorkSource.restore(origWorkSource);
                 if (traceTag != 0) {
                     Trace.traceEnd(traceTag);
                 }
             }

 
             if (logging != null) {
                 logging.println("<<<<< Finished to " + msg.target + " " + msg.callback);
             }

         }
     }
```



## 历史消息记录
首先，通过setMessageLogging设置了printer后，因消息分发是一个高频操作，频繁的创建实例会对CPU和内存产生影响，记录
保存历史消息的操作需要尽可能的减少对主线程的影响，所以这里采用一个环形队列，当队列未满时创建新的实例保存消息，当队列  
满时替换原队列中的内容从而节省CPU消耗

### 消息聚合策略

1.如果累计的消息分发超过阈值，则形成一个pack
2.如果单条消息分发超过阈值，则需要单独形成一个pack，之前的累计的消息形成一个pack
3.自定义消息聚合策略


## 阻塞消息获取
当发生ANR时，通过反射的方式，获取当前主线程中Pendding的消息，将其与历史调度消息一起进行回放，通过观察历史调度、当前  
调度以及Pending消息的耗时状况来辅助定位ANR问题
```
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
```

## License

    Copyright 2023 guoheng.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

## Email
   hitwh_guoheng@hotmail.com



