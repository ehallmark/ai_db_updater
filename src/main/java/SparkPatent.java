package main.java;

/**
 * Created by Evan on 4/2/2017.
 */
public class SparkPatent {
    private String patentNumber;
    private String assignee;
    private String document;
    public SparkPatent(String patentNumber, String assignee, String document) {
        this.patentNumber=patentNumber;
        this.assignee=assignee;
        this.document=document;
    }

    public String getPatentNumber() { return patentNumber; }
    public String getAssignee() { return assignee; }
    public String getDocument() { return document; }
    public void setPatentNumber(String patentNumber) { this.patentNumber=patentNumber; }
    public void setAssignee(String assignee) { this.assignee=assignee; }
    public void setDocument(String document) { this.document=document; }
}
