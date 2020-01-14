package org.glowroot.agent.central;

import java.text.MessageFormat;
import java.util.List;

import com.google.protobuf.ProtocolStringList;
import com.google.protobuf.Descriptors.EnumValueDescriptor;

import org.glowroot.wire.api.model.CollectorServiceOuterClass.TraceStreamMessage;
import org.glowroot.wire.api.model.TraceOuterClass.Trace.DetailEntry;
import org.glowroot.wire.api.model.TraceOuterClass.Trace.DetailValue;
import org.glowroot.wire.api.model.ProfileOuterClass.Profile;
import org.glowroot.wire.api.model.AggregateOuterClass.Aggregate.Query;
import org.glowroot.wire.api.model.ProfileOuterClass.Profile.ProfileNode;
import org.glowroot.wire.api.model.ProfileOuterClass.Profile.LeafThreadState;
import org.glowroot.wire.api.model.CollectorServiceOuterClass.TraceStreamMessage.MessageCase;
import org.glowroot.wire.api.model.CollectorServiceOuterClass.TraceStreamMessage.Queries;
import org.glowroot.wire.api.model.CollectorServiceOuterClass.TraceStreamMessage.TraceStreamCounts;
import org.glowroot.wire.api.model.CollectorServiceOuterClass.TraceStreamMessage.TraceStreamHeader;
import org.glowroot.wire.api.model.TraceOuterClass.Trace;
import org.glowroot.wire.api.model.TraceOuterClass.Trace.Header;
import org.glowroot.wire.api.model.TraceOuterClass.Trace.OldThreadStats;
import org.glowroot.wire.api.model.TraceOuterClass.Trace.Timer;
import org.glowroot.wire.api.model.TraceOuterClass.Trace.Error;
import org.glowroot.wire.api.model.TraceOuterClass.Trace.Attribute;
import org.glowroot.wire.api.model.TraceOuterClass.Trace.ThreadStats;
import org.glowroot.wire.api.model.TraceOuterClass.Trace.Entry;
import org.glowroot.wire.api.model.TraceOuterClass.Trace.QueryEntryMessage;
import org.glowroot.wire.api.model.TraceOuterClass.Trace.SharedQueryText;
import org.glowroot.wire.api.model.Proto.StackTraceElement;
import org.glowroot.wire.api.model.Proto.Throwable;
import org.glowroot.agent.collector.Collector.TraceReader;
import org.glowroot.common.util.CoreProtoToString;
import org.glowroot.common.util.GrProtoObjToString;



public class GrCentralObjToString {
    
    public static String TraceStreamMessageToString(TraceStreamMessage tsm){

        StringBuilder retVal = 
                new StringBuilder("****************************************\n"). 
                           append("****************************************\n").
                           append("*****Begin TraceStreamMessageToString***\n").
                           append("****************************************\n"). 
                           append("****************************************\n");

        if(tsm != null && !tsm.equals(org.glowroot.wire.api.model.CollectorServiceOuterClass.TraceStreamMessage.getDefaultInstance())){
            retVal.append(MessageFormat.format("Has Aux Thread Profile: {0}\n", 
                         new Object[]{tsm.hasAuxThreadProfile()})).            
                  append(MessageFormat.format("Auxillary Profile: {0}\n", 
                     new Object[]{ProfileToString(tsm.getAuxThreadProfile())})).
                  append(MessageFormat.format("Is initialized: {0}\n", 
                     new Object[]{tsm.isInitialized()})).
                   append(MessageFormat.format("Get Serialized Size: {0}\n", 
                     new Object[]{tsm.getSerializedSize()})).
                   
               append(MessageFormat.format("Has Queries: {0}\n", 
                     new Object[]{tsm.hasQueries()})).
               append(MessageFormat.format("Queries: {0}\n", 
                     new Object[]{QueriesToString(tsm.getQueries())})).
               append(MessageFormat.format("Descriptor: {0}\n", 
                     new Object[]{CoreProtoToString.DescriptorToString(
                                     TraceStreamMessage.getDescriptor())})).
               append(MessageFormat.format("Has Entry: {0}\n", 
                     new Object[]{tsm.hasEntry()})).
               append(MessageFormat.format("getEntry(): {0}\n", 
                     new Object[]{EntryToString(tsm.getEntry())})). 
               append(MessageFormat.format("Has Header: {0}\n", 
                     new Object[]{tsm.hasHeader()})).              
               append(MessageFormat.format("getHeader(): {0}\n", 
                     new Object[]{HeaderToString(tsm.getHeader())})).
               append(MessageFormat.format("Has Main Thread Profile: {0}\n", 
                     new Object[]{tsm.hasMainThreadProfile()})).
               append(MessageFormat.format("**getMainThreadProfile()**: {0}\n", 
                     new Object[]{ProfileToString(tsm.getMainThreadProfile())})).
               append(MessageFormat.format("getMessageCase(): {0}\n", 
                     new Object[]{(tsm.getMessageCase() != null) ? tsm.getMessageCase().toString() : null})).
               append(MessageFormat.format("**hasSharedQueryText()**: {0}\n", 
                     new Object[]{SharedQueryTextToString(tsm.getSharedQueryText())})).
               append(MessageFormat.format("**getSharedQueryTextToString()**: {0}\n", 
                     new Object[]{tsm.hasSharedQueryText()})).
               append(MessageFormat.format("getMessageCase(): {0}\n", 
                     new Object[]{(tsm.getMessageCase() != null) ? tsm.getMessageCase().toString() : null})).
               append(MessageFormat.format("hasStreamCounts(): {0}\n", 
                     new Object[]{tsm.hasStreamCounts()})).
               append(MessageFormat.format("hasStreamHeader(): {0}\n", 
                     new Object[]{tsm.hasStreamHeader()})).
               append(MessageFormat.format("getStreamHeader(): {0}\n", 
                     new Object[]{TraceStreamHeaderToString(tsm.getStreamHeader())})).
               append(MessageFormat.format("getTraceSteamCounts(): {0}\n", 
                     new Object[]{TraceStreamCountsToString(tsm.getStreamCounts())})).
               append(MessageFormat.format("hasTrace(): {0}\n", 
                     new Object[]{tsm.hasTrace()})).
               append(MessageFormat.format("getTrace(): {0}\n", 
                     new Object[]{TraceToString(tsm.getTrace())}));          
               
        }else{
            retVal.append("TraceStreamMessage == null\n");
        }
        retVal.append("****************************************\n"). 
               append("****************************************\n").
               append("*******End TraceStreamMessageToString********\n").
               append("****************************************\n"). 
               append("****************************************\n");
        return retVal.toString();
        
    }

