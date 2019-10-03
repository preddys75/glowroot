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
package org.glowroot.central;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.glowroot.agent.api.Instrumentation;
import org.glowroot.central.repo.ActiveAgentDao;
import org.glowroot.central.repo.AgentConfigDao;
import org.glowroot.central.repo.AgentDisplayDao;
import org.glowroot.central.repo.AggregateDao;
import org.glowroot.central.repo.EnvironmentDao;
import org.glowroot.central.repo.GaugeValueDao;
import org.glowroot.central.repo.HeartbeatDao;
import org.glowroot.central.repo.SchemaUpgrade;
import org.glowroot.central.repo.TraceDao;
import org.glowroot.central.repo.V09AgentRollupDao;
import org.glowroot.central.util.MoreFutures;
import org.glowroot.common.util.Clock;
import org.glowroot.wire.api.model.AgentConfigOuterClass.AgentConfig;
import org.glowroot.wire.api.model.AggregateOuterClass.Aggregate;
import org.glowroot.wire.api.model.AggregateOuterClass.OldAggregatesByType;
import org.glowroot.wire.api.model.AggregateOuterClass.OldTransactionAggregate;
import org.glowroot.wire.api.model.CollectorServiceGrpc;
import org.glowroot.wire.api.model.CollectorServiceOuterClass.AggregateResponseMessage;
import org.glowroot.wire.api.model.CollectorServiceOuterClass.AggregateStreamMessage;
import org.glowroot.wire.api.model.CollectorServiceOuterClass.AggregateStreamMessage.AggregateStreamHeader;
import org.glowroot.wire.api.model.CollectorServiceOuterClass.AggregateStreamMessage.OverallAggregate;
import org.glowroot.wire.api.model.CollectorServiceOuterClass.AggregateStreamMessage.TransactionAggregate;
import org.glowroot.wire.api.model.CollectorServiceOuterClass.EmptyMessage;
import org.glowroot.wire.api.model.CollectorServiceOuterClass.GaugeValueMessage;
import org.glowroot.wire.api.model.CollectorServiceOuterClass.GaugeValueMessage.GaugeValue;
import org.glowroot.wire.api.model.CollectorServiceOuterClass.GaugeValueResponseMessage;
import org.glowroot.wire.api.model.CollectorServiceOuterClass.InitMessage;
import org.glowroot.wire.api.model.CollectorServiceOuterClass.InitResponse;
import org.glowroot.wire.api.model.CollectorServiceOuterClass.LogMessage;
import org.glowroot.wire.api.model.CollectorServiceOuterClass.LogMessage.LogEvent;
import org.glowroot.wire.api.model.CollectorServiceOuterClass.LogMessage.LogEvent.Level;
import org.glowroot.wire.api.model.CollectorServiceOuterClass.OldAggregateMessage;
import org.glowroot.wire.api.model.CollectorServiceOuterClass.OldTraceMessage;
import org.glowroot.wire.api.model.CollectorServiceOuterClass.TraceStreamMessage;
import org.glowroot.wire.api.model.CollectorServiceOuterClass.TraceStreamMessage.TraceStreamCounts;
import org.glowroot.wire.api.model.CollectorServiceOuterClass.TraceStreamMessage.TraceStreamHeader;
import org.glowroot.wire.api.model.ProfileOuterClass.Profile;
import org.glowroot.wire.api.model.Proto;
import org.glowroot.wire.api.model.TraceOuterClass.Trace;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MINUTES;

class CollectorServiceImpl extends CollectorServiceGrpc.CollectorServiceImplBase {

    private static final Logger logger = LoggerFactory.getLogger(CollectorServiceImpl.class);

    private final AgentDisplayDao agentDisplayDao;
    private final AgentConfigDao agentConfigDao;
    private final ActiveAgentDao activeAgentDao;
    private final EnvironmentDao environmentDao;
    private final HeartbeatDao heartbeatDao;
    private final AggregateDao aggregateDao;
    private final GaugeValueDao gaugeValueDao;
    private final TraceDao traceDao;
    private final V09AgentRollupDao v09AgentRollupDao;
    private final GrpcCommon grpcCommon;
    private final CentralAlertingService centralAlertingService;
    private final Clock clock;
    private final String version;

    private volatile long currentMinute;
    private final AtomicInteger nextDelay = new AtomicInteger();

    private final LoadingCache<String, Semaphore> throttlePerAgentId = CacheBuilder.newBuilder()
            .weakValues()
            .build(new CacheLoader<String, Semaphore>() {
                @Override
                public Semaphore load(String key) throws Exception {
                    return new Semaphore(1);
                }
            });

    CollectorServiceImpl(AgentDisplayDao agentDisplayDao, AgentConfigDao agentConfigDao,
            ActiveAgentDao activeAgentDao, EnvironmentDao environmentDao, HeartbeatDao heartbeatDao,
            AggregateDao aggregateDao, GaugeValueDao gaugeValueDao, TraceDao traceDao,
            V09AgentRollupDao v09AgentRollupDao, GrpcCommon grpcCommon,
            CentralAlertingService centralAlertingService, Clock clock, String version) {
        this.agentDisplayDao = agentDisplayDao;
        this.agentConfigDao = agentConfigDao;
        this.activeAgentDao = activeAgentDao;
        this.environmentDao = environmentDao;
        this.heartbeatDao = heartbeatDao;
        this.aggregateDao = aggregateDao;
        this.gaugeValueDao = gaugeValueDao;
        this.traceDao = traceDao;
        this.v09AgentRollupDao = v09AgentRollupDao;
        this.grpcCommon = grpcCommon;
        this.centralAlertingService = centralAlertingService;
        this.clock = clock;
        this.version = version;
    }

