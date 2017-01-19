package main.java.tools;

/**
 * Created by ehallmark on 1/3/17.
 */

import main.java.database.Database;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

/**

 */
public class AssignmentSAXHandler extends DefaultHandler{
    private boolean inPatentAssignment = false;
    private boolean isConveyanceText=false;
    private boolean inPatentAssignee=false;
    private boolean isName=false;
    private boolean inDocumentID=false;
    private boolean isDocNumber=false;
    private boolean isDocKind=false;
    boolean shouldTerminate = false;
    private Set<String> allPatents;
    private List<String>documentPieces=new ArrayList<>();
    private List<String> currentPatents = new ArrayList<>();
    private List<String> currentAssignees = new ArrayList<>();
    private List<String> currentDocKinds = new ArrayList<>();

    public AssignmentSAXHandler(Set<String> allPatents) {
        this.allPatents=allPatents;
    }

    public void reset() {
        // DO NOT CLEAR PATENT TO ASSIGNEE MAP!!!!
        inPatentAssignment=false;
        isConveyanceText=false;
        inPatentAssignee=false;
        isDocNumber=false;
        isDocKind=false;
        inDocumentID=false;
        isName=false;
        shouldTerminate = false;
        currentAssignees.clear();
        currentDocKinds.clear();
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

        if(inDocumentID&&qName.equals("doc-kind")) {
            isDocKind = true;
        }

        if(inPatentAssignment&&qName.equals("patent-assignee")) {
            inPatentAssignee=true;
        }

        if(inPatentAssignee&&qName.equals("name")) {
            isName=true;
        }
    }

    public void endElement(String uri,String localName,
        String qName)throws SAXException{

        //System.out.println("End Element :" + qName);


        if(qName.equals("patent-assignment")){
            inPatentAssignment=false;
            // done with patent so update patent map and reset data
            if(!currentAssignees.isEmpty()) {
                for(int i = 0; i < currentPatents.size(); i++) {
                    String patent = currentPatents.get(i);
                    String docKind = currentDocKinds.get(i);
                    if(docKind!=null&&docKind.startsWith("B")&&patent!=null&&!patent.isEmpty()) {
                        if (allPatents.contains(patent)) {
                            System.out.println("Updating " + patent + " with assignees: " + String.join("; ", currentAssignees));
                            try {
                                Database.updateAssigneeForPatent(patent, currentAssignees.toArray(new String[currentAssignees.size()]));
                            } catch (SQLException sql) {
                                System.out.print("SQL ERROR: ");
                                sql.printStackTrace();
                            }
                        } else {
                            System.out.println(patent + " does not exist in database");
                        }
                    }
                }
            }
            reset();
        }

        if(inPatentAssignment&&qName.equals("conveyance-text")){
            isConveyanceText=false;
            String text = cleanAssignee(String.join("",documentPieces));
            if(text==null||text.length()==0||!text.startsWith("ASSIGNMENT OF ASSIGNOR")) {
                shouldTerminate = true;
            }
        }

        if(inPatentAssignment&&qName.equals("document-id")){
            inDocumentID=false;
        }

        if(inDocumentID&&qName.equals("doc-number")) {
            isDocNumber = false;
            String text = cleanAssignee(String.join("",documentPieces));
            currentPatents.add(text);
        }

        if(inDocumentID&&qName.equals("doc-kind")) {
            isDocKind = false;
            String text = cleanAssignee(String.join("",documentPieces));
            currentDocKinds.add(text);
        }

        if(inPatentAssignment&&qName.equals("patent-assignee")) {
            inPatentAssignee=false;
        }

        if(inPatentAssignee&&qName.equals("name")) {
            isName=false;
            String text = cleanAssignee(String.join("",documentPieces));
            if(text!=null&&text.length()>0) {
                currentAssignees.add(text);
            }
        }

    }

    public void characters(char ch[],int start,int length)throws SAXException{

        // Example
        // if (bfname) {
        //    System.out.println("First Name : " + new String(ch, start, length));
        //    bfname = false;
        // }

        if((!shouldTerminate)&&(isName||isDocNumber||isDocKind||isConveyanceText)){
            documentPieces.add(new String(ch,start,length));
        }

    }

    public static String cleanAssignee(String toExtract) {
        String data = toExtract.toUpperCase().replaceAll("[^A-Z0-9 ]","");
        while(data.contains("  ")) data=data.replaceAll("  "," "); // strip double spaces
        return data.trim();
    }

}