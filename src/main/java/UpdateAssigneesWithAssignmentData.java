package main.java;

import main.java.database.Database;

/**
 * Created by ehallmark on 1/19/17.
 */
public class UpdateAssigneesWithAssignmentData {

    public static void main(String[] args) {
        try {
            // update latest assignees
            System.out.println("Starting to update latest assignees...");
            Database.setupLatestAssigneesFromAssignmentRecords();
            Database.commit();
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