    public static String TraceToString(Trace tsm){

      StringBuilder retVal = 
              new StringBuilder("****************************************\n").
                         append("*******Begin TraceToString**************\n").
                         append("****************************************\n");

      if(tsm != null && !tsm.equals(org.glowroot.wire.api.model.TraceOuterClass.Trace.getDefaultInstance())){
          retVal.append(MessageFormat.format("Auxillary Profile: {0}\n", 
                   new Object[]{ProfileToString(tsm.getAuxThreadProfile())})).
                 append(MessageFormat.format("Descriptor: {0}\n", 
                   new Object[]{CoreProtoToString.DescriptorToString(
                                   Trace.getDescriptor())})).
                 append(MessageFormat.format("getUpdate(): {0}\n", 
                                   new Object[]{tsm.getUpdate()})).
                 append(MessageFormat.format("hasAuxThreadProfile(): {0}\n", 
                                   new Object[]{tsm.hasAuxThreadProfile()})).
                 append(MessageFormat.format("hasMainThreadProfile(): {0}\n", 
                                   new Object[]{tsm.hasMainThreadProfile()})).
                 append(MessageFormat.format("hasHeader(): {0}\n", 
                                   new Object[]{tsm.hasHeader()})).
                 append(MessageFormat.format("isInitialized(): {0}\n", 
                                   new Object[]{tsm.isInitialized()})).
                 append(MessageFormat.format("getEntryCount(): {0}\n", 
                   new Object[]{tsm.getEntryCount()})).
                 append(MessageFormat.format("getEntryList(): {0}\n", 
                   new Object[]{tsm.getEntryList()})).    
                 append(MessageFormat.format("getHeader(): {0}\n", 
                   new Object[]{HeaderToString(tsm.getHeader())})).
                 append(MessageFormat.format("**getMainThreadProfile()**: {0}\n", 
                   new Object[]{ProfileToString(tsm.getMainThreadProfile())})).
                 append(MessageFormat.format("getId(): {0}\n", 
                   new Object[]{tsm.getId()})).
                 append(MessageFormat.format("Get Query Count: {0}\n", 
                   new Object[]{tsm.getQueryCount()})). 
                 append(MessageFormat.format("****Get Query List: {0}\n", 
                   new Object[]{QueryListToString(tsm.getQueryList())})).               
                 append(MessageFormat.format("Get Serialized Size: {0}\n", 
                   new Object[]{tsm.getSerializedSize()})).
                 append(MessageFormat.format("Get Shared Query Text Count: {0}\n", 
                   new Object[]{tsm.getSharedQueryTextCount()})).
                append(MessageFormat.format("Get Shared Query Text List: {0}\n", 
                   new Object[]{tsm.getSharedQueryTextList()}));
             
      }else{
          retVal.append("Trace == null\n");
      }

      retVal.append("****************************************\n").
            append("*******End TraceToString***************\n").
            append("****************************************\n");
      return retVal.toString();
      
  }

  public static String TraceReaderToString(TraceReader tsm){

      StringBuilder retVal = 
              new StringBuilder("****************************************\n").
                         append("*******Begin TraceReaderToString**************\n").
                         append("****************************************\n");
      
      if(tsm != null){
          retVal.append(MessageFormat.format("TraceId: {0}\n", 
                   new Object[]{tsm.traceId()})).
                 append(MessageFormat.format("Capture time: {0}\n", 
                   new Object[]{tsm.captureTime()})).
                 append(MessageFormat.format("Partial?: {0}\n", 
                                   new Object[]{tsm.partial()})).
                 append(MessageFormat.format("Update?: {0}\n", 
                                   new Object[]{tsm.update()})).
                 append(MessageFormat.format("Header: \n{0}\n", 
                                   new Object[]{HeaderToString(tsm.readHeader())}));
             
      }else{
          retVal.append("TraceReader == null\n");
      }

      retVal.append("****************************************\n").
            append("*******End TraceReaderToString***************\n").
            append("****************************************\n");
      return retVal.toString();
      
  }

    public static String SharedQueryTextToString(SharedQueryText tsc){

      StringBuilder retVal = 
              new StringBuilder("*******Begin SharedQueryText********\n");

      if(tsc != null && !tsc.equals(org.glowroot.wire.api.model.TraceOuterClass.Trace.SharedQueryText.getDefaultInstance())){
              retVal.append(MessageFormat.format("Descriptor: {0}\n", 
                        new Object[]{CoreProtoToString.DescriptorToString(SharedQueryText.getDescriptor())})).
                     append(MessageFormat.format("Is initialized?: {0}\n", 
                        new Object[]{tsc.isInitialized()})).
                     append(MessageFormat.format("Serialized Size: {0}\n", 
                        new Object[]{tsc.getSerializedSize()})).
                     append(MessageFormat.format("Get full text: {0}\n", 
                        new Object[]{tsc.getFullText()})).
                     append(MessageFormat.format("Get truncated end text: {0}\n", 
                        new Object[]{tsc.getTruncatedEndText()})).
                     append(MessageFormat.format("Get truncated text: {0}\n", 
                        new Object[]{tsc.getTruncatedText()}));
      }else{
          retVal.append("SharedQueryText == null\n");
      }

      retVal.append("*******End SharedQueryText********\n");
      return retVal.toString();
      
  }  
  
  public static String SharedQueryTextListToString(List<SharedQueryText> sql) {

      StringBuilder retVal = new StringBuilder("*******Begin SharedQueryTextListToString********\n");

      if (sql != null && !sql.isEmpty()) {
            
            for (SharedQueryText sq : sql) {
                  

                  retVal.append("*******Begin SharedQueryTextToString********\n").

                         append(MessageFormat.format("{0}\n", new Object[] { SharedQueryTextToString(sq)})).

                         append("*******End SharedQueryTextToString********\n");
            }
            

      } else {
            retVal.append("SharedQueryTextList == null or empty\n");
      }
      retVal.append("*******End SharedQueryTextToString********\n");

      return retVal.toString();

}


