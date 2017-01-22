package main.java;

import main.java.database.Database;
import main.java.tools.AssignmentSAXHandler;
import main.java.tools.InventionTitleSAXHandler;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Created by Evan on 1/22/2017.
 */
public class UpdateInventionTitleHash {
    public static Map<String,String> load() throws IOException,ClassNotFoundException {
        return InventionTitleSAXHandler.load();
    }

    public static void main(String[] args) {
        try {
            // update latest assignees
            System.out.println("Starting to pull latest invention title data from uspto...");
            int numThreads = 100;
            Database.loadAndIngestInventionTitleData(numThreads);

        } catch(Exception sql) {
            sql.printStackTrace();
        }

    }
}
