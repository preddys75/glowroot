package org.glowroot.agent.model;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.text.MessageFormat;
import java.util.List;
import java.util.Map;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.TextFormat;
import com.google.protobuf.GeneratedMessageV3;
import org.glowroot.agent.model.QueryEntryBase;
import org.glowroot.common.util.GrProtoObjToString;



public class GrModelObjToString {

    
   public static String QueryDataToString(QueryData qd){

      String retVal = "QueryData == null";

      if(qd != null){
            retVal = new StringBuilder().
            append("***************************************************\n").
            append("********Begin logging QueryData************\n").
            append(MessageFormat.format("****QueryData.getQueryText(): {0}***\n", qd.getQueryText())).
            append("********Done logging QueryData********\n").
            append("***************************************************\n").toString();
      }
      return retVal;

   }
  
   public static String QueryEntryBaseToString(QueryEntryBase qeb){

      String retVal = "QueryEntryBase == null";

      if(qeb != null){
         retVal = new StringBuilder().
         append("***************************************************\n").
         append("********Begin logging QueryEntryBase************\n").
         append(MessageFormat.format("****QueryEntryBase.getQueryData(): {0}***\n", 
               QueryDataToString(qeb.getQueryData()))).
         append(MessageFormat.format("****QueryEntryBase.getQueryText(): {0}***\n", 
         qeb.getQueryText())).
         append(MessageFormat.format("****QueryEntryBase.getRowCount(): {0}***\n", qeb.getRowCount())).
         append("********Done logging QueryEntryBase********\n").
         append("***************************************************\n").toString();
      }
      return retVal;

   }  

   public static String ErrorMessageToString(ErrorMessage errorMesg){

      String retVal = "ErrorMessage == null";

      if(errorMesg != null){
            retVal = new StringBuilder().
            append("***************************************************\n").
            append("********Begin logging ErrorMessage************\n").
            append(MessageFormat.format("****ErrorMessage.getMessage(): {0}***\n", errorMesg.message())).
            append(MessageFormat.format("****ErrorMessage.throwable: {0}***\n", GrProtoObjToString.ThrowableToString(errorMesg.throwable()))).
            append("********Done logging ErrorMessage********\n").
            append("***************************************************\n").toString();
      }
    
      return retVal;

   }

  



}
