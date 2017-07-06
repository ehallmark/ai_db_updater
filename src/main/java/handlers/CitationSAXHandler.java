package main.java.handlers;

/**
 * Created by ehallmark on 1/3/17.
 */

import main.java.database.Database;
import main.java.handlers.CustomHandler;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**

 */
public class CitationSAXHandler extends CustomHandler{
    private static Map<String,LocalDate> patentToPubDateMap = Collections.synchronizedMap(new HashMap<>());
    private static Map<String,LocalDate> patentToAppDateMap = Collections.synchronizedMap(new HashMap<>());
    private static Map<String,Set<String>> patentToCitedPatentsMap = Collections.synchronizedMap(new HashMap<>());
    private static Map<String,Set<String>> patentToRelatedDocMap = Collections.synchronizedMap(new HashMap());
    private static Map<String,LocalDate> patentToPriorityDateMap = Collections.synchronizedMap(new HashMap<>());
    private static Set<String> lapsedPatentsSet = Collections.synchronizedSet(new HashSet<>());
    public static Set<String> allPatents = Collections.synchronizedSet(new HashSet<>());


    boolean inPublicationReference=false;
    boolean inApplicationReference=false;
    boolean isDocNumber=false;
    boolean isAppDate=false;
    boolean isPubDate=false;
    boolean inCitation=false;
    boolean isRelatedDocNumber=false;
    boolean inRelatedDoc=false;
    boolean inPriorityClaims=false;
    boolean isCitedDocNumber = false;
    boolean isPriorityDate = false;
    boolean shouldTerminate = false;
    String pubDocNumber;
    LocalDate appDate;
    LocalDate pubDate;
    LocalDate priorityDate;
    List<String> documentPieces = new ArrayList<>();
    private Set<String> citedDocuments = new HashSet<>();
    private Set<String> relatedDocuments = new HashSet<>();

    @Override
    public CustomHandler newInstance() {
        return new CitationSAXHandler();
    }

    private void update() {
        if(pubDocNumber!=null) {
            allPatents.add(pubDocNumber);
            if (pubDate != null) {
                patentToPubDateMap.put(pubDocNumber, pubDate);
            }
            if (appDate != null) {
                patentToAppDateMap.put(pubDocNumber, appDate);
            }
            if (priorityDate != null) {
                patentToPriorityDateMap.put(pubDocNumber, priorityDate);
                if (priorityDate.plusYears(20).isBefore(LocalDate.now())) {
                    lapsedPatentsSet.add(pubDocNumber);
                }
            }
            if (!citedDocuments.isEmpty()) {
                //System.out.println(patNum+" has "+cited.size()+" cited documents");
                patentToCitedPatentsMap.put(pubDocNumber, citedDocuments);
            }
            if (!relatedDocuments.isEmpty()) {
                //System.out.println(patNum+ " has "+related.size()+" related documents");
                patentToRelatedDocMap.put(pubDocNumber, relatedDocuments);
            }
        }
    }

    public void reset() {
        update();
        isCitedDocNumber=false;
        inPublicationReference=false;
        inApplicationReference=false;
        isDocNumber=false;
        isAppDate=false;
        isPubDate=false;
        inCitation=false;
        shouldTerminate = false;
        appDate=null;
        pubDate=null;
        inRelatedDoc=false;
        inPriorityClaims=false;
        isRelatedDocNumber=false;
        isPriorityDate=false;
        priorityDate=null;
        pubDocNumber=null;
        documentPieces.clear();
        citedDocuments = new HashSet<>();
        relatedDocuments = new HashSet<>();
    }

    @Override
    public void save() {
        // invert patent map to get referenced by instead of referencing
        Map<String,Set<String>> patentToReferencedByMap = Collections.synchronizedMap(new HashMap<>());
        patentToCitedPatentsMap.forEach((patent,citedSet)->{
            if(allPatents.contains(patent)) {
                citedSet.forEach(cited->{
                    if(allPatents.contains(cited)) {
                        if(patentToReferencedByMap.containsKey(cited)) {
                            patentToReferencedByMap.get(cited).add(patent);
                        } else {
                            Set<String> set = new HashSet<>();
                            set.add(patent);
                            patentToReferencedByMap.put(cited,set);
                        }
                    }
                });
            }
        });

        // date to patent map
        Map<LocalDate,Set<String>> pubDateToPatentMap = Collections.synchronizedMap(new HashMap<>());
        patentToPubDateMap.forEach((patent,pubDate)->{
            if(pubDateToPatentMap.containsKey(pubDate)) {
                pubDateToPatentMap.get(pubDate).add(patent);
            } else {
                Set<String> set = new HashSet<>();
                set.add(patent);
                pubDateToPatentMap.put(pubDate,set);
            }
        });


        Database.saveObject(patentToCitedPatentsMap,Database.patentToCitedPatentsMapFile);
        Database.saveObject(pubDateToPatentMap,Database.pubDateToPatentMapFile);
        Database.saveObject(patentToRelatedDocMap,Database.patentToRelatedDocMapFile);
        Database.saveObject(patentToReferencedByMap,Database.patentToReferencedByMapFile);
        Database.saveObject(patentToAppDateMap,Database.patentToAppDateMapFile);
        Database.saveObject(patentToPubDateMap,Database.patentToPubDateMapFile);
        Database.saveObject(patentToPriorityDateMap,Database.patentToPriorityDateMapFile);
        Database.saveObject(lapsedPatentsSet,Database.lapsedPatentsSetFile);
    }

