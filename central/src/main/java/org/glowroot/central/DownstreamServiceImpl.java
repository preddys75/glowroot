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
package org.glowroot.central;

import static com.google.common.base.Preconditions.checkState;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

//ADDED
import java.io.ByteArrayOutputStream;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Exchanger;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.google.common.base.Optional;
import com.google.common.base.Stopwatch;
import com.google.common.cache.CacheBuilder;
import com.google.protobuf.CodedOutputStream;

import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.glowroot.central.util.ClusterManager;
import org.glowroot.central.util.DistributedExecutionMap;
import org.glowroot.common.live.ImmutableEntries;
import org.glowroot.common.live.ImmutableQueries;
import org.glowroot.common.live.LiveJvmService.AgentNotConnectedException;
import org.glowroot.common.live.LiveJvmService.AgentUnsupportedOperationException;
import org.glowroot.common.live.LiveJvmService.DirectoryDoesNotExistException;
import org.glowroot.common.live.LiveJvmService.UnavailableDueToRunningInJ9JvmException;
import org.glowroot.common.live.LiveJvmService.UnavailableDueToRunningInJreException;
import org.glowroot.common.live.LiveTraceRepository.Entries;
import org.glowroot.common.live.LiveTraceRepository.Queries;
import org.glowroot.common.util.OnlyUsedByTests;
import org.glowroot.wire.api.model.AgentConfigOuterClass.AgentConfig;
import org.glowroot.wire.api.model.AggregateOuterClass.Aggregate;
import org.glowroot.wire.api.model.DownstreamServiceGrpc.DownstreamServiceImplBase;
import org.glowroot.wire.api.model.DownstreamServiceOuterClass.AgentConfigUpdateRequest;
import org.glowroot.wire.api.model.DownstreamServiceOuterClass.AgentResponse;
import org.glowroot.wire.api.model.DownstreamServiceOuterClass.AuxThreadProfileRequest;
import org.glowroot.wire.api.model.DownstreamServiceOuterClass.AuxThreadProfileResponse;
import org.glowroot.wire.api.model.DownstreamServiceOuterClass.AvailableDiskSpaceRequest;
import org.glowroot.wire.api.model.DownstreamServiceOuterClass.AvailableDiskSpaceResponse;
import org.glowroot.wire.api.model.DownstreamServiceOuterClass.Capabilities;
import org.glowroot.wire.api.model.DownstreamServiceOuterClass.CapabilitiesRequest;
import org.glowroot.wire.api.model.DownstreamServiceOuterClass.CentralRequest;
import org.glowroot.wire.api.model.DownstreamServiceOuterClass.CurrentTimeRequest;
import org.glowroot.wire.api.model.DownstreamServiceOuterClass.EntriesRequest;
import org.glowroot.wire.api.model.DownstreamServiceOuterClass.EntriesResponse;
import org.glowroot.wire.api.model.DownstreamServiceOuterClass.ExplicitGcDisabledRequest;
import org.glowroot.wire.api.model.DownstreamServiceOuterClass.ForceGcRequest;
import org.glowroot.wire.api.model.DownstreamServiceOuterClass.FullTraceRequest;
import org.glowroot.wire.api.model.DownstreamServiceOuterClass.FullTraceResponse;
import org.glowroot.wire.api.model.DownstreamServiceOuterClass.GlobalMeta;
import org.glowroot.wire.api.model.DownstreamServiceOuterClass.GlobalMetaRequest;
import org.glowroot.wire.api.model.DownstreamServiceOuterClass.HeaderRequest;
import org.glowroot.wire.api.model.DownstreamServiceOuterClass.HeaderResponse;
import org.glowroot.wire.api.model.DownstreamServiceOuterClass.HeapDumpFileInfo;
import org.glowroot.wire.api.model.DownstreamServiceOuterClass.HeapDumpRequest;
import org.glowroot.wire.api.model.DownstreamServiceOuterClass.HeapDumpResponse;
import org.glowroot.wire.api.model.DownstreamServiceOuterClass.HeapHistogram;
import org.glowroot.wire.api.model.DownstreamServiceOuterClass.HeapHistogramRequest;
import org.glowroot.wire.api.model.DownstreamServiceOuterClass.HeapHistogramResponse;
import org.glowroot.wire.api.model.DownstreamServiceOuterClass.Hello;
import org.glowroot.wire.api.model.DownstreamServiceOuterClass.HelloAck;
import org.glowroot.wire.api.model.DownstreamServiceOuterClass.JstackRequest;
import org.glowroot.wire.api.model.DownstreamServiceOuterClass.JstackResponse;
import org.glowroot.wire.api.model.DownstreamServiceOuterClass.MBeanDump;
import org.glowroot.wire.api.model.DownstreamServiceOuterClass.MBeanDumpRequest;
import org.glowroot.wire.api.model.DownstreamServiceOuterClass.MBeanDumpRequest.MBeanDumpKind;
import org.glowroot.wire.api.model.DownstreamServiceOuterClass.MBeanMeta;
import org.glowroot.wire.api.model.DownstreamServiceOuterClass.MBeanMetaRequest;
import org.glowroot.wire.api.model.DownstreamServiceOuterClass.MainThreadProfileRequest;
import org.glowroot.wire.api.model.DownstreamServiceOuterClass.MainThreadProfileResponse;
import org.glowroot.wire.api.model.DownstreamServiceOuterClass.MatchingClassNamesRequest;
import org.glowroot.wire.api.model.DownstreamServiceOuterClass.MatchingMBeanObjectNamesRequest;
import org.glowroot.wire.api.model.DownstreamServiceOuterClass.MatchingMethodNamesRequest;
import org.glowroot.wire.api.model.DownstreamServiceOuterClass.MethodSignature;
import org.glowroot.wire.api.model.DownstreamServiceOuterClass.MethodSignaturesRequest;
import org.glowroot.wire.api.model.DownstreamServiceOuterClass.PreloadClasspathCacheRequest;
import org.glowroot.wire.api.model.DownstreamServiceOuterClass.QueriesRequest;
import org.glowroot.wire.api.model.DownstreamServiceOuterClass.QueriesResponse;
import org.glowroot.wire.api.model.DownstreamServiceOuterClass.ReweaveRequest;
import org.glowroot.wire.api.model.DownstreamServiceOuterClass.SystemPropertiesRequest;
import org.glowroot.wire.api.model.DownstreamServiceOuterClass.ThreadDump;
import org.glowroot.wire.api.model.DownstreamServiceOuterClass.ThreadDumpRequest;
import org.glowroot.wire.api.model.DownstreamServiceOuterClass.ThreadDumpResponse;
import org.glowroot.wire.api.model.ProfileOuterClass.Profile;
import org.glowroot.wire.api.model.TraceOuterClass.Trace;
import org.immutables.serial.Serial;
import org.immutables.value.Value;
import org.infinispan.util.function.SerializableFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.grpc.stub.StreamObserver;

