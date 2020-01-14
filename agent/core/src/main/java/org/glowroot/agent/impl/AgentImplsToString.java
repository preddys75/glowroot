package org.glowroot.agent.impl;

import java.text.MessageFormat;
import java.util.List;

import org.glowroot.agent.impl.Transaction;
import org.glowroot.agent.model.GrModelObjToString;
import org.glowroot.agent.model.ThreadProfile;
import org.glowroot.common.util.GrObjToString;
import org.glowroot.common.util.GrProtoObjToString;
import org.glowroot.agent.impl.ThreadContextImpl;

import org.glowroot.agent.plugin.api.ThreadContext.ServletRequestInfo;



public class AgentImplsToString{


    public static String TransactionListToString(List<Transaction> tciList){

        String retVal = "Transaction List == null";
        retVal += "\n********Begin logging Transaction List********\n";
        
        if(tciList != null && !tciList.isEmpty()){
  
           StringBuilder strVal = new StringBuilder().
           append("********Begin logging Transaction List********\n");
           
           for(Transaction tci : tciList){       
              strVal.append(TransactionToString(tci));      
           }
           retVal = strVal.append("********Done logging Transaction List********\n").toString();
       }else{

          retVal += "\n********Done logging Transaction List********\n";
       }
  
       return retVal;
           
     }



