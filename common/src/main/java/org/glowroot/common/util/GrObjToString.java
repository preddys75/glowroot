package org.glowroot.common.util;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.text.MessageFormat;
import java.util.List;

import org.glowroot.wire.api.model.DownstreamServiceOuterClass.AgentResponse;
import org.glowroot.wire.api.model.DownstreamServiceOuterClass.CentralRequest;
import org.glowroot.wire.api.model.DownstreamServiceOuterClass.ThreadDump;
import org.glowroot.wire.api.model.DownstreamServiceOuterClass.ThreadDump.Transaction;
import org.glowroot.wire.api.model.DownstreamServiceOuterClass.ThreadDump.LockInfo;
import org.glowroot.wire.api.model.DownstreamServiceOuterClass.ThreadDump.Thread;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import org.glowroot.wire.api.model.DownstreamServiceOuterClass.ThreadDump.StackTraceElement;


public class GrObjToString {

    public static String getCallerInfo(StackTraceElement ste){

        String retVal = "StackTraceElement == null";

        if(ste != null && !ste.equals(org.glowroot.wire.api.model.DownstreamServiceOuterClass.ThreadDump.StackTraceElement.getDefaultInstance())){

            String callingMethod = ste.getMethodName();
            String callingClass = ste.getClassName();
            Integer lineNum = ste.getLineNumber();

            retVal = new StringBuilder(). 
               append(MessageFormat.format("callingClass: {0}, callingMethod: {1}, lineNumber: {2}\n", 
                     new Object[]{callingClass, callingMethod, lineNum})).
               append(StackTraceElementToString(ste)).toString();
        }
        return retVal;
        
    }

