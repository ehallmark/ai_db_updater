package main.java.tools;

/**
 * Created by ehallmark on 1/3/17.
 */

import main.java.database.Database;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.*;
import java.util.*;

/**

 */
public class TransactionSAXHandler extends DefaultHandler{
    private boolean inPatentAssignment = false;
    private boolean isConveyanceText=false;
    private boolean isAssignorsInterest = false;
    private boolean isSecurityInterest = false;
    private boolean inDocumentID=false;
    private boolean isDocNumber=false;
    boolean shouldTerminate = false;
    private List<String> documentPieces=new ArrayList<>();
    private List<String> currentPatents = new ArrayList<>();

    private static final File patentToSecurityInterestCountMapFile = new File("patent_to_security_interest_count_map.jobj");
    private static Map<String,Integer> patentToSecurityInterestCountMap = new HashMap<>();
    private static final File patentToTransactionCountMapFile = new File("patent_to_transaction_count_map.jobj");
    private static Map<String,Integer> patentToTransactionCountMap = new HashMap<>();
    private static final File patentToTransactionSizesMapFile = new File("patent_to_transaction_sizes_map.jobj");
    private static Map<String,List<Integer>> patentToTransactionSizeMap = new HashMap<>();

    public static void save() throws IOException {
        Database.saveObject(patentToSecurityInterestCountMap,patentToSecurityInterestCountMapFile);
        Database.saveObject(patentToTransactionCountMap,patentToTransactionCountMapFile);
        Database.saveObject(patentToTransactionSizeMap,patentToTransactionSizesMapFile);
    }

    public void reset() {
        // DO NOT CLEAR PATENT TO ASSIGNEE MAP!!!!
        inPatentAssignment=false;
        isConveyanceText=false;
        isAssignorsInterest=false;
        isSecurityInterest=false;
        isDocNumber=false;
        inDocumentID=false;
        shouldTerminate = false;
        currentPatents.clear();
        documentPieces.clear();
    }

    public void startElement(String uri,String localName,String qName,
        Attributes attributes)throws SAXException{

        //System.out.println("Start Element :" + qName);

        if(qName.equals("patent-assignment")){
            shouldTerminate=false;
            inPatentAssignment=true;
        }

        if(inPatentAssignment&&qName.equals("conveyance-text")){
            isConveyanceText=true;
        }

        if(inPatentAssignment&&qName.equals("document-id")){
            inDocumentID=true;
        }

        if(inDocumentID&&qName.equals("doc-number")) {
            isDocNumber = true;
        }
    }

    public void endElement(String uri,String localName,
        String qName)throws SAXException{

        //System.out.println("End Element :" + qName);


        if(qName.equals("patent-assignment")){
            inPatentAssignment=false;
            // done with patent so update patent map and reset data
            if(!shouldTerminate&&!currentPatents.isEmpty()) {
                for(int i = 0; i < currentPatents.size(); i++) {
                    String patent = currentPatents.get(i);
                    if(patent!=null&&patent.length()==7&&patent.replaceAll("[^0-9]","").length()==7) {
                        try {
                            if(Integer.valueOf(patent) >= 7000000) {
                                // good to go
                                if(isAssignorsInterest) {
                                    System.out.println("Assignors interest: "+patent);
                                    // transaction
                                    if(patentToTransactionCountMap.containsKey(patent)) {
                                        patentToTransactionCountMap.put(patent,patentToTransactionCountMap.get(patent)+1);
                                    } else {
                                        patentToTransactionCountMap.put(patent,1);
                                    }
                                    if(patentToTransactionSizeMap.containsKey(patent)) {
                                        patentToTransactionSizeMap.get(patent).add(currentPatents.size());
                                    } else {
                                        List<Integer> sizes = new ArrayList<>();
                                        sizes.add(currentPatents.size());
                                        patentToTransactionSizeMap.put(patent,sizes);
                                    }
                                } else if (isSecurityInterest) {
                                    if(patentToSecurityInterestCountMap.containsKey(patent)) {
                                        patentToSecurityInterestCountMap.put(patent,patentToSecurityInterestCountMap.get(patent)+1);
                                    } else {
                                        patentToSecurityInterestCountMap.put(patent,1);
                                    }
                                    System.out.println("Security Interest: "+patent);
                                }
                            }
                        } catch (NumberFormatException nfe) {
                            // not a utility patent
                        }
                    } else {
                    }
                }
            }
            reset();
        }

        if(inPatentAssignment&&qName.equals("conveyance-text")){
            isConveyanceText=false;
            String text = AssigneeTrimmer.cleanAssignee(String.join("",documentPieces));
            if(text.contains("ASSIGNMENT OF ASSIGN")) isAssignorsInterest=true;
            else if(text.contains("SECURITY INTEREST")) isSecurityInterest=true;
            if(!(isSecurityInterest||isAssignorsInterest)) {
                shouldTerminate = true;
            }
            documentPieces.clear();
        }

        if(inPatentAssignment&&qName.equals("document-id")){
            inDocumentID=false;
        }

        if(inDocumentID&&qName.equals("doc-number")) {
            isDocNumber = false;
            String text = AssigneeTrimmer.cleanAssignee(String.join("",documentPieces));
            currentPatents.add(text);
            documentPieces.clear();

        }
    }

    public void characters(char ch[],int start,int length)throws SAXException{

        // Example
        // if (bfname) {
        //    System.out.println("First Name : " + new String(ch, start, length));
        //    bfname = false;
        // }

        if((!shouldTerminate)&&(isDocNumber||isConveyanceText)){
            documentPieces.add(new String(ch,start,length));
        }

    }

}