    public static String TimerToString(Timer tsc){

      StringBuilder retVal = 
              new StringBuilder("*******Begin Timer********\n");

      if(tsc != null && !tsc.equals(Timer.getDefaultInstance())){
             retVal.append(MessageFormat.format("Get Name: {0}\n", 
                         new Object[]{tsc.getName()})).
             append(MessageFormat.format("Descriptor: {0}\n", 
                        new Object[]{CoreProtoToString.DescriptorToString(Timer.getDescriptor())})).
             append(MessageFormat.format("Serialized Size: {0}\n", 
                   new Object[]{tsc.getSerializedSize()})).
             append(MessageFormat.format("Is initialized: {0}\n", 
                   new Object[]{tsc.isInitialized()})).
             append(MessageFormat.format("Get Extended: {0}\n", 
                   new Object[]{tsc.getExtended()})).       
             append(MessageFormat.format("Get Count: {0}\n", 
                   new Object[]{tsc.getCount()})).       
             append(MessageFormat.format("Active: {0}\n", 
                   new Object[]{tsc.getActive()})).
             append(MessageFormat.format("Get Total Nanos: {0}\n", 
                   new Object[]{tsc.getTotalNanos()})).
             append(MessageFormat.format("Get Child Timer Count: {0}\n", 
                   new Object[]{tsc.getChildTimerCount()})).
             append(MessageFormat.format("Get Child Timer List: {0}\n", 
                   new Object[]{TimerListToString(tsc.getChildTimerList())}));
      }else{
          retVal.append("Timer == null\n");
      }

      retVal.append("*******End Timer********\n");
      return retVal.toString();
      
  }    

      public static String TimerListToString(List<Timer> timers) {

            StringBuilder retVal = new StringBuilder("****************************************\n").
                                              append("*********Begin TimerListToString********\n");

            if (timers != null && !timers.isEmpty()) {
                  
                  for (Timer timer : timers) {
                        

                        retVal.append("*******Begin TimerToString********\n").

                               append(MessageFormat.format("{0}\n", new Object[] { TimerToString(timer)})).

                               append("*******End TimerToString********\n");
                  }
                  
            } else {
                  retVal.append("TimerList == null or empty\n");
            }
            retVal.append("****************************************\n").
                   append("***********End TimerListToString********\n");

            return retVal.toString();

      }


      public static String AttributeListToString(List<Attribute> attrs) {

            StringBuilder retVal = new StringBuilder("****************************************\n").
                                              append("*******Begin AttributeListToString********\n");

            if (attrs != null && !attrs.isEmpty()) {
                  int cnt = 0;
                  for (Attribute attr : attrs) {
                        cnt++;

                        retVal.append("*******Begin AttributeToString********\n").

                               append(MessageFormat.format("{0}:\n", new Object[] { AttributeToString(attr)})).

                               append("*******End AttributeToString********\n");
                  }
                  if(cnt == 0){
                      retVal.append("AttributeListToString == null or empty\n");
                  }

            } else {
                  retVal.append("AttributeList == null or empty\n");
            }
            retVal.append("****************************************\n").
                   append("*******End AttributeListToString********\n");

            return retVal.toString();

      } 

    public static String AttributeToString(Attribute attr) {

            StringBuilder retVal = 
                    new StringBuilder("*******Begin AttributeToString********\n");
    
            if(attr != null && !attr.equals(Attribute.getDefaultInstance())){
                  retVal.append(MessageFormat.format("Descriptor: {0}\n", 
                     new Object[]{CoreProtoToString.DescriptorToString(Attribute.getDescriptor())})).
                  append(MessageFormat.format("Name: {0}\n", 
                     new Object[]{attr.getName()})).
                  append(MessageFormat.format("Value count: {0}\n", 
                     new Object[]{attr.getValueCount()})).
                  append(MessageFormat.format("****Value List: {0}\n", 
                     new Object[]{ProtocolStringListToString(null, attr.getValueList())})).
                  append(MessageFormat.format("Is initialized?: {0}\n", 
                     new Object[]{attr.isInitialized()})).
                  append(MessageFormat.format("Get Serialized Size: {0}\n", 
                     new Object[]{attr.getSerializedSize()}));
            }else{
                  retVal.append("Attribute == null\n");
               }
         
               retVal.append("*******End AttributeToString********\n");
               return retVal.toString();
      }

    public static String ThreadStatsToString(ThreadStats ts){


      StringBuilder retVal = 
      new StringBuilder("*******Begin ThreadStatsToString********\n");

      if(ts != null && !ts.equals(ThreadStats.getDefaultInstance())){
         retVal.append(MessageFormat.format("Get Allocated Bytes: {0}\n", 
                  new Object[]{ts.getAllocatedBytes()})).
                append(MessageFormat.format("Get Blocked Nanos: {0}\n", 
                  new Object[]{ts.getBlockedNanos()})).
                append(MessageFormat.format("Get CPU Nanos: {0}\n", 
                  new Object[]{ts.getCpuNanos()})).
                append(MessageFormat.format("Descriptor: {0}\n", 
                  new Object[]{CoreProtoToString.DescriptorToString(ThreadStats.getDescriptor())})).  
                append(MessageFormat.format("Serialized Size: {0}\n", 
                  new Object[]{ts.getSerializedSize()})).
                append(MessageFormat.format("Get Waited Nanos: {0}\n", 
                  new Object[]{ts.getWaitedNanos()})).
                append(MessageFormat.format("isInitialized(): {0}\n", 
                  new Object[]{ts.isInitialized()}));
                

      }else{
         retVal.append("ThreadStats == null\n");
      }

      retVal.append("*******End ThreadStatsToString********\n");
      return retVal.toString();

    }

    public static String QueryEntryMessageToString(QueryEntryMessage de) {

            StringBuilder retVal = new StringBuilder("*******Begin QueryEntryMessageToString********\n");

            if (de != null && !de.equals(QueryEntryMessage.getDefaultInstance())) {
                  retVal.append(MessageFormat.format("Descriptor: {0}\n", 
                              new Object[]{CoreProtoToString.DescriptorToString(
                                    QueryEntryMessage.getDescriptor())})).  
                        append(MessageFormat.format("Get Prefix: {0}\n",
                              new Object[] { de.getPrefix() })).
                        append(MessageFormat.format("Get Serialized Size: {0}\n",
                              new Object[] { de.getSerializedSize() })).
                        append(MessageFormat.format("Get Shared Query Text Index: {0}\n",
                              new Object[] { de.getSharedQueryTextIndex() })).
                        append(MessageFormat.format("Get Suffix: {0}\n",
                              new Object[] { de.getSuffix() })).
                        append(MessageFormat.format("Is initialized: {0}\n",
                              new Object[] { de.isInitialized() }));

            }else{
                  retVal.append("*******QueryEntryMessage == null********\n");
            }

            retVal.append("*******End QueryEntryMessageToString********\n");

            return retVal.toString();
      }