    public static String CentralRequestToString(CentralRequest request){
      
        return (request == null || request.equals(org.glowroot.wire.api.model.DownstreamServiceOuterClass.CentralRequest.getDefaultInstance())) ? null :
         new StringBuilder().
                     append(MessageFormat.format("request.getMessageCase(): {0}\n", new Object[]{request.getMessageCase()})).
                     append(MessageFormat.format("CentralRequest.getDescriptor(): {0}\n", new Object[]{CentralRequest.getDescriptor()})).
                     append(MessageFormat.format("request.getRequestId(): {0}\n", new Object[]{request.getRequestId()})).
                     append(MessageFormat.format("request.isInitialized(): {0}\n", new Object[]{request.isInitialized()})).
                     append(MessageFormat.format("request.getSerializedSize(): {0}\n", new Object[]{request.getSerializedSize()})).
                     
                     append(MessageFormat.format("CentralRequest.REQUEST_ID_FIELD_NUMBER: {0}\n", new Object[]{CentralRequest.REQUEST_ID_FIELD_NUMBER})).
                     append(MessageFormat.format("request.getRequestId(): {0}\n", new Object[]{request.getRequestId()})).
                     append(MessageFormat.format("CentralRequest.HELLO_ACK_FIELD_NUMBER: {0}\n", new Object[]{CentralRequest.HELLO_ACK_FIELD_NUMBER})).
                     append(MessageFormat.format("request.hasHelloAck(): {0}\n", new Object[]{request.hasHelloAck()})).
                     append(MessageFormat.format("request.getHelloAck(): {0}\n", new Object[]{request.getHelloAck()})).
                     append(MessageFormat.format("request.getHelloAckOrBuilder(): {0}\n", new Object[]{request.getHelloAckOrBuilder()})).
                     append(MessageFormat.format("CentralRequest.AGENT_CONFIG_UPDATE_REQUEST_FIELD_NUMBER: {0}\n", new Object[]{CentralRequest.AGENT_CONFIG_UPDATE_REQUEST_FIELD_NUMBER})).
                     append(MessageFormat.format("request.hasAgentConfigUpdateRequest(): {0}\n", new Object[]{request.hasAgentConfigUpdateRequest()})).                     
                     append(MessageFormat.format("request.getAgentConfigUpdateRequest(): {0}\n", new Object[]{request.getAgentConfigUpdateRequest()})).
                     append(MessageFormat.format("request.getAgentConfigUpdateRequestOrBuilder();: {0}\n", new Object[]{request.getAgentConfigUpdateRequestOrBuilder()})).
                     append(MessageFormat.format("CentralRequest.THREAD_DUMP_REQUEST_FIELD_NUMBER: {0}\n", new Object[]{CentralRequest.THREAD_DUMP_REQUEST_FIELD_NUMBER})).
                     append(MessageFormat.format("request.hasThreadDumpRequest(): {0}\n", new Object[]{request.hasThreadDumpRequest()})).                     
                     append(MessageFormat.format("request.getThreadDumpRequest(): {0}\n", new Object[]{request.getThreadDumpRequest()})).
                     append(MessageFormat.format("request.getThreadDumpRequestOrBuilder(): {0}\n", new Object[]{request.getThreadDumpRequestOrBuilder()})).
                     append(MessageFormat.format("CentralRequest.JSTACK_REQUEST_FIELD_NUMBER: {0}\n", new Object[]{CentralRequest.JSTACK_REQUEST_FIELD_NUMBER})).
                     append(MessageFormat.format("request.getJstackRequest(): {0}\n", new Object[]{request.getJstackRequest()})).                     
                     append(MessageFormat.format("request.getJstackRequest(): {0}\n", new Object[]{request.getJstackRequest()})).
                     append(MessageFormat.format("request.getJstackRequestOrBuilder(): {0}\n", new Object[]{request.getJstackRequestOrBuilder()})).
                     append(MessageFormat.format("CentralRequest.AVAILABLE_DISK_SPACE_REQUEST_FIELD_NUMBER: {0}\n", new Object[]{CentralRequest.AVAILABLE_DISK_SPACE_REQUEST_FIELD_NUMBER})).
                     append(MessageFormat.format("request.hasAvailableDiskSpaceRequest(): {0}\n", new Object[]{request.hasAvailableDiskSpaceRequest()})).                     
                     append(MessageFormat.format("request.getAvailableDiskSpaceRequest(): {0}\n", new Object[]{request.getAvailableDiskSpaceRequest()})).
                     append(MessageFormat.format("request.getAvailableDiskSpaceRequestOrBuilder(): {0}\n", new Object[]{request.getAvailableDiskSpaceRequestOrBuilder()})).
                     append(MessageFormat.format("CentralRequest.HEAP_DUMP_REQUEST_FIELD_NUMBER: {0}\n", new Object[]{CentralRequest.HEAP_DUMP_REQUEST_FIELD_NUMBER})).
                     append(MessageFormat.format("request.hasHeapDumpRequest(): {0}\n", new Object[]{request.hasHeapDumpRequest()})).                     
                     append(MessageFormat.format("request.getHeapDumpRequest(): {0}\n", new Object[]{request.getHeapDumpRequest()})).
                     append(MessageFormat.format("request.getHeapDumpRequestOrBuilder(): {0}\n", new Object[]{request.getHeapDumpRequestOrBuilder()})).
                     append(MessageFormat.format("CentralRequest.HEAP_HISTOGRAM_REQUEST_FIELD_NUMBER: {0}\n", new Object[]{CentralRequest.HEAP_HISTOGRAM_REQUEST_FIELD_NUMBER})).
                     append(MessageFormat.format("request.hasHeapHistogramRequest(): {0}\n", new Object[]{request.hasHeapHistogramRequest()})).                     
                     append(MessageFormat.format("request.getHeapHistogramRequest(): {0}\n", new Object[]{request.getHeapHistogramRequest()})).
                     append(MessageFormat.format("request.getHeapHistogramRequestOrBuilder(): {0}\n", new Object[]{request.getHeapHistogramRequestOrBuilder()})).
                     append(MessageFormat.format("CentralRequest.FORCE_GC_REQUEST_FIELD_NUMBER: {0}\n", new Object[]{CentralRequest.FORCE_GC_REQUEST_FIELD_NUMBER})).
                     append(MessageFormat.format("request.hasForceGcRequest(): {0}\n", new Object[]{request.hasForceGcRequest()})).                     
                     append(MessageFormat.format("request.getForceGcRequest(): {0}\n", new Object[]{request.getForceGcRequest()})).
                     append(MessageFormat.format("request.hasForceGcRequestOrBuilder(): {0}\n", new Object[]{request.getForceGcRequestOrBuilder()})).
                     append(MessageFormat.format("CentralRequest.MBEAN_DUMP_REQUEST_FIELD_NUMBER: {0}\n", new Object[]{CentralRequest.MBEAN_DUMP_REQUEST_FIELD_NUMBER})).
                     append(MessageFormat.format("request.hasMbeanDumpRequest(): {0}\n", new Object[]{request.hasMbeanDumpRequest()})).                     
                     append(MessageFormat.format("request.getMbeanDumpRequest(): {0}\n", new Object[]{request.getMbeanDumpRequest()})).
                     append(MessageFormat.format("request.getMbeanDumpRequestOrBuilder(): {0}\n", new Object[]{request.getMbeanDumpRequestOrBuilder()})).
                     append(MessageFormat.format("CentralRequest.MATCHING_MBEAN_OBJECT_NAMES_REQUEST_FIELD_NUMBER: {0}\n", new Object[]{CentralRequest.MATCHING_MBEAN_OBJECT_NAMES_REQUEST_FIELD_NUMBER})).
                     append(MessageFormat.format("request.hasMatchingMbeanObjectNamesRequest(): {0}\n", new Object[]{request.hasMatchingMbeanObjectNamesRequest()})).                     
                     append(MessageFormat.format("request.getMatchingMbeanObjectNamesRequest(): {0}\n", new Object[]{request.getMatchingMbeanObjectNamesRequest()})).
                     append(MessageFormat.format("request.getMatchingMbeanObjectNamesRequestOrBuilder(): {0}\n", new Object[]{request.getMatchingMbeanObjectNamesRequestOrBuilder()})).
                     append(MessageFormat.format("CentralRequest.MBEAN_META_REQUEST_FIELD_NUMBER: {0}\n", new Object[]{CentralRequest.MBEAN_META_REQUEST_FIELD_NUMBER})).
                     append(MessageFormat.format("request.hasMbeanMetaRequest(): {0}\n", new Object[]{request.hasMbeanMetaRequest()})).                     
                     append(MessageFormat.format("request.getMbeanMetaRequest(): {0}\n", new Object[]{request.getMbeanMetaRequest()})).
                     append(MessageFormat.format("request.getMbeanMetaRequestOrBuilder(): {0}\n", new Object[]{request.getMbeanMetaRequestOrBuilder()})).
                     append(MessageFormat.format("CentralRequest.SYSTEM_PROPERTIES_REQUEST_FIELD_NUMBER: {0}\n", new Object[]{CentralRequest.SYSTEM_PROPERTIES_REQUEST_FIELD_NUMBER})).
                     append(MessageFormat.format("request.hasSystemPropertiesRequest(): {0}\n", new Object[]{request.hasSystemPropertiesRequest()})).                     
                     append(MessageFormat.format("request.getSystemPropertiesRequest(): {0}\n", new Object[]{request.getSystemPropertiesRequest()})).
                     append(MessageFormat.format("request.getSystemPropertiesRequestOrBuilder(): {0}\n", new Object[]{request.getSystemPropertiesRequestOrBuilder()})).
                     append(MessageFormat.format("CentralRequest.CURRENT_TIME_REQUEST_FIELD_NUMBER: {0}\n", new Object[]{CentralRequest.CURRENT_TIME_REQUEST_FIELD_NUMBER})).
                     append(MessageFormat.format("request.hasCurrentTimeRequest(): {0}\n", new Object[]{request.hasCurrentTimeRequest()})).                     
                     append(MessageFormat.format("request.getCurrentTimeRequest(): {0}\n", new Object[]{request.getCurrentTimeRequest()})).
                     append(MessageFormat.format("request.getCurrentTimeRequestOrBuilder(): {0}\n", new Object[]{request.getCurrentTimeRequestOrBuilder()})).
                     append(MessageFormat.format("CentralRequest.CAPABILITIES_REQUEST_FIELD_NUMBER: {0}\n", new Object[]{CentralRequest.CAPABILITIES_REQUEST_FIELD_NUMBER})).
                     append(MessageFormat.format("request.hasCapabilitiesRequest(): {0}\n", new Object[]{request.hasCapabilitiesRequest()})).                     
                     append(MessageFormat.format("request.getCapabilitiesRequest(): {0}\n", new Object[]{request.getCapabilitiesRequest()})).
                     append(MessageFormat.format("request.getCapabilitiesRequestOrBuilder(): {0}\n", new Object[]{request.getCapabilitiesRequestOrBuilder()})).
                     append(MessageFormat.format("CentralRequest.GLOBAL_META_REQUEST_FIELD_NUMBER: {0}\n", new Object[]{CentralRequest.GLOBAL_META_REQUEST_FIELD_NUMBER})).
                     append(MessageFormat.format("request.hasGlobalMetaRequest(): {0}\n", new Object[]{request.hasGlobalMetaRequest()})).                     
                     append(MessageFormat.format("request.getGlobalMetaRequest(): {0}\n", new Object[]{request.getGlobalMetaRequest()})).
                     append(MessageFormat.format("request.getGlobalMetaRequestOrBuilder(): {0}\n", new Object[]{request.getGlobalMetaRequestOrBuilder()})).
                     append(MessageFormat.format("CentralRequest.PRELOAD_CLASSPATH_CACHE_REQUEST_FIELD_NUMBER: {0}\n", new Object[]{CentralRequest.PRELOAD_CLASSPATH_CACHE_REQUEST_FIELD_NUMBER})).
                     append(MessageFormat.format("request.hasPreloadClasspathCacheRequest(): {0}\n", new Object[]{request.hasPreloadClasspathCacheRequest()})).                     
                     append(MessageFormat.format("request.getPreloadClasspathCacheRequest(): {0}\n", new Object[]{request.getPreloadClasspathCacheRequest()})).
                     append(MessageFormat.format("request.getPreloadClasspathCacheRequestOrBuilder(): {0}\n", new Object[]{request.getPreloadClasspathCacheRequestOrBuilder()})).
                     append(MessageFormat.format("CentralRequest.MATCHING_CLASS_NAMES_REQUEST_FIELD_NUMBER: {0}\n", new Object[]{CentralRequest.MATCHING_CLASS_NAMES_REQUEST_FIELD_NUMBER})).
                     append(MessageFormat.format("request.hasMatchingClassNamesRequest(): {0}\n", new Object[]{request.hasMatchingClassNamesRequest()})).                     
                     append(MessageFormat.format("request.getMatchingClassNamesRequest(): {0}\n", new Object[]{request.getMatchingClassNamesRequest()})).
                     append(MessageFormat.format("request.getMatchingClassNamesRequestOrBuilder(): {0}\n", new Object[]{request.getMatchingClassNamesRequestOrBuilder()})).
                     append(MessageFormat.format("CentralRequest.MATCHING_METHOD_NAMES_REQUEST_FIELD_NUMBER: {0}\n", new Object[]{CentralRequest.MATCHING_METHOD_NAMES_REQUEST_FIELD_NUMBER})).
                     append(MessageFormat.format("request.hasMatchingMethodNamesRequest(): {0}\n", new Object[]{request.hasMatchingMethodNamesRequest()})).                     
                     append(MessageFormat.format("request.getMatchingMethodNamesRequest(): {0}\n", new Object[]{request.getMatchingMethodNamesRequest()})).
                     append(MessageFormat.format("request.getMatchingMethodNamesRequestOrBuilder(): {0}\n", new Object[]{request.getMatchingMethodNamesRequestOrBuilder()})).
                     append(MessageFormat.format("CentralRequest.METHOD_SIGNATURES_REQUEST_FIELD_NUMBER: {0}\n", new Object[]{CentralRequest.METHOD_SIGNATURES_REQUEST_FIELD_NUMBER})).
                     append(MessageFormat.format("request.hasMethodSignaturesRequest(): {0}\n", new Object[]{request.hasMethodSignaturesRequest()})).                     
                     append(MessageFormat.format("request.getMethodSignaturesRequest(): {0}\n", new Object[]{request.getMethodSignaturesRequest()})).
                     append(MessageFormat.format("request.getMethodSignaturesRequestOrBuilder(): {0}\n", new Object[]{request.getMethodSignaturesRequestOrBuilder()})).
                     append(MessageFormat.format("CentralRequest.REWEAVE_REQUEST_FIELD_NUMBER: {0}\n", new Object[]{CentralRequest.REWEAVE_REQUEST_FIELD_NUMBER})).
                     append(MessageFormat.format("request.hasReweaveRequest(): {0}\n", new Object[]{request.hasReweaveRequest()})).                     
                     append(MessageFormat.format("request.getReweaveRequest(): {0}\n", new Object[]{request.getReweaveRequest()})).
                     append(MessageFormat.format("request.getReweaveRequestOrBuilder(): {0}\n", new Object[]{request.getReweaveRequestOrBuilder()})).
                     append(MessageFormat.format("CentralRequest.HEADER_REQUEST_FIELD_NUMBER: {0}\n", new Object[]{CentralRequest.HEADER_REQUEST_FIELD_NUMBER})).
                     append(MessageFormat.format("request.hasHeaderRequest(): {0}\n", new Object[]{request.hasHeaderRequest()})).                     
                     append(MessageFormat.format("request.getHeaderRequest(): {0}\n", new Object[]{request.getHeaderRequest()})).
                     append(MessageFormat.format("request.getHeaderRequestOrBuilder(): {0}\n", new Object[]{request.getHeaderRequestOrBuilder()})).
                     append(MessageFormat.format("CentralRequest.ENTRIES_REQUEST_FIELD_NUMBER: {0}\n", new Object[]{CentralRequest.ENTRIES_REQUEST_FIELD_NUMBER})).
                     append(MessageFormat.format("request.hasEntriesRequest(): {0}\n", new Object[]{request.hasEntriesRequest()})).                     
                     append(MessageFormat.format("request.getEntriesRequest(): {0}\n", new Object[]{request.getEntriesRequest()})).
                     append(MessageFormat.format("request.getEntriesRequestOrBuilder(): {0}\n", new Object[]{request.getEntriesRequestOrBuilder()})).
                     append(MessageFormat.format("CentralRequest.QUERIES_REQUEST_FIELD_NUMBER: {0}\n", new Object[]{CentralRequest.QUERIES_REQUEST_FIELD_NUMBER})).
                     append(MessageFormat.format("request.hasQueriesRequest(): {0}\n", new Object[]{request.hasQueriesRequest()})).                     
                     append(MessageFormat.format("request.getQueriesRequest(): {0}\n", new Object[]{request.getQueriesRequest()})).
                     append(MessageFormat.format("request.getQueriesRequestOrBuilder(): {0}\n", new Object[]{request.getQueriesRequestOrBuilder()})).
                     append(MessageFormat.format("CentralRequest.MAIN_THREAD_PROFILE_REQUEST_FIELD_NUMBER: {0}\n", new Object[]{CentralRequest.MAIN_THREAD_PROFILE_REQUEST_FIELD_NUMBER})).
                     append(MessageFormat.format("request.hasMainThreadProfileRequest(): {0}\n", new Object[]{request.hasMainThreadProfileRequest()})).                     
                     append(MessageFormat.format("request.getMainThreadProfileRequest(): {0}\n", new Object[]{request.getMainThreadProfileRequest()})).
                     append(MessageFormat.format("request.getMainThreadProfileRequestOrBuilder(): {0}\n", new Object[]{request.getMainThreadProfileRequestOrBuilder()})).
                     append(MessageFormat.format("CentralRequest.AUX_THREAD_PROFILE_REQUEST_FIELD_NUMBER: {0}\n", new Object[]{CentralRequest.AUX_THREAD_PROFILE_REQUEST_FIELD_NUMBER})).
                     append(MessageFormat.format("request.hasAuxThreadProfileRequest(): {0}\n", new Object[]{request.hasAuxThreadProfileRequest()})).                     
                     append(MessageFormat.format("request.getAuxThreadProfileRequest(): {0}\n", new Object[]{request.getAuxThreadProfileRequest()})).
                     append(MessageFormat.format("request.getAuxThreadProfileRequestOrBuilder(): {0}\n", new Object[]{request.getAuxThreadProfileRequestOrBuilder()})).
                     append(MessageFormat.format("CentralRequest.FULL_TRACE_REQUEST_FIELD_NUMBER: {0}\n", new Object[]{CentralRequest.FULL_TRACE_REQUEST_FIELD_NUMBER})).
                     append(MessageFormat.format("request.hasFullTraceRequest(): {0}\n", new Object[]{request.hasFullTraceRequest()})).                     
                     append(MessageFormat.format("request.getFullTraceRequest(): {0}\n", new Object[]{request.getFullTraceRequest()})).
                     append(MessageFormat.format("request.getFullTraceRequestOrBuilder(): {0}\n", new Object[]{request.getFullTraceRequestOrBuilder()})).toString();    
   }


