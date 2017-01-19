package main.java;

import main.java.database.Database;
import main.java.tools.AssignmentSAXHandler;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by ehallmark on 1/19/17.
 */
public class CleanUpAndStandardizeAssigneeNames {
    public static void run() throws SQLException {
        ResultSet rs = Database.loadPatentNumbersWithAssignees();
        try {
            while (rs.next()) {
                String patent = rs.getString(1);
                System.out.println("Cleaning assignee data for: "+patent);
                String[] assignees = (String[]) rs.getArray(2).getArray();
                List<String> cleanAssignees = new ArrayList<>(assignees.length);
                for (int i = 0; i < assignees.length; i++) {
                    String assignee = assignees[i];
                    if (assignee == null || assignee.isEmpty()) continue;
                    String cleanAssignee = AssignmentSAXHandler.cleanAssignee(assignee);
                    if (cleanAssignee != null && !cleanAssignee.isEmpty()) {
                        cleanAssignees.add(cleanAssignee);
                    }
                }
                Database.updateAssigneeForPatent(patent, cleanAssignees.toArray(new String[cleanAssignees.size()]));
            }
            Database.commit();
        } finally {
            rs.close();
        }
    }

    public static void main(String[] args) {
        try {
            run();
        } catch(Exception e) {
            e.printStackTrace();
        } finally {
            try {
                Database.close();
            } catch(Exception e2) {
                e2.printStackTrace();
            }
        }
    }
}