    public static String TransactionToString(Transaction transaction){

        String retVal = "Transaction == null";

        if(transaction != null){
            StringBuilder strVal = new StringBuilder().
            append("********Begin logging Transaction********\n").
            append(MessageFormat.format("transaction.getActiveAuxThreadContexts(): {0}\n", 
                new Object[]{ThreadContextImplListToString(transaction.getActiveAuxThreadContexts())}));
           
            
            if(transaction.getAttributes() != null){
                strVal = strVal.append("********Attributes********\n");
                for(String attrName : transaction.getAttributes().keySet()){
                    strVal = strVal.append(MessageFormat.format("attrKey: {0}, attrVal: {1}:\n", 
                         new Object[]{attrName, transaction.getAttributes().get(attrName)}));
                }
            }
            ThreadProfile tp = transaction.getAuxThreadProfile();
            
            strVal = strVal.append(MessageFormat.format("transaction.getAuxThreadProfile(): {0}\n", 
               new Object[]{(tp != null) ? tp.ToString() : null}));

            retVal = strVal.append("********Done logging Transaction********\n").toString();
        }

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

   public static String JAVAStackTraceElementListToString(List<java.lang.StackTraceElement> steList){

        String retVal = "StackTraceElement List == null";
        
        if(steList != null && !steList.isEmpty()){
  
           StringBuilder strVal = new StringBuilder().
           append("********Begin logging StackTraceElement List********\n");
           
           for(StackTraceElement ste : steList){       
              strVal.append(MessageFormat.format("****java.lang.StackTraceElement.toString():***\n{0}\n", 
              new Object[]{ste.toString()}));     
           }
           retVal = strVal.append("********Done logging StackTraceElement List********\n").toString();
       }
  
       return retVal;
           
     }

     public static String TraceEntryImplToStringHelper(TraceEntryImpl trImpl){

        String retVal = "TraceEntry Impl == null";

        if(trImpl != null){
            StringBuilder strVal = new StringBuilder().
            append("********Begin logging TraceEntryImpl********\n").
            append(MessageFormat.format("****TraceEntryImpl.getErrorMessage(): {0}***\n", 
                               GrModelObjToString.ErrorMessageToString(trImpl.getErrorMessage()))).
            append(MessageFormat.format("****TraceEntryImpl.getLocationStackTrace(): {0}***\n", 
                                JAVAStackTraceElementListToString(trImpl.getLocationStackTrace()))).
            append(MessageFormat.format("****TraceEntryImpl.getMessageSupplier(): {0}***\n", 
                                 (trImpl.getMessageSupplier() != null) ? trImpl.getMessageSupplier() : null)).
            append(MessageFormat.format("****TraceEntryImpl.getStartTick(): {0}***\n", 
            (trImpl.getParentTraceEntry() != null) ? trImpl.getStartTick() : null));
            //*****************threadcontext*************************************
            //*******************************************************************/
            ThreadContextImpl tci = trImpl.getThreadContext();
            String tcStrVal = "ThreadContextImpl == null";
            if(tci  != null){
                tcStrVal = ThreadContextImplToStringForTrace(tci);
            } 
            strVal.append(MessageFormat.format("****TraceEntryImpl.getThreadContext(): {0}***\n", tcStrVal));
            
            strVal.append(MessageFormat.format("****ThreadContextImpl.getStartTick(): {0}***\n", 
                  (trImpl.getThreadContext() != null) ? trImpl.getStartTick() : null));
             //*****************threadcontext*************************************
            //*******************************************************************/
             
            strVal.append(MessageFormat.format("transaction {0}\n", 
              new Object[]{2})).
            append("********Done logging TraceEntryImpl********\n");
            

            retVal = strVal.toString();
          }
  
          return retVal;
        }



    public static String TraceEntryImplToString(TraceEntryImpl trImpl){

        String retVal = "TraceEntry Impl == null";

        if(trImpl != null){
            retVal = new StringBuilder().
            append("********Begin logging TraceEntryImpl********\n").
            append(MessageFormat.format("****TraceEntryImpl.getNextTraceEntry(): {0}***\n", 
                 (trImpl.getNextTraceEntry() != null) ? TraceEntryImplToStringHelper(trImpl.getNextTraceEntry()) : null)).
            append(MessageFormat.format("****TraceEntryImpl.getParentTracetEntry(): {0}***\n", 
            (trImpl.getParentTraceEntry() != null) ? TraceEntryImplToStringHelper(trImpl.getParentTraceEntry()) : null)).
     



            append(MessageFormat.format("transaction {0}\n", 
              new Object[]{2})).
            append("********Done logging TraceEntryImpl********\n").
            

              toString();
          }
  
          return retVal;
        }

    public static String ThreadContextImplToStringForTrace(ThreadContextImpl tci){

        String retVal = "ThreadContextImplToString == null";
        
        if(tci != null){

            retVal = new StringBuilder(). 
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

            toString();
        }

        return retVal;
    }

    public static String ServletRequestInfoToString(ServletRequestInfo sri){
        
        String retVal = "ServletRequestInfo == null";
        
        if(sri != null){

            retVal = new StringBuilder(). 
            append(MessageFormat.format("sri.getMethod(): {0}\n", 
                new Object[]{sri.getMethod()})).

            append(MessageFormat.format("sri.getContextPath(): {0}\n", 
               new Object[]{sri.getContextPath()})).

            append(MessageFormat.format("sri.getServletPath(): {0}\n", 
               new Object[]{sri.getServletPath()})).

            append(MessageFormat.format("sri.getPathInfo(): {0}\n", 
               new Object[]{sri.getPathInfo()})).

            append(MessageFormat.format("sri.getUri(): {0}\n", 
               new Object[]{sri.getUri()})).toString();
        }

        return retVal;
    }

    public static String ThreadContextImplListToString(List<ThreadContextImpl> tciList){

        String retVal = "ThreadContextImpl List == null";
        
        if(tciList != null){
  
           StringBuilder strVal = new StringBuilder().
           append("********Begin logging ThreadContextImpl List********\n");
           
           for(ThreadContextImpl tci : tciList){       
              strVal.append(tci.toString());      
           }
           retVal = strVal.append("********Done logging ThreadContextImpl List********\n").toString();
       }
  
       return retVal;
           
     }

    public static String ThreadContextImplToString(ThreadContextImpl tci){

        String retVal = "ThreadContextImpl == null";
        
        if(tci != null){
            retVal = new StringBuilder(). 
            append("********Begin logging  ThreadContextImpl********\n").
            append(MessageFormat.format("tci.getCpuNanos(): {0}\n", 
                new Object[]{tci.getCpuNanos()})).
            append(MessageFormat.format("tci.getCaptureThreadStats(): {0}\n", 
                new Object[]{tci.getCaptureThreadStats()})).
            append(MessageFormat.format("tci.getCurrentNestingGroupId(): {0}\n", 
                new Object[]{tci.getCurrentNestingGroupId()})).
            append(MessageFormat.format("tci.getCurrentSuppressionKeyId(): {0}\n", 
                new Object[]{tci.getCurrentSuppressionKeyId()})).
            append(MessageFormat.format("tci.getCurrentTimer(): {0}\n", 
                new Object[]{TimerImplToString(tci.getCurrentTimer())})).
            append(MessageFormat.format("tci.getParentThreadContextPriorEntry(): {0}\n", 
                new Object[]{tci.getParentThreadContextPriorEntry()})).
            append(MessageFormat.format("tci.getRootEntry(): {0}\n", 
                new Object[]{tci.getRootEntry()})).
            append(MessageFormat.format("tci.getRootTimer(): {0}\n", 
                new Object[]{TimerImplToString(tci.getRootTimer())})).
            append(MessageFormat.format("tci.getTailEntry(): {0}\n", 
                new Object[]{tci.getTailEntry()})).
            append(MessageFormat.format("tci.getThreadId(): {0}\n", 
                new Object[]{tci.getThreadId()})).
            append(MessageFormat.format("tci.getThreadStats(): {0}\n", 
                new Object[]{(tci.getThreadStats() != null) ? tci.getThreadStats().ToString() : null})).
            append(MessageFormat.format("tci.getTransaction(): {0}\n", 
                new Object[]{TransactionToString(tci.getTransaction())})).
            append("********End logging  ThreadContextImpl********\n").
                
                
            toString();
         }

        return retVal;
    }

}