   public static String StackTraceElementToString(StackTraceElement ste){

      String retVal = "StackTraceElement == null";

      if(ste != null && !ste.equals(org.glowroot.wire.api.model.DownstreamServiceOuterClass.ThreadDump.StackTraceElement.getDefaultInstance())){
                  retVal = new StringBuilder().
                         append("********Begin logging StackTraceElement********\n").
                         append(MessageFormat.format("****StackTraceElement.getClassName(): {0}***\n", ste.getClassName())).
                         append(MessageFormat.format("****StackTraceElement.getFileName(): {0}***\n", ste.getFileName())).
                         append(MessageFormat.format("****StackTraceElement.getLineNumber(): {0}***\n", ste.getLineNumber())).
                         append(MessageFormat.format("****StackTraceElement.getMethodName(): {0}***\n", ste.getMethodName())).
                         append(CoreProtoToString.GeneratedMessageV3ToString(ste)).
                         append(MessageFormat.format("****StackTraceElement.getMonitorInfoCount(): {0}***\n", ste.getMonitorInfoCount())).
                         append(LockInfoListToString(ste.getMonitorInfoList())).
                         append("********Done logging StackTraceElement********\n").toString();
      }
      return retVal;

   }

   public static String StackTraceElementListToString(List<StackTraceElement> steList){

      String retVal = "StackTraceElement List == null";
      
      if(steList != null && !steList.isEmpty()){

         StringBuilder strVal = new StringBuilder().
         append("********Begin logging StackTraceElement List********\n");
         
         for(StackTraceElement ste : steList){       
            strVal.append(StackTraceElementToString(ste));      
         }
         retVal = strVal.append("********Done logging StackTraceElement List********\n").toString();
     }

     return retVal;
         
   }

   


