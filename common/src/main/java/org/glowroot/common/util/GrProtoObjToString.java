package org.glowroot.common.util;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.text.MessageFormat;
import java.util.List;
import java.util.Map;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.TextFormat;
import com.google.protobuf.GeneratedMessageV3;
import org.glowroot.wire.api.model.Proto.StackTraceElement;
import org.glowroot.wire.api.model.Proto.Throwable;
import org.glowroot.common.util.CoreProtoToString;

public class GrProtoObjToString {

    

  
   public static String StackTraceElementToString(StackTraceElement ste){

      String retVal = "StackTraceElement == null";

      if(ste != null &&  !ste.equals(org.glowroot.wire.api.model.Proto.StackTraceElement.getDefaultInstance())){
                  retVal = new StringBuilder().
                         append("***************************************************\n").
                         append("********Begin logging StackTraceElement************\n").
                         append(MessageFormat.format("****StackTraceElement.getClassName(): {0}***\n", ste.getClassName())).
                         append(MessageFormat.format("****StackTraceElement.getFileName(): {0}***\n", ste.getFileName())).
                         append(MessageFormat.format("****StackTraceElement.getLineNumber(): {0}***\n", ste.getLineNumber())).
                         append(MessageFormat.format("****StackTraceElement.getMethodName(): {0}***\n", ste.getMethodName())).
                         append(MessageFormat.format("****StackTraceElement.isInitialized(): {0}***\n", ste.isInitialized())).
                         append(MessageFormat.format("****CoreProtoToString.GeneratedMessageV3ToString(stacktracelem)**************** \n{0}", 
                         CoreProtoToString.GeneratedMessageV3ToString(ste))).
                         append("********Done logging StackTraceElement********\n").
                         append("***************************************************\n").toString();
      }
      return retVal;

   }

   public static String StackTraceElementListToString(List<StackTraceElement> steList){

      String retVal = "StackTraceElement List == null";
      
      if(steList != null && !steList.isEmpty()){

         StringBuilder strVal = new StringBuilder().
         append("********Begin logging StackTraceElement List********\n");
         
         for(StackTraceElement ste : steList) {       
            strVal.append(StackTraceElementToString(ste));      
         }
         retVal = strVal.append("********Done logging StackTraceElement List********\n").toString();
     }

     return retVal;
         
   }

   public static String SuppressedThrowableListToString(List<Throwable> list){

      StringBuilder retVal = new StringBuilder();
      
      retVal.append("***************************************************\n"). 
      append("********Begin logging SuppressedThrowableList********\n");
      if(list != null && !list.isEmpty()){
         for(Throwable throwable : list){

            retVal.append("***************************************************\n").         
            append("********Begin logging SuppressedThrowable********\n").
            append(MessageFormat.format("****throwable.getClassMame(): {0}***\n", throwable.getClassName())).
            append(MessageFormat.format("****throwable.getMessage(): {0}***\n",        throwable.getMessage())).
            append(MessageFormat.format("****throwable.getCause(): {0}***\n", ThrowableToString(throwable.getCause()))).         
            append(MessageFormat.format("****throwable.getFramesInCommonWithEnclosing(): {0}***\n", 
            throwable.getFramesInCommonWithEnclosing())).
            append(MessageFormat.format("****StackTraceElement.getStackTraceElementCount(): {0}***\n", throwable.getStackTraceElementCount())).
            append(MessageFormat.format("****throwable.getStackTraceElementList()**************** \n{0}", 
                        StackTraceElementListToString(throwable.getStackTraceElementList()))).
            append(MessageFormat.format("****throwable.getSuppressedCount()**************** \n{0}", 
            throwable.getSuppressedCount())).
            append(MessageFormat.format("****throwable.getSuppressedList()**************** \n{0}", 
            throwable.getSuppressedList())).
            append("***************************************************\n"). 
            append("********Done logging Throwable********\n");         

      }
     
   }else retVal.append("**********EMPTY*****************************\n");

   retVal.append("***************************************************\n"). 
   append("********Done logging SuppressedThrowableList********\n"); 

   return retVal.toString();
         

}

public static String ThrowableListToString(List<Throwable> steList){

   String retVal = "Throwable List == null";
   
   if(steList != null && !steList.isEmpty()){

      StringBuilder strVal = new StringBuilder().
      append("********Begin logging Throwable List********\n");
      
      for(Throwable ste : steList) {       
         strVal.append(ThrowableToString(ste));      
      }
      retVal = strVal.append("********Done logging Throwable List********\n").toString();
  }

  return retVal;
      
}

   public static String ThrowableToString(Throwable throwable){

      String retVal = "throwable == null";
      

      if(throwable != null && !throwable.equals(org.glowroot.wire.api.model.Proto.Throwable.getDefaultInstance())){

        retVal = new StringBuilder().
        append("***************************************************\n").
        append("********Begin logging Throwable********\n").
        append(MessageFormat.format("****throwable.getClassName(): {0}***\n", throwable.getClassName())).
        append(MessageFormat.format("****throwable.getMessage(): {0}***\n",  throwable.getMessage())).

        append(MessageFormat.format("****throwable.getCause(): {0}***\n", (throwable.hasCause()) ? ThrowableToString(throwable.getCause()) : "no cause")).         
        append(MessageFormat.format("****throwable.getFramesInCommonWithEnclosing(): {0}***\n", 
        throwable.getFramesInCommonWithEnclosing())).
        append(MessageFormat.format("****StackTraceElement.getStackTraceElementCount(): {0}***\n", throwable.getStackTraceElementCount())).
        append(MessageFormat.format("****throwable.getStackTraceElementList()**************** \n{0}", 
                      StackTraceElementListToString(throwable.getStackTraceElementList()))).
        append(MessageFormat.format("****throwable.getSuppressedCount()**************** \n{0}", 
        throwable.getSuppressedCount())).
        append(MessageFormat.format("****throwable.getSuppressedList()**************** \n{0}", 
       ThrowableListToString(throwable.getSuppressedList()))).
        append(MessageFormat.format("****CoreProtoToString.GeneratedMessageV3ToString(throwable)**************** \n{0}", 
        CoreProtoToString.GeneratedMessageV3ToString(throwable))).
        append("***************************************************\n").
        append("********Done logging Throwable********\n").toString();
          
      }

      return retVal;

}   

}
