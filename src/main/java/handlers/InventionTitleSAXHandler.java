package main.java.handlers;

/**
 * Created by ehallmark on 1/3/17.
 */

import main.java.database.Database;
import main.java.tools.AssigneeTrimmer;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

/**

 */
public class InventionTitleSAXHandler extends CustomHandler{
    private static Map<String,String> patentToInventionTitleMap = Collections.synchronizedMap(new HashMap<>());
    private static Map<String,List<String>> patentToOriginalAssigneeMap = Collections.synchronizedMap(new HashMap<>());

    boolean inPublicationReference=false;
    boolean isDocNumber=false;
    boolean isInventionTitle=false;
    boolean shouldTerminate = false;
    boolean inAssignee=false;
    boolean isOrgname = false;
    String pubDocNumber;
    String inventionTitle;
    List<String> documentPieces = new ArrayList<>();
    private List<String> originalAssignees = new ArrayList<>();

    protected void update() {
        if (pubDocNumber != null&&inventionTitle!=null) {
            patentToInventionTitleMap.put(pubDocNumber,inventionTitle);
        }
        if(pubDocNumber!=null && !originalAssignees.isEmpty()) {
            List<String> cloneAssignees = new ArrayList<>(originalAssignees);
            patentToOriginalAssigneeMap.put(pubDocNumber,cloneAssignees);
        }
    }

    @Override
    public CustomHandler newInstance() {
        return new InventionTitleSAXHandler();
    }

    @Override
    public void reset() {
        update();
        isInventionTitle=false;
        inPublicationReference=false;
        isDocNumber=false;
        inAssignee=false;
        isOrgname=false;
        shouldTerminate = false;
        inventionTitle=null;
        pubDocNumber=null;
        documentPieces.clear();
        originalAssignees.clear();
    }

    @Override
    public void save() {
        Database.saveObject(patentToInventionTitleMap,Database.patentToInventionTitleMapFile);
        Database.saveObject(patentToOriginalAssigneeMap,Database.patentToOriginalAssigneeMapFile);
    }

    public void startElement(String uri,String localName,String qName,
        Attributes attributes)throws SAXException{

        //System.out.println("Start Element :" + qName);

        if(qName.equalsIgnoreCase("publication-reference")){
            inPublicationReference=true;
        }

        if(qName.equalsIgnoreCase("doc-number")&&inPublicationReference){
            isDocNumber=true;
        }

        if(qName.equalsIgnoreCase("invention-title")){
            isInventionTitle=true;
        }

        if(qName.toLowerCase().endsWith("assignee")) {
            inAssignee=true;
        }

        if(inAssignee&&qName.equalsIgnoreCase("orgname")) {
            isOrgname=true;
        }
    }

    public void endElement(String uri,String localName,
        String qName)throws SAXException{

        //System.out.println("End Element :" + qName);

        if(qName.equalsIgnoreCase("doc-number")&&inPublicationReference){
            isDocNumber=false;
            pubDocNumber=String.join("",documentPieces).replaceAll("[^A-Z0-9]","");
            if(pubDocNumber.startsWith("0"))pubDocNumber = pubDocNumber.substring(1,pubDocNumber.length());

            if(pubDocNumber.replaceAll("[^0-9]","").length()!=pubDocNumber.length()) {
                pubDocNumber=null;
                shouldTerminate = true;
            }
            documentPieces.clear();
        }

        if(qName.equalsIgnoreCase("invention-title")){
            isInventionTitle=false;
            inventionTitle=String.join("",documentPieces).trim().toUpperCase().replaceAll("[^A-Z0-9 ]","");

            if(inventionTitle.isEmpty()) {
                inventionTitle=null;
                shouldTerminate = true;
            }
            documentPieces.clear();
        }

        if(qName.equalsIgnoreCase("publication-reference")){
            inPublicationReference=false;
        }

        if(inAssignee&&qName.equalsIgnoreCase("orgname")) {
            isOrgname=false;
            String assignee = AssigneeTrimmer.standardizedAssignee(String.join(" ",documentPieces));
            if(assignee.length()>0) {
                originalAssignees.add(assignee);
            }
            documentPieces.clear();
        }

        if(qName.toLowerCase().endsWith("assignee")) {
            inAssignee=false;
        }

    }

    public void characters(char ch[],int start,int length)throws SAXException{

        // Example
        // if (bfname) {
        //    System.out.println("First Name : " + new String(ch, start, length));
        //    bfname = false;
        // }

        if((!shouldTerminate)&&(isInventionTitle||isDocNumber||isOrgname)){
            documentPieces.add(new String(ch,start,length));
        }

    }
}