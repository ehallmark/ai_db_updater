package main.java;

import main.java.database.Database;

/**
 * Created by ehallmark on 1/19/17.
 */
public class UpdatePatentsWithMaintenanceFeeData {
    public static void main(String[] args) {
        try {
            // update latest assignees
            System.out.println("Starting to update latest maintenace fee data...");
            Database.loadAndIngestMaintenanceFeeData();
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
