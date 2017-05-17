package main.java.tools;

/**
 * Created by ehallmark on 1/3/17.
 */

import main.java.database.Database;
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
public class CitationSAXHandler extends DefaultHandler{
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

    public String getPatentNumber() {
        return pubDocNumber;
    }

    public LocalDate getAppDate() {
        return appDate;
    }

    public LocalDate getPubDate() {
        return pubDate;
    }

    public LocalDate getPriorityDate() { return (priorityDate==null)?appDate:priorityDate; }

    public Set<String> getCitedDocuments() {
        return new HashSet<>(citedDocuments);
    }

    public Set<String> getRelatedDocuments() {
        return new HashSet<>(relatedDocuments);
    }

    public void reset() {
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
        citedDocuments.clear();
        relatedDocuments.clear();
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

        if(qName.contains("related-doc")) {
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

        if(qName.contains("related-doc")) {
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