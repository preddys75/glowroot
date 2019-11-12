package org.glowroot.agent.impl;

import java.text.MessageFormat;

import org.glowroot.agent.impl.Transaction;
import org.glowroot.agent.model.GrModelObjToString;
import org.glowroot.agent.impl.ThreadContextImpl;


public class AgentImplsToString{

    public static String TransactionToString(Transaction transaction){

        String retVal = new StringBuilder(). 
        append(MessageFormat.format("transaction {0}\n", 
              new Object[]{2})).toString();

        return retVal;        
    }

    public static String TimerImplToString(TimerImpl timerImpl){

        String retVal = "TimerImpl == null";


        if(timerImpl != null){
            retVal = new StringBuilder(). 
            append("********Begin logging TimerImpl********\n").
            append(MessageFormat.format("timerImpl.getCount(): {0}\n", 
                new Object[]{timerImpl.getCount()})).
            append(MessageFormat.format("timerImpl.getName(): {0}\n", 
                new Object[]{timerImpl.getName()})).
            append(MessageFormat.format("timerImpl.getSnapshot(): {0}\n", 
            new Object[]{timerImpl.getSnapshot()})).
            append(MessageFormat.format("timerImpl.getTotalNanos(): {0}\n", 
            new Object[]{timerImpl.getTotalNanos()})).
            append(MessageFormat.format("timerImpl.isExtended(): {0}\n", 
            new Object[]{timerImpl.isExtended()})).
            append("********Done logging TimerImpl********\n").
            

            toString();
        }

        return retVal;



    }

    public static String TraceEntryImplToString(TraceEntryImpl trImpl){

        String retVal = "TraceEntry Impl == null";

        if(trImpl != null){
            retVal = new StringBuilder().
            append("********Begin logging TraceEntryImpl********\n").
            append(MessageFormat.format("****TraceEntryImpl.getErrorMessage(): {0}***\n", trImpl.getErrorMessage())). 
            append(MessageFormat.format("transaction {0}\n", 
              new Object[]{2})).
            append("********Done logging TraceEntryImpl********\n").
            

              toString();
          }
  
          return retVal;
        }

    public static String ThreadContextImplToString(ThreadContextImpl tci){

        String retVal = new StringBuilder(). 
        append(MessageFormat.format("tci.getCpuNanos(): {0}\n", 
              new Object[]{tci.getCpuNanos()})).
        append(MessageFormat.format("tci.getCaptureThreadStats(): {0}\n", 
              new Object[]{tci.getCaptureThreadStats()})).
        append(MessageFormat.format("tci.getCurrentNestingGroupId(): {0}\n", 
        new Object[]{tci.getCurrentNestingGroupId()})).
        append(MessageFormat.format("tci.getCurrentSuppressionKeyId(): {0}\n", 
        new Object[]{tci.getCurrentSuppressionKeyId()})).
        append(MessageFormat.format("tci.getCurrentSuppressionKeyId(): {0}\n", 
        new Object[]{tci.getCurrentSuppressionKeyId()})).
        append(MessageFormat.format("tci.getCurrentTimer(): {0}\n", 
        new Object[]{TimerImplToString(tci.getCurrentTimer())})).
        append(MessageFormat.format("tci.getParentThreadContextDisplay(): {0}\n", 
        new Object[]{tci.getParentThreadContextDisplay()})).

        toString();

        return retVal;
    }

}