//ADDED
import org.glowroot.common.util.GrObjToString;


//CHECK - INSTRUMENT
//LOG
class DownstreamServiceImpl extends DownstreamServiceImplBase {

    private static final Logger logger = LoggerFactory.getLogger(DownstreamServiceImpl.class);

    private final GrpcCommon grpcCommon;

    private final DistributedExecutionMap<String, ConnectedAgent> connectedAgents;

    private final ReadWriteLock shuttingDownLock = new ReentrantReadWriteLock(true);

    DownstreamServiceImpl(GrpcCommon grpcCommon, ClusterManager clusterManager) {
        this.grpcCommon = grpcCommon;
        connectedAgents = clusterManager.createDistributedExecutionMap("connectedAgents");

    }

    void stopSendingDownstreamRequests() {
        
        //ADDED - edited
        logger.info("******************stopSendingDownstreamRequests(): obtaining shuttingDownLock.writeLock().lock()***********");
        shuttingDownLock.writeLock().lock();
         //ADDED - edited
        logger.info("******************stopSendingDownstreamRequests(): OBTAINED shuttingDownLock.writeLock().lock()***********");
    }

    @Override
    public StreamObserver<AgentResponse> connect(StreamObserver<CentralRequest> requestObserver) {
        return new ConnectedAgent(requestObserver);
    }

    // returns true if agent was updated
    boolean updateAgentConfigIfConnected(String agentId, AgentConfig agentConfig) throws Exception {
        // no need to retry on shutting-down response
        //ADDED - edited
        logger.info("******************updateAgentConfigIfConnected(): agentId: {},\n agentConfig: {}***********", agentId, agentConfig);

        boolean retVal =  connectedAgents.execute(agentId, 60, new SendDownstreamFunction(
                CentralRequest.newBuilder()
                        .setAgentConfigUpdateRequest(AgentConfigUpdateRequest.newBuilder()
                                .setAgentConfig(agentConfig))
                        .build(),
                60))
                .isPresent();

        //ADDED
        logger.info(new StringBuilder("***************updateAgentConfigIfConnected()********************************").
                              append("SendDownstreamFunction() returned: " + retVal).
                              append((retVal) ? "****Updated config with: " + agentConfig : "Did not update config").toString());
        
        return retVal;
    }

