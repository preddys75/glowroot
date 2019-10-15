/*
 * Copyright 2017-2019 the original author or authors.
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
package org.glowroot.agent.plugin.servlet;

import java.lang.management.ManagementFactory;

import org.glowroot.agent.plugin.api.Logger;
import org.glowroot.agent.plugin.api.MessageSupplier;
import org.glowroot.agent.plugin.api.OptionalThreadContext;
import org.glowroot.agent.plugin.api.ThreadContext.Priority;
import org.glowroot.agent.plugin.api.TimerName;
import org.glowroot.agent.plugin.api.TraceEntry;
import org.glowroot.agent.plugin.api.checker.Nullable;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class ContainerStartup {

    private static final Logger logger = Logger.getLogger(ContainerStartup.class);

    private ContainerStartup() {}

    static TraceEntry onBeforeCommon(OptionalThreadContext context, @Nullable String path,
            TimerName timerName) {
        initPlatformMBeanServer();

        //ADDED
        logger.info("************Entering static - onBeforeCommon()***********************");

        /*ADDED*/
        StackTraceElement st = new Throwable().fillInStackTrace().getStackTrace()[1];
        String caller = st.getClassName() + ":  " + st.getMethodName();
        logger.info("Caller: {}", caller);

        String transactionName;
        if (path == null || path.isEmpty()) {
            // root context path is empty "", but makes more sense to display "/"
            transactionName = "Servlet context: /";
        } else {
            transactionName = "Servlet context: " + path;
        }
        logger.info("*******transactionName: {} ************************", transactionName);

        //ADDED
        MessageSupplier temp = MessageSupplier.create(transactionName);
        logger.info("Params passed to startTransaction()\n transactionType: {}, transactionName: {}, messageSupplier: {}, " +
                    "timerName: {}", "Startup", transactionName, temp, timerName);

        TraceEntry traceEntry = context.startTransaction("Startup", transactionName,
                temp, timerName);

        context.setTransactionSlowThreshold(0, MILLISECONDS, Priority.CORE_PLUGIN);

        //ADDED
        logger.info("************Exiting static - onBeforeCommon()***********************");

        return traceEntry;
    }

    static void initPlatformMBeanServer() {
        // make sure the platform mbean server gets created so that it can then be retrieved by
        // LazyPlatformMBeanServer which may be waiting for it to be created (the current
        // thread context class loader should have access to the platform mbean server that is set
        // via the javax.management.builder.initial system property)
        try {
            ManagementFactory.getPlatformMBeanServer();
        } catch (Throwable t) {
            logger.error("could not create platform mbean server: {}", t.getMessage(), t);
        }
    }
}
