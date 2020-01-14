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

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Exchanger;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import com.google.common.base.Stopwatch;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.hash.Hashing;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.grpc.Server;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import io.netty.channel.EventLoopGroup;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.glowroot.agent.central.GrCentralObjToString;
import org.glowroot.common.Constants;
import org.glowroot.wire.api.model.AgentConfigOuterClass.AgentConfig;
import org.glowroot.wire.api.model.AggregateOuterClass.Aggregate;
import org.glowroot.wire.api.model.CollectorServiceGrpc.CollectorServiceImplBase;
import org.glowroot.wire.api.model.CollectorServiceOuterClass.AggregateResponseMessage;
import org.glowroot.wire.api.model.CollectorServiceOuterClass.AggregateStreamMessage;
import org.glowroot.wire.api.model.CollectorServiceOuterClass.EmptyMessage;
import org.glowroot.wire.api.model.CollectorServiceOuterClass.GaugeValueMessage;
import org.glowroot.wire.api.model.CollectorServiceOuterClass.GaugeValueResponseMessage;
import org.glowroot.wire.api.model.CollectorServiceOuterClass.InitMessage;
import org.glowroot.wire.api.model.CollectorServiceOuterClass.InitResponse;
import org.glowroot.wire.api.model.CollectorServiceOuterClass.LogMessage;
import org.glowroot.wire.api.model.CollectorServiceOuterClass.TraceStreamMessage;
import org.glowroot.wire.api.model.DownstreamServiceGrpc.DownstreamServiceImplBase;
import org.glowroot.wire.api.model.DownstreamServiceOuterClass.AgentConfigUpdateRequest;
import org.glowroot.wire.api.model.DownstreamServiceOuterClass.AgentResponse;
import org.glowroot.wire.api.model.DownstreamServiceOuterClass.AgentResponse.MessageCase;
import org.glowroot.wire.api.model.DownstreamServiceOuterClass.CentralRequest;
import org.glowroot.wire.api.model.DownstreamServiceOuterClass.ReweaveRequest;
import org.glowroot.wire.api.model.ProfileOuterClass.Profile;
import org.glowroot.wire.api.model.TraceOuterClass.Trace;

import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

class GrpcServerWrapper {

    private static final Logger logger = LoggerFactory.getLogger(GrpcServerWrapper.class);

    private final EventLoopGroup bossEventLoopGroup;
    private final EventLoopGroup workerEventLoopGroup;
    private final ExecutorService executor;
    private final Server server;

    private final DownstreamServiceImpl downstreamService;

    private volatile @MonotonicNonNull AgentConfig agentConfig;

    GrpcServerWrapper(TraceCollector collector, int port) throws IOException {
        bossEventLoopGroup = EventLoopGroups.create("Glowroot-IT-Harness-GRPC-Boss-ELG");
        workerEventLoopGroup = EventLoopGroups.create("Glowroot-IT-Harness-GRPC-Worker-ELG");
        executor = Executors.newCachedThreadPool(
                new ThreadFactoryBuilder()
                        .setDaemon(true)
                        .setNameFormat("Glowroot-IT-Harness-GRPC-Executor-%d")
                        .build());
        downstreamService = new DownstreamServiceImpl();
        server = NettyServerBuilder.forPort(port)
                .bossEventLoopGroup(bossEventLoopGroup)
                .workerEventLoopGroup(workerEventLoopGroup)
                .executor(executor)
                .addService(new CollectorServiceImpl(collector).bindService())
                .addService(downstreamService.bindService())
                .maxInboundMessageSize(1024 * 1024 * 100)
                .build()
                .start();
    }

    AgentConfig getAgentConfig() throws InterruptedException {
        Stopwatch stopwatch = Stopwatch.createStarted();
        while (agentConfig == null && stopwatch.elapsed(SECONDS) < 10) {
            MILLISECONDS.sleep(10);
        }
        if (agentConfig == null) {
            throw new IllegalStateException("Timed out waiting to receive agent config");
        }
        return agentConfig;
    }

    void updateAgentConfig(AgentConfig agentConfig) throws Exception {
        downstreamService.updateAgentConfig(agentConfig);
        this.agentConfig = agentConfig;
    }

    int reweave() throws Exception {
        return downstreamService.reweave();
    }