      public static String DetailEntryListToString(List<DetailEntry> deList) {

            StringBuilder retVal = new StringBuilder("*******Begin DetailEntryListToString********\n");
            
            if (deList != null && !deList.isEmpty()) {
                  for (DetailEntry de : deList) {
                        
                        retVal.append("*******Begin DetailEntryToString********\n"). 
                        append(MessageFormat.format("Detail Entry To String: {0}\n",
                                    new Object[] {  DetailEntryToString(de) })).
                        append("*******End DetailEntryToString********\n");                        
                   
                  }
            }else{
                  retVal.append("List<DetailEntry> == null\n");
            }

            retVal.append("*******End DetailEntryListToString********\n");
            return retVal.toString();
      }

      public static String DetailValueListToString(List<DetailValue> deList) {

            StringBuilder retVal = new StringBuilder("*******Begin DetailValueListToString********\n");
            
            if (deList != null && !deList.isEmpty()) {
                  for (DetailValue de : deList) {
                        
                        retVal.append("*******Begin DetailValueToString********\n"). 
                        append(MessageFormat.format("Detail Value To String: {0}\n",
                                    new Object[] {  DetailValueToString(de) })).
                        append("*******End DetailValueToString********\n");                        
                   
                  }
            }else{
                  retVal.append("List<DetailValue> == null\n");
            }

            retVal.append("*******End DetailValueListToString********\n");
            return retVal.toString();
      }


//buildPartial?
      public static String DetailEntryToString(DetailEntry de) {

            StringBuilder retVal = new StringBuilder("*******Begin DetailEntry********\n");

            if (de != null && !de.equals(DetailEntry.getDefaultInstance())) {
                  retVal.append(MessageFormat.format("Get Name: {0}\n",
                              new Object[] { de.getName() })).
                         append(MessageFormat.format("Get Serialized Size: {0}\n",
                              new Object[] { de.getSerializedSize() })).
                         append(MessageFormat.format("Descriptor: {0}\n", 
                              new Object[]{CoreProtoToString.DescriptorToString(DetailEntry.getDescriptor())})).  
                         append(MessageFormat.format("GetChildEntryCount: {0}\n",
                              new Object[] { de.getChildEntryCount() })).
                         append(MessageFormat.format("GetChildEntryList: {0}\n",
                              new Object[] { DetailEntryListToString(de.getChildEntryList()) })).
                         append(MessageFormat.format("Get Serialized Size: {0}\n",
                              new Object[] { de.getSerializedSize() })).
                         append(MessageFormat.format("Get Value Count: {0}\n",
                              new Object[] { de.getValueCount() })).
                         append(MessageFormat.format("Get Value List: {0}\n",
                              new Object[] { DetailValueListToString(de.getValueList()) })).
                         append(MessageFormat.format("Is initialized: {0}\n",
                              new Object[] { de.isInitialized() }));
                  
            }else{
                  retVal.append("*******DetailEntry == null********\n");
            }

            retVal.append("*******End DetailEntry********\n");

            return retVal.toString();
      }

      public static String DetailValueToString(DetailValue de) {

            StringBuilder retVal = new StringBuilder("*******Begin DetailValue********\n");

            if (de != null && !de.equals(DetailValue.getDefaultInstance())) {
                  retVal.append(MessageFormat.format("GetBoolean: {0}\n",
                              new Object[] { de.getBoolean() })).
                         append(MessageFormat.format("Get Long: {0}\n",
                              new Object[] { de.getLong() })).
                         append(MessageFormat.format("Get String: {0}\n",
                              new Object[] { de.getString() })).
                         append(MessageFormat.format("Descriptor: {0}\n", 
                              new Object[]{CoreProtoToString.DescriptorToString(DetailValue.getDescriptor())})).  
                         append(MessageFormat.format("Get Double: {0}\n",
                              new Object[] { de.getDouble() })).
                         append(MessageFormat.format("Get Serialized Size: {0}\n",
                              new Object[] { de.getSerializedSize() })).
                         append(MessageFormat.format("Get Val Case: {0}\n",
                              new Object[] { de.getValCase() })).
                         append(MessageFormat.format("Is initialized: {0}\n",
                              new Object[] { de.isInitialized() }));
                  
            }else{
                  retVal.append("*******DetailValue == null********\n");
            }

            retVal.append("*******End DetailValue********\n");

            return retVal.toString();
      }

      public static String EntryListToString(List<Entry> eList){

            StringBuilder retVal = 
            new StringBuilder("*******Begin EntryListToString********\n");
    
            if(eList != null && !eList.isEmpty()){
                
                for(Entry entry : eList){
                         
                      retVal.append(EntryToString(entry));                      
    
                }            
            
            }else{
                retVal.append("EntryListToString == null or empty\n");
            }
    
            retVal = 
            new StringBuilder("*******End EntryListToString********\n");
    
            return retVal.toString();
    
        }