    @Instrumentation.Transaction(transactionType = "gRPC", transactionName = "Init",
            traceHeadline = "Collect init: {{0.agentId}}", timer = "init")
    @Override
    public void collectInit(InitMessage request, StreamObserver<InitResponse> responseObserver) {
        //ADDED
        logger.info("******************************************************************************");
        logger.info("Incoming request, collectInit(): request -> {}", request);
        logger.info("Incoming request, collectInit(): responseObserver -> {}", responseObserver);

        String agentId = request.getAgentId();
        String v09AgentRollupId = request.getV09AgentRollupId();

        //ADDED
        logger.info("******************************************************************************");
        logger.info("agentId -> {}, v09AgentRollupId -> {}", agentId, v09AgentRollupId);

        if (!v09AgentRollupId.isEmpty()) {
            // handle agents prior to 0.10.0
            String v09AgentId = agentId;
            agentId = GrpcCommon.convertFromV09AgentRollupId(v09AgentRollupId) + v09AgentId;
            //ADDED
            logger.info("******************************************************************************");
            logger.info("collectInit(): agentId = GrpcCommon.convertFromV09AgentRollupId(v09AgentRollupId) + v09AgentId -> {}", agentId);
            try {
                v09AgentRollupDao.store(v09AgentId, v09AgentRollupId);
            } catch (Throwable t) {
                logger.error("{} - {}", agentId, t.getMessage(), t);
                responseObserver.onError(t);
                return;
            }
        }
        AgentConfig updatedAgentConfig;
        try {
            //ADDED
            logger.info("********************************************************************************");
            logger.info("Request.getAgentConfig(): {}", request.getAgentConfig());

            updatedAgentConfig = SchemaUpgrade.upgradeOldAgentConfig(request.getAgentConfig());
            updatedAgentConfig = agentConfigDao.store(agentId, updatedAgentConfig,
                    request.getOverwriteExistingAgentConfig());

            //ADDED
            logger.info("********************************************************************************");
            logger.info("Request.getEnvironment(): {}", request.getEnvironment());


            environmentDao.store(agentId, request.getEnvironment());
            MoreFutures.waitForAll(activeAgentDao.insert(agentId, clock.currentTimeMillis()));
        } catch (Throwable t) {
            logger.error("{} - {}", agentId, t.getMessage(), t);
            responseObserver.onError(t);
            return;
        }
        logger.info("agent connected: {}, request.getEnvironment().getJavaInfo().getGlowrootAgentVersion() -> {}", agentId,
                request.getEnvironment().getJavaInfo().getGlowrootAgentVersion());
        InitResponse.Builder response = InitResponse.newBuilder()
                .setGlowrootCentralVersion(version);


        //ADDED
        logger.info("********************************************************************************");
        logger.info("InitResponse.Builder response: {}", response);
        logger.info("updatedAgentConfig.equals(request.getAgentConfig(): {} <equals> ", updatedAgentConfig, request.getAgentConfig());

        if (!updatedAgentConfig.equals(request.getAgentConfig())) {
            response.setAgentConfig(updatedAgentConfig);
        }

        InitResponse init_response = response.build();
        //ADDED
        logger.info("********************************************************************************");
        logger.info("InitResponse init_response: {}", init_response);

        //ADDED
        logger.info("********************************************************************************");
        logger.info("Response observer before onNext(): {}", responseObserver);
        responseObserver.onNext(init_response);
        //ADDED
        logger.info("********************************************************************************");
        logger.info("Response observer before onCompleted(): {}", responseObserver);
        responseObserver.onCompleted();

        //ADDED
        logger.info("********************************************************************************");
        logger.info("Response observer post onCompleted(): {}", responseObserver);

        //ADDED
        logger.info("********************************************************************************");
        logger.info("%%%%%%%%%%%%%%%%%%%%%%%End of CollectorServiceImpl.collectInit() %%%%%%%%%%%%%%%%%%%%%%%%%%%%");
        logger.info("%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%");


    }

    @Override
    public StreamObserver<AggregateStreamMessage> collectAggregateStream(
            StreamObserver<AggregateResponseMessage> responseObserver) {

        //ADDED
        logger.info("********************************************************************************");
        logger.info("*************Entering collectAggregateStream()**************************************");
        logger.info("ResponseObserver passed to method: {}", responseObserver);

        AggregateStreamObserver retVal = new AggregateStreamObserver(responseObserver);
        //ADDED
        logger.info("Exiting collectAggregateStream(), AggregateStreamObserver created from responseObserver: {}", retVal);
        logger.info("********************************************************************************");

        return retVal;
    }