    void close() throws InterruptedException {
        Stopwatch stopwatch = Stopwatch.createStarted();
        while (stopwatch.elapsed(SECONDS) < 10 && !downstreamService.closedByAgent) {
            MILLISECONDS.sleep(10);
        }
        checkState(downstreamService.closedByAgent);
        // TODO shutdownNow() has been needed to interrupt grpc threads since grpc-java 1.7.0
        server.shutdownNow();
        if (!server.awaitTermination(10, SECONDS)) {
            throw new IllegalStateException("Could not terminate channel");
        }
        // not sure why, but server needs a little extra time to shut down properly
        // without this sleep, this warning is logged (but tests still pass):
        // io.grpc.netty.NettyServerHandler - Connection Error: RejectedExecutionException
        MILLISECONDS.sleep(100);
        executor.shutdown();
        if (!executor.awaitTermination(10, SECONDS)) {
            throw new IllegalStateException("Could not terminate executor");
        }
        if (!bossEventLoopGroup.shutdownGracefully(0, 0, SECONDS).await(10, SECONDS)) {
            throw new IllegalStateException("Could not terminate event loop group");
        }
        if (!workerEventLoopGroup.shutdownGracefully(0, 0, SECONDS).await(10, SECONDS)) {
            throw new IllegalStateException("Could not terminate event loop group");
        }
    }

    private class CollectorServiceImpl extends CollectorServiceImplBase {

        private final TraceCollector collector;
        private final Map<String, String> fullTexts = Maps.newConcurrentMap();

        private CollectorServiceImpl(TraceCollector collector) {
            this.collector = collector;
        }

        @Override
        public void collectInit(InitMessage request,
                StreamObserver<InitResponse> responseObserver) {

            /*ADDED*/
            logger.info("********************************************************************************");
            logger.info("****Entering CollectorServiceImpl.collectInit(): Request -> {}*******", request);
            
            agentConfig = request.getAgentConfig();
            responseObserver.onNext(InitResponse.getDefaultInstance());
            responseObserver.onCompleted();

            /*ADDED*/
            logger.info("********************************************************************************");
            logger.info("****Exiting CollectorServiceImpl.collectInit(): **************");
        }

        @Override
        public StreamObserver<AggregateStreamMessage> collectAggregateStream(
                final StreamObserver<AggregateResponseMessage> responseObserver) {

            /*ADDED*/
            logger.info("********************************************************************************");
            logger.info("****Entering CollectorServiceImpl.collectAggregateStream, returning stream observer(): *******");
            
            return new StreamObserver<AggregateStreamMessage>() {
                @Override
                public void onNext(AggregateStreamMessage value) {}
                @Override
                public void onError(Throwable t) {
                    logger.error(t.getMessage(), t);
                }
                @Override
                public void onCompleted() {
                    responseObserver.onNext(AggregateResponseMessage.getDefaultInstance());
                    responseObserver.onCompleted();
                }
            };
        }

        @Override
        public void collectGaugeValues(GaugeValueMessage request,
                StreamObserver<GaugeValueResponseMessage> responseObserver) {
            /*ADDED*/
            logger.info("********************************************************************************");
            logger.info("****Entering CollectorServiceImpl.collectGaugeValues(), Request -> {}: *******", request);
            responseObserver.onNext(GaugeValueResponseMessage.getDefaultInstance());
            responseObserver.onCompleted();
            /*ADDED*/
            logger.info("********************************************************************************");
            logger.info("****Exiting CollectorServiceImpl.collectGaugeValues()******");
        }

