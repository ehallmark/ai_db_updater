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
import java.util.*;
import java.util.stream.Collectors;

/**

 */
public class InventionTitleSAXHandler extends DefaultHandler{
    boolean inPublicationReference=false;
    boolean isDocNumber=false;
    boolean isInventionTitle=false;
    boolean shouldTerminate = false;
    String pubDocNumber;
    String inventionTitle;
    List<String> documentPieces = new ArrayList<>();

    public static Map<String,String> load() throws IOException, ClassNotFoundException {
        ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(Database.patentToInventionTitleMapFile)));
        Map<String,String> patentToAssigneeMap = (Map<String,String>)ois.readObject();
        ois.close();
        return patentToAssigneeMap;
    }

    public String getPatentNumber() {
        return pubDocNumber;
    }

    public String getInventionTitle() {
        return inventionTitle;
    }

    public void reset() {
        isInventionTitle=false;
        inPublicationReference=false;
        isDocNumber=false;
        shouldTerminate = false;
        inventionTitle=null;
        pubDocNumber=null;
        documentPieces.clear();
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

        if(qName.equalsIgnoreCase("invention-title")&&inPublicationReference){
            isInventionTitle=true;
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

        if(qName.equalsIgnoreCase("invention-title")&&inPublicationReference){
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

    }

    public void characters(char ch[],int start,int length)throws SAXException{

        // Example
        // if (bfname) {
        //    System.out.println("First Name : " + new String(ch, start, length));
        //    bfname = false;
        // }

        if((!shouldTerminate)&&(isInventionTitle||isDocNumber)){
            documentPieces.add(new String(ch,start,length));
        }

    }
}