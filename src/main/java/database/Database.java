package main.java.database;

import main.java.tools.AssignmentSAXHandler;
import main.java.tools.SAXHandler;
import main.java.tools.ZipHelper;
import net.lingala.zip4j.core.ZipFile;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 1/3/17.
 */
public class Database {
    private static final String patentDBUrl = "jdbc:postgresql://localhost/patentdb?user=postgres&password=&tcpKeepAlive=true";
    private static Connection conn;
    private static Map<String,Set<String>> patentToClassificationHash;
    private static String CPC_ZIP_FILE_NAME = "patent_grant_classifications.zip";
    private static String CPC_DESTINATION_FILE_NAME = "patent_grant_classifications_folder";
    private static String ASSIGNEE_ZIP_FILE_NAME = "patent_grant_assignees.zip";
    private static String ASSIGNEE_DESTINATION_FILE_NAME = "patent_grant_assignees_folder";
    private static String MAINT_ZIP_FILE_NAME = "patent_grant_maint_fees_folder";
    private static String MAINT_DESTINATION_FILE_NAME = "patent_grant_maint_fees_folder";

    static {
        try {
            conn = DriverManager.getConnection(patentDBUrl);
            conn.setAutoCommit(false);
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public static ResultSet loadPatentNumbersWithAssignees() throws SQLException {
        PreparedStatement ps = conn.prepareStatement("select distinct on (pub_doc_number) pub_doc_number, assignees from paragraph_tokens order by pub_doc_number");
        ps.setFetchSize(5);
        return ps.executeQuery();
    }

    public static void loadAndIngestMaintenanceFeeData() throws Exception {
        Set<String> allPatents = loadAllPatents();
        // should be one at least every other month
        // Load file from Google
        if(! (new File(MAINT_DESTINATION_FILE_NAME).exists())) {
            boolean found = false;
            LocalDate date = LocalDate.now();
            while (!found) {
                try {
                    String dateStr = String.format("%04d", date.getYear()) + "-" + String.format("%02d", date.getMonthValue()) + "-" + String.format("%02d", date.getDayOfMonth());
                    String url = "http://patents.reedtech.com/downloads/PatentClassInfo/ClassData/US_Grant_CPC_MCF_Text_" + dateStr + ".zip";
                    URL website = new URL(url);
                    System.out.println("Trying: " + website.toString());
                    ReadableByteChannel rbc = Channels.newChannel(website.openStream());
                    FileOutputStream fos = new FileOutputStream(MAINT_ZIP_FILE_NAME);
                    fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
                    fos.close();

                    ZipFile zipFile = new ZipFile(MAINT_ZIP_FILE_NAME);
                    zipFile.extractAll(MAINT_DESTINATION_FILE_NAME);

                    found = true;
                } catch (Exception e) {
                    //e.printStackTrace();
                    System.out.println("Not found");
                }
                date = date.minusDays(1);
            }
        }


        Arrays.stream(new File(MAINT_DESTINATION_FILE_NAME).listFiles()).forEach(file->{
            if(!file.getName().endsWith(".txt")) return;
            try(BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line = reader.readLine();
                while(line!=null) {
                    if(line.length() >= 50) {
                        String patNum = line.substring(0, 7);
                        if(allPatents.contains(patNum)) { // should update
                            try {
                                if (Integer.valueOf(patNum) >= 6000000) {
                                    String maintenanceCode = line.substring(46, 51).trim();
                                    if (patNum != null && maintenanceCode != null && maintenanceCode.equals("EXP.")) {
                                        System.out.println(patNum + " has expired... Updating database now.");
                                        updateIsExpiredForPatent(patNum, true);
                                    }
                                }
                            } catch (NumberFormatException nfe) {
                                // not a utility patent
                                // skip...
                            }
                        } else {
                            System.out.println(patNum+ " not expired");
                        }
                    }
                    line = reader.readLine();
                }
            } catch(Exception e) {
                e.printStackTrace();
            }
        });
    }

    public static Set<String> loadAllPatents() throws SQLException {
        // Get all pub_doc_numbers
        PreparedStatement ps = conn.prepareStatement("select distinct pub_doc_number from paragraph_tokens");
        Set<String> allPatents = new HashSet<>();
        ResultSet rs = ps.executeQuery();
        System.out.println("Loading all patent numbers from db...");
        while (rs.next()) {
            allPatents.add(rs.getString(1));
        }
        System.out.println("Finished loading...");
        return allPatents;
    }

    public static void setupLatestAssigneesFromAssignmentRecords() throws Exception {
        Set<String> allPatents = loadAllPatents();

        // go through assignment xml data and update records using assignment sax handler
        LocalDate date = LocalDate.now();
        String endDateStr = String.valueOf(date.getYear()).substring(2, 4) + String.format("%02d", date.getMonthValue()) + String.format("%02d", date.getDayOfMonth());
        Integer endDateInt = Integer.valueOf(endDateStr);

        final int backYearDataDate = 151231;
        int numFilesForBackYearData = 14;
        List<String> backYearDates = new ArrayList<>(numFilesForBackYearData);
        for(int i = 1; i <= numFilesForBackYearData; i++) {
            backYearDates.add(String.format("%06d", backYearDataDate)+"-"+String.format("%02d", i));
        }
        int lastIngestedDate = 160000;
        System.out.println("Starting with date: " + lastIngestedDate);
        System.out.println("Ending with date: " + endDateInt);
        String base_url = "https://bulkdata.uspto.gov/data2/patent/assignment/ad20";
        while (lastIngestedDate <= endDateInt||backYearDates.size()>0) {
            String finalUrlString;
            if(backYearDates.isEmpty()) {
                lastIngestedDate = lastIngestedDate + 1;
                // don't over search days
                if (lastIngestedDate % 100 > 31) {
                    lastIngestedDate = lastIngestedDate + 100 - (lastIngestedDate % 100);
                }
                if (lastIngestedDate % 10000 > 1231) {
                    lastIngestedDate = lastIngestedDate + 10000 - (lastIngestedDate % 10000);
                }
                finalUrlString=base_url + String.format("%06d", lastIngestedDate) + ".zip";
            } else {
                finalUrlString=base_url + backYearDates.remove(0) + ".zip";
            }

            try {
                try {
                    // Unzip file
                    URL website = new URL(finalUrlString);
                    System.out.println("Trying: " + website.toString());
                    ReadableByteChannel rbc = Channels.newChannel(website.openStream());
                    FileOutputStream fos = new FileOutputStream(ASSIGNEE_ZIP_FILE_NAME);
                    fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
                    fos.close();

                    ZipFile zipFile = new ZipFile(ASSIGNEE_ZIP_FILE_NAME);
                    zipFile.extractAll(ASSIGNEE_DESTINATION_FILE_NAME);

                } catch (Exception e) {
                    System.out.println("Unable to get file");
                     continue;
                }


                // Ingest data for each file
                try {

                    SAXParserFactory factory = SAXParserFactory.newInstance();
                    factory.setNamespaceAware(false);
                    factory.setValidating(false);
                    // security vulnerable
                    factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
                    factory.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
                    SAXParser saxParser = factory.newSAXParser();

                    for(File file : new File(ASSIGNEE_DESTINATION_FILE_NAME).listFiles()) {
                        if(!file.getName().endsWith(".xml")) continue;
                        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))) {
                            AssignmentSAXHandler handler = new AssignmentSAXHandler(allPatents);
                            saxParser.parse(bis, handler);
                            Database.commit();
                        } catch(Exception e) {
                            System.out.println("Error ingesting file: "+file.getName());
                            e.printStackTrace();
                        }
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }

            } finally {
                // cleanup
                // Delete zip and related folders
                File zipFile = new File(ASSIGNEE_ZIP_FILE_NAME);
                if (zipFile.exists()) zipFile.delete();

                File xmlFile = new File(ASSIGNEE_DESTINATION_FILE_NAME);
                if (xmlFile.exists()) xmlFile.delete();
            }
        }
    }