    @Instrumentation.Transaction(transactionType = "gRPC", transactionName = "Aggregates",
            traceHeadline = "Collect aggregates: {{0.agentId}}", timer = "aggregates")
    @Override
    public void collectAggregates(OldAggregateMessage request,
            StreamObserver<AggregateResponseMessage> responseObserver) {

        //ADDED
        logger.info("********************************************************************************");
        logger.info("*************In collectAggregates()**************************************");
        logger.info("OldAggregateMessage passed to method, request=: {}", request);


        List<Aggregate.SharedQueryText> sharedQueryTexts;
        List<String> oldSharedQueryTexts = request.getOldSharedQueryTextList();

        //ADDED
        logger.info("List of oldSharedQueryTexts  retrieved from request, oldSharedQueryTexts=: {}", oldSharedQueryTexts);

        if (oldSharedQueryTexts.isEmpty()) {
            sharedQueryTexts = request.getSharedQueryTextList();
            //ADDED
            logger.info("oldSharedQueryTexts is Empty, setting sharedQueryTexts = request.getSharedQueryTextList(): -> {}",sharedQueryTexts);

        } else {
            //ADDED
            logger.info("oldSharedQueryTexts NOT Empty, adding it to NEW sharedQueryText List -> printed below:");
            // handle agents prior to 0.9.3
            sharedQueryTexts = new ArrayList<>();
            for (String oldSharedQueryText : oldSharedQueryTexts) {
                //ADDED
                logger.info("oldSharedQueryText -> {}", oldSharedQueryText);
                sharedQueryTexts.add(Aggregate.SharedQueryText.newBuilder()
                        .setFullText(oldSharedQueryText)
                        .build());
            }
        }

        //ADDED
        logger.info("********************************************************************************");
        logger.info("*************Calling throttleCollectAggregates()*************************************");
        throttleCollectAggregates(request.getAgentId(), false,
                getFutureProofAggregateCaptureTime(request.getCaptureTime()),
                sharedQueryTexts, request.getAggregatesByTypeList(), responseObserver);

        logger.info("*************Exiting collectAggregates()*************************************");
        logger.info("********************************************************************************");
    }

    @Instrumentation.Transaction(transactionType = "gRPC", transactionName = "Gauges",
            traceHeadline = "Collect gauge values: {{0.agentId}}", timer = "gauges")
    @Override
    public void collectGaugeValues(GaugeValueMessage request,
            StreamObserver<GaugeValueResponseMessage> responseObserver) {

        //ADDED
        logger.info("********************************************************************************");
        logger.info("*************Entering collectGaugeValues()*************************************");
        logger.info("****GaugeValueMessage request: {}", request);
        logger.info("****StreamObserver<GaugeValueResponseMessage> responseObserver: {}", responseObserver);
        throttledCollectGaugeValues(request, responseObserver);
        logger.info("*************Exiting collectGaugeValues()************************************");
        logger.info("********************************************************************************");

    }

    @Override
    public StreamObserver<TraceStreamMessage> collectTraceStream(
            StreamObserver<EmptyMessage> responseObserver) {

        //ADDED
        logger.info("********************************************************************************");
        logger.info("*************Entering collectTraceStream()*************************************");
        TraceStreamObserver retVal = new TraceStreamObserver(responseObserver);
        logger.info("****StreamObserver<EmptyMessage> responseObserver: {}", responseObserver);
        logger.info("*************Exiting collectTraceStream()************************************");
        logger.info("********************************************************************************");

        return retVal;
    }

    @Instrumentation.Transaction(transactionType = "gRPC", transactionName = "Trace",
            traceHeadline = "Collect trace: {{0.agentId}}", timer = "trace")
    @Override
    public void collectTrace(OldTraceMessage request,
            StreamObserver<EmptyMessage> responseObserver) {

        //ADDED
        logger.info("----------------------------------------------------------------");
        logger.info("********************************************************************************");
        logger.info("Entering collectTrace(), agentId=<{}>, postV09 -> <{}>, traceId=<{}>", request.getAgentId(), false, request.getTrace());

        throttledCollectTrace(request.getAgentId(), false, request.getTrace(), responseObserver);

        //ADDED
        logger.info("*************Exiting collectTrace()************************************");
        logger.info("********************************************************************************");
    }

    @Instrumentation.Transaction(transactionType = "gRPC", transactionName = "Log",
            traceHeadline = "Log: {{0.agentId}}", timer = "log")
    @Override
    public void log(LogMessage request, StreamObserver<EmptyMessage> responseObserver) {
        String agentId;
        //ADDED
        logger.info("********************************************************************************");
        logger.info("*************Entering log()*************************************");
        logger.info("****LogMessage request: {}", request);
        try {
            agentId = grpcCommon.getAgentId(request.getAgentId(), request.getPostV09());
        } catch (Throwable t) {
            logger.error("{} - {}",
                    getAgentIdForLogging(request.getAgentId(), request.getPostV09()),
                    t.getMessage(), t);
            responseObserver.onError(t);
            return;
        }
        try {
            LogEvent logEvent = request.getLogEvent();
            Level level = logEvent.getLevel();
            String formattedTimestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")
                    .format(new Date(logEvent.getTimestamp()));
            if (logEvent.hasThrowable()) {
                log(level, "{} - {} {} {} - {}\n{}", agentId, formattedTimestamp,
                        level, logEvent.getLoggerName(), logEvent.getMessage(),
                        toString(logEvent.getThrowable()));
            } else {
                log(level, "{} - {} {} {} - {}", agentId, formattedTimestamp, level,
                        logEvent.getLoggerName(), logEvent.getMessage());
            }
        } catch (Throwable t) {
            logger.error("{} - {}", agentId, t.getMessage(), t);
            responseObserver.onError(t);
            return;
        }
        //ADDED logging
        logger.info("****StreamObserver<EmptyMessage> responseObserver BEFORE responseObserver.onNext(): {}", responseObserver);
        logger.info("*************************************************");
        responseObserver.onNext(EmptyMessage.getDefaultInstance());
        responseObserver.onCompleted();
        logger.info("****StreamObserver<EmptyMessage> responseObserver AFTER responseObserver.onCompleted(): {}", responseObserver);
        logger.info("*************Exiting log()************************************");
        logger.info("********************************************************************************");
    }