      public static String EntryToString(Entry de) {

            StringBuilder retVal = new StringBuilder("*******Begin EntryToString********\n");

            if (de != null && !de.equals(org.glowroot.wire.api.model.TraceOuterClass.Trace.Entry.getDefaultInstance())) {
                  retVal.append(MessageFormat.format("GetActive: {0}\n",
                              new Object[] { de.getActive() })).
                         append(MessageFormat.format("Get Depth: {0}\n",
                              new Object[] { de.getDepth() })).
                         append(MessageFormat.format("Get Serialized Size: {0}\n",
                              new Object[] { de.getSerializedSize() })).
                         append(MessageFormat.format("Descriptor: {0}\n", 
                              new Object[]{CoreProtoToString.DescriptorToString(Entry.getDescriptor())})).  
                         append(MessageFormat.format("GetDetailEntryCount: {0}\n",
                              new Object[] { de.getDetailEntryCount() })).
                         append(MessageFormat.format("Get Serialized Size: {0}\n",
                              new Object[] { de.getSerializedSize() })).
                         append(MessageFormat.format("Get Detail Entry List: {0}\n",
                              new Object[] { DetailEntryListToString(de.getDetailEntryList()) })).
                         append(MessageFormat.format("Get Duration Nanos: {0}\n",
                              new Object[] { de.getDurationNanos() })).
                         append(MessageFormat.format("hasError(): {0}\n", 
                              new Object[]{de.hasError()})).
                         append(MessageFormat.format("getError(): {0}\n", 
                              new Object[]{ErrorToString(de.getError())})).
                         append(MessageFormat.format("getLocationStackTraceElementCount(): {0}\n", 
                              new Object[]{de.getLocationStackTraceElementCount()})).
                         append(MessageFormat.format("getLocationStackTraceElementList(): {0}\n", 
                              new Object[]{StackTraceElementListToString(
                                    de.getLocationStackTraceElementList())})).
                         append(MessageFormat.format("Get Message: {0}\n",
                                    new Object[] { de.getMessage() })).
                        append(MessageFormat.format("Has Query Entry Message: {0}\n",
                                    new Object[] { de.hasQueryEntryMessage() })).
                        append(MessageFormat.format("Get Query Entry Message: {0}\n",
                                    new Object[] { QueryEntryMessageToString(de.getQueryEntryMessage()) })).
                        append(MessageFormat.format("Get Start Offset Nanos: {0}\n",
                                    new Object[] { de.getStartOffsetNanos() })).
                              
                         append(MessageFormat.format("Is initialized: {0}\n",
                              new Object[] { de.isInitialized() }));
                  
            }else{
                  retVal.append("*******Entry == null********\n");
            }

            retVal.append("*******End EntryToString********\n");

            return retVal.toString();
      }

     public static String ErrorToString(Error tsc){

            StringBuilder retVal = 
                        new StringBuilder("*******Begin ErrorToString********\n");

            if(tsc != null && !tsc.equals(org.glowroot.wire.api.model.TraceOuterClass.Trace.Error.getDefaultInstance())){
                        retVal.append(MessageFormat.format("Serialized Size: {0}\n", 
                                  new Object[]{tsc.getSerializedSize()})).
                               append(MessageFormat.format("Descriptor: {0}\n", 
                                  new Object[]{CoreProtoToString.DescriptorToString(Error.getDescriptor())})).
                               append(MessageFormat.format("Has Exception: {0}\n", 
                                  new Object[]{tsc.hasException()})).  
                              append(MessageFormat.format("Exception: {0}\n", 
                                  new Object[]{GrProtoObjToString.ThrowableToString(tsc.getException())})).
                              append(MessageFormat.format("Get Message: {0}\n", 
                                  new Object[]{tsc.getMessage()})).
                              append(MessageFormat.format("Is initialized: {0}\n", 
                                  new Object[]{tsc.isInitialized()}));
            }else{
                  retVal.append("******Error == null********\n");
            }

            retVal.append("*******End ErrorToString********\n");

            return retVal.toString();
      }

      public static String StackTraceElementToString(StackTraceElement ste){

            StringBuilder retVal = 
                        new StringBuilder("*******Begin StackTraceElement********\n");

      
            if(ste != null && !ste.equals(org.glowroot.wire.api.model.Proto.StackTraceElement.getDefaultInstance())){
                        retVal = new StringBuilder().
                               append("***************************************************\n").
                               append("********Begin logging StackTraceElement************\n").
                               append(MessageFormat.format("****StackTraceElement.getClassName(): {0}***\n", ste.getClassName())).
                               append(MessageFormat.format("****StackTraceElement.getFileName(): {0}***\n", ste.getFileName())).
                               append(MessageFormat.format("****StackTraceElement.getLineNumber(): {0}***\n", ste.getLineNumber())).
                               append(MessageFormat.format("****StackTraceElement.getMethodName(): {0}***\n", ste.getMethodName())).
                               append(MessageFormat.format("****StackTraceElement.isInitialized(): {0}***\n", ste.isInitialized())).
                               append(MessageFormat.format("Descriptor: {0}\n", 
                                   new Object[]{CoreProtoToString.DescriptorToString(StackTraceElement.getDescriptor())})).  
                               append(MessageFormat.format("Get Serialized Size: {0}\n",
                                   new Object[] { ste.getSerializedSize() })).
                               append("********Done logging StackTraceElement********\n").
                               append("***************************************************\n");
            }else{
                  retVal.append("******StackTraceElement == null********\n");
            }

            retVal.append("*******End StackTraceElement********\n");

            return retVal.toString();
      
         }

         public static String StackTraceElementListToString(List<StackTraceElement> steList){

            StringBuilder retVal = new StringBuilder("*******Begin StackTraceElementListToString********\n");
            
            if (steList != null && !steList.isEmpty()) {
                  for (StackTraceElement de : steList) {
                        
                        retVal.append("*******Begin StackTraceElement********\n"). 
                        append(MessageFormat.format("StackTraceElement Value To String: {0}\n",
                                    new Object[] {  StackTraceElementToString(de) })).
                        append("*******End StackTraceElement********\n");                        
                   
                  }
            }else{
                  retVal.append("List<StackTraceElement> == null\n");
            }

            retVal.append("*******End StackTraceElementListToString********\n");
            return retVal.toString();         
               
         }