    public static void updateAssigneeForPatent(String patent, String[] latestAssignees) throws SQLException {
        PreparedStatement ps = conn.prepareStatement("update paragraph_tokens set assignees=? where pub_doc_number=?");
        ps.setArray(1, conn.createArrayOf("varchar",latestAssignees));
        ps.setString(2,patent);
        ps.executeUpdate();
    }

    public static void updateIsExpiredForPatent(String patent, boolean isExpired) throws SQLException {
        PreparedStatement ps = conn.prepareStatement("update paragraph_tokens set is_expired=? where pub_doc_number=?");
        ps.setBoolean(1, isExpired);
        ps.setString(2,patent);
        ps.executeUpdate();
    }

    public static void setupClassificationsHash() throws Exception{
        // should be one at least every other month
        // Load file from Google
        patentToClassificationHash = new HashMap<>();
        if(! (new File(CPC_DESTINATION_FILE_NAME).exists())) {
            boolean found = false;
            LocalDate date = LocalDate.now();
            while (!found) {
                try {
                    String dateStr = String.format("%04d", date.getYear()) + "-" + String.format("%02d", date.getMonthValue()) + "-" + String.format("%02d", date.getDayOfMonth());
                    String url = "http://patents.reedtech.com/downloads/PatentClassInfo/ClassData/US_Grant_CPC_MCF_Text_" + dateStr + ".zip";
                    URL website = new URL(url);
                    System.out.println("Trying: " + website.toString());
                    ReadableByteChannel rbc = Channels.newChannel(website.openStream());
                    FileOutputStream fos = new FileOutputStream(CPC_ZIP_FILE_NAME);
                    fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
                    fos.close();

                    ZipFile zipFile = new ZipFile(CPC_ZIP_FILE_NAME);
                    zipFile.extractAll(CPC_DESTINATION_FILE_NAME);

                    found = true;
                } catch (Exception e) {
                    //e.printStackTrace();
                    System.out.println("Not found");
                }
                date = date.minusDays(1);
            }
        }


        Arrays.stream(new File(CPC_DESTINATION_FILE_NAME).listFiles(File::isDirectory)[0].listFiles()).forEach(file->{
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
                                List<String> dataList = Arrays.asList(
                                        cpcSection,
                                        cpcClass,
                                        cpcSubclass,
                                        cpcMainGroup,
                                        cpcSubGroup
                                );
                                System.out.println("Data for " + patNum + ": " + String.join(", ", dataList));
                                if(patentToClassificationHash!=null) {
                                    Set<String> data = dataList.stream().collect(Collectors.toSet());
                                    if (patentToClassificationHash.containsKey(patNum)) {
                                        patentToClassificationHash.get(patNum).addAll(data);
                                    } else {
                                        patentToClassificationHash.put(patNum, data);
                                    }
                                }
                            }
                        } catch(NumberFormatException nfe) {
                            // not a utility patent
                            // skip...
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

    public static void ingestRecords(String patentNumber,Set<String> assigneeData, List<List<String>> documents) throws SQLException {
        PreparedStatement ps = conn.prepareStatement("INSERT INTO paragraph_tokens (pub_doc_number,classifications,assignees,tokens) VALUES (?,?,?,?) ON CONFLICT DO NOTHING");
        System.out.println("Ingesting Patent: "+patentNumber+", Assignee(s): "+String.join("; ",assigneeData));
        Set<String> classificationData = patentToClassificationHash.get(patentNumber);
        final Set<String> cleanAssigneeData = assigneeData==null ? Collections.emptySet() : assigneeData;
        final Set<String> cleanClassificationData = classificationData==null ? Collections.emptySet() : classificationData;
        documents.forEach(doc->{
            try {
                synchronized (ps) {
                    ps.setString(1, patentNumber);
                    ps.setArray(2, conn.createArrayOf("varchar", cleanClassificationData.toArray()));
                    ps.setArray(3, conn.createArrayOf("varchar", cleanAssigneeData.toArray()));
                    ps.setArray(4, conn.createArrayOf("varchar", doc.toArray()));
                    ps.executeUpdate();
                }
            } catch(SQLException e) {
                e.printStackTrace();
            }
        });
        ps.close();
    }

    public static synchronized void commit() throws SQLException {
        conn.commit();
    }

    public static synchronized void close() throws SQLException {
        conn.close();
    }

    public static synchronized void updateLastIngestedDate(Integer date) throws IOException {
        FileWriter fw = new FileWriter(new File("lastDateFile.txt"));
        fw.write(date.toString());
        fw.close();
    }
}