    private void throttleCollectAggregates(String agentId, boolean postV09, long captureTime,
            List<Aggregate.SharedQueryText> sharedQueryTexts,
            List<OldAggregatesByType> aggregatesByTypeList,
            StreamObserver<AggregateResponseMessage> responseObserver) {

        //ADDED
        logger.info("********************************************************************************");
        logger.info("----------------------------------------------------------------");
        logger.info("Entering throttleCollectAggregates(), agentId=<{}>, postV09 -> <{}>, captureTime=<{}>", agentId, postV09, captureTime);


        //ADDED
        logger.info("Logging shared query texts");
        for (Aggregate.SharedQueryText sharedQueryText : sharedQueryTexts) {
            //ADDED
            logger.info("shared query text -> {}", sharedQueryText);
        }

        //ADDED
        logger.info("----------------------------------------------------------------");
        logger.info("Logging List<OldAggregatesByType> aggregatesByTypeList");
        for ( OldAggregatesByType aggregatesByTypeObj :  aggregatesByTypeList) {
            //ADDED
            logger.info("aggregatesByTypeObj -> {}", aggregatesByTypeObj);
        }


        //ADDED
        logger.info("--Calling throttle()----------------------------------------------");
        throttle(agentId, postV09, "aggregate", responseObserver, new Runnable() {
            @Override
            public void run() {
                collectAggregatesUnderThrottle(agentId, postV09, captureTime, sharedQueryTexts,
                        aggregatesByTypeList, responseObserver);
            }
        });
        //ADDED
        logger.info("********************************************************************************");
        logger.info("----------------------------------------------------------------");
        logger.info("Exiting throttleCollectAggregates(), agentId=<{}>, postV09 -> <{}>, captureTime=<{}>", agentId, postV09, captureTime);
    }

    private void throttledCollectGaugeValues(GaugeValueMessage request,
            StreamObserver<GaugeValueResponseMessage> responseObserver) {

        //ADDED
        logger.info("********************************************************************************");
        logger.info("*************Entering throttledCollectGaugeValues()*************************************");
        logger.info("****GaugeValueMessage request: {}", request);
        logger.info("--Calling throttle()----------------------------------------------");
        throttle(request.getAgentId(), request.getPostV09(), "gauge value", responseObserver,
                new Runnable() {
                    @Override
                    public void run() {
                        collectGaugeValuesUnderThrottle(request, responseObserver);
                    }
                });
        //ADDED
        logger.info("****StreamObserver<GaugeValueResponseMessage> responseObserver: {}", responseObserver);
        logger.info("*************Exiting throttledCollectGaugeValues()************************************");
        logger.info("********************************************************************************");

    }

    private void throttledCollectTrace(String agentId, boolean postV09, Trace trace,
            StreamObserver<EmptyMessage> responseObserver) {

        //ADDED
        logger.info("********************************************************************************");
        logger.info("*************Entering throttledCollectTrace()*************************************");
        logger.info("****agentId=<{}>, postV09=<{}>, trace -> {}", agentId, postV09, trace);
        logger.info("--Calling throttle()----------------------------------------------");


        throttle(agentId, postV09, "trace", responseObserver, new Runnable() {
            @Override
            public void run() {
                collectTraceUnderThrottle(agentId, postV09, trace, responseObserver);
            }
        });
        //ADDED
        logger.info("****StreamObserver<EmptyMessage> responseObserver: {}", responseObserver);
        logger.info("*************Exiting throttledCollectTrace()************************************");
        logger.info("********************************************************************************");
    }

    private <T> void throttle(String agentId, boolean postV09, String collectionType,
            StreamObserver<T> responseObserver, Runnable runnable) {

        //ADDED
        logger.info("********************************************************************************");
        logger.info("*************Entering throttle()*************************************");
        logger.info("****agentId=<{}>, postV09=<{}>, collectionType=<{}>", agentId, postV09, collectionType);
        logger.info("*************Will attempt to obtain semaphore and then kick off thread***********");

        Semaphore semaphore = throttlePerAgentId.getUnchecked(agentId);
        boolean acquired;
        try {
            acquired = semaphore.tryAcquire(1, MINUTES);
        } catch (InterruptedException e) {
            // probably shutdown requested
            responseObserver.onError(e);
            return;
        }
        if (!acquired) {
            logger.warn("{} - {} collection rejected due to backlog",
                    getAgentIdForLogging(agentId, postV09), collectionType);
            responseObserver.onError(Status.RESOURCE_EXHAUSTED
                    .withDescription("collection rejected due to backlog")
                    .asRuntimeException());
            return;
        }
        try {
            runnable.run();
            logger.info("*************Kicked off thread in throttle()*******************************");
        } finally {
            semaphore.release();
        }
        //ADDED
        logger.info("****StreamObserver<T> responseObserver: {}", responseObserver);
        logger.info("*************Exiting throttle()************************************");
        logger.info("********************************************************************************");
    }