         public static String OldThreadStatsToString(OldThreadStats ts){


            StringBuilder retVal = 
            new StringBuilder("*******Begin OldThreadStatsToString********\n");
      
            if(ts != null && !ts.equals(OldThreadStats.getDefaultInstance())){
               retVal.append(MessageFormat.format("Get Allocated Bytes: {0}\n", 
                        new Object[]{ts.getAllocatedBytes()})).
                      append(MessageFormat.format("Get Blocked Nanos: {0}\n", 
                        new Object[]{ts.getBlockedNanos()})).
                      append(MessageFormat.format("Get CPU Nanos: {0}\n", 
                        new Object[]{ts.getCpuNanos()})).                      
                      append(MessageFormat.format("Descriptor: {0}\n", 
                        new Object[]{CoreProtoToString.DescriptorToString(OldThreadStats.getDescriptor())})).  
                      append(MessageFormat.format("Serialized Size: {0}\n", 
                        new Object[]{ts.getSerializedSize()})).
                      append(MessageFormat.format("Get Waited Nanos: {0}\n", 
                        new Object[]{ts.getWaitedNanos()})).
                      append(MessageFormat.format("Has Allocated Bytes: {0}\n", 
                        new Object[]{ts.hasAllocatedBytes()})).
                      append(MessageFormat.format("Has CPU Nanos: {0}\n", 
                        new Object[]{ts.hasCpuNanos()})).   
                      append(MessageFormat.format("Has Blocked Nanos: {0}\n", 
                        new Object[]{ts.hasBlockedNanos()})).   
                      append(MessageFormat.format("Has Waited Nanos: {0}\n", 
                        new Object[]{ts.hasWaitedNanos()})).  
                      append(MessageFormat.format("isInitialized(): {0}\n", 
                        new Object[]{ts.isInitialized()}));
                      
      
            }else{
               retVal.append("OldThreadStats == null\n");
            }
      
            retVal.append("*******End OldThreadStatsToString********\n");
            return retVal.toString();
      
          }
//append(MessageFormat.format("getBlockedNanos(): {0}\n", 
//new Object[]{tsc.getBlockedNanos()})).

      
    public static String HeaderToString(Header tsc){

        StringBuilder retVal = 
                new StringBuilder("***********************************\n").
                           append("*******Begin HeaderToString********\n").
                           append("***********************************\n");

        if(tsc != null && !tsc.equals(org.glowroot.wire.api.model.TraceOuterClass.Trace.Header.getDefaultInstance())){
                retVal.append(MessageFormat.format("Serialized Size: {0}\n", 
                     new Object[]{tsc.getSerializedSize()})).
               append(MessageFormat.format("Async: {0}\n", 
                     new Object[]{tsc.getAsync()})).
               append(MessageFormat.format("getAsyncTimerList(): {0}\n", 
                     new Object[]{TimerListToString(tsc.getAsyncTimerList())})).
               append(MessageFormat.format("getAsyncTimerCount(): {0}\n", 
                     new Object[]{tsc.getAsyncTimerCount()})).
               append(MessageFormat.format("Descriptor: {0}\n", 
                     new Object[]{CoreProtoToString.DescriptorToString(Header.getDescriptor())})).
               append(MessageFormat.format("getAttributeCount(): {0}\n", 
                     new Object[]{tsc.getAttributeCount()})).
               append(MessageFormat.format("getAttributeList(): {0}\n", 
                     new Object[]{AttributeListToString(tsc.getAttributeList())})).
               append(MessageFormat.format("getAuxThreadProfileSampleCount(): {0}\n", 
                     new Object[]{tsc.getAuxThreadProfileSampleCount()})).
               append(MessageFormat.format("getAuxThreadProfileSampleLimitExceeded(): {0}\n", 
                     new Object[]{tsc.getAuxThreadProfileSampleLimitExceeded()})).
               append(MessageFormat.format("getAuxThreadRootTimer(): {0}\n", 
                     new Object[]{TimerToString(tsc.getAuxThreadRootTimer())})).
               append(MessageFormat.format("getAuxThreadStats(): {0}\n", 
                     new Object[]{ThreadStatsToString(tsc.getAuxThreadStats())})).
               append(MessageFormat.format("getCAptureTime(): {0}\n", 
                     new Object[]{tsc.getCaptureTime()})).
               append(MessageFormat.format("getCAptureTimePartialRollup(): {0}\n", 
                     new Object[]{tsc.getCaptureTimePartialRollup()})).
               append(MessageFormat.format("getDetailEntryCount(): {0}\n", 
                     new Object[]{tsc.getDetailEntryCount()})).
               append(MessageFormat.format("getDetailEntryList(): {0}\n", 
                     new Object[]{tsc.getDetailEntryList()})).
               append(MessageFormat.format("getDurationNanos(): {0}\n", 
                     new Object[]{tsc.getDurationNanos()})).
               append(MessageFormat.format("getEntryCount(): {0}\n", 
                     new Object[]{tsc.getEntryCount()})).
               append(MessageFormat.format("getEntryLimitExceeded(): {0}\n", 
                     new Object[]{tsc.getEntryLimitExceeded()})).
               append(MessageFormat.format("getError(): {0}\n", 
                     new Object[]{ErrorToString(tsc.getError())})).
               append(MessageFormat.format("getHeadline(): {0}\n", 
                     new Object[]{tsc.getHeadline()})).
               append(MessageFormat.format("getLocationStackTraceElementCount(): {0}\n", 
                     new Object[]{tsc.getLocationStackTraceElementCount()})).
               append(MessageFormat.format("getLocationStackTraceElementList(): {0}\n", 
                     new Object[]{StackTraceElementListToString(
                           tsc.getLocationStackTraceElementList())})).
               append(MessageFormat.format("getMainThreadProfileSampleCount(): {0}\n", 
                           new Object[]{tsc.getMainThreadProfileSampleCount()})).
               append(MessageFormat.format("getMainThreadProfileSampleLimitExceeded(): {0}\n", 
                           new Object[]{tsc.getMainThreadProfileSampleLimitExceeded()})).
               append(MessageFormat.format("getMainThreadRootTimer(): {0}\n", 
                           new Object[]{TimerToString(tsc.getMainThreadRootTimer())})).
               append(MessageFormat.format("getMainThreadStats(): {0}\n", 
                           new Object[]{ThreadStatsToString(tsc.getMainThreadStats())})).
               append(MessageFormat.format("getOldAuxThreadStats(): {0}\n", 
                           new Object[]{OldThreadStatsToString(tsc.getOldAuxThreadStats())})).
               append(MessageFormat.format("getOldMainThreadStats(): {0}\n", 
                           new Object[]{OldThreadStatsToString(tsc.getOldMainThreadStats())})).
               append(MessageFormat.format("getPartial(): {0}\n", 
                           new Object[]{tsc.getPartial()})).
               append(MessageFormat.format("getQueryCount(): {0}\n", 
                           new Object[]{tsc.getQueryCount()})).
               append(MessageFormat.format("getQueryLimitExceeded(): {0}\n", 
                           new Object[]{tsc.getQueryLimitExceeded()})).
               append(MessageFormat.format("getQueryCount(): {0}\n", 
                           new Object[]{tsc.getQueryCount()})).
               append(MessageFormat.format("getSlow(): {0}\n", 
                           new Object[]{tsc.getSlow()})).
               append(MessageFormat.format("getStartTime(): {0}\n", 
                           new Object[]{tsc.getStartTime()})).
               append(MessageFormat.format("getTransactionName(): {0}\n", 
                           new Object[]{tsc.getTransactionName()})).
               append(MessageFormat.format("getTransactionType(): {0}\n", 
                           new Object[]{tsc.getTransactionName()})).
               append(MessageFormat.format("getUser(): {0}\n", 
                           new Object[]{tsc.getUser()})).
               append(MessageFormat.format("getTransactionType(): {0}\n", 
                           new Object[]{tsc.getTransactionName()})).
               append(MessageFormat.format("hasAuxThreadRootTimer(): {0}\n", 
                           new Object[]{tsc.hasAuxThreadRootTimer()})).
               append(MessageFormat.format("hasAuxThreadStats(): {0}\n", 
                           new Object[]{tsc.hasAuxThreadStats()})).
               append(MessageFormat.format("hasError(): {0}\n", 
                           new Object[]{tsc.hasError()})).
               append(MessageFormat.format("hasMainThreadRootTimer(): {0}\n", 
                           new Object[]{tsc.hasAuxThreadRootTimer()})).
               append(MessageFormat.format("hasMainThreadStats(): {0}\n", 
                           new Object[]{tsc.hasAuxThreadStats()})).
               append(MessageFormat.format("hasOldAuxThreadStats(): {0}\n", 
                           new Object[]{tsc.hasOldAuxThreadStats()})).
               append(MessageFormat.format("hasOldMainThreadStats(): {0}\n", 
                           new Object[]{tsc.hasOldMainThreadStats()})).
               append(MessageFormat.format("isInitialized(): {0}\n", 
                           new Object[]{tsc.isInitialized()}));              
               
        }else{
            retVal.append("Header == null\n");
        }

        retVal.append("***********************************\n").
               append("*******End HeaderToString********\n").
               append("***********************************\n");
        return retVal.toString();
        
    }

