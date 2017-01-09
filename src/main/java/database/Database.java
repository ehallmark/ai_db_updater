package main.java.database;

import main.java.tools.ZipHelper;
import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;

import java.io.*;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by ehallmark on 1/3/17.
 */
public class Database {
    private static final String patentDBUrl = "jdbc:postgresql://localhost/patentdb?user=postgres&password=&tcpKeepAlive=true";
    private static Connection conn;
    private static Map<String,String[]> patentToClassificationHash;
    private static String ZIP_FILE_NAME = "patent_grant_classifications.zip";
    private static String DESTINATION_FILE_NAME = "patent_grant_classifications_folder";
    private static final String SECTION_PREFIX = "SECTION - ";
    private static final String CLASS_PREFIX = "CLASS - ";
    private static final String SUBCLASS_PREFIX = "SUBCLASS - ";
    private static final String MAINGROUP_PREFIX = "MAINGROUP - ";
    private static final String SUBGROUP_PREFIX = "SUBGROUP - ";

    static {
        try {
            conn = DriverManager.getConnection(patentDBUrl);
            conn.setAutoCommit(false);
            setupClassificationsHash();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    private static void setupClassificationsHash() throws Exception{
        // should be one at least every other month
        // Load file from Google
        patentToClassificationHash = new HashMap<>();
        if(! (new File(DESTINATION_FILE_NAME).exists())) {
            boolean found = false;
            LocalDate date = LocalDate.now();
            while (!found) {
                try {
                    String dateStr = String.format("%04d", date.getYear()) + "-" + String.format("%02d", date.getMonthValue()) + "-" + String.format("%02d", date.getDayOfMonth());
                    String url = "http://patents.reedtech.com/downloads/PatentClassInfo/ClassData/US_Grant_CPC_MCF_Text_" + dateStr + ".zip";
                    URL website = new URL(url);
                    System.out.println("Trying: " + website.toString());
                    ReadableByteChannel rbc = Channels.newChannel(website.openStream());
                    FileOutputStream fos = new FileOutputStream(ZIP_FILE_NAME);
                    fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
                    fos.close();

                    ZipFile zipFile = new ZipFile(ZIP_FILE_NAME);
                    zipFile.extractAll(DESTINATION_FILE_NAME);

                    found = true;
                } catch (Exception e) {
                    //e.printStackTrace();
                    System.out.println("Not found");
                }
                date = date.minusDays(1);
            }
        }


        Arrays.stream(new File(DESTINATION_FILE_NAME).listFiles(File::isDirectory)[0].listFiles()).forEach(file->{
            try(BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line = reader.readLine();
                while(line!=null) {
                    if(line.length() >= 32) {
                        String patNum = line.substring(10, 17).trim();
                        try {
                            if(Integer.valueOf(patNum) >= 6000000) {
                                String cpcSection = line.substring(17, 18);
                                String cpcClass = cpcSection + line.substring(18, 20);
                                String cpcSubclass = cpcClass + line.substring(20, 21);
                                String cpcMainGroup = cpcSubclass + line.substring(21, 25);
                                String cpcSubGroup = cpcMainGroup + line.substring(26, 32);
                                String[] data = new String[]{
                                        cpcSection,
                                        cpcClass,
                                        cpcSubclass,
                                        cpcMainGroup,
                                        cpcSubGroup
                                };
                                System.out.println("Data for " + patNum + ": " + String.join(", ", data));
                                patentToClassificationHash.put(patNum, data);
                            }
                        } catch(NumberFormatException nfe) {

                        }
                    }
                    line = reader.readLine();
                }
            } catch(Exception e) {
                e.printStackTrace();
            }
        });

    }

    public static Integer lastIngestedDate() throws IOException {
        FileReader fr = new FileReader(new File("lastDateFile.txt"));
        BufferedReader br = new BufferedReader(fr);
        Integer lastDate = Integer.valueOf(br.readLine());
        fr.close();
        br.close();
        return lastDate;
    }

    public static void ingestRecords(String patentNumber, List<List<String>> documents) throws SQLException {
        System.out.println("Ingesting: "+patentNumber);
        PreparedStatement ps = conn.prepareStatement("INSERT INTO paragraph_tokens (pub_doc_number,classifications,tokens) VALUES (?,?,?)");
        documents.forEach(doc->{
            try {
                synchronized (ps) {
                    ps.setString(1, patentNumber);
                    ps.setArray(2, conn.createArrayOf("varchar", patentToClassificationHash.get(patentNumber)));
                    ps.setArray(3, conn.createArrayOf("varchar", doc.toArray()));
                    ps.executeUpdate();
                }
            } catch(SQLException e) {
                e.printStackTrace();
            }
        });
        ps.close();
    }

    public static void commit() throws SQLException {
        conn.commit();
    }

    public static void close() throws SQLException {
        conn.close();
    }

    public static void updateLastIngestedDate(Integer date) throws IOException {
        FileWriter fw = new FileWriter(new File("lastDateFile.txt"));
        fw.write(date.toString());
        fw.close();
    }
}
