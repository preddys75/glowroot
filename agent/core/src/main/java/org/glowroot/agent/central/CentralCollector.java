/*
 * Copyright 2015-2019 the original author or authors.
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
package org.glowroot.agent.central;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.io.Closer;
import io.grpc.stub.StreamObserver;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.glowroot.agent.central.CentralConnection.GrpcCall;
import org.glowroot.agent.collector.Collector;
import org.glowroot.agent.config.ConfigService;
import org.glowroot.agent.live.LiveJvmServiceImpl;
import org.glowroot.agent.live.LiveTraceRepositoryImpl;
import org.glowroot.agent.live.LiveWeavingServiceImpl;
import org.glowroot.common.util.OnlyUsedByTests;
import org.glowroot.common.util.PropertiesFiles;
import org.glowroot.common.util.Version;
import org.glowroot.wire.api.model.AgentConfigOuterClass.AgentConfig;
import org.glowroot.wire.api.model.AggregateOuterClass.Aggregate;
import org.glowroot.wire.api.model.CollectorServiceGrpc;
import org.glowroot.wire.api.model.CollectorServiceGrpc.CollectorServiceStub;
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
import org.glowroot.wire.api.model.CollectorServiceOuterClass.InitMessage.Environment;
import org.glowroot.wire.api.model.CollectorServiceOuterClass.InitResponse;
import org.glowroot.wire.api.model.CollectorServiceOuterClass.LogMessage;
import org.glowroot.wire.api.model.CollectorServiceOuterClass.LogMessage.LogEvent;
import org.glowroot.wire.api.model.CollectorServiceOuterClass.LogMessage.LogEvent.Level;
import org.glowroot.wire.api.model.CollectorServiceOuterClass.TraceStreamMessage;
import org.glowroot.wire.api.model.CollectorServiceOuterClass.TraceStreamMessage.Queries;
import org.glowroot.wire.api.model.CollectorServiceOuterClass.TraceStreamMessage.TraceStreamCounts;
import org.glowroot.wire.api.model.CollectorServiceOuterClass.TraceStreamMessage.TraceStreamHeader;
import org.glowroot.wire.api.model.ProfileOuterClass.Profile;
import org.glowroot.wire.api.model.TraceOuterClass.Trace;

import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class CentralCollector implements Collector {

    private static final Logger logger = LoggerFactory.getLogger(CentralCollector.class);

    // log startup messages using logger name "org.glowroot"
    private static final Logger startupLogger = LoggerFactory.getLogger("org.glowroot");

    // this is needed for webdriver tests that set aggregate interval to 1 second, which
    // then causes issues with aggregate backlogging due to the 0-10 seconds delay
    private static final boolean SKIP_DELAY;

    static {
        SKIP_DELAY = Integer.getInteger("glowroot.internal.rollup.0.intervalMillis", 60000) < 10000;
    }

    private final String agentId;
    private final String collectorAddress;
    private final ConfigService configService;

    private final boolean configReadOnly;
    private final File configSyncedFile;

    private final CentralConnection centralConnection;
    private final CollectorServiceStub collectorServiceStub;
    private final DownstreamServiceObserver downstreamServiceObserver;

    private final SharedQueryTextLimiter sharedQueryTextLimiter = new SharedQueryTextLimiter();

    private volatile @MonotonicNonNull Environment environment;
    private volatile int nextAggregateDelayMillis;

    public CentralCollector(Map<String, String> properties, String collectorAddress,
            @Nullable String collectorAuthority, List<File> confDirs, boolean configReadOnly,
            LiveJvmServiceImpl liveJvmService, LiveWeavingServiceImpl liveWeavingService,
            LiveTraceRepositoryImpl liveTraceRepository, AgentConfigUpdater agentConfigUpdater,
            ConfigService configService) throws Exception {

        String agentId = properties.get("glowroot.agent.id");
        if (agentId == null) {
            agentId = escapeHostname(InetAddress.getLocalHost().getHostName());
        } else if (agentId.endsWith("::")) {
            agentId += escapeHostname(InetAddress.getLocalHost().getHostName());
        } else if (!agentId.contains("::")) {
            // check for 0.9.x agent rollup id
            String v09AgentRollupId = properties.get("glowroot.agent.rollup.id");
            if (v09AgentRollupId != null) {
                agentId = convertFromV09AgentRollupId(v09AgentRollupId) + agentId;
            }
        }
        this.agentId = agentId;
        this.collectorAddress = collectorAddress;
        this.configService = configService;
        this.configReadOnly = configReadOnly;
        configSyncedFile = new File(confDirs.get(0), "config.synced");

        startupLogger.info("agent id: {}", agentId);

        AtomicBoolean inConnectionFailure = new AtomicBoolean();
        centralConnection = new CentralConnection(collectorAddress, collectorAuthority, confDirs,
                inConnectionFailure);
        collectorServiceStub = CollectorServiceGrpc.newStub(centralConnection.getChannel())
                .withCompression("gzip");
        downstreamServiceObserver = new DownstreamServiceObserver(centralConnection,
                agentConfigUpdater, configReadOnly, liveJvmService, liveWeavingService,
                liveTraceRepository, agentId, inConnectionFailure, sharedQueryTextLimiter);
    }

    @Override
    public void init(List<File> confDirs, final Environment environment, AgentConfig agentConfig,
            final AgentConfigUpdater agentConfigUpdater) throws IOException {

        /*ADDED*/
        startupLogger.info("********************************************************************************");
        startupLogger.info("init(): confDirs: {}, env: {}, agentConfig: {}, agentConfigUpdater: {}",
                confDirs, environment, agentConfig, agentConfigUpdater);


        final String configSyncedAgentId;
        if (configReadOnly) {
            configSyncedAgentId = "";
        } else {
            configSyncedAgentId = readConfigSyncedAgentId(configSyncedFile);
        }
        final InitMessage initMessage = InitMessage.newBuilder()
                .setAgentId(agentId)
                .setEnvironment(environment)
                .setAgentConfig(agentConfig.toBuilder()
                        .setConfigReadOnly(configReadOnly))
                .setOverwriteExistingAgentConfig(
                        !agentId.equals(configSyncedAgentId) || configReadOnly)
                .build();
        centralConnection.asyncCallInit(new GrpcCall<InitResponse>() {
            @Override
            public void call(StreamObserver<InitResponse> responseObserver) {

                /*ADDED*/
                startupLogger.info("********************************************************************************");
                startupLogger.info("InitMessage: {}, responseObserver {}",
                        initMessage, responseObserver);

                collectorServiceStub.collectInit(initMessage, responseObserver);
            }
            @Override
            void doWithResponse(InitResponse response) {
                CentralCollector.this.environment = environment;
                // don't need to suppress sending this log message to the central collector because
                // startup logger info messages are never sent to the central collector
                startupLogger.info("connected to the central collector {}, version {}",
                        collectorAddress, response.getGlowrootCentralVersion());
                if (isAgentVersionGreaterThanCentralVersion(
                        environment.getJavaInfo().getGlowrootAgentVersion(),
                        response.getGlowrootCentralVersion())) {
                    startupLogger.warn("the central collector version is older than the agent"
                            + " version which could cause unpredictable issues");
                }

                /*ADDED*/
                startupLogger.info("********************************************************************************");
                startupLogger.info("In doWithResponse, Here is response from Collector: {}", response);

                if (response.hasAgentConfig() && !configReadOnly) {
                    try {
                        /*ADDED*/
                        startupLogger.info("********************************************************************************");
                        startupLogger.info("Here is retrieved config: '{}'", response.hasAgentConfig());

                        agentConfigUpdater.update(response.getAgentConfig());

                        /*ADDED*/
                        startupLogger.info("********************************************************************************");
                        startupLogger.info("Here is agent config from response: '{}'", response.getAgentConfig());

                    } catch (IOException e) {
                        logger.error(e.getMessage(), e);
                    }
                }
                if (!agentId.equals(configSyncedAgentId) && !configReadOnly) {
                    try {
                        writeConfigSyncedFile(configSyncedFile, agentId);
                    } catch (IOException e) {
                        startupLogger.error("could not write to file '{}': {}",
                                configSyncedFile.getAbsolutePath(), e.getMessage(), e);
                    }
                }
                downstreamServiceObserver.connectAsync();
            }
        });
    }

    // collecting even when no aggregates since collection triggers transaction-based alerts
    @Override
    public void collectAggregates(AggregateReader aggregateReader) throws InterruptedException {

        /*ADDED*/
        logger.info("********************************************************************************");
        logger.info("***************Entering collectAggregates()**************************************");
        logger.info("AggregateReader aggregateReader: {}",aggregateReader);


        if (!SKIP_DELAY) {
            MILLISECONDS.sleep(nextAggregateDelayMillis);
        }
        centralConnection
                .blockingCallWithAFewRetries(new CollectAggregatesGrpcCall(aggregateReader));
        /*ADDED*/
        logger.info("********************************************************************************");
        logger.info("***************Exiting collectAggregates()**************************************");
        logger.info("AggregateReader aggregateReader: {}",aggregateReader);

    }

    @Override
    public void collectGaugeValues(final List<GaugeValue> gaugeValues) throws InterruptedException {

        /*ADDED*/
        logger.info("********************************************************************************");
        logger.info("***************Entering collectGaugeValues()**************************************");
        logger.info("***Printing list of List<GaugeValue> gaugeValues below");

        String g_print = "";
        for(GaugeValue gaugeValue : gaugeValues){
            g_print += "\ngaugeValue -> " + gaugeValue;
        }
        logger.info("********************************************************************************");
        logger.info("*********Gauge values: {}******************", g_print);



        final GaugeValueMessage gaugeValueMessage = GaugeValueMessage.newBuilder()
                .setAgentId(agentId)
                .addAllGaugeValue(gaugeValues)
                .setPostV09(true)
                .build();
        //ADDED
        logger.info("*********Gauge values combined message: {}", gaugeValueMessage);
        centralConnection.blockingCallWithAFewRetries(new GrpcCall<GaugeValueResponseMessage>() {
            @Override
            public void call(StreamObserver<GaugeValueResponseMessage> responseObserver) {
                /*ADDED*/
                logger.info("Response observer BEFORE collectorServiceStub.collectGaugeValues(): {}", responseObserver);
                collectorServiceStub.collectGaugeValues(gaugeValueMessage, responseObserver);
                logger.info("Response observer AFTER collectorServiceStub.collectGaugeValues(): {}", responseObserver);
            }
            @Override
            public void doWithResponse(GaugeValueResponseMessage response) {
                if (response.getResendInit() && environment != null) {
                    final InitMessage initMessage = InitMessage.newBuilder()
                            .setAgentId(agentId)
                            .setEnvironment(environment)
                            .setAgentConfig(configService.getAgentConfig())
                            .build();
                    //ADDED
                    logger.info("*********In doWithResponse() -> GaugeValueResponseMessage response: {}", response);
                    //ADDED
                    logger.info("*********Resending InitMessage in collectGaugeValues(: {}", initMessage);
                    // only once, since resendInit will continue to be sent back until it succeeds
                    centralConnection.asyncCallOnce(new GrpcCall<InitResponse>() {
                        @Override
                        void call(StreamObserver<InitResponse> responseObserver) {
                            collectorServiceStub.collectInit(initMessage, responseObserver);
                        }
                    });
                }
            }
        });
        /*ADDED*/
        logger.info("********************************************************************************");
        logger.info("***************Exiting collectGaugeValues()**************************************");

    }

    @Override
    public void collectTrace(TraceReader traceReader) throws InterruptedException {

        /*ADDED*/
        logger.info("********************************************************************************");
        logger.info("***************Entering collectTrace()**************************************");
        logger.info("***TraceReader traceReader entering collectTrace -> {}", traceReader);


        if (traceReader.partial()) {
            // do not retry partial transactions since they are live and reading from the trace
            // reader will not be idempotent, so could lead to confusing results
            centralConnection.blockingCallOnce(new CollectTraceGrpcCall(traceReader));
        } else {
            centralConnection.blockingCallWithAFewRetries(new CollectTraceGrpcCall(traceReader));
        }

        /*ADDED*/
        logger.info("********************************************************************************");
        logger.info("***************Exiting collectTrace()**************************************");
        logger.info("***TraceReader traceReader exiting collectTrace() -> {}", traceReader);
        logger.info("********************************************************************************");
    }

    @Override
    public void log(LogEvent logEvent) throws InterruptedException {

        /*ADDED*/
        // logger.info("********************************************************************************");
        // logger.info("***************Entering log()**************************************");
        // logger.info("***LogEvent logEvent -> {}", logEvent);



        if (centralConnection.suppressLogCollector()) {
            return;
        }
        if (logEvent.getLoggerName().equals("org.glowroot") && logEvent.getLevel() == Level.INFO) {
            // never send startup logger info messages to the central collector
            return;
        }
        final LogMessage logMessage = LogMessage.newBuilder()
                .setAgentId(agentId)
                .setLogEvent(logEvent)
                .setPostV09(true)
                .build();
        centralConnection.blockingCallWithAFewRetries(new GrpcCall<EmptyMessage>() {
            @Override
            public void call(StreamObserver<EmptyMessage> responseObserver) {
                collectorServiceStub.log(logMessage, responseObserver);
            }
        });
        /*ADDED*/
        // logger.info("********************************************************************************");
        // logger.info("***************Exiting log()**************************************");
        // logger.info("***LogEvent logEvent -> {}", logEvent);
    }

    @OnlyUsedByTests
    public void close() throws InterruptedException {
        downstreamServiceObserver.close();
        centralConnection.close();
    }

    @OnlyUsedByTests
    public void awaitClose() throws InterruptedException {
        centralConnection.awaitClose();
    }

    @VisibleForTesting
    static String escapeHostname(String hostname) {
        hostname = hostname.replace("\\", "\\\\");
        if (hostname.startsWith(":")) {
            hostname = "\\" + hostname;
        }
        while (hostname.contains("::")) {
            hostname = hostname.replace("::", ":\\:");
        }
        return hostname;
    }

    private static String convertFromV09AgentRollupId(String agentRollupId) {
        // old agent rollup id supported spaces around separator
        return agentRollupId.replaceAll(" */ *", "::").trim() + "::";
    }

    @VisibleForTesting
    static boolean isAgentVersionGreaterThanCentralVersion(String agentVersion,
            String centralVersion) {
        Pattern pattern = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)\\b.*");
        Matcher matcher = pattern.matcher(agentVersion);
        if (!matcher.matches()) {
            if (!agentVersion.equals(Version.UNKNOWN_VERSION)) {
                // conditional is to suppress warning when running tests
                startupLogger.warn("could not parse agent version: {}", agentVersion);
            }
            return false;
        }
        int agentMajor = Integer.parseInt(checkNotNull(matcher.group(1)));
        int agentMinor = Integer.parseInt(checkNotNull(matcher.group(2)));
        int agentPatch = Integer.parseInt(checkNotNull(matcher.group(3)));

        matcher = pattern.matcher(centralVersion);
        if (!matcher.matches()) {
            if (!centralVersion.equals("")) {
                // conditional is to suppress warning when running tests
                startupLogger.warn("could not parse central version: {}", centralVersion);
            }
            return false;
        }
        int centralMajor = Integer.parseInt(checkNotNull(matcher.group(1)));
        int centralMinor = Integer.parseInt(checkNotNull(matcher.group(2)));
        int centralPatch = Integer.parseInt(checkNotNull(matcher.group(3)));

        if (agentMajor > centralMajor) {
            return true;
        }
        if (agentMajor < centralMajor) {
            return false;
        }
        if (agentMinor > centralMinor) {
            return true;
        }
        if (agentMinor < centralMinor) {
            return false;
        }
        return agentPatch > centralPatch;
    }

    private static String readConfigSyncedAgentId(File file) throws IOException {
        if (file.exists()) {
            Properties properties = PropertiesFiles.load(file);
            return properties.getProperty("agent.id", "").trim();
        } else {
            return "";
        }
    }

    private static void writeConfigSyncedFile(File file, String agentId) throws IOException {
        Closer closer = Closer.create();
        try {
            PrintWriter out = closer.register(new PrintWriter(file, UTF_8.name()));
            out.println("# this file is created after the agent has pushed its local configuration"
                    + " to the central collector");
            out.println("#");
            out.println("# when this file is present (and the agent.id below matches the running"
                    + " agent's agent.id), the agent");
            out.println("# will overwrite its local configuration with the agent configuration it"
                    + " retrieves from the central");
            out.println("# collector on JVM startup");
            out.println("#");
            out.println("# when this file is not present (or the agent.id below does not match the"
                    + " running agent's agent.id),");
            out.println("# the agent will push its local configuration to the central collector on"
                    + " JVM startup (overwriting");
            out.println("# any existing remote configuration), after which the agent will"
                    + " (re-)create this file using the");
            out.println("# running agent's agent.id");
            out.println("");
            out.println("agent.id=" + agentId);
        } catch (Throwable t) {
            throw closer.rethrow(t);
        } finally {
            closer.close();
        }
    }

    private class CollectAggregatesGrpcCall extends GrpcCall<AggregateResponseMessage> {

        private final AggregateReader aggregateReader;
        private final List<String> fullTextSha1s = Lists.newArrayList();

        private CollectAggregatesGrpcCall(AggregateReader aggregateReader) {
            this.aggregateReader = aggregateReader;
        }

        @Override
        public void call(StreamObserver<AggregateResponseMessage> responseObserver) {
            final StreamObserver<AggregateStreamMessage> requestObserver =
                    collectorServiceStub.collectAggregateStream(responseObserver);
            requestObserver.onNext(AggregateStreamMessage.newBuilder()
                    .setStreamHeader(AggregateStreamHeader.newBuilder()
                            .setAgentId(agentId)
                            .setCaptureTime(aggregateReader.captureTime())
                            .setPostV09(true))
                    .build());
            // need to clear in case this is a retry
            fullTextSha1s.clear();
            try {
                aggregateReader.accept(new AggregateVisitorImpl(requestObserver));
            } catch (Throwable t) {
                logger.error(t.getMessage(), t);
                requestObserver.onError(t);
                return;
            }
            requestObserver.onCompleted();
        }

        @Override
        public void doWithResponse(AggregateResponseMessage response) {
            // Math.min is just for safety
            nextAggregateDelayMillis = Math.min(response.getNextDelayMillis(), 30000);
            for (String fullTextSha1 : fullTextSha1s) {
                sharedQueryTextLimiter.onSuccessfullySentToCentralCollector(fullTextSha1);
            }
        }

        private class AggregateVisitorImpl implements AggregateVisitor {

            private final StreamObserver<AggregateStreamMessage> requestObserver;

            private AggregateVisitorImpl(StreamObserver<AggregateStreamMessage> requestObserver) {
                this.requestObserver = requestObserver;
            }

            @Override
            public void visitOverallAggregate(String transactionType,
                    List<String> sharedQueryTexts, Aggregate overallAggregate) {

                //ADDED
                logger.info("******************************************************************************");
                logger.info("visitOverallAggregate(), transactionType: {}", transactionType);

                for (String sharedQueryText : sharedQueryTexts) {

                    Aggregate.SharedQueryText aggregateSharedQueryText = sharedQueryTextLimiter
                            .buildAggregateSharedQueryText(sharedQueryText, fullTextSha1s);
                    //ADDED
                    logger.info("******************************************************************************");
                    logger.info("visitOverallAggregate(), aggSharedQueryText: {}", sharedQueryText);

                    requestObserver.onNext(AggregateStreamMessage.newBuilder()
                            .setSharedQueryText(aggregateSharedQueryText)
                            .build());
                }
                //ADDED
                logger.info("****End visitOverallAggregate()*************************************************");


                requestObserver.onNext(AggregateStreamMessage.newBuilder()
                        .setOverallAggregate(OverallAggregate.newBuilder()
                                .setTransactionType(transactionType)
                                .setAggregate(overallAggregate))
                        .build());
            }

            @Override
            public void visitTransactionAggregate(String transactionType,
                    String transactionName, List<String> sharedQueryTexts,
                    Aggregate transactionAggregate) {


                //ADDED
                logger.info("******************************************************************************");
                logger.info("visitTransactionAggregate(), transactionType: {}, transactionName: {}", transactionType,transactionName);


                for (String sharedQueryText : sharedQueryTexts) {

                    //ADDED
                    logger.info("******************************************************************************");
                    logger.info("visitTransactionAggregate(), aggSharedQueryText: {}", sharedQueryText);

                    requestObserver.onNext(AggregateStreamMessage.newBuilder()
                            .setSharedQueryText(sharedQueryTextLimiter
                                    .buildAggregateSharedQueryText(sharedQueryText, fullTextSha1s))
                            .build());
                }
                requestObserver.onNext(AggregateStreamMessage.newBuilder()
                        .setTransactionAggregate(TransactionAggregate.newBuilder()
                                .setTransactionType(transactionType)
                                .setTransactionName(transactionName)
                                .setAggregate(transactionAggregate))
                        .build());
            }
        }
    }

    private class CollectTraceGrpcCall extends GrpcCall<EmptyMessage> {

        private final TraceReader traceReader;

        private final List<String> fullTextSha1s = Lists.newArrayList();

        private CollectTraceGrpcCall(TraceReader traceReader) {
            this.traceReader = traceReader;
        }

        @Override
        public void call(StreamObserver<EmptyMessage> responseObserver) {

            //ADDED
            logger.info("******************************************************************************");
            logger.info("CollectTraceGrpcCall.Scall(): observer, {}", responseObserver);

            StreamObserver<TraceStreamMessage> requestObserver =
                    collectorServiceStub.collectTraceStream(responseObserver);

            requestObserver.onNext(TraceStreamMessage.newBuilder()
                    .setStreamHeader(TraceStreamHeader.newBuilder()
                            .setAgentId(agentId)
                            .setTraceId(traceReader.traceId())
                            .setUpdate(traceReader.update())
                            .setPostV09(true))
                    .build());

            //ADDED
            logger.info("******************************************************************************");
            logger.info("agentId: {}, traceReader.traceId(): {}, traceReader.update(): {}",
                    agentId, traceReader.traceId(), traceReader.update());

            // need to clear in case this is a retry
            fullTextSha1s.clear();
            TraceVisitorImpl traceVisitor = new TraceVisitorImpl(requestObserver, fullTextSha1s);
            try {
                traceReader.accept(traceVisitor);
            } catch (Throwable t) {
                logger.error(t.getMessage(), t);
                requestObserver.onError(t);
                return;
            }
            requestObserver.onNext(TraceStreamMessage.newBuilder()
                    .setStreamCounts(TraceStreamCounts.newBuilder()
                            .setEntryCount(traceVisitor.entryCount)
                            .setSharedQueryTextCount(traceVisitor.sharedQueryTextCount))
                    .build());
            requestObserver.onCompleted();
        }

        @Override
        public void doWithResponse(EmptyMessage response) {
            for (String fullTextSha1 : fullTextSha1s) {
                sharedQueryTextLimiter.onSuccessfullySentToCentralCollector(fullTextSha1);
            }
        }
    }

    private class TraceVisitorImpl implements TraceVisitor {

        private final StreamObserver<TraceStreamMessage> requestObserver;
        private final List<String> fullTextSha1s;

        private int entryCount;
        private int sharedQueryTextCount;

        private TraceVisitorImpl(StreamObserver<TraceStreamMessage> requestObserver,
                List<String> fullTextSha1s) {
            this.requestObserver = requestObserver;
            this.fullTextSha1s = fullTextSha1s;
        }

        @Override
        public void visitEntry(Trace.Entry entry) {
            requestObserver.onNext(TraceStreamMessage.newBuilder()
                    .setEntry(entry)
                    .build());
            entryCount++;
        }

        @Override
        public void visitQueries(List<Aggregate.Query> queries) {
            requestObserver.onNext(TraceStreamMessage.newBuilder()
                    .setQueries(Queries.newBuilder()
                            .addAllQuery(queries))
                    .build());
        }

        @Override
        public void visitSharedQueryTexts(List<String> sharedQueryTexts) {
            for (String sharedQueryText : sharedQueryTexts) {
                Trace.SharedQueryText traceSharedQueryText = sharedQueryTextLimiter
                        .buildTraceSharedQueryText(sharedQueryText, fullTextSha1s);
                requestObserver.onNext(TraceStreamMessage.newBuilder()
                        .setSharedQueryText(traceSharedQueryText)
                        .build());
            }
            sharedQueryTextCount = sharedQueryTexts.size();
        }

        @Override
        public void visitMainThreadProfile(Profile profile) {
            requestObserver.onNext(TraceStreamMessage.newBuilder()
                    .setMainThreadProfile(profile)
                    .build());
        }

        @Override
        public void visitAuxThreadProfile(Profile profile) {
            requestObserver.onNext(TraceStreamMessage.newBuilder()
                    .setAuxThreadProfile(profile)
                    .build());
        }

        @Override
        public void visitHeader(Trace.Header header) {
            requestObserver.onNext(TraceStreamMessage.newBuilder()
                    .setHeader(header)
                    .build());
        }
    }
}
