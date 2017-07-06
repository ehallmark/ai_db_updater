package main.java.tools;

/**
 * Created by ehallmark on 1/3/17.
 */

import main.java.database.Database;
import main.java.handlers.CustomHandler;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**

 */
public class SAXHandler extends CustomHandler{
    boolean isClaim=false;
    boolean inPublicationReference=false;
    boolean isDocNumber=false;
    boolean inAssignee=false;
    boolean isOrgname = false;
    boolean shouldTerminate = false;
    String pubDocNumber;
    List<List<String>>fullDocuments=new ArrayList<>();
    List<String>documentPieces=new ArrayList<>();
    private Set<String> assignees= new HashSet<>();
    private static AtomicInteger cnt = new AtomicInteger(0);
    private static PhrasePreprocessor phrasePreprocessor = new PhrasePreprocessor();

    private void update() {
        if (pubDocNumber != null && !fullDocuments.isEmpty()) {
            try {
                Database.ingestRecords(pubDocNumber, assignees, fullDocuments);
            } catch(Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void reset() {
        update();
        isClaim=false;
        inPublicationReference=false;
        isDocNumber=false;
        inAssignee=false;
        isOrgname=false;
        shouldTerminate = false;
        fullDocuments.clear();
        documentPieces.clear();
        assignees.clear();
        pubDocNumber=null;
        if (cnt.getAndIncrement()%1000==0)
            try {
                Database.commit();
            } catch(Exception e) {
                e.printStackTrace();
            }
    }

    @Override
    public void save() {
        // do nothing
        try {
            Database.commit();
            Database.close();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public CustomHandler newInstance() {
        return new SAXHandler();
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
            if(pubDocNumber.isEmpty()) {
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

        if(inAssignee&&qName.equalsIgnoreCase("orgname")) {
            isOrgname=false;
            String assignee = AssigneeTrimmer.standardizedAssignee(String.join(" ",documentPieces));
            if(assignee.length()>0) {
                assignees.add(assignee);
            }
            documentPieces.clear();
        }

        if(qName.toLowerCase().endsWith("assignee")) {
            inAssignee=false;
        }
    }

    public void characters(char ch[],int start,int length)throws SAXException{
        if((!shouldTerminate)&&(isClaim||isDocNumber||isOrgname)){
            documentPieces.add(new String(ch,start,length));
        }

    }

    private static List<String> extractTokens(String toExtract,boolean phrases) {
        String data = toExtract.toLowerCase().replaceAll("[^a-z ]"," ");
        return Arrays.stream((phrases?phrasePreprocessor.preProcess(data):data).split("\\s+"))
                .filter(t->t!=null&&t.length()>0).limit(10000)
                .collect(Collectors.toList());
    }

    private static List<String> extractTokens(String toExtract) {
        return extractTokens(toExtract,false);
    }
}