   public static String LockInfoToString(LockInfo li){

      String retVal = "LockInfo == null";

      if(li != null && !li.equals(org.glowroot.wire.api.model.DownstreamServiceOuterClass.ThreadDump.LockInfo.getDefaultInstance())){

         retVal = new StringBuilder().
                             append("********Begin logging LockInfo********\n").
                             append(MessageFormat.format("****li.getClassName(): {0}***\n", li.getClassName())).
                             append(MessageFormat.format("****li.isInitialized(): {0}***\n", li.isInitialized())).
                             append(CoreProtoToString.GeneratedMessageV3ToString(li)).
                             append("********Done logging LockInfo********\n").toString();
      }
   
      return retVal;
   }

   public static String LockInfoListToString(List<LockInfo> liList){

      String retVal = "LockInfo List == null";

      if(liList != null && !liList.isEmpty()){
      
         StringBuilder strVal = new StringBuilder().
         append("********Begin logging LockInfo List********\n");
         
         for(LockInfo li : liList){       
            strVal.append(LockInfoToString(li));      
         }
         retVal = strVal.append("********Done logging LockInfo List********\n").toString();
      }

      return retVal;
         
   }

   public static String ThreadToString(Thread thread){

      String retVal = "Thread == null";

      if(thread != null && !thread.equals(org.glowroot.wire.api.model.DownstreamServiceOuterClass.ThreadDump.Thread.getDefaultInstance())){
      
         StringBuilder strVal = new StringBuilder();
         
         strVal.append("********Begin logging Thread object********\n").
            append(MessageFormat.format("****thread.getClassName(): {0}***\n", thread.getClass().getName())).
            append(MessageFormat.format("Thread getName() --> {0}\n", thread.getName())).
            append(MessageFormat.format("Thread getState() --> {0}\n", thread.getState())).
            append(MessageFormat.format("Thread getId() --> {0}\n", thread.getId())).
            append(MessageFormat.format("Thread getDescriptor() --> {0}", Thread.getDescriptor())).
            append(CoreProtoToString.GeneratedMessageV3ToString(thread)).
            append(MessageFormat.format("Thread lockOwnerId() --> {0}\n", thread.getLockOwnerId())).        
            append(MessageFormat.format("Thread getLockName() --> {0}\n", thread.getLockName())).
            append(LockInfoToString(thread.getLockInfo())).
            append(MessageFormat.format("Thread getStackTraceCount() --> {0}\n", thread.getStackTraceElementCount())).
            append(StackTraceElementListToString(thread.getStackTraceElementList())).
            append("********Done logging Thread object********\n");

         retVal = strVal.toString();
      }
   
      return retVal; 

   }

