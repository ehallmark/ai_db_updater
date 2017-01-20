package main.java;

import main.java.database.Database;
import main.java.tools.AssignmentSAXHandler;

import java.util.List;
import java.util.Map;

/**
 * Created by ehallmark on 1/19/17.
 */
public class UpdateAssigneesWithAssignmentData {

    public static void main(String[] args) {
        try {
            Map<String,List<String>> patentToAssigneeMap = AssignmentSAXHandler.load();
            if(patentToAssigneeMap==null||patentToAssigneeMap.isEmpty()) {
                // update latest assignees
                System.out.println("Starting to pull latest assignee data from uspto...");
                Database.setupLatestAssigneesFromAssignmentRecords();
            }
            patentToAssigneeMap = AssignmentSAXHandler.load();
            patentToAssigneeMap.forEach((patent,assignees)->{
                try {
                    Database.updateAssigneeForPatent(patent, assignees.toArray(new String[assignees.size()]));
                    System.out.println("Updated: "+patent);
                } catch(Exception e) {
                    e.printStackTrace();
                }
            });

        } catch(Exception sql) {
            sql.printStackTrace();
        } finally {
            try {
                Database.close();
            } catch(Exception sql) {
                sql.printStackTrace();
            }
        }

    }
}