        @Override
        public StreamObserver<TraceStreamMessage> collectTraceStream(
                final StreamObserver<EmptyMessage> responseObserver) {

            /*ADDED*/
            logger.info("********************************************************************************");
            logger.info("****Entering ++GrpcServerWrapper.collectTraceStream(), returning StreamObserver<TraceStreamMessage>()*****");

            return new StreamObserver<TraceStreamMessage>() {

                private @MonotonicNonNull String traceId;
                private List<Trace.SharedQueryText> sharedQueryTexts = Lists.newArrayList();
                private List<Trace.Entry> entries = Lists.newArrayList();
                private List<Aggregate.Query> queries = Lists.newArrayList();
                private @MonotonicNonNull Profile mainThreadProfile;
                private @MonotonicNonNull Profile auxThreadProfile;
                private Trace. /*@MonotonicNonNull*/ Header header;

                @Override
                public void onNext(TraceStreamMessage value) {

                    StringBuilder toLog = new StringBuilder("+++++++++++++++GrpcServerWrapper.collectTraceStream+++++++++++++++GrpcServerWrapper.collectTraceStream++++++++++++\n").
                                                     append("+++++++++++++++GrpcServerWrapper.collectTraceStream+++++++++++++++GrpcServerWrapper.collectTraceStream++++++++++++\n").
                                                     append("+++++++++++++++GrpcServerWrapper.collectTraceStream+++++++++++++++GrpcServerWrapper.collectTraceStream++++++++++++\n").
                                                     append(GrCentralObjToString.TraceStreamMessageToString(value)).
                                                     append("+++++++++++++++GrpcServerWrapper.collectTraceStream+++++++++++++++GrpcServerWrapper.collectTraceStream++++++++++++\n").
                                                     append("+++++++++++++++GrpcServerWrapper.collectTraceStream+++++++++++++++GrpcServerWrapper.collectTraceStream++++++++++++\n").
                                                     append("+++++++++++++++GrpcServerWrapper.collectTraceStream+++++++++++++++GrpcServerWrapper.collectTraceStream++++++++++++\n");

                   
                                                     

                    /*ADDED*/
                    logger.info("********************************************************************************\n");
                    logger.info("****Entering GrpcServerWrapper.onNext()***** TraceStreamMessage -> {}\n", value);

                    logger.info(toLog.toString());

                    try {
                        onNextInternal(value);
                    } catch (RuntimeException t) {
                        logger.error(t.getMessage(), t);
                        throw t;
                    } catch (Throwable t) {
                        logger.error(t.getMessage(), t);
                        throw new RuntimeException(t);
                    }

                    /*ADDED*/
                    logger.info("********************************************************************************\n");
                    logger.info("****Exiting GrpcServerWrapper.onNext()*****\n");
                }

                @Override
                public void onCompleted() {

                    /*ADDED*/
                    logger.info("********************************************************************************\n");
                    logger.info("****Entering GrpcServerWrapper.onCompleted()*****\n");

                    try {
                        onCompletedInternal(responseObserver);
                    } catch (RuntimeException t) {
                        logger.error(t.getMessage(), t);
                        throw t;
                    } catch (Throwable t) {
                        logger.error(t.getMessage(), t);
                        throw new RuntimeException(t);
                    }

                    /*ADDED*/
                    logger.info("********************************************************************************");
                    logger.info("****Exiting GrpcServerWrapper.onCompleted()*****");
                }

                @Override
                public void onError(Throwable t) {
                    logger.error(t.getMessage(), t);
                }

                private void onNextInternal(TraceStreamMessage value) {                    


                     /*ADDED*/
                     logger.info("********************************************************************************\n");
                     logger.info("****Entering GrpcServerWrapper.onNextInternal()*****: TraceStreamMessage -> {}\n", value);
 
                    String messageStr = value.getMessageCase().toString();
                    String val = "";

                    switch (value.getMessageCase()) {
                        case STREAM_HEADER:
                            traceId = value.getStreamHeader().getTraceId();
                            val = traceId;
                            break;
                        case SHARED_QUERY_TEXT:
                            sharedQueryTexts.add(Trace.SharedQueryText.newBuilder()
                                    .setFullText(resolveFullText(value.getSharedQueryText()))
                                    .build());
                            val = GrCentralObjToString.SharedQueryTextToString(value.getSharedQueryText());
                            break;
                        case ENTRY:
                            entries.add(value.getEntry());
                            val = GrCentralObjToString.EntryToString(value.getEntry());
                            break;
                        case QUERIES:
                            queries.addAll(value.getQueries().getQueryList());
                            val = GrCentralObjToString.QueryListToString(value.getQueries().getQueryList());
                            break;
                        case MAIN_THREAD_PROFILE:
                            mainThreadProfile = value.getMainThreadProfile();
                            val = GrCentralObjToString.ProfileToString(mainThreadProfile);
                            break;
                        case AUX_THREAD_PROFILE:
                            auxThreadProfile = value.getAuxThreadProfile();
                            val = GrCentralObjToString.ProfileToString(auxThreadProfile);
                            break;
                        case HEADER:
                            header = value.getHeader();
                            val = GrCentralObjToString.HeaderToString(header);
                            break;
                        case STREAM_COUNTS:
                            val = "No value, ITS STREAM_COUNTS^^^" + GrCentralObjToString.TraceStreamCountsToString(value.getStreamCounts());                           
                            break;
                        default:
                            throw new RuntimeException(
                                    "Unexpected message: " + value.getMessageCase());
                    }

                    StringBuilder toLog = new StringBuilder("+++++++++++++++GrpcServerWrapper.onNextInternal+++++++++++++++GrpcServerWrapper.onNextInternal++++++++++++\n").
                                                     append("+++++++++++++++GrpcServerWrapper.onNextInternal+++++++++++++++GrpcServerWrapper.onNextInternal++++++++++++\n").
                                                     append("+++++++++++++++GrpcServerWrapper.onNextInternal+++++++++++++++GrpcServerWrapper.onNextInternal++++++++++++\n").
                                                     append("++++++++Message Type: " + messageStr + "++++\n").
                                                     append("+++++++Messasge Contents:+++++++++++++\n" + val).
                                                     append("+++++++++++++++GrpcServerWrapper.onNextInternal+++++++++++++++GrpcServerWrapper.onNextInternal++++++++++++\n").
                                                     append("+++++++++++++++GrpcServerWrapper.onNextInternal+++++++++++++++GrpcServerWrapper.onNextInternal+++++++++++\n").
                                                     append("+++++++++++++++GrpcServerWrapper.onNextInternal+++++++++++++++GrpcServerWrapper.onNextInternal++++++++++++\n").
                                                     append("********************************************************************************\n").
                                                     append("****Exiting GrpcServerWrapper.onNextInternal()*************************\n");
                    logger.info(toLog.toString());
                }

                private void onCompletedInternal(
                        final StreamObserver<EmptyMessage> responseObserver) {

                    StringBuilder retVal = new StringBuilder("********************************************************************************\n").
                                                            append("********************GrpcServerWrapper.onCompletedInternal()***************\n");

                                       
                    Trace.Builder trace = Trace.newBuilder()
                            .setId(checkNotNull(traceId))
                            .setHeader(checkNotNull(header))
                            .addAllSharedQueryText(sharedQueryTexts)
                            .addAllEntry(entries)
                            .addAllQuery(queries);

                   
                    
                    if (mainThreadProfile != null) {
                        trace.setMainThreadProfile(mainThreadProfile);
                    }
                    if (auxThreadProfile != null) {
                        trace.setAuxThreadProfile(auxThreadProfile);
                    }
                    try {

                      /*ADDED*/
                      Trace tempTrace = trace.build();
                      retVal.append("********************************************************************************\n").
                           append("***GrpcServerWrapper.onCompletedInternal trace obj passed to collector.collectTrace(trace.build()\n" 
                           + GrCentralObjToString.TraceToString(tempTrace));


                        collector.collectTrace(tempTrace);
                    } catch (Throwable t) {
                        logger.error(t.getMessage(), t);
                        responseObserver.onError(t);
                        return;
                    }
                    responseObserver.onNext(EmptyMessage.getDefaultInstance());
                    responseObserver.onCompleted();

                    /*ADDED*/
                    retVal.append("********************************************************************************\n").
                           append("Just called: responseObserver.onNext(EmptyMessage.getDefaultInstance()) & responseObserver.onCompleted()\n").         
                           append("****Exiting CollectorServiceImpl.onCompletedInternal()\n");

                    logger.info(retVal.toString());
                }
            };
        }

