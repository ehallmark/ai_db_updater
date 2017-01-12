package main.java.tools;

/**
 * Created by ehallmark on 1/3/17.
 */

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.*;
import java.util.stream.Collectors;

/**

 */
public class SAXHandler extends DefaultHandler{
    boolean isClaim=false;
    boolean inPublicationReference=false;
    boolean isAbstract=false;
    boolean inDescription=false;
    boolean inDescriptionParagraph=false;
    boolean isDocNumber=false;
    boolean isInventor=false;
    boolean shouldTerminate = false;
    String pubDocNumber;
    List<List<String>>fullDocuments=new ArrayList<>();
    List<String>documentPieces=new ArrayList<>();
    private Set<String> inventors= new HashSet<>();
    private static PhrasePreprocessor phrasePreprocessor = new PhrasePreprocessor();

    public String getPatentNumber() {
        return pubDocNumber;
    }

    public List<List<String>> getFullDocuments() {
        return fullDocuments;
    }

    public Set<String> getInventors() { return inventors; }

    public void reset() {
        fullDocuments.clear();
        documentPieces.clear();
        shouldTerminate=false;
        inventors.clear();
        pubDocNumber=null;
    }

    public void startElement(String uri,String localName,String qName,
        Attributes attributes)throws SAXException{

        //System.out.println("Start Element :" + qName);

        if(qName.equalsIgnoreCase("publication-reference")){
            inPublicationReference=true;
        }

        if(qName.equalsIgnoreCase("claim")){
            isClaim=true;
        }

        if(qName.equalsIgnoreCase("doc-number")&&inPublicationReference){
            isDocNumber=true;
        }

        if(qName.equalsIgnoreCase("abstract")) {
            isAbstract = true;
        }

        if(qName.equalsIgnoreCase("description")) {
            inDescription=true;
        }

        if(inDescription && qName.equalsIgnoreCase("p")) {
            inDescriptionParagraph=true;
        }

        if(qName.toLowerCase().endsWith("inventor")||qName.toLowerCase().endsWith("applicant")) {
            isInventor=true;
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

        if(qName.equalsIgnoreCase("publication-reference")){
            inPublicationReference=false;
        }

        if(qName.equalsIgnoreCase("claim")){
            isClaim=false;
            List<String> tokens = extractTokens(String.join(" ",documentPieces));
            if(tokens.size() > 5) {
                fullDocuments.add(tokens);
            }
            documentPieces.clear();
        }

        if(qName.equalsIgnoreCase("abstract")){
            isAbstract=false;
            List<String> tokens = extractTokens(String.join(" ",documentPieces));
            if(tokens.size() > 5) {
                fullDocuments.add(tokens);
            }
            documentPieces.clear();
        }

        if(inDescription && qName.equalsIgnoreCase("p")) {
            inDescriptionParagraph=false;
        }

        if(qName.equalsIgnoreCase("description")) {
            inDescription=false;
            List<String> tokens = extractTokens(String.join(" ",documentPieces));
            if(tokens.size() > 5) {
                fullDocuments.add(tokens);
            }
            documentPieces.clear();
        }

        if(qName.toLowerCase().endsWith("inventor")||qName.toLowerCase().endsWith("applicant")) {
            isInventor=false;
            List<String> tokens = extractTokens(String.join(" ",documentPieces),false);
            if(tokens.size() > 5) {
                inventors.add(String.join(" ",tokens));
            }
            documentPieces.clear();
        }
    }

    public void characters(char ch[],int start,int length)throws SAXException{

        // Example
        // if (bfname) {
        //    System.out.println("First Name : " + new String(ch, start, length));
        //    bfname = false;
        // }

        if((!shouldTerminate)&&(isClaim||isDocNumber||isAbstract||inDescriptionParagraph||isInventor)){
            documentPieces.add(new String(ch,start,length));
        }

    }

    private static List<String> extractTokens(String toExtract,boolean phrases) {
        String data = toExtract.toLowerCase().replaceAll("[^a-z ]"," ");
        return Arrays.stream((phrases?phrasePreprocessor.preProcess(data):data).split("\\s+"))
                .filter(t->t!=null&&t.length()>0)
                .collect(Collectors.toList());
    }

    private static List<String> extractTokens(String toExtract) {
        return extractTokens(toExtract,true);
    }
}