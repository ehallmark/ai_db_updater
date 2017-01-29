package main.java;

import main.java.database.Database;

/**
 * Created by Evan on 1/29/2017.
 */
public class UpdateCitationsAndPubDateData {
    public static void main(String[] args) {
        try {
            // update latest assignees
            System.out.println("Starting to pull latest invention title data from uspto...");
            int numThreads = 120;
            Database.loadAndIngestCitationAndDateData(numThreads);

        } catch(Exception sql) {
            sql.printStackTrace();
        }

    }
}
