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
package org.glowroot.agent.model;

import java.lang.management.ThreadInfo;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.List;

import javax.annotation.concurrent.GuardedBy;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

import org.glowroot.common.model.MutableProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ThreadProfile {

    private final int maxSamples;
    private final Object lock = new Object();
    @GuardedBy("lock")
    private final List<List<StackTraceElement>> unmergedStackTraces = Lists.newArrayList();
    @GuardedBy("lock")
    private final List<Thread.State> unmergedStackTraceThreadStates = Lists.newArrayList();
    @GuardedBy("lock")
    private @MonotonicNonNull MutableProfile profile;
    @GuardedBy("lock")
    private long sampleCount;

    private static final Logger logger = LoggerFactory.getLogger(ThreadProfile.class);


    @VisibleForTesting
    public ThreadProfile(int maxSamples) {
        this.maxSamples = maxSamples;
    }

    public void mergeInto(MutableProfile profile) {
        synchronized (lock) {
            if (this.profile == null) {
                mergeTheUnmergedInto(profile);
            } else {
                profile.merge(this.profile);
            }
        }
    }

    public org.glowroot.wire.api.model.ProfileOuterClass.Profile toProto() {
        synchronized (lock) {
            if (profile == null) {
                profile = new MutableProfile();
                mergeTheUnmergedInto(profile);
                unmergedStackTraces.clear();
                unmergedStackTraceThreadStates.clear();
            }
            return profile.toProto();
        }
    }

    public long getSampleCount() {
        // lock is needed for visibility
        synchronized (lock) {
            return Math.min(sampleCount, maxSamples);
        }
    }

    public boolean isSampleLimitExceeded() {
        // lock is needed for visibility
        synchronized (lock) {
            return sampleCount > maxSamples;
        }
    }

    // limit is just to cap memory consumption for a single transaction profile in case it runs for
    // a very very very long time
    public void addStackTrace(ThreadInfo threadInfo) {
        synchronized (lock) {
            if (++sampleCount > maxSamples) {
                return;
            }
            List<StackTraceElement> stackTrace = Arrays.asList(threadInfo.getStackTrace());
            Thread.State threadState = threadInfo.getThreadState();
            if (profile == null) {
                unmergedStackTraces.add(stackTrace);
                unmergedStackTraceThreadStates.add(threadState);
                if (unmergedStackTraces.size() >= 10) {
                    // merged stack tree takes up less memory
                    profile = new MutableProfile();
                    mergeTheUnmergedInto(profile);
                    unmergedStackTraces.clear();
                    unmergedStackTraceThreadStates.clear();
                }
            } else {
                profile.merge(stackTrace, threadState);
            }
            logger.info("***********After addStackTrace() - ThreadProfile.ToString()\n" + ToString() + "\n");
        }
    }

    @GuardedBy("lock")
    private void mergeTheUnmergedInto(MutableProfile profile) {
        for (int i = 0; i < unmergedStackTraces.size(); i++) {
            List<StackTraceElement> stackTrace = unmergedStackTraces.get(i);
            Thread.State threadState = unmergedStackTraceThreadStates.get(i);
            profile.merge(stackTrace, threadState);
        }
    }

    
    //ADDED
    public String ToString(){

        StringBuffer sb = new StringBuffer(). 
        append("**************************************************\n").      
        append("********Begin logging ThreadProfile********\n");

        synchronized (lock) {
        
        sb.append(MessageFormat.format("Max Samples: {0}\n", new Object[]{maxSamples})).
           append(MessageFormat.format("Is sample limit exceeded: {0}\n", new Object[]{isSampleLimitExceeded()})).

        append(MessageFormat.format("Sample count: {0}\n", new Object[]{getSampleCount()})).
        append("********Begin Logging Unmerged Stack Traces********\n");
        int count = 1;
    
        for (List<StackTraceElement> unMergedList : unmergedStackTraces) {

            sb.append(MessageFormat.format("Unmerged List: {0}\n, {1}\n",
                    new Object[] { count, JAVAStackTraceElementListToString(unMergedList) }));

            count++;
        }

        sb = sb.append("********Done Logging Unmerged Stack Traces********\n").

                append("********Begin Logging Unmerged StackTrace ThreadSTATES********\n");
        count = 1;
        for (Thread.State ts : unmergedStackTraceThreadStates) {
            sb = sb.append(MessageFormat.format("Thread State: {0}\n, {1}\n", new Object[] { count, ts }));
            count++;
        }
        sb = sb.append("********End Logging Unmerged StackTrace ThreadSTATES********\n");
        sb.append("********Mutable Profile********\n");
        if (profile != null) {
            sb = sb.append(MessageFormat.format("Mutable profile: {0}\n", new Object[] { profile.ToString() }));
        }
        
        
        sb = sb.append(MessageFormat.format("Max Samples: {0}\n", new Object[]{maxSamples})).            
                append("********End logging ThreadProfile********\n").
                append("**************************************************\n");
    }

        return sb.toString();
    }

    //ADDED
    private String JAVAStackTraceElementListToString(List<java.lang.StackTraceElement> steList){

        String retVal = "Unmerged List == null\n";
        
        if(steList != null && !steList.isEmpty()){
  
           StringBuilder strVal = new StringBuilder().
           append("********Begin logging Unmerged List********\n");
           
           for(StackTraceElement ste : steList){       
              strVal.append(StackTraceElementToString(ste));      
           }
           retVal = strVal.append("********Done logging Unmerged List********\n").toString();
       }
  
       return retVal;
           
     }

     private String StackTraceElementToString(StackTraceElement ste){

        String retVal = "StackTraceElement == null";
  
        if(ste != null){
            retVal = new StringBuilder().
                    append("***************************************************\n").
                    append("********Begin logging StackTraceElement************\n").
                    append(MessageFormat.format("****StackTraceElement.getClassName(): {0}***\n", ste.getClassName())).
                    append(MessageFormat.format("****StackTraceElement.getFileName(): {0}***\n", ste.getFileName())).
                    append(MessageFormat.format("****StackTraceElement.getLineNumber(): {0}***\n", ste.getLineNumber())).
                    append(MessageFormat.format("****StackTraceElement.getMethodName(): {0}***\n", ste.getMethodName())).
                    append("********Done logging StackTraceElement********\n").
                    append("***************************************************\n").toString();
        }
        return retVal;
  
     }
}
