package org.glowroot.common.util;

import java.text.MessageFormat;
import java.util.List;
import java.util.Map;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.TextFormat;
import com.google.protobuf.GeneratedMessageV3;

public class CoreProtoToString {

    

   public static String DescriptorToString(Descriptor descriptor){

      String retVal = "Descriptor == null";

      if(descriptor != null){
         retVal = new StringBuilder().
           append(MessageFormat.format("****Descriptor fullName: {0}***\n", descriptor.getFullName())).                                         
           append(MessageFormat.format("****Descriptor shortName: {0}***\n", descriptor.getName())).toString();
      }

      return retVal;

   }

   public static String GeneratedMessageV3ToString(GeneratedMessageV3 gmV3){

      if(gmV3 == null){
         return "GeneratedMessageV3 == null";
      }
      
      Descriptor descriptor = gmV3.getDescriptorForType();
      String unQualifiedFieldName = descriptor.getName();
      FieldDescriptor fd = descriptor.findFieldByName(unQualifiedFieldName);

         
      StringBuilder retVal = new StringBuilder().
                  append(MessageFormat.format("****Field descriptor: {0}***\n", DescriptorToString(descriptor))).                                         
                  append(MessageFormat.format("****Is initialized: {0}***\n", gmV3.isInitialized())).
                  append(MessageFormat.format("****Initialization Error String: {0}***\n", gmV3.getInitializationErrorString())).
                  append("**********************************************************\n"). 
                  append("**********Begin findInitializationErrors()**********************\n");
                  for(String error : gmV3.findInitializationErrors()){
                     retVal.append(MessageFormat.format("*****Error: {0}***\n", error)); 
                  }
           retVal.append("**********************************************************\n"). 
                  append("**********End findInitializationErrors()**********************\n").
                  append("**********************************************************\n").
                  append(MessageFormat.format("****FieldDescriptor info: {0}***\n", 
                                             (fd != null) ? TextFormat.printFieldToString(fd, gmV3) : "Field Descriptor == null")).
                  append("&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&\n").
                  append("&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&\n").
                  append("&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&\n").
                  append("***Begin TextFormat.printToString(gmV3)***\n").
                  append(TextFormat.printToString(gmV3)).
                  append("\n***End TextFormat.printToString(gmV3)****\n").
                  append("&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&\n").
                  append("&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&\n").
                  append("&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&\n").
                  append("***Begin TextFormat.printToString(UnknownFieldSet)****\n").
                  append((gmV3.getUnknownFields() != null) ? TextFormat.printToString(gmV3.getUnknownFields()) : "Unknown fields are null").
                  append("\n***End TextFormat.printToString(UnknownFieldSet)****\n").
                  append("&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&\n").
                  append("&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&\n").
                  append("&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&\n").            
                  append("***Begin gmV3.getAllFields()****\n");
                  if(gmV3.getAllFields() != null){
                     for(Map.Entry<FieldDescriptor, Object> entry :  gmV3.getAllFields().entrySet()){

                        retVal.append(MessageFormat.format("[FieldDescriptor, Object] -----> {0}\n", 
                        new Object[]{TextFormat.printFieldToString(entry.getKey(), entry.getValue())}));

                     }
                  }      
                  retVal.append("***End gmV3.getAllFields()****\n").
                  append("&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&\n").
                  append("&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&\n").
                  append("&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&\n").
                  append("*************short debug api***************\n").
                  append("**********************************************************\n").
                  append("**********************************************************\n").
                  append("**********************************************************\n").
                  append("**********************************************************\n").
                  append("**********************************************************\n").
                  append("***************shortDebugString(gmV3 base obj)*****************\n").
                  append(TextFormat.shortDebugString(gmV3)).
                  append("\n**********************************************************\n").
                  append("**********************************************************\n").
                  append("**********************************************************\n").
                  append("**********shortDebugString(gmV3 base obj.getUnknownFields())**********************\n").
                  append((gmV3.getUnknownFields() != null) ? 
                         TextFormat.shortDebugString(gmV3.getUnknownFields()) : "gmV3.getUnknownFields() == null").
                  append("\n**********************************************************\n").
                  append("**********************************************************\n").
                  append("**********************************************************\n").
                  append("***Begin shortDebugString -> gmV3 base obj.getAllFields()****\n");
                  if(gmV3.getAllFields() != null){
                        for(Map.Entry<FieldDescriptor, Object> entry :  gmV3.getAllFields().entrySet()){

                           retVal.append(TextFormat.shortDebugString(entry.getKey(), entry.getValue())).append("\n");
                  
                        }
                  }
                  retVal.append("***\nEnd TextFormat.shortDebug api printout****\n").
                  append("**********************************************************\n").
                  append("**********************************************************\n").
                  append("&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&\n").
                  append("&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&\n").
                  append("&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&\n");
             
            
      
      return retVal.toString();
   }

  

}