    boolean isAvailable(String agentId) throws Exception {
        // retry up to 5 seconds on shutting-down response to give agent time to reconnect to
        // another cluster node
        Stopwatch stopwatch = Stopwatch.createStarted();
        while (stopwatch.elapsed(SECONDS) < 5) {
            java.util.Optional<AgentResult> optional =
                    connectedAgents.execute(agentId, 30, new IsAvailableFunction());
            if (!optional.isPresent()) {
                //ADDED - edited
                logger.info("**********isAvailable(): false** -> !optional.isPresent()***********");
                return false;
            }
            AgentResult result = optional.get();
            if (!result.shuttingDown()) {
                //ADDED - edited
                logger.info("**********isAvailable(): true**  -> !result.shuttingDown()***********");
                return true;
            }
            MILLISECONDS.sleep(100);
        }
        // received shutting-down response for 5+ seconds
        //ADDED - edited
        logger.info("**********isAvailable(): exited while loop, exiting method -> returning false*************");
        return false;
    }

    ThreadDump threadDump(String agentId) throws Exception {

        CentralRequest cr = CentralRequest.newBuilder().setThreadDumpRequest(ThreadDumpRequest.getDefaultInstance()).build();

        AgentResponse responseWrapper = runOnCluster(agentId, cr);
        //ADDED - edited
        ThreadDumpResponse tdr = responseWrapper.getThreadDumpResponse();
        logger.info("**Exiting threadDump() -> ****agentId -> {}, ****threadDumppResponse(): {}*************", 
                    agentId, tdr);

        logger.info("***************Thread Dump received from agent********:\n {}", 
                    GrObjToString.ThreadDumpToString(tdr.getThreadDump()));

        return tdr.getThreadDump();
    }

    String jstack(String agentId) throws Exception {
        //ADDED 
        logger.info("****Enter jstack() -> agentId: {}", agentId);
        AgentResponse responseWrapper = runOnCluster(agentId, CentralRequest.newBuilder()
                .setJstackRequest(JstackRequest.getDefaultInstance())
                .build());
        JstackResponse response = responseWrapper.getJstackResponse();
        if (response.getUnavailableDueToRunningInJre()) {
            //ADDED 
            logger.info("**********UnavailableDueToRunningInJreException thrown: *************");
            throw new UnavailableDueToRunningInJreException();
        }
        if (response.getUnavailableDueToRunningInJ9Jvm()) {
             //ADDED 
            logger.info("**********UnavailableDueToRunningInJreException thrown: *************");
            // Eclipse OpenJ9 VM or IBM J9 VM
            throw new UnavailableDueToRunningInJ9JvmException();
        }
         //ADDED 
        logger.info("**********Exiting jstack() returned: {}, jstack response obj: {}*************", response.getJstack(), response);
        return response.getJstack();
    }

    long availableDiskSpaceBytes(String agentId, String directory) throws Exception {
         //ADDED 
        logger.info("**********Entering availableDiskSpaceBytes()*************");
        AgentResponse responseWrapper = runOnCluster(agentId, CentralRequest.newBuilder()
                .setAvailableDiskSpaceRequest(AvailableDiskSpaceRequest.newBuilder()
                        .setDirectory(directory))
                .build());
        AvailableDiskSpaceResponse response = responseWrapper.getAvailableDiskSpaceResponse();
        if (response.getDirectoryDoesNotExist()) {
            //ADDED 
            logger.info("**********response.getDirectoryDoesNotExist() == true, exception thrown*************");
            throw new DirectoryDoesNotExistException();
        }
        //ADDED 
        logger.info("*****Exiting availableDiskSpaceBytes()*****Response obj: {} --> Disk availability returned: {}*************", response, response.getAvailableBytes());
        return response.getAvailableBytes();
    }

