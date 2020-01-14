/*
 * Copyright 2011-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.glowroot.agent.impl;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.util.List;
import java.util.Random;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.glowroot.agent.config.ConfigService;
import org.glowroot.agent.plugin.api.config.ConfigListener;
import org.glowroot.common.util.OnlyUsedByTests;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class StackTraceCollector {

    private static final Logger logger = LoggerFactory.getLogger(StackTraceCollector.class);

    private final TransactionRegistry transactionRegistry;
    private final ConfigService configService;
    private final Random random;

    private final InternalRunnable runnable;
    private final Thread processingThread;

    public StackTraceCollector(TransactionRegistry transactionRegistry,
            final ConfigService configService, Random random) {
        this.transactionRegistry = transactionRegistry;
        this.configService = configService;
        this.random = random;

        runnable = new InternalRunnable();
        // dedicated thread to give best chance of consistent stack trace capture
        // this is important for unit tests, but seems good for real usage as well
        processingThread = new Thread(runnable);
        processingThread.setDaemon(true);
        processingThread.setName("Glowroot-Stack-Trace-Collector");
        processingThread.start();

        configService.addConfigListener(new ConfigListener() {
            @Override
            public void onChange() {
                int intervalMillis = configService.getTransactionConfig().profilingIntervalMillis();
                // TODO report checker framework issue that occurs without checkNotNull
                if (intervalMillis != checkNotNull(runnable).currIntervalMillis) {
                    checkNotNull(processingThread);
                    processingThread.interrupt();
                }
            }
        });
    }

    @OnlyUsedByTests
    public void close() throws InterruptedException {
        runnable.closed = true;
        processingThread.interrupt();
        processingThread.join();
    }

    static void captureStackTraces(List<ThreadContextImpl> threadContexts) {

        StringBuilder sb = new StringBuilder();
            sb.append("\n********************************************************************************\n");
            sb.append("*********************Enter StackTraceCollector captureStackTraces()***********************\n");

        if (threadContexts.isEmpty()) {
            // critical not to call ThreadMXBean.getThreadInfo() with empty id list
            // see https://bugs.openjdk.java.net/browse/JDK-8074368
            sb.append("******************threadContexts.isEmpty() returning*******************\n");
            logger.info(sb.toString());
            return;
        }
        long[] threadIds = new long[threadContexts.size()];
        for (int i = 0; i < threadContexts.size(); i++) {
            threadIds[i] = threadContexts.get(i).getThreadId();
        }
        @Nullable
        ThreadInfo[] threadInfos =
                ManagementFactory.getThreadMXBean().getThreadInfo(threadIds, Integer.MAX_VALUE);
        sb.append("***************Cycling through list of threadcontexts*******************\n");
        for (int i = 0; i < threadContexts.size(); i++) {
            ThreadContextImpl threadContext = threadContexts.get(i);
            
            ThreadInfo threadInfo = threadInfos[i];
            if (threadInfo != null) {
                sb.append("***************Capture stack for threadcontext below*******************\n");
                sb.append(AgentImplsToString.ThreadContextImplToString(threadContext));
                sb.append("****************Use this threadInfo*************************\n");
                threadContext.captureStackTrace(threadInfo);
            }else{
                sb.append("***************theadInfo == null for threadContext below, not capturing stack trace*******************\n");
                sb.append(AgentImplsToString.ThreadContextImplToString(threadContext));
            }
        }
        sb.append("***************Done Cycling through list of threadcontexts*******************\n");
        logger.info(sb.toString());
    }

    private class InternalRunnable implements Runnable {

        private volatile int currIntervalMillis;
        private volatile boolean closed;

        @Override
        public void run() {
            // delay for first
            long remainingMillisInInterval = 0;
            while (!closed) {
                currIntervalMillis = configService.getTransactionConfig().profilingIntervalMillis();
                if (currIntervalMillis <= 0) {
                    try {
                        MILLISECONDS.sleep(Long.MAX_VALUE);
                    } catch (InterruptedException e) {
                        // probably interrupt from config listener (see above)
                        logger.debug(e.getMessage(), e);
                        // re-start loop
                        remainingMillisInInterval = 0;
                        continue;
                    }
                }
                long randomDelayMillisFromIntervalStart =
                        (long) (random.nextFloat() * currIntervalMillis);
                try {
                    MILLISECONDS
                            .sleep(remainingMillisInInterval + randomDelayMillisFromIntervalStart);
                } catch (InterruptedException e) {
                    // probably interrupt from config listener (see above)
                    logger.debug(e.getMessage(), e);
                    // re-start loop
                    remainingMillisInInterval = 0;
                    continue;
                }
                remainingMillisInInterval = currIntervalMillis - randomDelayMillisFromIntervalStart;
                try {
                    runInternal();
                } catch (Throwable t) {
                    logger.error(t.getMessage(), t);
                }
            }
        }

        private void runInternal() {

            StringBuilder sb = new StringBuilder();
            sb.append("\n********************************************************************************\n");
            sb.append("*********************Enter StackTraceCollector runInternal()***********************\n");

            List<Transaction> transactions =
                    ImmutableList.copyOf(transactionRegistry.getTransactions());            

            if (transactions.isEmpty()) {
                sb.append("******************Transaction list from transaction registry empty, returning************************************\n");
                logger.info(sb.toString());
                return;
            }

            ///ADDED
            sb.append("*********************List obtianed from transaction registry***********************\n");
            sb.append(AgentImplsToString.TransactionListToString(transactions));

            List<ThreadContextImpl> activeThreadContexts =
                    Lists.newArrayListWithCapacity(2 * transactions.size());

            sb.append("********************************************************************************\n");
            sb.append("********************************************************************************\n");
            sb.append("*********************Processing transaction list***********************\n");
            for (int i = 0; i < transactions.size(); i++) {
                Transaction transaction = transactions.get(i);
                //ADDED
                sb.append(AgentImplsToString.TransactionToString(transaction));
                ThreadContextImpl mainThreadContext = transaction.getMainThreadContext();
                sb.append("************Main thread context\n");
                sb.append(AgentImplsToString.ThreadContextImplToString(mainThreadContext));

                if (mainThreadContext.isActive()) {
                    //ADDED
                    sb.append("**(mainThreadContext.isActive() so adding mainThreadContext to activeThreadContexts\n");
                    sb.append(AgentImplsToString.ThreadContextImplToString(mainThreadContext));
                    sb.append("********************************************************************************\n");
                    activeThreadContexts.add(mainThreadContext);
                }else{
                    //ADDED
                    sb.append("**(mainThreadContext.is NOT Active so NOT adding mainThreadContext to activeThreadContexts\n");
                    sb.append("********************************************************************************\n");
                }
                sb.append("**Adding all trnsaciton's ActiveAuxThreadContexts to activeThreadContextList\n");
                activeThreadContexts.addAll(transaction.getActiveAuxThreadContexts());
            }
            sb.append("*********************Done Processing transaction list***********************\n");
            sb.append("********************************************************************************\n");
            sb.append("********************************************************************************\n");
            sb.append("*********************Capturing stack Traces now***********************\n");
            logger.info(sb.toString());
            captureStackTraces(activeThreadContexts);
        }
    }
}