   public static String ThreadListToString(List<Thread> tList){

      String retVal = "Thread List == null";
      
      if(tList != null && !tList.isEmpty()){

         StringBuilder strVal = new StringBuilder();
         strVal.append("********Begin logging Thread List********\n");
         for(Thread thread : tList){
            strVal.append(MessageFormat.format("Thread --> {0}\n", ThreadToString(thread)));
         }
         retVal = strVal.append("********End logging Thread List********\n").toString();
      }

      return retVal;
   }


   public static String TransactionToString(Transaction transaction){     
      
      String retVal = "Transaction == null";
      
      if(transaction != null && !transaction.equals(org.glowroot.wire.api.model.DownstreamServiceOuterClass.ThreadDump.Transaction.getDefaultInstance())){

         Descriptor descriptor = transaction.getDescriptorForType();
         String unQualifiedFieldName = descriptor.getName();
         FieldDescriptor fd = descriptor.findFieldByName(unQualifiedFieldName);
         

         retVal = new StringBuilder().
                              append("********Begin logging Transaction********\n").
                              append(MessageFormat.format("Transaction getTraceId() --> {0}\n", transaction.getTraceId())).
                              append(MessageFormat.format("Transaction getTransactionName() --> {0}\n", transaction.getTransactionName())).
                              append(MessageFormat.format("Transaction getTransactionType() --> {0}\n", transaction.getTransactionType())).
                              append(MessageFormat.format("Transaction getDurationNanos() --> {0}\n", transaction.getDurationNanos())).
                              append(MessageFormat.format("Transaction getHeadline() --> {0}\n", transaction.getHeadline())).
                              append(MessageFormat.format("Transaction getThreadCount() --> {0}\n", transaction.getThreadCount())).
                              append(MessageFormat.format("Transaction getCpuNamos() --> {0}\n", transaction.getCpuNanos().getValue())).
                              append(MessageFormat.format("****Field descriptor: {0}\n", CoreProtoToString.DescriptorToString(descriptor))).   
                              append(CoreProtoToString.GeneratedMessageV3ToString(transaction)).                         
                              append(ThreadListToString(transaction.getThreadList())).                            
                              append("********End logging Transaction********\n").toString();
      }

      return retVal;
   }