    private void collectAggregatesUnderThrottle(String agentId, boolean postV09, long captureTime,
            List<Aggregate.SharedQueryText> sharedQueryTexts,
            List<OldAggregatesByType> aggregatesByTypeList,
            StreamObserver<AggregateResponseMessage> responseObserver) {

        //ADDED
        logger.info("********************************************************************************");
        logger.info("*************Entering collectAggregatesUnderThrottle()*************************************");
        logger.info("****agentId=<{}>, postV09=<{}>, captureTime=<{}>", agentId, postV09, captureTime);


        //ADDED
        logger.info("Logging shared query texts");
        for (Aggregate.SharedQueryText sharedQueryText : sharedQueryTexts) {
            //ADDED
            logger.info("shared query text -> {}", sharedQueryText);
        }

        //ADDED
        logger.info("----------------------------------------------------------------");
        logger.info("Logging List<OldAggregatesByType> aggregatesByTypeList");
        for ( OldAggregatesByType aggregatesByTypeObj :  aggregatesByTypeList) {
            //ADDED
            logger.info("aggregatesByTypeObj -> {}", aggregatesByTypeObj);
        }


        String postV09AgentId;
        try {
            postV09AgentId = grpcCommon.getAgentId(agentId, postV09);
        } catch (Throwable t) {
            logger.error("{} - {}", getAgentIdForLogging(agentId, postV09), t.getMessage(), t);
            responseObserver.onError(t);
            return;
        }
        try {
            aggregateDao.store(postV09AgentId, captureTime, aggregatesByTypeList, sharedQueryTexts);
        } catch (Throwable t) {
            logger.error("{} - {}", postV09AgentId, t.getMessage(), t);
            responseObserver.onError(t);
            return;
        }
        String agentDisplay;
        try {
            agentDisplay = agentDisplayDao.readFullDisplay(postV09AgentId);
        } catch (Exception e) {
            logger.error("{} - {}", postV09AgentId, e.getMessage(), e);
            responseObserver.onError(e);
            return;
        }
        try {
            centralAlertingService.checkForDeletedAlerts(postV09AgentId);
            centralAlertingService.checkAggregateAlertsAsync(postV09AgentId, agentDisplay,
                    captureTime);
        } catch (InterruptedException e) {
            // probably shutdown requested
            logger.debug(e.getMessage(), e);
        }
        //ADDED
        //ADDED
        logger.info("****StreamObserver<AggregateResponseMessage> responseObserver BEFORE responseObserver.onNext(): {}", responseObserver);
        logger.info("*************************************************");
        responseObserver.onNext(AggregateResponseMessage.newBuilder()
                .setNextDelayMillis(getNextDelayMillis())
                .build());
        responseObserver.onCompleted();
        logger.info("****StreamObserver<AggregateResponseMessage> responseObserver AFTER responseObserver.onCompleted(): {}", responseObserver);
        logger.info("*************Exiting collectAggregatesUnderThrottle()************************************");
        logger.info("********************************************************************************");


    }

    private void collectGaugeValuesUnderThrottle(GaugeValueMessage request,
            StreamObserver<GaugeValueResponseMessage> responseObserver) {
        String postV09AgentId;


        //ADDED
        logger.info("********************************************************************************");
        logger.info("*************Entering collectGaugeValuesUnderThrottle()*************************************");
        logger.info("****request {}", request);


        try {
            postV09AgentId = grpcCommon.getAgentId(request.getAgentId(), request.getPostV09());
        } catch (Throwable t) {
            logger.error("{} - {}",
                    getAgentIdForLogging(request.getAgentId(), request.getPostV09()),
                    t.getMessage(), t);
            responseObserver.onError(t);
            return;
        }
        long maxCaptureTime = 0;
        try {
            List<GaugeValue> gaugeValues = getFutureProofGaugeValues(request.getGaugeValueList());
            gaugeValueDao.store(postV09AgentId, gaugeValues);
            for (GaugeValue gaugeValue : gaugeValues) {
                maxCaptureTime = Math.max(maxCaptureTime, gaugeValue.getCaptureTime());
            }
        } catch (Throwable t) {
            logger.error("{} - {}", postV09AgentId, t.getMessage(), t);
            responseObserver.onError(t);
            return;
        }
        try {
            heartbeatDao.store(postV09AgentId);
        } catch (Throwable t) {
            logger.error("{} - {}", postV09AgentId, t.getMessage(), t);
            responseObserver.onError(t);
            return;
        }
        String agentDisplay;
        try {
            agentDisplay = agentDisplayDao.readFullDisplay(postV09AgentId);
        } catch (Throwable t) {
            logger.error("{} - {}", postV09AgentId, t.getMessage(), t);
            responseObserver.onError(t);
            return;
        }
        try {
            centralAlertingService.checkForDeletedAlerts(postV09AgentId);
            centralAlertingService.checkGaugeAndHeartbeatAlertsAsync(postV09AgentId, agentDisplay,
                    maxCaptureTime);
        } catch (InterruptedException e) {
            // probably shutdown requested
            logger.debug(e.getMessage(), e);
        }
        boolean resendInit;
        try {
            resendInit = agentConfigDao.read(postV09AgentId) == null
                    || environmentDao.read(postV09AgentId) == null;
        } catch (Throwable t) {
            // log as error, but not worth failing for this
            logger.error("{} - {}", postV09AgentId, t.getMessage(), t);
            resendInit = false;
        }

        //ADDED
        logger.info("****StreamObserver<GaugeValueResponseMessage> responseObserver BEFORE responseObserver.onNext(): {}", responseObserver);
        logger.info("*************************************************");
        responseObserver.onNext(GaugeValueResponseMessage.newBuilder()
                .setResendInit(resendInit)
                .build());
        responseObserver.onCompleted();
        logger.info("****StreamObserver<GaugeValueResponseMessage> responseObserver AFTER responseObserver.onCompleted(): {}", responseObserver);
        logger.info("*************Exiting collectGaugeValuesUnderThrottle()************************************");
        logger.info("********************************************************************************");


    }