    HeapDumpFileInfo heapDump(String agentId, String directory) throws Exception {
        //ADDED 
        logger.info("**********Entering heapDump()****directory=={}*********", directory);
        CentralRequest cr = CentralRequest.newBuilder().setHeapDumpRequest(HeapDumpRequest.newBuilder().setDirectory(directory)).build();
        HeapDumpRequest hdr = cr.getHeapDumpRequest();
         //ADDED 
        logger.info("**********Heap dump request: {}, ***agent id: {}", hdr, agentId);
        AgentResponse responseWrapper = runOnCluster(agentId, cr);
        HeapDumpResponse response = responseWrapper.getHeapDumpResponse();
        //ADDED 
        logger.info("**********Heap dump response: {}, ***agent id: {}", response, agentId);
        if (response.getDirectoryDoesNotExist()) {
            throw new DirectoryDoesNotExistException();
        }
        //ADDED 
        //write heapinfo to string buffer for logging
        HeapDumpFileInfo hdfi = response.getHeapDumpFileInfo();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        CodedOutputStream cos = CodedOutputStream.newInstance(baos);
        hdfi.writeTo(cos);
        logger.info("**********Exiting heapDump()********\\nResponse obj: {} --> \\nHeapdump fileinfo returned: {}*************", 
                  response, baos.toString());
        return response.getHeapDumpFileInfo();
    }

    HeapHistogram heapHistogram(String agentId) throws Exception {
        AgentResponse responseWrapper = runOnCluster(agentId, CentralRequest.newBuilder()
                .setHeapHistogramRequest(HeapHistogramRequest.newBuilder())
                .build());
        HeapHistogramResponse response = responseWrapper.getHeapHistogramResponse();
        if (response.getUnavailableDueToRunningInJre()) {
            throw new UnavailableDueToRunningInJreException();
        }
        if (response.getUnavailableDueToRunningInJ9Jvm()) {
            // Eclipse OpenJ9 VM or IBM J9 VM
            throw new UnavailableDueToRunningInJ9JvmException();
        }
        return response.getHeapHistogram();
    }

    boolean isExplicitGcDisabled(String agentId) throws Exception {
        AgentResponse responseWrapper = runOnCluster(agentId, CentralRequest.newBuilder()
                .setExplicitGcDisabledRequest(ExplicitGcDisabledRequest.getDefaultInstance())
                .build());
        return responseWrapper.getExplicitGcDisabledResponse().getDisabled();
    }

    void forceGC(String agentId) throws Exception {
        runOnCluster(agentId, CentralRequest.newBuilder()
                .setForceGcRequest(ForceGcRequest.getDefaultInstance())
                .build());
    }

    MBeanDump mbeanDump(String agentId, MBeanDumpKind mbeanDumpKind, List<String> objectNames)
            throws Exception {
        AgentResponse responseWrapper = runOnCluster(agentId, CentralRequest.newBuilder()
                .setMbeanDumpRequest(MBeanDumpRequest.newBuilder()
                        .setKind(mbeanDumpKind)
                        .addAllObjectName(objectNames))
                .build());
        return responseWrapper.getMbeanDumpResponse().getMbeanDump();
    }

    List<String> matchingMBeanObjectNames(String agentId, String partialObjectName, int limit)
            throws Exception {
        AgentResponse responseWrapper = runOnCluster(agentId, CentralRequest.newBuilder()
                .setMatchingMbeanObjectNamesRequest(MatchingMBeanObjectNamesRequest.newBuilder()
                        .setPartialObjectName(partialObjectName)
                        .setLimit(limit))
                .build());
        return responseWrapper.getMatchingMbeanObjectNamesResponse().getObjectNameList();
    }

    MBeanMeta mbeanMeta(String agentId, String objectName) throws Exception {
        AgentResponse responseWrapper = runOnCluster(agentId, CentralRequest.newBuilder()
                .setMbeanMetaRequest(MBeanMetaRequest.newBuilder()
                        .setObjectName(objectName))
                .build());
        return responseWrapper.getMbeanMetaResponse().getMbeanMeta();
    }

    Map<String, String> systemProperties(String agentId) throws Exception {
        AgentResponse responseWrapper = runOnCluster(agentId, CentralRequest.newBuilder()
                .setSystemPropertiesRequest(SystemPropertiesRequest.getDefaultInstance())
                .build());
        return responseWrapper.getSystemPropertiesResponse().getSystemPropertiesMap();
    }

    long currentTime(String agentId) throws Exception {
        AgentResponse responseWrapper = runOnCluster(agentId, CentralRequest.newBuilder()
                .setCurrentTimeRequest(CurrentTimeRequest.getDefaultInstance())
                .build());
        return responseWrapper.getCurrentTimeResponse().getCurrentTimeMillis();
    }