   public static String TransactionListToString(List<Transaction> tList){

      String retVal = "Transaction List == null";

      if(tList != null && !tList.isEmpty()){
         StringBuilder strVal = new StringBuilder();
         strVal.append("********Begin logging Transaction List********\n");
         for(Transaction transaction : tList){
            strVal.append(MessageFormat.format("Transaction --> {0}\n", TransactionToString(transaction)));
         }
         retVal = strVal.append("********End logging Transaction List********\n").toString();
     }

      return retVal;
   }

   public static String ThreadDumpToString(ThreadDump td){

      String retVal = "Thread Dump == null";

      if(td != null && !td.equals(org.glowroot.wire.api.model.DownstreamServiceOuterClass.ThreadDump.getDefaultInstance())){

            Descriptor descriptor = td.getDescriptorForType();
            String unQualifiedFieldName = descriptor.getName();
            FieldDescriptor fd = descriptor.findFieldByName(unQualifiedFieldName);
            

            retVal = new StringBuilder().
                                 append("********Begin logging ThreadDump********\n").
                                 append(MessageFormat.format("GetJStackAvailable --> {0}\n", td.getJstackAvailable())).
                                 append(MessageFormat.format("isINitialized --> {0}\n", td.isInitialized())).
                                 append(MessageFormat.format("****************td.getThreadDumpingThread()********\n{0}\n",
                                          ThreadToString(td.getThreadDumpingThread()))).
                                 append(MessageFormat.format("GetTransactionCount --> {0}\n", td.getTransactionCount())).
                                 append(MessageFormat.format("****************td.getTransactionList()********\n{0}\n",
                                          TransactionListToString(td.getTransactionList()))).
                                 append(MessageFormat.format("GetUnMatchedThreadCount() --> {0}\n", td.getUnmatchedThreadCount())).
                                 append(MessageFormat.format("****************td.getUnMatchedThreadList()********\n{0}\n",
                                          ThreadListToString(td.getUnmatchedThreadList()))).
                                 append(CoreProtoToString.GeneratedMessageV3ToString(td)).  
                                 
                                 append("********End logging ThreadDump********\n").toString();
      }

      return retVal;

   }

  

   public static String ExceptionToString(Throwable t){

      String retVal = " ";
      
      if (t != null & t.getStackTrace() != null) {
         final ByteArrayOutputStream baos = new ByteArrayOutputStream();
         PrintStream ps = new PrintStream(baos);
         t.printStackTrace(ps);
         retVal = new String(baos.toByteArray());
      }

      return retVal;
   }

}