    private void collectTraceUnderThrottle(String agentId, boolean postV09, Trace trace,
            StreamObserver<EmptyMessage> responseObserver) {

        //ADDED
        logger.info("********************************************************************************");
        logger.info("*************Entering collectTraceUnderThrottle()*************************************");
        logger.info("****agentId=<{}>, postV09=<{}>, trace -> {}", agentId, postV09, trace);



        String postV09AgentId;
        try {
            postV09AgentId = grpcCommon.getAgentId(agentId, postV09);
        } catch (Throwable t) {
            logger.error("{} - {}", getAgentIdForLogging(agentId, postV09), t.getMessage(), t);
            responseObserver.onError(t);
            return;
        }
        try {
            traceDao.store(postV09AgentId, getFutureProofTrace(trace));
        } catch (Throwable t) {
            logger.error("{} - {}", postV09AgentId, t.getMessage(), t);
            responseObserver.onError(t);
            return;
        }

        //ADDED
        logger.info("****StreamObserver<EmptyMessage> responseObserver BEFORE responseObserver.onNext(): {}", responseObserver);
        logger.info("*************************************************");
        responseObserver.onNext(EmptyMessage.getDefaultInstance());
        responseObserver.onCompleted();
        logger.info("****StreamObserver<EmptyMessage> responseObserver AFTER responseObserver.onCompleted(): {}", responseObserver);
        logger.info("*************Exiting collectTraceUnderThrottle()************************************");
        logger.info("********************************************************************************");
    }

    private long getFutureProofAggregateCaptureTime(long captureTime) {
        long currentTimeMillis = clock.currentTimeMillis();
        if (tooFarInTheFuture(captureTime, currentTimeMillis)) {
            // too far in the future just use current time
            return (long) Math.floor(currentTimeMillis / 60000.0) * 60000;
        } else {
            return captureTime;
        }
    }

    private List<GaugeValue> getFutureProofGaugeValues(List<GaugeValue> gaugeValues) {
        List<GaugeValue> futureProofGaugeValues = new ArrayList<>(gaugeValues);
        long currentTimeMillis = clock.currentTimeMillis();
        for (ListIterator<GaugeValue> i = futureProofGaugeValues.listIterator(); i.hasNext();) {
            GaugeValue gaugeValue = i.next();
            if (tooFarInTheFuture(gaugeValue.getCaptureTime(), currentTimeMillis)) {
                // too far in the future just use current time
                i.set(gaugeValue.toBuilder()
                        .setCaptureTime(currentTimeMillis)
                        .build());
            }
        }
        return futureProofGaugeValues;
    }

    private Trace getFutureProofTrace(Trace trace) {
        long currentTimeMillis = clock.currentTimeMillis();
        if (tooFarInTheFuture(trace.getHeader().getCaptureTime(), currentTimeMillis)) {
            // too far in the future just use current time
            return trace.toBuilder()
                    .setHeader(trace.getHeader().toBuilder()
                            .setCaptureTime(currentTimeMillis))
                    .build();
        } else {
            return trace;
        }
    }

    private int getNextDelayMillis() {
        long currentishTimeMillis = clock.currentTimeMillis() + 10000;
        if (currentishTimeMillis > currentMinute) {
            // race condition here is ok, at worst results in resetting nextDelay multiple times
            nextDelay.set(0);
            currentMinute = (long) Math.ceil(currentishTimeMillis / 60000.0) * 60000;
        }
        // spread out aggregate collections 100 milliseconds a part, rolling over at 10 seconds
        return nextDelay.getAndAdd(100) % 10000;
    }

    private String getAgentIdForLogging(String agentId, boolean postV09) {
        return grpcCommon.getAgentIdForLogging(agentId, postV09);
    }

    // far in the future capture times cause major problems for Cassandra TWCS since then every
    // rollup of recent data must consider an (increasingly) old sstable and (worse) all of the
    // sstables in between (because of possibility of tombstones in more recent sstables
    // invalidating the data read from the old sstable)
    //
    // another problem with far in the future capture times is that the adjusted TTL (see
    // Common.getAdjustedTTL()) will be really large, preventing that data from expiring along with
    // its friends (probably also contributing to TWCS problems)
    private static boolean tooFarInTheFuture(long captureTime, long currentTimeMillis) {
        return captureTime - currentTimeMillis > HOURS.toMillis(1);
    }