    Capabilities capabilities(String agentId) throws Exception {
        AgentResponse responseWrapper = runOnCluster(agentId, CentralRequest.newBuilder()
                .setCapabilitiesRequest(CapabilitiesRequest.getDefaultInstance())
                .build());
        return responseWrapper.getCapabilitiesResponse().getCapabilities();
    }

    GlobalMeta globalMeta(String agentId) throws Exception {
        AgentResponse responseWrapper = runOnCluster(agentId, CentralRequest.newBuilder()
                .setGlobalMetaRequest(GlobalMetaRequest.getDefaultInstance())
                .build());
        return responseWrapper.getGlobalMetaResponse().getGlobalMeta();
    }

    void preloadClasspathCache(String agentId) throws Exception {
        runOnCluster(agentId, CentralRequest.newBuilder()
                .setPreloadClasspathCacheRequest(
                        PreloadClasspathCacheRequest.getDefaultInstance())
                .build());
    }

    List<String> matchingClassNames(String agentId, String partialClassName, int limit)
            throws Exception {
        AgentResponse responseWrapper = runOnCluster(agentId, CentralRequest.newBuilder()
                .setMatchingClassNamesRequest(MatchingClassNamesRequest.newBuilder()
                        .setPartialClassName(partialClassName)
                        .setLimit(limit))
                .build());
        return responseWrapper.getMatchingClassNamesResponse().getClassNameList();
    }

    List<String> matchingMethodNames(String agentId, String className, String partialMethodName,
            int limit) throws Exception {
        AgentResponse responseWrapper = runOnCluster(agentId, CentralRequest.newBuilder()
                .setMatchingMethodNamesRequest(MatchingMethodNamesRequest.newBuilder()
                        .setClassName(className)
                        .setPartialMethodName(partialMethodName)
                        .setLimit(limit))
                .build());
        return responseWrapper.getMatchingMethodNamesResponse().getMethodNameList();
    }

    List<MethodSignature> methodSignatures(String agentId, String className, String methodName)
            throws Exception {
        AgentResponse responseWrapper = runOnCluster(agentId, CentralRequest.newBuilder()
                .setMethodSignaturesRequest(MethodSignaturesRequest.newBuilder()
                        .setClassName(className)
                        .setMethodName(methodName))
                .build());
        return responseWrapper.getMethodSignaturesResponse().getMethodSignatureList();
    }

    int reweave(String agentId) throws Exception {
        AgentResponse responseWrapper = runOnCluster(agentId, CentralRequest.newBuilder()
                .setReweaveRequest(ReweaveRequest.getDefaultInstance())
                .build());
        return responseWrapper.getReweaveResponse().getClassUpdateCount();
    }

    Trace. /*@Nullable*/ Header getHeader(String agentId, String traceId) throws Exception {
        AgentResponse responseWrapper = runOnCluster(agentId, CentralRequest.newBuilder()
                .setHeaderRequest(HeaderRequest.newBuilder()
                        .setTraceId(traceId))
                .build());
        HeaderResponse response = responseWrapper.getHeaderResponse();
        if (response.hasHeader()) {
            return response.getHeader();
        } else {
            return null;
        }
    }

    @Nullable
    Entries getEntries(String agentId, String traceId) throws Exception {
        AgentResponse responseWrapper = runOnCluster(agentId, CentralRequest.newBuilder()
                .setEntriesRequest(EntriesRequest.newBuilder()
                        .setTraceId(traceId))
                .build());
        EntriesResponse response = responseWrapper.getEntriesResponse();
        List<Trace.Entry> entries = response.getEntryList();
        if (entries.isEmpty()) {
            return null;
        } else {
            return ImmutableEntries.builder()
                    .addAllEntries(entries)
                    .addAllSharedQueryTexts(response.getSharedQueryTextList())
                    .build();
        }
    }

    @Nullable
    Queries getQueries(String agentId, String traceId) throws Exception {
        AgentResponse responseWrapper = runOnCluster(agentId, CentralRequest.newBuilder()
                .setQueriesRequest(QueriesRequest.newBuilder()
                        .setTraceId(traceId))
                .build());
        QueriesResponse response = responseWrapper.getQueriesResponse();
        List<Aggregate.Query> queries = response.getQueryList();
        if (queries.isEmpty()) {
            return null;
        } else {
            return ImmutableQueries.builder()
                    .addAllQueries(queries)
                    .addAllSharedQueryTexts(response.getSharedQueryTextList())
                    .build();
        }
    }

