package main.java;

import main.java.database.Database;
import main.java.tools.AssignmentSAXHandler;

import java.io.*;
import java.util.Collection;
import java.util.Set;

/**
 * Created by Evan on 4/7/2017.
 */
public class LoadSetObjectToCSV {
    public static void main(String[] args)  throws Exception {
        Set<String> patentsSold = (Set<String>)loadObject(AssignmentSAXHandler.avayaToAssetsSoldMapFile);
        writeToCSV(patentsSold,new File("avaya_assets_sold.csv"));
        Set<String> patentsPurchased = (Set<String>)loadObject(AssignmentSAXHandler.avayaToAssetsPurchasedMapFile);
        writeToCSV(patentsPurchased,new File("avaya_assets_purchased.csv"));
    }

    public static Object loadObject(File file) throws Exception{
        ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(file)));
        Object obj = ois.readObject();
        ois.close();
        return obj;
    }

    public static void writeToCSV(Collection<String> patents, File file) throws Exception{
        BufferedWriter writer = new BufferedWriter(new FileWriter(file));
        for(String patent : patents) {
            writer.write(patent+"\n");
        }
        writer.flush();
        writer.close();
    }
}