    private static String toString(Proto.Throwable t) {
        StringBuilder sb = new StringBuilder();
        sb.append(t.getClassName());
        String message = t.getMessage();
        if (!message.isEmpty()) {
            sb.append(": ");
            sb.append(message);
        }
        for (Proto.StackTraceElement stackTraceElement : t.getStackTraceElementList()) {
            sb.append("\n\tat ");
            sb.append(new StackTraceElement(stackTraceElement.getClassName(),
                    stackTraceElement.getMethodName(), stackTraceElement.getFileName(),
                    stackTraceElement.getLineNumber()));
        }
        int framesInCommonWithEnclosing = t.getFramesInCommonWithEnclosing();
        if (framesInCommonWithEnclosing > 0) {
            sb.append("\n\t... ");
            sb.append(framesInCommonWithEnclosing);
            sb.append(" more");
        }
        if (t.hasCause()) {
            sb.append("\nCaused by: ");
            sb.append(toString(t.getCause()));
        }
        return sb.toString();
    }

    private static void log(Level level, String format, Object... arguments) {
        switch (level) {
            case ERROR:
                logger.error(format, arguments);
                break;
            case WARN:
                logger.warn(format, arguments);
                break;
            default:
                logger.info(format, arguments);
                break;
        }
    }

    private final class AggregateStreamObserver implements StreamObserver<AggregateStreamMessage> {

        private final StreamObserver<AggregateResponseMessage> responseObserver;
        private @MonotonicNonNull AggregateStreamHeader streamHeader;
        private List<Aggregate.SharedQueryText> sharedQueryTexts = new ArrayList<>();
        private Map<String, OldAggregatesByType.Builder> aggregatesByTypeMap = new HashMap<>();

        private AggregateStreamObserver(StreamObserver<AggregateResponseMessage> responseObserver) {
            this.responseObserver = responseObserver;
        }

        @Override
        public void onNext(AggregateStreamMessage value) {
            try {
                onNextInternal(value);
            } catch (Throwable t) {
                logError(t);
                throw t;
            }
        }

        @Instrumentation.Transaction(transactionType = "gRPC", transactionName = "Aggregates",
                traceHeadline = "Collect aggregates: {{this.streamHeader.agentId}}",
                timer = "aggregates")
        @Override
        public void onCompleted() {
            try {
                onCompletedInternal();
            } catch (Throwable t) {
                logError(t);
                throw t;
            }
        }

        @Override
        public void onError(Throwable t) {
            logError(t);
        }

        private void onNextInternal(AggregateStreamMessage value) {
            switch (value.getMessageCase()) {
                case STREAM_HEADER:
                    streamHeader = value.getStreamHeader();
                    break;
                case SHARED_QUERY_TEXT:
                    sharedQueryTexts.add(value.getSharedQueryText());
                    break;
                case OVERALL_AGGREGATE:
                    OverallAggregate overallAggregate = value.getOverallAggregate();
                    String transactionType = overallAggregate.getTransactionType();

                    //ADDED
                    logger.info("******************************************************************************");
                    logger.info("onNextInternal: overallAggregate: {}: transactionType: {}", overallAggregate, transactionType);


                    aggregatesByTypeMap.put(transactionType, OldAggregatesByType.newBuilder()
                            .setTransactionType(transactionType)
                            .setOverallAggregate(overallAggregate.getAggregate()));
                    break;
                case TRANSACTION_AGGREGATE:
                    TransactionAggregate transactionAggregate = value.getTransactionAggregate();
                    OldAggregatesByType.Builder builder = checkNotNull(
                            aggregatesByTypeMap.get(transactionAggregate.getTransactionType()));
                    builder.addTransactionAggregate(OldTransactionAggregate.newBuilder()
                            .setTransactionName(transactionAggregate.getTransactionName())
                            .setAggregate(transactionAggregate.getAggregate())
                            .build());
                    break;
                default:
                    throw new RuntimeException("Unexpected message: " + value.getMessageCase());
            }
        }

        private void onCompletedInternal() {
            checkNotNull(streamHeader);
            List<OldAggregatesByType> aggregatesByTypeList = new ArrayList<>();
            for (OldAggregatesByType.Builder aggregatesByType : aggregatesByTypeMap.values()) {
                aggregatesByTypeList.add(aggregatesByType.build());
            }
            throttleCollectAggregates(streamHeader.getAgentId(), streamHeader.getPostV09(),
                    getFutureProofAggregateCaptureTime(streamHeader.getCaptureTime()),
                    sharedQueryTexts,
                    aggregatesByTypeList, responseObserver);
        }

        private void logError(Throwable t) {
            if (streamHeader == null) {
                logger.error(t.getMessage(), t);
            } else {
                logger.error("{} - {}", grpcCommon.getAgentIdForLogging(streamHeader.getAgentId(),
                        streamHeader.getPostV09()), t.getMessage(), t);
            }
        }
    }

    private final class TraceStreamObserver implements StreamObserver<TraceStreamMessage> {