    @Nullable
    Profile getMainThreadProfile(String agentId, String traceId) throws Exception {
        AgentResponse responseWrapper = runOnCluster(agentId, CentralRequest.newBuilder()
                .setMainThreadProfileRequest(MainThreadProfileRequest.newBuilder()
                        .setTraceId(traceId))
                .build());
        MainThreadProfileResponse response = responseWrapper.getMainThreadProfileResponse();
        if (response.hasProfile()) {
            return response.getProfile();
        } else {
            return null;
        }
    }

    @Nullable
    Profile getAuxThreadProfile(String agentId, String traceId) throws Exception {
        AgentResponse responseWrapper = runOnCluster(agentId, CentralRequest.newBuilder()
                .setAuxThreadProfileRequest(AuxThreadProfileRequest.newBuilder()
                        .setTraceId(traceId))
                .build());
        AuxThreadProfileResponse response = responseWrapper.getAuxThreadProfileResponse();
        if (response.hasProfile()) {
            return response.getProfile();
        } else {
            return null;
        }
    }

    @Nullable
    Trace getFullTrace(String agentId, String traceId) throws Exception {
        AgentResponse responseWrapper = runOnCluster(agentId, CentralRequest.newBuilder()
                .setFullTraceRequest(FullTraceRequest.newBuilder()
                        .setTraceId(traceId))
                .build());
        FullTraceResponse response = responseWrapper.getFullTraceResponse();
        if (response.hasTrace()) {
            return response.getTrace();
        } else {
            return null;
        }
    }

    private AgentResponse runOnCluster(String agentId, CentralRequest centralRequest)
            throws Exception {
        int timeoutSeconds;
        switch (centralRequest.getMessageCase()) {
            case HEADER_REQUEST:
            case ENTRIES_REQUEST:
            case MAIN_THREAD_PROFILE_REQUEST:
            case AUX_THREAD_PROFILE_REQUEST:
            case FULL_TRACE_REQUEST:
                timeoutSeconds = 5;
                break;
            case HEAP_DUMP_REQUEST:
                timeoutSeconds = 300;
                break;
            default:
                timeoutSeconds = 60;
        }
        // retry up to 5 seconds on shutting-down response to give agent time to reconnect to
        // another cluster node
        //ADDED
        logger.info("*****Enter runOnCluster(), agentId={}, timeoutSeconds={}, centralReq=<{}>************", agentId, timeoutSeconds, centralRequest);
        //String callerInfo = GrObjToString.getCallerInfo(Thread.currentThread().getStackTrace()[2]);
        String crString = GrObjToString.CentralRequestToString(centralRequest);
        
        //logger.info("**********Caller information********************\n{}", callerInfo);
        logger.info("**********Printable contents of CentralRequest********************\n{}", crString);

        Stopwatch stopwatch = Stopwatch.createStarted();
        while (stopwatch.elapsed(SECONDS) < 5) {
            java.util.Optional<AgentResult> optional = connectedAgents.execute(agentId,
                    timeoutSeconds, new SendDownstreamFunction(centralRequest, timeoutSeconds));
            if (!optional.isPresent()) {
                logger.error("*****<AgentNotConnectedException()> thrown************");
                throw new AgentNotConnectedException();
            }
            AgentResult result = optional.get();
            Optional<AgentResponse> value = result.value();
            //result.
            if (value.isPresent()) {
                AgentResponse response = value.get();
                if (response
                        .getMessageCase() == AgentResponse.MessageCase.UNKNOWN_REQUEST_RESPONSE) {
                    throw new AgentUnsupportedOperationException();
                }
                if (response.getMessageCase() == AgentResponse.MessageCase.EXCEPTION_RESPONSE) {
                    throw new AgentException();
                }
                return response;
            } else if (result.timeout()) {
                throw new TimeoutException();
            } else if (result.interrupted()) {
                // this should not happen
                throw new RuntimeException(
                        "Glowroot central thread was interrupted while waiting for agent response");
            }
            // only other case is shutting-down response
            checkState(result.shuttingDown());
            MILLISECONDS.sleep(100);
        }
        // received shutting-down response for 5+ seconds
        throw new AgentNotConnectedException();
    }

    private class ConnectedAgent implements StreamObserver<AgentResponse> {

        private final AtomicLong nextRequestId = new AtomicLong(1);