    public static String TraceStreamHeaderToString(TraceStreamHeader tsc){

        StringBuilder retVal = 
                new StringBuilder("*******Begin TraceStreamHeader********\n");

        if(tsc != null && !tsc.equals(TraceStreamHeader.getDefaultInstance())){
                retVal.append(MessageFormat.format("Serialized Size: {0}\n", 
                     new Object[]{tsc.getSerializedSize()})).
               append(MessageFormat.format("PostV09: {0}\n", 
                     new Object[]{tsc.getPostV09()})).
               append(MessageFormat.format("Descriptor: {0}\n", 
                     new Object[]{CoreProtoToString.DescriptorToString(TraceStreamHeader.getDescriptor())})).
               append(MessageFormat.format("getAgentId(): {0}\n", 
                     new Object[]{tsc.getAgentId()})).
               append(MessageFormat.format("getTraceId(): {0}\n", 
                     new Object[]{tsc.getTraceId()})).
               append(MessageFormat.format("isInitialized(): {0}\n", 
                     new Object[]{tsc.isInitialized()})).
               append(MessageFormat.format("getUpdate(): {0}\n", 
                     new Object[]{tsc.getUpdate()}));
        }else{
            retVal.append("TraceStreamHeader == null\n");
        }

        retVal.append("*******End TraceStreamHeader********\n");
        return retVal.toString();
        
    }

    public static String TraceStreamCountsToString(TraceStreamCounts tsc){

        StringBuilder retVal = 
                new StringBuilder("*******Begin TraceStreamCounts********\n");

        if(tsc != null && !tsc.equals(TraceStreamCounts.getDefaultInstance())){
                retVal.append(MessageFormat.format("Serialized Size: {0}\n", 
                     new Object[]{tsc.getSerializedSize()})).
               append(MessageFormat.format("SharedQueryTextCount: {0}\n", 
                     new Object[]{tsc.getSharedQueryTextCount()})).
               append(MessageFormat.format("Descriptor: {0}\n", 
                     new Object[]{CoreProtoToString.DescriptorToString(TraceStreamCounts.getDescriptor())})).
               append(MessageFormat.format("getEntryCount(): {0}\n", 
                     new Object[]{tsc.getEntryCount()})).
               append(MessageFormat.format("isInitialized(): {0}\n", 
                     new Object[]{tsc.isInitialized()}));
        }else{
            retVal.append("TraceStreamCounts == null\n");
        }

        retVal.append("*******TraceStreamCounts********\n");
        return retVal.toString();
        
    }

    public static String ProtocolStringListToString(String TYPE, ProtocolStringList psl){

        StringBuilder retVal = 
        new StringBuilder("*******Begin ProtocolStringList********\n");

        if(psl != null && !psl.isEmpty()){
            
            for(String protocol : psl){
                     
                  if(TYPE != null){
                    retVal.append(MessageFormat.format("List value\n{0}: {1}\n", 
                       new Object[]{TYPE, protocol}));
                  }else{
                        retVal.append(MessageFormat.format("List value\n", 
                        new Object[]{protocol}));  
                  }

            }            
        
        }else{
            retVal.append("ProtocolStringList == null or empty\n");
        }

        retVal.append("*******End ProtocolStringListToString********\n");

        return retVal.toString();

    }

    public static String LeafThreadStateToString(LeafThreadState lts){

        StringBuilder retVal = 
            new StringBuilder("*******Begin LeafThreadStateToString********\n");

        if(lts != null){
            int number = lts.getNumber();
            EnumValueDescriptor evd = lts.getValueDescriptor();
            String name = lts.name();
            int od = lts.ordinal();

              
              retVal.append(MessageFormat.format("Name: {0}\n", 
                     new Object[]{name})).
              append(MessageFormat.format("getNumber(): {0}\n", 
                    new Object[]{number})).
              append(MessageFormat.format("EnumValueDescriptor: {0}\n", 
                     new Object[]{evd.toString()})).              
              append(MessageFormat.format("od: {0}\n", 
                     new Object[]{od})).

               toString();
           }else{
            retVal.append("LeafThreadState == null or empty\n");
           }

         retVal.append("*******End LeafThreadStateToString********\n");

          return retVal.toString();

        }