        private final StreamObserver<EmptyMessage> responseObserver;
        private @MonotonicNonNull TraceStreamHeader streamHeader;
        private List<Trace.SharedQueryText> sharedQueryTexts = new ArrayList<>();
        private @MonotonicNonNull Trace trace;
        private List<Trace.Entry> entries = new ArrayList<>();
        private List<Aggregate.Query> queries = new ArrayList<>();
        private @MonotonicNonNull Profile mainThreadProfile;
        private @MonotonicNonNull Profile auxThreadProfile;
        private Trace. /*@MonotonicNonNull*/ Header header;
        private @MonotonicNonNull TraceStreamCounts streamCounts;

        private TraceStreamObserver(StreamObserver<EmptyMessage> responseObserver) {
            this.responseObserver = responseObserver;
        }

        @Override
        public void onNext(TraceStreamMessage value) {
            try {
                onNextInternal(value);
            } catch (Throwable t) {
                logError(t);
                throw t;
            }
        }

        @Instrumentation.Transaction(transactionType = "gRPC", transactionName = "Trace",
                traceHeadline = "Collect trace: {{this.streamHeader.agentId}}", timer = "trace")
        @Override
        public void onCompleted() {
            try {
                onCompletedInternal();
            } catch (Throwable t) {
                logError(t);
                throw t;
            }
        }

        @Override
        public void onError(Throwable t) {
            logError(t);
        }

        private void onNextInternal(TraceStreamMessage value) {
            switch (value.getMessageCase()) {
                case STREAM_HEADER:
                    streamHeader = value.getStreamHeader();
                    break;
                case SHARED_QUERY_TEXT:
                    sharedQueryTexts.add(value.getSharedQueryText());
                    break;
                case TRACE:
                    // this is for 0.9.12 and prior agents
                    trace = value.getTrace();
                    break;
                case ENTRY:
                    entries.add(value.getEntry());
                    break;
                case QUERIES:
                    queries.addAll(value.getQueries().getQueryList());
                    break;
                case MAIN_THREAD_PROFILE:
                    mainThreadProfile = value.getMainThreadProfile();
                    break;
                case AUX_THREAD_PROFILE:
                    auxThreadProfile = value.getAuxThreadProfile();
                    break;
                case HEADER:
                    header = value.getHeader();
                    break;
                case STREAM_COUNTS:
                    streamCounts = value.getStreamCounts();
                    break;
                default:
                    throw new RuntimeException("Unexpected message: " + value.getMessageCase());
            }
        }

        private void onCompletedInternal() {
            checkNotNull(streamHeader);
            if (trace == null) {
                // this is for 0.9.13 and later agents
                checkNotNull(streamCounts);
                if (!isEverythingReceived()) {
                    // no point in calling onError to force re-try since gRPC maxMessageSize limit
                    // will just be hit again
                    responseObserver.onNext(EmptyMessage.getDefaultInstance());
                    responseObserver.onCompleted();
                    return;
                }
                Trace.Builder builder = Trace.newBuilder()
                        .setId(streamHeader.getTraceId())
                        .setUpdate(streamHeader.getUpdate())
                        .setHeader(checkNotNull(header))
                        .addAllEntry(entries)
                        .addAllQuery(queries)
                        .addAllSharedQueryText(sharedQueryTexts);
                if (mainThreadProfile != null) {
                    builder.setMainThreadProfile(mainThreadProfile);
                }
                if (auxThreadProfile != null) {
                    builder.setAuxThreadProfile(auxThreadProfile);
                }
                trace = builder.build();
            } else {
                trace = trace.toBuilder()
                        .addAllSharedQueryText(sharedQueryTexts)
                        .build();
            }
            throttledCollectTrace(streamHeader.getAgentId(), streamHeader.getPostV09(), trace,
                    responseObserver);
        }

        @RequiresNonNull({"streamHeader", "streamCounts"})
        private boolean isEverythingReceived() {
            // validate that all data was received, may not receive everything due to gRPC
            // maxMessageSize limit exceeded: "Compressed frame exceeds maximum frame size"
            if (header == null) {
                logger.error("{} - did not receive header, likely due to gRPC maxMessageSize limit"
                        + " exceeded", getAgentIdForLogging());
                return false;
            }
            if (sharedQueryTexts.size() < streamCounts.getSharedQueryTextCount()) {
                logger.error("{} - expected {} shared query texts, but only received {}, likely due"
                        + " to gRPC maxMessageSize limit exceeded for some of them",
                        getAgentIdForLogging(), streamCounts.getSharedQueryTextCount(),
                        sharedQueryTexts.size());
                return false;
            }
            if (entries.size() < streamCounts.getEntryCount()) {
                logger.error("{} - expected {} entries, but only received {}, likely due to gRPC"
                        + " maxMessageSize limit exceeded for some of them", getAgentIdForLogging(),
                        streamCounts.getEntryCount(), entries.size());
                return false;
            }
            checkState(sharedQueryTexts.size() == streamCounts.getSharedQueryTextCount());
            checkState(entries.size() == streamCounts.getEntryCount());
            return true;
        }

        private void logError(Throwable t) {
            if (streamHeader == null) {
                logger.error(t.getMessage(), t);
            } else {
                logger.error("{} - {}", getAgentIdForLogging(), t.getMessage(), t);
            }
        }

        @RequiresNonNull("streamHeader")
        private String getAgentIdForLogging() {
            return grpcCommon.getAgentIdForLogging(streamHeader.getAgentId(),
                    streamHeader.getPostV09());
        }
    }
}
