package main.java.handlers;

/**
 * Created by ehallmark on 1/3/17.
 */

import main.java.database.Database;
import main.java.tools.AssigneeTrimmer;
import main.java.tools.PhrasePreprocessor;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**

 */
public class SAXHandler extends CustomHandler{
    private boolean inPublicationReference=false;
    private boolean isDocNumber=false;
    private boolean inAssignee=false;
    private boolean isOrgname = false;
    private boolean isWithinDocument=false;
    private boolean shouldTerminate = false;
    private String pubDocNumber;
    private List<List<String>>fullDocuments=new ArrayList<>();
    private List<String>documentPieces=new ArrayList<>();
    private List<List<String>>tokenPieces=new ArrayList<>();
    private Set<String> assignees= new HashSet<>();
    private static AtomicInteger cnt = new AtomicInteger(0);
    private static PhrasePreprocessor phrasePreprocessor = new PhrasePreprocessor();

    private void update() {
        if (pubDocNumber != null && !fullDocuments.isEmpty() && !shouldTerminate) {
            try {
                Database.ingestRecords(pubDocNumber, assignees, fullDocuments);
            } catch(Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void reset() {
        update();
        inPublicationReference=false;
        isDocNumber=false;
        inAssignee=false;
        isOrgname=false;
        shouldTerminate = false;
        isWithinDocument=false;
        fullDocuments.clear();
        documentPieces.clear();
        tokenPieces.clear();
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
        if(shouldTerminate) return;

        //System.out.println("Start Element :" + qName);

        if(qName.equals("publication-reference")){
            inPublicationReference=true;
        }

        if(qName.equals("claim")||qName.equals("description")||qName.equals("abstract")){
            isWithinDocument=true;
        }

        if(qName.equals("doc-number")&&inPublicationReference){
            isDocNumber=true;
        }

        if(qName.endsWith("assignee")) {
            inAssignee=true;
        }

        if(inAssignee&&qName.equals("orgname")) {
            isOrgname=true;
        }
    }

    public void endElement(String uri,String localName,
        String qName)throws SAXException{
        if(shouldTerminate) return;

        if(qName.equals("doc-number")&&inPublicationReference){
            isDocNumber=false;
            pubDocNumber=String.join("",documentPieces).replaceAll("[^A-Z0-9]","");
            if(pubDocNumber.startsWith("0"))pubDocNumber = pubDocNumber.substring(1,pubDocNumber.length());
            if(pubDocNumber.isEmpty()) {
                pubDocNumber=null;
                shouldTerminate = true;
            }
            tokenPieces.clear();
            documentPieces.clear();
        }

        if(qName.equals("publication-reference")){
            inPublicationReference=false;
        }

        if(qName.equals("claim")||qName.equals("description")||qName.equals("abstract")){
            isWithinDocument=false;
            List<String> tokens = tokenPieces.stream().flatMap(list->list.stream()).collect(Collectors.toList());
            if(tokens.size() > 5) {
                fullDocuments.add(tokens);
            }
            tokenPieces.clear();
            documentPieces.clear();
        }

        if(inAssignee&&qName.equals("orgname")) {
            isOrgname=false;
            String assignee = AssigneeTrimmer.standardizedAssignee(String.join(" ",documentPieces));
            if(assignee.length()>0) {
                assignees.add(assignee);
            }
            tokenPieces.clear();
            documentPieces.clear();
        }

        if(qName.endsWith("assignee")) {
            inAssignee=false;
        }
    }

    public void characters(char ch[],int start,int length)throws SAXException{
        if((!shouldTerminate)&&(isWithinDocument||isDocNumber||isOrgname)){
            if(isWithinDocument) {
                length=Math.min(length,10000); // avoid overflow
                tokenPieces.add(extractTokens(new String(ch, start, length)));
            } else {
                documentPieces.add(new String(ch, start, length));
            }
        }

    }

    private static List<String> extractTokens(String toExtract,boolean phrases) {
        return Arrays.stream((phrases?phrasePreprocessor.preProcess(toExtract):toExtract).split("\\s+"))
                .map(t->t.toLowerCase().replaceAll("[^a-z.]","")).filter(t->t.length()>0).limit(500)
                .flatMap(t->Arrays.stream(t.split("\\."))).filter(t->t.length()>0).collect(Collectors.toList());
    }

    private static List<String> extractTokens(String toExtract) {
        return extractTokens(toExtract,false);
    }
}