        @Override
        public void log(LogMessage request, StreamObserver<EmptyMessage> responseObserver) {

            /*ADDED*/
            logger.info("********************************************************************************");
            logger.info("****Entering CollectorServiceImpl.log(): Request -> {}", request);

            try {
                collector.log(request.getLogEvent());
            } catch (Throwable t) {
                logger.error(t.getMessage(), t);
                responseObserver.onError(t);
                return;
            }
            responseObserver.onNext(EmptyMessage.getDefaultInstance());
            responseObserver.onCompleted();

             /*ADDED*/
             logger.info("********************************************************************************");
             logger.info("****Exiting CollectorServiceImpl.log()***************");

        }

        private String resolveFullText(Trace.SharedQueryText sharedQueryText) {
            String fullTextSha1 = sharedQueryText.getFullTextSha1();
            if (fullTextSha1.isEmpty()) {
                String fullText = sharedQueryText.getFullText();
                if (fullText.length() > 2 * Constants.TRACE_QUERY_TEXT_TRUNCATE) {
                    fullTextSha1 = Hashing.sha1().hashString(fullText, UTF_8).toString();
                    fullTexts.put(fullTextSha1, fullText);
                }
                return fullText;
            }
            String fullText = fullTexts.get(fullTextSha1);
            if (fullText == null) {
                throw new IllegalStateException(
                        "Full text not found for sha1: " + fullTextSha1);
            }
            return fullText;
        }
    }

    private static class DownstreamServiceImpl extends DownstreamServiceImplBase {

        private final AtomicLong nextRequestId = new AtomicLong(1);

        // expiration in the unlikely case that response is never returned from agent
        private final Cache<Long, ResponseHolder> responseHolders = CacheBuilder.newBuilder()
                .expireAfterWrite(1, HOURS)
                .build();