        // expiration in the unlikely case that response is never returned from agent
        private final com.google.common.cache.Cache<Long, ResponseHolder> responseHolders =
                CacheBuilder.newBuilder()
                        .expireAfterWrite(1, HOURS)
                        .build();

        private volatile @MonotonicNonNull String agentId;

        private final StreamObserver<CentralRequest> requestObserver;

        private ConnectedAgent(StreamObserver<CentralRequest> requestObserver) {
            this.requestObserver = requestObserver;
        }

        @Override
        public void onNext(AgentResponse value) {
            try {
                onNextInternal(value);
            } catch (Throwable t) {
                logger.error(t.getMessage(), t);
                throw t;
            }
        }

        @Override
        @OnlyUsedByTests
        public void onCompleted() {
            synchronized (requestObserver) {
                requestObserver.onCompleted();
            }
            if (agentId != null) {
                connectedAgents.remove(agentId, ConnectedAgent.this);
            }
        }

        @Override
        public void onError(Throwable t) {
            logger.debug("{} - {}", t.getMessage(), t);
            if (agentId != null) {
                //ADDED - edited
                logger.info("*******************downstream connection lost with agent: {}*************", agentId);
                connectedAgents.remove(agentId, ConnectedAgent.this);
            }
        }

        private void onNextInternal(AgentResponse value) {
            if (value.getMessageCase() == AgentResponse.MessageCase.HELLO) {
                Hello hello = value.getHello();  
                //ADDED
                logger.info("*******************Hello Message received: {}*************", value);
                try {
                    agentId = grpcCommon
               .getAgentId(hello.getAgentId(), hello.getPostV09());
                } catch (Exception e) {
                    //ADDED -- edited
                    logger.error("*************Exception caught getting agent id: {} - {}****************",
                            getAgentIdForLogging(hello.getAgentId(), hello.getPostV09()),
                            e.getMessage(), e);
                    return;
                }
                connectedAgents.put(agentId, ConnectedAgent.this);
                synchronized (requestObserver) {

                    HelloAck ha = HelloAck.getDefaultInstance();

                    //ADDED
                    logger.info("****************Hello Ack created: {}*************", ha);

                    CentralRequest cr = CentralRequest.newBuilder()
                            .setHelloAck(ha).build();

                            
                    requestObserver.onNext(cr);

                     //ADDED
                     logger.info("*******************Hello ACk central request: {}*************", cr);
                }
                //ADDED -- edited
                logger.info("**************downstream connection (re-)established with agent: {}**************", agentId);
                return;
            }
            if (agentId == null) {
                //ADDED -- edited
                logger.error("***********AgentId == null, first message from agent to downstream service must be HELLO*************");
                return;
            }
            long requestId = value.getRequestId();
            //ADDED -- edited
            logger.info("**************Agent response --> Request id: {}**************", value.getRequestId());
            ResponseHolder responseHolder = responseHolders.getIfPresent(requestId);
            responseHolders.invalidate(requestId);
            if (responseHolder == null) {
                //ADDED -- edited
                logger.error("************no response holder for request id: {}******************", requestId);
                return;
            }
            try {
                // this shouldn't timeout since it is the other side of the exchange that is waiting
                //ADDED -- edited
                AgentResponse ar = responseHolder.response.exchange(value, 1, MINUTES);
                //ADDED
                logger.info("**************Agent response --> Request id: {}, AgentResponse: {}**************", ar.getRequestId(), ar);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                //ADDED -- edited
                logger.error("************InterruptedException caught: {} - {}***********", agentId, e.getMessage(), e);
            } catch (TimeoutException e) {
                //ADDED -- edited
                logger.error("************TimeoutExcepti caught: {} - {}***********", agentId, e.getMessage(), e);
                logger.error("{} - {}", agentId, e.getMessage(), e);
            }
        }

        private AgentResult isAvailable() {
            Lock readLock = shuttingDownLock.readLock();
            if (!readLock.tryLock()) {
                return ImmutableAgentResult.builder()
                        .shuttingDown(true)
                        .build();
            }
            try {
                return ImmutableAgentResult.builder()
                        .build();
            } finally {
                readLock.unlock();
            }
        }

