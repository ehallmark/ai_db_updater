package main.java.tools;

/**
 * Created by ehallmark on 1/3/17.
 */

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.*;
import java.util.*;

/**

 */
public class AssignmentSAXHandler extends DefaultHandler{
    private boolean inPatentAssignment = false;
    private boolean isConveyanceText=false;
    private boolean inPatentAssignee=false;
    private boolean isName=false;
    private boolean inDocumentID=false;
    private boolean isDocNumber=false;
    boolean shouldTerminate = false;
    private List<String>documentPieces=new ArrayList<>();
    private List<String> currentPatents = new ArrayList<>();
    private List<String> currentAssignees = new ArrayList<>();

    private static File patentToAssigneeMapFile = new File("patent_to_assignee_map_latest.jobj");
    private static Map<String,List<String>> patentToAssigneeMap;

    static {
        try {
            if (patentToAssigneeMapFile.exists()) {
                ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(patentToAssigneeMapFile)));
                patentToAssigneeMap = (Map<String,List<String>>)ois.readObject();
                ois.close();
            } else {
                patentToAssigneeMap=new HashMap<>();
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public static void save() throws IOException {
        if(patentToAssigneeMap==null) return;
        ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(patentToAssigneeMapFile)));
        oos.writeObject(patentToAssigneeMap);
        oos.flush();
        oos.close();
    }

    public void reset() {
        // DO NOT CLEAR PATENT TO ASSIGNEE MAP!!!!
        inPatentAssignment=false;
        isConveyanceText=false;
        inPatentAssignee=false;
        isDocNumber=false;
        inDocumentID=false;
        isName=false;
        shouldTerminate = false;
        currentAssignees.clear();
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
            if(!shouldTerminate&&!currentAssignees.isEmpty()) {
                for(int i = 0; i < currentPatents.size(); i++) {
                    String patent = currentPatents.get(i);
                    if(patent!=null&&patent.length()==7&&patent.replaceAll("[^0-9]","").length()==7) {
                        try {
                            if(Integer.valueOf(patent) >= 7000000) {
                                patentToAssigneeMap.put(patent, currentAssignees);
                            }
                        } catch (NumberFormatException nfe) {
                            // not a utility patent
                        }
                    } else {
                        System.out.println(patent + " does not exist in database");
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
            documentPieces.clear();
        }

        if(inPatentAssignment&&qName.equals("document-id")){
            inDocumentID=false;
        }

        if(inDocumentID&&qName.equals("doc-number")) {
            isDocNumber = false;
            String text = cleanAssignee(String.join("",documentPieces));
            currentPatents.add(text);
            documentPieces.clear();

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
            documentPieces.clear();
        }

    }

    public void characters(char ch[],int start,int length)throws SAXException{

        // Example
        // if (bfname) {
        //    System.out.println("First Name : " + new String(ch, start, length));
        //    bfname = false;
        // }

        if((!shouldTerminate)&&(isName||isDocNumber||isConveyanceText)){
            documentPieces.add(new String(ch,start,length));
        }

    }

    public static String cleanAssignee(String toExtract) {
        String data = toExtract.toUpperCase().replaceAll("[^A-Z0-9 ]","");
        while(data.contains("   ")) data=data.replaceAll("   "," "); // strip triple spaces (might be faster)
        while(data.contains("  ")) data=data.replaceAll("  "," "); // strip double spaces
        return data.trim();
    }

}