        private final StreamObserver<AgentResponse> responseObserver =
                new StreamObserver<AgentResponse>() {
                    @Override
                    public void onNext(AgentResponse value) {

                        /*ADDED*/
                        logger.info("********************************************************************************");
                        logger.info("****Entering DownstreamServiceImpl.StreamObserver.onNext() {}", value);

                        if (value.getMessageCase() == MessageCase.HELLO) {
                            return;
                        }
                        long requestId = value.getRequestId();
                        ResponseHolder responseHolder = responseHolders.getIfPresent(requestId);
                        responseHolders.invalidate(requestId);
                        if (responseHolder == null) {
                            logger.error("no response holder for request id: {}", requestId);
                            return;
                        }
                        try {
                            // this shouldn't timeout since it is the other side of the exchange
                            // that is waiting
                            responseHolder.response.exchange(value, 1, MINUTES);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            logger.error(e.getMessage(), e);
                        } catch (TimeoutException e) {
                            logger.error(e.getMessage(), e);
                        }

                        /*ADDED*/
                        logger.info("********************************************************************************");
                        logger.info("****Exiting DownstreamServiceImpl.StreamObserver.onNext()*********");
                    }
                    @Override
                    public void onCompleted() {

                        /*ADDED*/
                        logger.info("********************************************************************************");
                        logger.info("****Entering StreamObserver.onCompleted()***********");

                        checkNotNull(requestObserver).onCompleted();
                        closedByAgent = true;

                        /*ADDED*/
                        logger.info("********************************************************************************");
                        logger.info("****Exiting StreamObserver.onCompleted()***********");
                    }
                    @Override
                    public void onError(Throwable t) {
                        logger.error(t.getMessage(), t);
                    }
                };

        private volatile @MonotonicNonNull StreamObserver<CentralRequest> requestObserver;

        private volatile boolean closedByAgent;

        @Override
        public StreamObserver<AgentResponse> connect(
                StreamObserver<CentralRequest> requestObserver) {
         
            /*ADDED*/
            logger.info("********************************************************************************");
            logger.info("****Entering DownstreamServiceImpl.connect()****");
            
            this.requestObserver = requestObserver;

            /*ADDED*/
            logger.info("********************************************************************************");
            logger.info("****Exiting DownstreamServiceImpl.connect()****");

            return responseObserver;
        }

        private void updateAgentConfig(AgentConfig agentConfig) throws Exception {
  
            /*ADDED*/
            logger.info("********************************************************************************");
            logger.info("****Entering DownstreamServiceImpl.updateAgentConfig()****, agentConfig: {}", agentConfig);

            sendRequest(CentralRequest.newBuilder()
                    .setRequestId(nextRequestId.getAndIncrement())
                    .setAgentConfigUpdateRequest(AgentConfigUpdateRequest.newBuilder()
                            .setAgentConfig(agentConfig))
                    .build());

            /*ADDED*/
            logger.info("********************************************************************************");
            logger.info("****Exiting DownstreamServiceImpl.updateAgentConfig()****");
        }

        private int reweave() throws Exception {
            AgentResponse response = sendRequest(CentralRequest.newBuilder()
                    .setRequestId(nextRequestId.getAndIncrement())
                    .setReweaveRequest(ReweaveRequest.getDefaultInstance())
                    .build());
            return response.getReweaveResponse().getClassUpdateCount();
        }

        private AgentResponse sendRequest(CentralRequest request) throws Exception {

            /*ADDED*/
            logger.info("********************************************************************************");
            logger.info("****Entering DownstreamServiceImpl.sendRequest()****, request: {}", request);


            ResponseHolder responseHolder = new ResponseHolder();
            responseHolders.put(request.getRequestId(), responseHolder);
            while (requestObserver == null) {
                MILLISECONDS.sleep(10);
            }
            requestObserver.onNext(request);
            // timeout is in case agent never responds
            // passing AgentResponse.getDefaultInstance() is just dummy (non-null) value
            AgentResponse response = responseHolder.response
                    .exchange(AgentResponse.getDefaultInstance(), 1, MINUTES);
            if (response.getMessageCase() == MessageCase.UNKNOWN_REQUEST_RESPONSE) {
                throw new IllegalStateException();
            }
            if (response.getMessageCase() == MessageCase.EXCEPTION_RESPONSE) {
                throw new IllegalStateException();
            }

            /*ADDED*/
            logger.info("********************************************************************************");
            logger.info("****Exiting DownstreamServiceImpl.sendRequest(): response -> {}****", response); 

            return response;
        }
    }

    private static class ResponseHolder {
        private final Exchanger<AgentResponse> response = new Exchanger<AgentResponse>();
    }
}