    public static String ProfileNodeListToString(List<ProfileNode> pnl){

        StringBuilder retVal = new StringBuilder("*****Begin ProfileNodeListToString*****\n");

        if(pnl != null && !pnl.isEmpty()){

            for(ProfileNode pn : pnl){
                
                    retVal.append(MessageFormat.format("Classname index: {0}\n", 
                            new Object[]{pn.getClassNameIndex()})).
                    append(MessageFormat.format("Filename index: {0}\n", 
                            new Object[]{pn.getFileNameIndex()})).
                    append(MessageFormat.format("Method name index: {0}\n", 
                            new Object[]{pn.getMethodNameIndex()})).
                    append(MessageFormat.format("Package name index: {0}\n", 
                            new Object[]{pn.getPackageNameIndex()})).
                    append(MessageFormat.format("Descriptor: {0}\n", 
                            new Object[]{CoreProtoToString.DescriptorToString(ProfileNode.getDescriptor())})).

                    append(MessageFormat.format("Leaf thread state: {0}\n", 
                            new Object[]{LeafThreadStateToString(pn.getLeafThreadState())})).
                    append(MessageFormat.format("Leaf thread state value: {0}\n", 
                            new Object[]{pn.getLeafThreadStateValue()})).

                    append(MessageFormat.format("Get depth: {0}\n", 
                            new Object[]{pn.getDepth()})).
                    append(MessageFormat.format("Get line number: {0}\n", 
                            new Object[]{pn.getLineNumber()})).
                    append(MessageFormat.format("Get sample count: {0}\n", 
                            new Object[]{pn.getSampleCount()})).
                    append(MessageFormat.format("Get serialized size: {0}\n", 
                            new Object[]{pn.getSerializedSize()}));
                    //might need unknown fields, run first                                  
                    
            
            }
    }else{
      retVal.append("ProfileNode == null or empty\n"); 
    }

    retVal.append("*****End ProfileNodeListToString*****\n");

     return retVal.toString();

    }

    

    public static String ProfileToString(Profile prof){

      StringBuilder retVal = new StringBuilder("*****Begin ProfileToString*****\n");  
    
        if(prof != null && !prof.equals(Profile.getDefaultInstance())){
    
           retVal = new StringBuilder().
              append(MessageFormat.format("getDescriptor(): {0}\n", 
                    new Object[]{(CoreProtoToString.DescriptorToString(Profile.getDescriptor()))})).
              append(MessageFormat.format("getClassNameCount(): {0}\n", 
                     new Object[]{prof.getClassNameCount()})).
              append(MessageFormat.format("getClassNameList(): {0}\n", 
                     new Object[]{ProtocolStringListToString("className",prof.getClassNameList())})).
              append(MessageFormat.format("getFileNameList(): {0}\n", 
                    new Object[]{ProtocolStringListToString("fileNameList", prof.getFileNameList())})).
              append(MessageFormat.format("getFileNameCount(): {0}\n", 
                    new Object[]{prof.getFileNameCount()})).
              append(MessageFormat.format("getMethodNameList(): {0}\n", 
                    new Object[]{ProtocolStringListToString("methodNameList", prof.getMethodNameList())})).
              append(MessageFormat.format("getMethodNameCount(): {0}\n", 
                    new Object[]{prof.getMethodNameCount()})).
              append(MessageFormat.format("getPackageNameCount(): {0}\n", 
                    new Object[]{prof.getPackageNameCount()})).
              append(MessageFormat.format("getPackageNameList(): {0}\n", 
                    new Object[]{ProtocolStringListToString("getPackageNameList()",prof.getPackageNameList())})).
              append(MessageFormat.format("getNodeCount(): {0}\n", 
                    new Object[]{prof.getNodeCount()})).
              append(MessageFormat.format("getNodeList(): {0}\n", 
                     new Object[]{ProfileNodeListToString(prof.getNodeList())})).
              append(MessageFormat.format("Get serialized size: {0}\n", 
                     new Object[]{prof.getSerializedSize()}));

        }else{
            retVal.append("Profile == null or empty\n"); 
          }
      

        retVal.append("*****End ProfileToString*****\n");  
        return retVal.toString();
        
    }

    public static String QueryListToString(List<Query> ql){

        StringBuilder retVal = new StringBuilder("*******Begin List<Query>********\n");

        if(ql != null && !ql.isEmpty()){
            for(Query q : ql){
                retVal.
                  append("*******Begin Query********\n").
                  append(MessageFormat.format("getDescriptor(): {0}\n", 
                     new Object[]{CoreProtoToString.DescriptorToString(Query.getDescriptor())})).
                  append(MessageFormat.format("getActive(): {0}\n", 
                     new Object[]{q.getActive()})).
                  append(MessageFormat.format("getExecutionCount(): {0}\n", 
                     new Object[]{q.getExecutionCount()})).
                  append(MessageFormat.format("getSerializedSize(): {0}\n", 
                     new Object[]{q.getSerializedSize()})).
                  append(MessageFormat.format("getSharedQueryTextIndex(): {0}\n", 
                     new Object[]{q.getSharedQueryTextIndex()})).
                  append(MessageFormat.format("getTotalDurationNanos(): {0}\n", 
                     new Object[]{q.getTotalDurationNanos()})).
                  append(MessageFormat.format("getTotalRows(): {0}\n", 
                     new Object[]{q.getTotalRows().getValue()})).
                  append(MessageFormat.format("getType(): {0}\n", 
                     new Object[]{q.getType()})).
                  append(MessageFormat.format("hasTotalRows(): {0}\n", 
                     new Object[]{q.hasTotalRows()})).
                  append(MessageFormat.format("isInitialized(): {0}\n", 
                     new Object[]{q.isInitialized()})).
                  append("*******End Query********\n");                  
            }
            
        }else{
            retVal.append("QueryList is == null\n");
        }
        retVal.append("*******End List<Query>********\n");

        return retVal.toString();

    }

    public static String QueriesToString(Queries queries){

        StringBuilder retVal = new StringBuilder("*******Begin Queries********\n");       
    
        if(queries != null && !queries.equals(Queries.getDefaultInstance())){
    
           retVal = new StringBuilder().
                       append(MessageFormat.format("getDescriptor(): {0}\n", 
                          new Object[]{CoreProtoToString.DescriptorToString(Queries.getDescriptor())})).
                       append(MessageFormat.format("getQueryCount(): {0}\n", 
                          new Object[]{queries.getQueryCount()})).
                       append(MessageFormat.format("getQueryList(): {0}\n", 
                          new Object[]{QueryListToString(queries.getQueryList())})).
                       append(MessageFormat.format("getSerializedSize(): {0}\n", 
                          new Object[]{queries.getSerializedSize()})).
                       append(MessageFormat.format("isInitialized(): {0}\n", 
                          new Object[]{queries.isInitialized()}));

        }else{
            retVal.append("Queries == null\n");
        }
        retVal.append("*******End Queries********\n");
        return retVal.toString();

    }
}