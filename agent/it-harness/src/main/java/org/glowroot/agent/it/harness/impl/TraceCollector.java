/*
 * Copyright 2015-2018 the original author or authors.
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
package org.glowroot.agent.it.harness.impl;

import java.util.List;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Joiner;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.immutables.value.Value;

import org.glowroot.wire.api.model.CollectorServiceOuterClass.LogMessage.LogEvent;
import org.glowroot.wire.api.model.TraceOuterClass.Trace;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class TraceCollector {

    private static final Logger logger = LoggerFactory.getLogger(TraceCollector.class);

    private final List<Trace> traces = Lists.newCopyOnWriteArrayList();

    private final List<ExpectedLogMessage> expectedMessages = Lists.newCopyOnWriteArrayList();
    private final List<LogEvent> unexpectedMessages = Lists.newCopyOnWriteArrayList();

    Trace getCompletedTrace(@Nullable String transactionType, @Nullable String transactionName,
            int timeout, TimeUnit unit) throws InterruptedException {

         /*ADDED*/
        logger.info("********************************************************************************");
        logger.info("****Entering getCompletedTrace()****transactionType: {}, transactionName: {}, timeout: {}, unit: {}**********",
        transactionType, transactionName, timeout, unit);

        int i = 0;
        if (transactionName != null) {
            checkNotNull(transactionType);
        }
        Stopwatch stopwatch = Stopwatch.createStarted();
        while (stopwatch.elapsed(unit) < timeout) {
            for (Trace trace : traces) {
                if (!trace.getHeader().getPartial()
                        && (transactionType == null
                                || trace.getHeader().getTransactionType().equals(transactionType))
                        && (transactionName == null || trace.getHeader().getTransactionName()
                                .equals(transactionName))) {

                            /*ADDED*/
                            logger.info("********************************************************************************");
                            logger.info("****Exiting getCompletedTrace()****returning trace: {}", trace); 
                            return trace;
                }
            }
            //System.out.println("Print: " + ++i);
            MILLISECONDS.sleep(10);
        }
        /*ADDED*/
        logger.info("********************************************************************************");
        logger.info("****Exiting getCompletedTrace()****no trace returned, exception thrown"); 
        if (transactionName != null) {
            throw new IllegalStateException("No trace was collected for transaction type \""
                    + transactionType + "\" and transaction name \"" + transactionName + "\"");
        } else if (transactionType != null) {
            throw new IllegalStateException(
                    "No trace was collected for transaction type: " + transactionType);
        } else {
            throw new IllegalStateException("No trace was collected");
        }
        
    }

    Trace getPartialTrace(int timeout, TimeUnit unit) throws InterruptedException {
        /*ADDED*/
        logger.info("********************************************************************************");
        logger.info("****Entering getPartialTrace()****timeout: {}, unit: {}**********", timeout, unit);
        Stopwatch stopwatch = Stopwatch.createStarted();
        while (stopwatch.elapsed(unit) < timeout) {
            for (Trace trace : traces) {
                if (trace.getHeader().getPartial()) {
                    /*ADDED*/
                    logger.info("********************************************************************************");
                    logger.info("****Exiting getPartialTrace()****returning trace: {}", trace); 
                    return trace;
                }
            }
            MILLISECONDS.sleep(10);
        }
        /*ADDED*/
        logger.info("********************************************************************************");
        logger.info("****Exiting getPartialTrace()****no trace returned, exception thrown"); 
        if (traces.isEmpty()) {
            throw new IllegalStateException("No trace was collected");
        } else {
            throw new IllegalStateException("Trace was collected but is not partial");
        }
    }

    boolean hasTrace() {
        return !traces.isEmpty();
    }

    void clearTrace() {
        traces.clear();
    }

    void addExpectedLogMessage(String loggerName, String partialMessage) {
        expectedMessages.add(ImmutableExpectedLogMessage.of(loggerName, partialMessage));
    }

    public void checkAndResetLogMessages() throws InterruptedException {
        /*ADDED*/
        logger.info("********************************************************************************");
        logger.info("****Entering checkAndResetLogMessages()****");
        Stopwatch stopwatch = Stopwatch.createStarted();
        while (stopwatch.elapsed(SECONDS) < 10 && !expectedMessages.isEmpty()
                && unexpectedMessages.isEmpty()) {
            MILLISECONDS.sleep(10);
        }
        try {
            if (!unexpectedMessages.isEmpty()) {
                throw new AssertionError("Unexpected messages were logged:\n\n"
                        + Joiner.on("\n").join(unexpectedMessages));
            }
            if (!expectedMessages.isEmpty()) {
                throw new AssertionError("One or more expected messages were not logged");
            }
        } finally {
            expectedMessages.clear();
            unexpectedMessages.clear();
        }
        /*ADDED*/
        logger.info("********************************************************************************");
        logger.info("****Exiting checkAndResetLogMessages()****"); 
    }

    public void collectTrace(Trace trace) {
        StringBuilder sb = new StringBuilder("********************************************************************************\n");
        sb.append("****Entering collectTrace()********trace id==" + trace.getId() + "******************************\n");
        sb.append("****Cycling through " + traces.size() + "traces to insert new one\n");
        for (int i = 0; i < traces.size(); i++) {
            Trace loopTrace = traces.get(i);
            
            if (loopTrace.getId().equals(trace.getId())) {
                sb.append("****loopTrace.getId().equals(trace.getId()==" + trace.getId() + "\n");
                if (trace.getHeader().getDurationNanos() >= loopTrace.getHeader()
                        .getDurationNanos()) {
                    sb.append("trace.getHeader().getDurationNanos() == " + trace.getHeader().getDurationNanos() +  
                    ">= loopTrace.getHeader().getDurationNanos() == " + loopTrace.getHeader().getDurationNanos() + "\n").
                    append("updating trace entry at index(" + i + ") to trace passed to this method\n");
                    logger.info(sb.toString());
                    traces.set(i, trace);
                }
                sb.append("****Exitinging collectTrace()********trace id==" + trace.getId() + "******************************\n");
                logger.info(sb.toString());

                return;
            }
        }
        sb.append("****Exitinging collectTrace()********ADDING NEW trace id==" + trace.getId() + " to traces list******************************\n");
        traces.add(trace);
         /*ADDED*/
         sb.append("********************************************************************************\n");
         logger.info(sb.toString());

    }

    public void log(LogEvent logEvent) {
        /*ADDED*/
        logger.info("********************************************************************************");
        logger.info("****Entering log(): log event -> {}****", logEvent);
        if (isExpected(logEvent)) {
            /*ADDED*/
            logger.info("********************************************************************************");
            logger.info("***log() -> Log event expected, returning****");
            return;
        }
        if (logEvent.getLoggerName().equals("org.apache.catalina.loader.WebappClassLoaderBase")
                && logEvent.getMessage().matches(
                        "The web application \\[.*\\] appears to have started a thread named"
                                + " \\[.*\\] but has failed to stop it\\. This is very likely to"
                                + " create a memory leak\\.")) {
            /*ADDED*/
            logger.info("********************************************************************************");
            logger.info("***log() -> Memory leak assoicated with org.apache.catalina.loader.WebappClassLoaderBase detected, exiting****");
            return;
        }
        if (logEvent.getLevel() == LogEvent.Level.WARN
                || logEvent.getLevel() == LogEvent.Level.ERROR) {
            unexpectedMessages.add(logEvent);
        }
         /*ADDED*/
         logger.info("********************************************************************************");
         logger.info("***Exiting log() ->Log event NOT expected, returning****");
    }

    private boolean isExpected(LogEvent logEvent) {
        if (expectedMessages.isEmpty()) {
            return false;
        }
        ExpectedLogMessage expectedMessage = expectedMessages.get(0);
        if (expectedMessage.loggerName().equals(logEvent.getLoggerName())
                && logEvent.getMessage().contains(expectedMessage.partialMessage())) {
            expectedMessages.remove(0);
            return true;
        }
        return false;
    }

    @Value.Immutable
    @Value.Style(allParameters = true)
    interface ExpectedLogMessage {
        String loggerName();
        String partialMessage();
    }
}