    public void startElement(String uri,String localName,String qName,
        Attributes attributes)throws SAXException{

        //System.out.println("Start Element :" + qName);

        if(qName.equals("publication-reference")){
            inPublicationReference=true;
        }

        if(qName.equals("application-reference")) {
            inApplicationReference=true;
        }

        if(qName.equals("doc-number")&&inPublicationReference){
            isDocNumber=true;
        }

        if(qName.equals("date")&&inPublicationReference){
            isPubDate=true;
        }

        if(qName.equals("priority-claims")) {
            inPriorityClaims=true;
        }

        if(inPriorityClaims&&qName.equals("date")) {
            isPriorityDate=true;
        }

        if(qName.equals("date")&&inApplicationReference){
            isAppDate=true;
        }

        if(qName.equals("doc-number")&&inCitation) {
            isCitedDocNumber=true;
        }

        if(qName.equals("patcit")) {
            inCitation=true;
        }

        if(inRelatedDoc&&qName.equals("doc-number")) {
            isRelatedDocNumber=true;
        }

        if(qName.contains("related-doc")||qName.equals("relation")||qName.equals("us-relation")) {
            inRelatedDoc=true;
        }
    }

    public void endElement(String uri,String localName,
        String qName)throws SAXException{

        //System.out.println("End Element :" + qName);

        if(qName.equals("doc-number")&&inPublicationReference){
            isDocNumber=false;
            pubDocNumber=String.join("",documentPieces).replaceAll("[^A-Z0-9]","");
            if(pubDocNumber.startsWith("0"))pubDocNumber = pubDocNumber.substring(1,pubDocNumber.length());

            if(pubDocNumber.replaceAll("[^0-9]","").length()!=pubDocNumber.length() || (pubDocNumber.length()!=7 && pubDocNumber.length()!=8)) {
                pubDocNumber=null;
                shouldTerminate = true;
            }
            documentPieces.clear();
        }

        if(qName.equals("date")&&inPriorityClaims){
            isPriorityDate=false;
            try {
                LocalDate date = LocalDate.parse(String.join("", documentPieces).trim(), DateTimeFormatter.BASIC_ISO_DATE);
                if(priorityDate==null||(date.isBefore(priorityDate))) {
                    priorityDate=date;
                }
            } catch(Exception dateException) {
            }
            documentPieces.clear();
        }

        if(qName.equals("date")&&inPublicationReference){
            isPubDate=false;
            try {
                pubDate = LocalDate.parse(String.join("", documentPieces).trim(), DateTimeFormatter.BASIC_ISO_DATE);
            } catch(Exception dateException) {
            }
            documentPieces.clear();
        }

        if(qName.equals("date")&&inApplicationReference){
            isAppDate=false;
            try {
                appDate = LocalDate.parse(String.join("", documentPieces).trim(), DateTimeFormatter.BASIC_ISO_DATE);
            } catch(Exception dateException) {
            }
            documentPieces.clear();
        }

        if(qName.equals("publication-reference")){
            inPublicationReference=false;
        }

        if(qName.equals("patcit")) {
            inCitation=false;
        }

        if(qName.equals("application-reference")) {
            inApplicationReference=false;
        }

        if(qName.equals("doc-number")&&inCitation) {
            isCitedDocNumber=false;
            String docNumber=String.join("",documentPieces).replaceAll("[^A-Z0-9]","");
            if(docNumber.startsWith("0"))docNumber = docNumber.substring(1,docNumber.length());
            if(docNumber.replaceAll("[^0-9]","").length()==docNumber.length() && (docNumber.length()==7||docNumber.length()==8)) {
                citedDocuments.add(docNumber);
            }
            documentPieces.clear();
        }

        if(inRelatedDoc&&qName.equals("doc-number")) {
            isRelatedDocNumber=false;
            String docNumber=String.join("",documentPieces).replaceAll("[^A-Z0-9]","");
            if(docNumber.startsWith("0"))docNumber = docNumber.substring(1,docNumber.length());

            if(docNumber.replaceAll("[^0-9]","").length()==docNumber.length() && (docNumber.length()==7||docNumber.length()==8)) {
                relatedDocuments.add(docNumber);
            }
            documentPieces.clear();
        }

        if(qName.contains("related-doc")||qName.equals("relation")||qName.equals("us-relation")) {
            inRelatedDoc=false;
        }
    }

    public void characters(char ch[],int start,int length)throws SAXException{

        // Example
        // if (bfname) {
        //    System.out.println("First Name : " + new String(ch, start, length));
        //    bfname = false;
        // }

        if((!shouldTerminate)&&(isCitedDocNumber||isDocNumber||isPriorityDate||isRelatedDocNumber||isAppDate||isPubDate)){
            documentPieces.add(new String(ch,start,length));
        }

    }
}