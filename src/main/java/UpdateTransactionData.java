package main.java;

import main.java.database.Database;

/**
 * Created by ehallmark on 1/19/17.
 */
public class UpdateTransactionData {

    public static void main(String[] args) {
        try {
            // update latest assignees
            System.out.println("Starting to pull latest assignee data from uspto...");
            Database.setupLatestTransactionData();

        } catch(Exception sql) {
            sql.printStackTrace();
        }

    }
}
