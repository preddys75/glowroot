/*
 * Copyright 2016-2019 the original author or authors.
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

import java.util.List;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.checkerframework.checker.nullness.qual.Nullable;

import org.glowroot.common.live.LiveTraceRepository;
import org.glowroot.wire.api.model.ProfileOuterClass.Profile;
import org.glowroot.wire.api.model.TraceOuterClass.Trace;

//ADDED
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.glowroot.wire.api.model.AggregateOuterClass.Aggregate;

class LiveTraceRepositoryImpl implements LiveTraceRepository {
    //ADDED
    private static final Logger logger = LoggerFactory.getLogger(LiveTraceRepositoryImpl.class);


    private final DownstreamServiceImpl downstreamService;

    LiveTraceRepositoryImpl(DownstreamServiceImpl downstreamService) {
        this.downstreamService = downstreamService;
    }

    @Override
    public Trace. /*@Nullable*/ Header getHeader(String agentId, String traceId) throws Exception {
        logger.info("*********************Enter getHeader()***********************");
        Trace.Header header = downstreamService.getHeader(agentId, traceId);
        /*ADDED*/
        logger.info("******Printing and Exiting Header: " + header);
        return header;
         
    }

    @Override
    public @Nullable Entries getEntries(String agentId, String traceId) throws Exception {
        logger.info("*********************Enter getEntries()***********************");
        Entries entries = downstreamService.getEntries(agentId, traceId);
        if(entries != null && entries.entries() != null){
            for(Trace.Entry entry : entries.entries()){
                logger.info("******Entry: " + entry);
                
            }
        }
        logger.info("*********************Exit getEntries()***********************");
        return entries;
    }

    @Override
    public @Nullable Queries getQueries(String agentId, String traceId) throws Exception {
        logger.info("*********************Enter getQueries()***********************");
        Queries queries = downstreamService.getQueries(agentId, traceId);
        if(queries != null && queries.queries() != null){
            for(Aggregate.Query query :queries.queries()){
                logger.info("******Query: " + query);
                
            }
        }
        logger.info("*********************Exiting getQueries()***********************");
        return queries;
    }

    @Override
    public @Nullable Profile getMainThreadProfile(String agentId, String traceId) throws Exception {
        return downstreamService.getMainThreadProfile(agentId, traceId);
    }

    @Override
    public @Nullable Profile getAuxThreadProfile(String agentId, String traceId) throws Exception {
        return downstreamService.getAuxThreadProfile(agentId, traceId);
    }

    @Override
    public @Nullable Trace getFullTrace(String agentId, String traceId) throws Exception {
        logger.info("*********************Enter getFullTrace()***********************");
        
        Trace trace = downstreamService.getFullTrace(agentId, traceId);
        
        logger.info("******Full trace: " + trace);
        logger.info("*********************Exit getFullTrace()***********************");

        return trace;
    }

    @Override
    public int getMatchingTraceCount(String transactionType, @Nullable String transactionName) {
        return 0;
    }

    @Override
    public List<TracePoint> getMatchingActiveTracePoints(TraceKind traceKind,
            String transactionType, @Nullable String transactionName, TracePointFilter filter,
            int limit, long captureTime, long captureTick) {
        return ImmutableList.of();
    }

    @Override
    public List<TracePoint> getMatchingPendingPoints(TraceKind traceKind, String transactionType,
            @Nullable String transactionName, TracePointFilter filter, long captureTime) {
        return ImmutableList.of();
    }

    @Override
    public Set<String> getTransactionTypes(String agentId) {
        return ImmutableSet.of();
    }
}