        private AgentResult sendDownstream(CentralRequest requestWithoutRequestId,
                int timeoutSeconds) {
            Lock readLock = shuttingDownLock.readLock();
            if (!readLock.tryLock()) {
                ImmutableAgentResult iar = ImmutableAgentResult.builder()
                        .shuttingDown(true)
                        .build();

                //ADDED
                logger.info("*****Can't get readLock()**************Incoming -> CentralRequest requestWithoutRequestId: {}*************, ImmutableAgentResponse: <{}>", 
                            requestWithoutRequestId, iar);
            }
            try {                

                CentralRequest request = CentralRequest.newBuilder(requestWithoutRequestId)
                        .setRequestId(nextRequestId.getAndIncrement())
                        .build();

                //ADDED
                logger.info("*****Obtained readLock: {}*************", request);

                ResponseHolder responseHolder = new ResponseHolder();
                responseHolders.put(request.getRequestId(), responseHolder);
                
                //ADDED
                logger.info("*****Response Holder: {}*************", responseHolder);
                
                // synchronization required since individual StreamObservers are not thread-safe
                synchronized (requestObserver) {
                    requestObserver.onNext(request);
                    //ADDED
                    logger.info("*****Response Holder {}*************", responseHolder);
                
                }
                // timeout is in case agent never responds
                // passing AgentResponse.getDefaultInstance() is just dummy (non-null) value
                AgentResponse response = responseHolder.response
                        .exchange(AgentResponse.getDefaultInstance(), timeoutSeconds, SECONDS);
                
                //ADDED
                logger.info("*****************Response after exchange: {}***************", response);
                ImmutableAgentResult iar = ImmutableAgentResult.builder()
                        .value(response)
                        .build();
                return iar;
            } catch (InterruptedException e) {
                //ADDED
                logger.info("**********InterruptedException caught: {}", e);
                Thread.currentThread().interrupt();
                ImmutableAgentResult iar = ImmutableAgentResult.builder()
                        .interrupted(true)
                        .build();
                logger.info("**********Interrupted Agent Result: {}", iar);
                return iar;
            } catch (TimeoutException e) {
                //ADDED
                logger.info("**********TimeoutException caught: {}", e);
                ImmutableAgentResult iar =ImmutableAgentResult.builder()
                        .timeout(true)
                        .build();
                logger.info("**********Timeout Agent Result: {}", iar);
                return iar;
            } finally {
                readLock.unlock();
            }
        }

        private String getAgentIdForLogging(String agentId, boolean postV09) {
            return grpcCommon.getAgentIdForLogging(agentId, postV09);
        }
    }

    @Value.Immutable
    @Serial.Structural
    interface AgentResult extends Serializable {

        Optional<AgentResponse> value();

        @Value.Default
        default boolean timeout() {
            return false;
        }

        @Value.Default
        default boolean interrupted() {
            return false;
        }

        @Value.Default
        default boolean shuttingDown() {
            return false;
        }
    }

    private static class ResponseHolder {
        private final Exchanger<AgentResponse> response = new Exchanger<>();
    }

    @SuppressWarnings("serial")
    private static class AgentException extends Exception {}

    // using named class instead of lambda to avoid "Invalid lambda deserialization" when one node
    // is running with this class compiled by eclipse and one node is running with this class
    // compiled by javac, see https://bugs.eclipse.org/bugs/show_bug.cgi?id=516620
    private static class IsAvailableFunction
            implements SerializableFunction<ConnectedAgent, AgentResult> {

        private static final long serialVersionUID = 0L;

        @Override
        public AgentResult apply(ConnectedAgent connectedAgent) {
            return connectedAgent.isAvailable();
        }
    }

    // using named class instead of lambda to avoid "Invalid lambda deserialization" when one node
    // is running with this class compiled by eclipse and one node is running with this class
    // compiled by javac, see https://bugs.eclipse.org/bugs/show_bug.cgi?id=516620
    private static class SendDownstreamFunction
            implements SerializableFunction<ConnectedAgent, AgentResult> {

        private static final long serialVersionUID = 0L;

        private final CentralRequest centralRequest;
        private final int timeoutSeconds;

        private SendDownstreamFunction(CentralRequest centralRequest, int timeoutSeconds) {
            this.centralRequest = centralRequest;
            this.timeoutSeconds = timeoutSeconds;
        }

        @Override
        public AgentResult apply(ConnectedAgent connectedAgent) {
             //ADDED
            logger.info("*****static class SendDownstreamFunction.apply(), sendDownstream(): connectedAgent: {}*************", connectedAgent);

            return connectedAgent.sendDownstream(centralRequest, timeoutSeconds);
        }
    }
}
