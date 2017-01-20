package main.java;

import main.java.database.Database;
import main.java.tools.AssignmentSAXHandler;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Created by ehallmark on 1/19/17.
 */
public class UpdateAssigneeHash {
    public static Map<String,List<String>> load() throws IOException {
        return AssignmentSAXHandler.load();
    }

    public static void main(String[] args) {
        try {
            // update latest assignees
            System.out.println("Starting to pull latest assignee data from uspto...");
            Database.setupLatestAssigneesFromAssignmentRecords();

        } catch(Exception sql) {
            sql.printStackTrace();
        }

    }
}
