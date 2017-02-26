package main.java.database;

import main.java.tools.*;
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
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 1/3/17.
 */
public class Database {
    private static final String patentDBUrl = "jdbc:postgresql://localhost/patentdb?user=postgres&password=&tcpKeepAlive=true";
    private static Connection conn;
    private static String CPC_ZIP_FILE_NAME = "patent_grant_classifications.zip";
    private static String CPC_DESTINATION_FILE_NAME = "patent_grant_classifications_folder";
    private static String ASSIGNEE_ZIP_FILE_NAME = "patent_grant_assignees.zip";
    private static String ASSIGNEE_DESTINATION_FILE_NAME = "patent_grant_assignees_folder";
    private static String MAINT_ZIP_FILE_NAME = "patent_grant_maint_fees.zip";
    private static String MAINT_DESTINATION_FILE_NAME = "patent_grant_maint_fees_folder";
    private static String CITATION_DESTINATION_FILE_NAME = "citation_data_folder";
    private static String CITATION_ZIP_FILE_NAME = "citation_data.zip";
    private static String TRANSACTION_DESTINATION_FILE_NAME = "transaction_data_folder/";
    private static String TRANSACTION_ZIP_FILE_NAME = "transaction_data.zip";
    private static String INVENTION_TITLE_DESTINATION_FILE_NAME = "patent_grant_invention_title_folder";
    private static String INVENTION_TITLE_ZIP_FILE_NAME = "patent_grant_invention_titles.zip";
    private static File expiredPatentsSetFile = new File("expired_patents_set.jobj");
    private static File patentToClassificationMapFile = new File("patent_to_classification_map.jobj");
    public static File patentToInventionTitleMapFile = new File("patent_to_invention_title_map.jobj");
    public static File patentToOriginalAssigneeMapFile = new File("patent_to_original_assignee_map.jobj");
    public static File patentToReferencedByMapFile = new File("patent_to_referenced_by_map.jobj");
    public static File patentToPubDateMapFile = new File("patent_to_pubdate_map_file.jobj");
    public static File patentToAppDateMapFile = new File("patent_to_appdate_map_file.jobj");
    public static File patentToRelatedDocMapFile = new File("patent_to_related_docs_map_file.jobj");
    public static File pubDateToPatentMapFile = new File("pubdate_to_patent_map.jobj");
    public static File patentToCitedPatentsMapFile = new File("patent_to_cited_patents_map.jobj");
    public static File lapsedPatentsSetFile = new File("lapsed_patents_set.jobj");
    public static File patentToPriorityDateMapFile = new File("patent_to_priority_date_map.jobj");
    static {
        try {
            conn = DriverManager.getConnection(patentDBUrl);
            conn.setAutoCommit(false);
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public static void saveObject(Object obj, File toSave) {
        System.out.println("Saving "+toSave.getName()+"...");
        try {
            ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(toSave)));
            oos.writeObject(obj);
            oos.flush();
            oos.close();
            System.out.println("Saved " + toSave.getName() + ".");
        } catch(IOException ioe) {
            ioe.printStackTrace();
        }
    }

    public static void saveExpiredPatentsSet(Set<String> expiredPatentsSet) throws IOException {
        if(expiredPatentsSet!=null) {
            ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(expiredPatentsSetFile)));
            oos.writeObject(expiredPatentsSet);
            oos.flush();
            oos.close();
        }
    }

    public static void savePatentToClassificationHash(Map<String,Set<String>> patentToClassificationHash) throws IOException {
        if(patentToClassificationHash!=null) {
            ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(patentToClassificationMapFile)));
            oos.writeObject(patentToClassificationHash);
            oos.flush();
            oos.close();
        }
    }

    public static void savePatentToInventionTitleHash(Map<String,String> patentToInventionTitleMap) throws IOException {
        if(patentToInventionTitleMap!=null) {
            ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(patentToInventionTitleMapFile)));
            oos.writeObject(patentToInventionTitleMap);
            oos.flush();
            oos.close();
        }
    }

    public static void savePatentToOriginalAssigneeHash(Map<String,List<String>> patentToOriginalAssigneeMap) throws IOException {
        if(patentToOriginalAssigneeMap!=null) {
            ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(patentToOriginalAssigneeMapFile)));
            oos.writeObject(patentToOriginalAssigneeMap);
            oos.flush();
            oos.close();
        }
    }

    public static ResultSet loadPatentNumbersWithAssignees() throws SQLException {
        PreparedStatement ps = conn.prepareStatement("select distinct on (pub_doc_number) pub_doc_number, assignees from paragraph_tokens order by pub_doc_number");
        ps.setFetchSize(5);
        return ps.executeQuery();
    }

    public static void loadAndIngestInventionTitleAndOriginalAssigneeData(int numTasks) throws Exception {
        Map<String,String> patentToInventionTitleMap = Collections.synchronizedMap(new HashMap<>());
        Map<String,List<String>> patentToOriginalAssigneeMap = Collections.synchronizedMap(new HashMap<>());
        List<RecursiveAction> tasks = new ArrayList<>();
        Integer lastIngestedDate = 70000;
        LocalDate date = LocalDate.now();
        String endDateStr = String.valueOf(date.getYear()).substring(2,4)+String.format("%02d",date.getMonthValue())+String.format("%02d",date.getDayOfMonth());
        Integer endDateInt = Integer.valueOf(endDateStr);

        System.out.println("Starting with date: "+lastIngestedDate);
        System.out.println("Ending with date: "+endDateInt);
        String base_url = "http://storage.googleapis.com/patents/grant_full_text";
        String secondary_url = "https://bulkdata.uspto.gov/data2/patent/grant/redbook/fulltext";
        while(lastIngestedDate<=endDateInt) {
            // Commit results to DB and update last ingest table
            lastIngestedDate = lastIngestedDate+1;
            // don't over search days
            if(lastIngestedDate%100 > 31) {
                lastIngestedDate = lastIngestedDate+100 - (lastIngestedDate%100);
            }
            if(lastIngestedDate%10000 > 1231) {
                lastIngestedDate = lastIngestedDate+10000 - (lastIngestedDate%10000);
            }

            final int finalLastIngestedDate=lastIngestedDate;


            // Load file from Google
            RecursiveAction action = new RecursiveAction() {
                @Override
                protected void compute() {
                    try {
                        try {
                            String dateStr = String.format("%06d", finalLastIngestedDate);
                            URL website = new URL(base_url + "/20" + dateStr.substring(0, 2) + "/ipg" + String.format("%06d", finalLastIngestedDate) + ".zip");
                            System.out.println("Trying: " + website.toString());
                            ReadableByteChannel rbc = Channels.newChannel(website.openStream());
                            FileOutputStream fos = new FileOutputStream(INVENTION_TITLE_ZIP_FILE_NAME + finalLastIngestedDate);
                            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
                            fos.close();

                            try {
                                // Unzip file
                                BufferedInputStream bis = new BufferedInputStream(new FileInputStream(new File(INVENTION_TITLE_ZIP_FILE_NAME + finalLastIngestedDate)));
                                BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(new File(INVENTION_TITLE_DESTINATION_FILE_NAME + finalLastIngestedDate)));
                                ZipHelper.unzip(bis, bos);
                                bis.close();
                                bos.close();

                                System.out.println("FOUND GOOGLE FILE!");
                            } catch (Exception e) {
                                System.out.println("Unable to unzip google file");
                            }
                        } catch (Exception e) {
                            // try non Google
                            try {
                                String dateStr = String.format("%06d", finalLastIngestedDate);
                                URL website = new URL(secondary_url + "/20" + dateStr.substring(0, 2) + "/ipg" + String.format("%06d", finalLastIngestedDate) + ".zip");
                                System.out.println("Trying: " + website.toString());
                                ReadableByteChannel rbc = Channels.newChannel(website.openStream());
                                FileOutputStream fos = new FileOutputStream(INVENTION_TITLE_ZIP_FILE_NAME + finalLastIngestedDate);
                                fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
                                fos.close();
                            } catch (Exception e2) {
                                System.out.println("Not found");
                                return;
                            }

                            try {
                                // Unzip file
                                BufferedInputStream bis = new BufferedInputStream(new FileInputStream(new File(INVENTION_TITLE_ZIP_FILE_NAME + finalLastIngestedDate)));
                                BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(new File(INVENTION_TITLE_DESTINATION_FILE_NAME + finalLastIngestedDate)));
                                ZipHelper.unzip(bis, bos);
                                bis.close();
                                bos.close();
                                System.out.println("FOUND OTHER FILE!");

                            } catch (Exception e2) {
                                System.out.println("Unable to unzip file");
                                return;
                            }
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

                            InventionTitleSAXHandler handler = new InventionTitleSAXHandler();


                            FileReader fr = new FileReader(new File(INVENTION_TITLE_DESTINATION_FILE_NAME + finalLastIngestedDate));
                            BufferedReader br = new BufferedReader(fr);
                            String line;
                            boolean firstLine = true;
                            List<String> lines = new ArrayList<>();
                            while ((line = br.readLine()) != null) {
                                if (line.contains("<?xml") && !firstLine) {
                                    // stop
                                    saxParser.parse(new ByteArrayInputStream(String.join("", lines).getBytes()), handler);
                                    String patNum = handler.getPatentNumber();
                                    try {
                                        if (Integer.valueOf(patNum) >= 7000000) {
                                            if (patNum != null&&handler.getInventionTitle()!=null) {
                                                System.out.println(patNum + " has title: " + handler.getInventionTitle());
                                                patentToInventionTitleMap.put(handler.getPatentNumber(), handler.getInventionTitle());
                                            }
                                            if(patNum!=null && !handler.getOriginalAssignees().isEmpty()) {
                                                List<String> cloneAssignees = new ArrayList<>(handler.getOriginalAssignees().size());
                                                cloneAssignees.addAll(handler.getOriginalAssignees());
                                                patentToOriginalAssigneeMap.put(handler.getPatentNumber(),cloneAssignees);
                                            }
                                        }
                                    } catch (Exception nfe) {
                                        // not a utility patent
                                        // skip...
                                    }

                                    lines.clear();
                                    handler.reset();
                                }
                                if (firstLine) firstLine = false;
                                lines.add(line);
                            }
                            br.close();
                            fr.close();

                            // get the last one
                            if (!lines.isEmpty()) {
                                saxParser.parse(new ByteArrayInputStream(String.join("", lines).getBytes()), handler);

                                String patNum = handler.getPatentNumber();
                                try {
                                    if (Integer.valueOf(patNum) >= 7000000) {
                                        if (patNum != null&&handler.getInventionTitle()!=null) {
                                            System.out.println(patNum + " has title: " + handler.getInventionTitle());
                                            patentToInventionTitleMap.put(handler.getPatentNumber(), handler.getInventionTitle());
                                        }
                                        if(patNum!=null && !handler.getOriginalAssignees().isEmpty()) {
                                            List<String> cloneAssignees = new ArrayList<>(handler.getOriginalAssignees().size());
                                            cloneAssignees.addAll(handler.getOriginalAssignees());
                                            patentToOriginalAssigneeMap.put(handler.getPatentNumber(),cloneAssignees);
                                        }
                                    }
                                } catch (Exception nfe) {
                                    // not a utility patent
                                    // skip...
                                }

                                lines.clear();
                                handler.reset();
                            }

                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                    } finally {
                        // cleanup
                        // Delete zip and related folders
                        File zipFile = new File(INVENTION_TITLE_ZIP_FILE_NAME + finalLastIngestedDate);
                        if (zipFile.exists()) zipFile.delete();

                        File xmlFile = new File(INVENTION_TITLE_DESTINATION_FILE_NAME + finalLastIngestedDate);
                        if (xmlFile.exists()) xmlFile.delete();
                    }

                }
            };
            action.fork();
            tasks.add(action);

            while(tasks.size()>numTasks) {
                tasks.remove(0).join();
            }
        }

        while(!tasks.isEmpty()) {
            tasks.remove(0).join();
        }

        savePatentToInventionTitleHash(patentToInventionTitleMap);
        savePatentToOriginalAssigneeHash(patentToOriginalAssigneeMap);
    }

    public static void loadAndIngestCitationAndDateData(int numTasks) throws Exception {
        Map<String,LocalDate> patentToPubDateMap = Collections.synchronizedMap(new HashMap<>());
        Map<String,LocalDate> patentToAppDateMap = Collections.synchronizedMap(new HashMap<>());
        Map<String,Set<String>> patentToCitedPatentsMap = Collections.synchronizedMap(new HashMap<>());
        Map<String,Set<String>> patentToRelatedDocMap = Collections.synchronizedMap(new HashMap());
        Map<String,LocalDate> patentToPriorityDateMap = Collections.synchronizedMap(new HashMap<>());
        Set<String> lapsedPatentsSet = Collections.synchronizedSet(new HashSet<>());
        List<RecursiveAction> tasks = new ArrayList<>();
        Integer lastIngestedDate = 70000;
        LocalDate date = LocalDate.now();
        String endDateStr = String.valueOf(date.getYear()).substring(2,4)+String.format("%02d",date.getMonthValue())+String.format("%02d",date.getDayOfMonth());
        Integer endDateInt = Integer.valueOf(endDateStr);
        Set<String> allPatents = Collections.synchronizedSet(new HashSet<>());

        System.out.println("Starting with date: "+lastIngestedDate);
        System.out.println("Ending with date: "+endDateInt);
        String base_url = "http://storage.googleapis.com/patents/grant_full_text";
        String secondary_url = "https://bulkdata.uspto.gov/data2/patent/grant/redbook/fulltext";
        while(lastIngestedDate<=endDateInt) {
            // Commit results to DB and update last ingest table
            lastIngestedDate = lastIngestedDate+1;
            // don't over search days
            if(lastIngestedDate%100 > 31) {
                lastIngestedDate = lastIngestedDate+100 - (lastIngestedDate%100);
            }
            if(lastIngestedDate%10000 > 1231) {
                lastIngestedDate = lastIngestedDate+10000 - (lastIngestedDate%10000);
            }

            final int finalLastIngestedDate=lastIngestedDate;

            // Load file from Google
            RecursiveAction action = new RecursiveAction() {
                @Override
                protected void compute() {
                    try {
                        try {
                            String dateStr = String.format("%06d", finalLastIngestedDate);
                            URL website = new URL(base_url + "/20" + dateStr.substring(0, 2) + "/ipg" + String.format("%06d", finalLastIngestedDate) + ".zip");
                            System.out.println("Trying: " + website.toString());
                            ReadableByteChannel rbc = Channels.newChannel(website.openStream());
                            FileOutputStream fos = new FileOutputStream(CITATION_ZIP_FILE_NAME + finalLastIngestedDate);
                            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
                            fos.close();

                            try {
                                // Unzip file
                                BufferedInputStream bis = new BufferedInputStream(new FileInputStream(new File(CITATION_ZIP_FILE_NAME + finalLastIngestedDate)));
                                BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(new File(CITATION_DESTINATION_FILE_NAME + finalLastIngestedDate)));
                                ZipHelper.unzip(bis, bos);
                                bis.close();
                                bos.close();

                                System.out.println("FOUND GOOGLE FILE!");
                            } catch (Exception e) {
                                System.out.println("Unable to unzip google file");
                            }
                        } catch (Exception e) {
                            // try non Google
                            try {
                                String dateStr = String.format("%06d", finalLastIngestedDate);
                                URL website = new URL(secondary_url + "/20" + dateStr.substring(0, 2) + "/ipg" + String.format("%06d", finalLastIngestedDate) + ".zip");
                                System.out.println("Trying: " + website.toString());
                                ReadableByteChannel rbc = Channels.newChannel(website.openStream());
                                FileOutputStream fos = new FileOutputStream(CITATION_ZIP_FILE_NAME + finalLastIngestedDate);
                                fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
                                fos.close();
                            } catch (Exception e2) {
                                System.out.println("Not found");
                                return;
                            }

                            try {
                                // Unzip file
                                BufferedInputStream bis = new BufferedInputStream(new FileInputStream(new File(CITATION_ZIP_FILE_NAME + finalLastIngestedDate)));
                                BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(new File(CITATION_DESTINATION_FILE_NAME + finalLastIngestedDate)));
                                ZipHelper.unzip(bis, bos);
                                bis.close();
                                bos.close();
                                System.out.println("FOUND OTHER FILE!");

                            } catch (Exception e2) {
                                System.out.println("Unable to unzip file");
                                return;
                            }
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

                            CitationSAXHandler handler = new CitationSAXHandler();


                            FileReader fr = new FileReader(new File(CITATION_DESTINATION_FILE_NAME + finalLastIngestedDate));
                            BufferedReader br = new BufferedReader(fr);
                            String line;
                            boolean firstLine = true;
                            List<String> lines = new ArrayList<>();
                            while ((line = br.readLine()) != null) {
                                if (line.contains("<?xml") && !firstLine) {
                                    // stop
                                    saxParser.parse(new ByteArrayInputStream(String.join("", lines).getBytes()), handler);
                                    String patNum = handler.getPatentNumber();
                                    try {
                                        if (patNum!=null&&Integer.valueOf(patNum) >= 7000000) {
                                            allPatents.add(patNum);
                                            if (handler.getPubDate()!=null) {
                                                patentToPubDateMap.put(patNum, handler.getPubDate());
                                            }
                                            if(handler.getAppDate()!=null) {
                                                patentToAppDateMap.put(patNum, handler.getAppDate());
                                            }
                                            if(handler.getPriorityDate()!=null) {
                                                patentToPriorityDateMap.put(patNum,handler.getPriorityDate());
                                                LocalDate date = handler.getPriorityDate();
                                                if(date.plusYears(20).isBefore(LocalDate.now())) {
                                                    System.out.println(patNum + " isExpired!");
                                                    lapsedPatentsSet.add(patNum);
                                                }
                                            }
                                            Set<String> cited = handler.getCitedDocuments();
                                            if(!cited.isEmpty()) {
                                                //System.out.println(patNum+" has "+cited.size()+" cited documents");
                                                patentToCitedPatentsMap.put(patNum, cited);
                                            }
                                            Set<String> related = handler.getRelatedDocuments();
                                            if(!related.isEmpty()) {
                                                //System.out.println(patNum+ " has "+related.size()+" related documents");
                                                patentToRelatedDocMap.put(patNum,cited);
                                            }
                                        }
                                    } catch (Exception nfe) {
                                        // not a utility patent
                                        // skip...
                                    }

                                    lines.clear();
                                    handler.reset();
                                }
                                if (firstLine) firstLine = false;
                                lines.add(line);
                            }
                            br.close();
                            fr.close();

                            // get the last one
                            if (!lines.isEmpty()) {
                                saxParser.parse(new ByteArrayInputStream(String.join("", lines).getBytes()), handler);

                                String patNum = handler.getPatentNumber();
                                try {
                                    if (patNum!=null&&Integer.valueOf(patNum) >= 7000000) {
                                        if (handler.getPubDate()!=null) {
                                            System.out.println(patNum + " has pubDate: " + handler.getPubDate());
                                            patentToPubDateMap.put(patNum, handler.getPubDate());
                                        }
                                        if(handler.getAppDate()!=null) {
                                            patentToAppDateMap.put(patNum, handler.getAppDate());
                                        }
                                        if(handler.getPriorityDate()!=null) {
                                            patentToPriorityDateMap.put(patNum,handler.getPriorityDate());
                                            LocalDate date = handler.getPriorityDate();
                                            if(date.plusYears(20).isBefore(LocalDate.now())) {
                                                lapsedPatentsSet.add(patNum);
                                            }
                                        }
                                        Set<String> cited = handler.getCitedDocuments();
                                        if(!cited.isEmpty()) {
                                            System.out.println(patNum+" has "+cited.size()+" cited documents");
                                            patentToCitedPatentsMap.put(patNum, cited);
                                        }
                                        Set<String> related = handler.getRelatedDocuments();
                                        if(!related.isEmpty()) {
                                            System.out.println(patNum+ " has "+related.size()+" related documents");
                                            patentToRelatedDocMap.put(patNum,cited);
                                        }
                                    }
                                } catch (Exception nfe) {
                                    // not a utility patent
                                    // skip...
                                }

                                lines.clear();
                                handler.reset();
                            }

                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                    } finally {
                        // cleanup
                        // Delete zip and related folders
                        File zipFile = new File(CITATION_ZIP_FILE_NAME + finalLastIngestedDate);
                        if (zipFile.exists()) zipFile.delete();

                        File xmlFile = new File(CITATION_DESTINATION_FILE_NAME + finalLastIngestedDate);
                        if (xmlFile.exists()) xmlFile.delete();
                    }

                }
            };
            action.fork();
            tasks.add(action);

            while(tasks.size()>numTasks) {
                tasks.remove(0).join();
            }
        }

        while(!tasks.isEmpty()) {
            tasks.remove(0).join();
        }

        // invert patent map to get referenced by instead of referencing
        Map<String,Set<String>> patentToReferencedByMap = Collections.synchronizedMap(new HashMap<>());
        patentToCitedPatentsMap.forEach((patent,citedSet)->{
            if(allPatents.contains(patent)) {
                citedSet.forEach(cited->{
                    if(allPatents.contains(cited)) {
                        if(patentToReferencedByMap.containsKey(cited)) {
                            patentToReferencedByMap.get(cited).add(patent);
                        } else {
                            Set<String> set = new HashSet<>();
                            set.add(patent);
                            patentToReferencedByMap.put(cited,set);
                        }
                    }
                });
            }
        });

        // date to patent map
        Map<LocalDate,Set<String>> pubDateToPatentMap = Collections.synchronizedMap(new HashMap<>());
        patentToPubDateMap.forEach((patent,pubDate)->{
            if(pubDateToPatentMap.containsKey(pubDate)) {
                pubDateToPatentMap.get(pubDate).add(patent);
            } else {
                Set<String> set = new HashSet<>();
                set.add(patent);
                pubDateToPatentMap.put(pubDate,set);
            }
        });

        saveObject(patentToCitedPatentsMap,patentToCitedPatentsMapFile);
        saveObject(pubDateToPatentMap,pubDateToPatentMapFile);
        saveObject(patentToRelatedDocMap,patentToRelatedDocMapFile);
        saveObject(patentToReferencedByMap,patentToReferencedByMapFile);
        saveObject(patentToAppDateMap,patentToAppDateMapFile);
        saveObject(patentToPubDateMap,patentToPubDateMapFile);
        saveObject(patentToPriorityDateMap,patentToPriorityDateMapFile);
        saveObject(lapsedPatentsSet,lapsedPatentsSetFile);
    }

    public static void loadAndIngestMaintenanceFeeData() throws Exception {
        // should be one at least every other month
        // Load file from Google
        Map<String,Integer> patentToMaintenanceFeeReminderCount = new HashMap<>();
        Set<String> expiredPatentsSet = new HashSet<>();
        Set<String> largeEntityPatents = new HashSet<>();
        Set<String> smallEntityPatents = new HashSet<>();
        Set<String> microEntityPatents = new HashSet<>();
        if (!(new File(MAINT_DESTINATION_FILE_NAME).exists())) {
            try {
                String url = "https://bulkdata.uspto.gov/data2/patent/maintenancefee/MaintFeeEvents.zip";
                URL website = new URL(url);
                System.out.println("Trying: " + website.toString());
                ReadableByteChannel rbc = Channels.newChannel(website.openStream());
                FileOutputStream fos = new FileOutputStream(MAINT_ZIP_FILE_NAME);
                fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
                fos.close();

                ZipFile zipFile = new ZipFile(MAINT_ZIP_FILE_NAME);
                zipFile.extractAll(MAINT_DESTINATION_FILE_NAME);

            } catch (Exception e) {
                //e.printStackTrace();
                System.out.println("Not found");
            }
        }
        Arrays.stream(new File(MAINT_DESTINATION_FILE_NAME).listFiles()).forEach(file -> {
            if (!file.getName().endsWith(".txt")) return;
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line = reader.readLine();
                while (line != null) {
                    if (line.length() >= 50) {
                        String patNum = line.substring(0, 7);
                        try {
                            if (Integer.valueOf(patNum) >= 7000000) {
                                String maintenanceCode = line.substring(46, 51).trim();
                                if (patNum != null && maintenanceCode != null ) {
                                    if(maintenanceCode.equals("EXP.")) {
                                        System.out.println(patNum + " has expired... Updating database now.");
                                        expiredPatentsSet.add(patNum);
                                    } else if (maintenanceCode.equals("EXPX")) {
                                        // reinstated
                                        System.out.println(patNum+" was reinstated!");
                                        if(expiredPatentsSet.contains(patNum)) {
                                            expiredPatentsSet.remove(patNum);
                                        }
                                    } else if (maintenanceCode.equals("REM.")) {
                                        // reminder
                                        if(patentToMaintenanceFeeReminderCount.containsKey(patNum)) {
                                            patentToMaintenanceFeeReminderCount.put(patNum,patentToMaintenanceFeeReminderCount.get(patNum)+1);
                                        } else {
                                            patentToMaintenanceFeeReminderCount.put(patNum,1);
                                        }
                                    } else if (maintenanceCode.startsWith("M2")||maintenanceCode.startsWith("SM")||maintenanceCode.equals("LTOS")||maintenanceCode.equals("MTOS")) {
                                        smallEntityPatents.add(patNum);
                                        if(largeEntityPatents.contains(patNum)) largeEntityPatents.remove(patNum);
                                        if(microEntityPatents.contains(patNum)) microEntityPatents.remove(patNum);
                                    } else if (maintenanceCode.startsWith("M1")||maintenanceCode.startsWith("LSM")) {
                                        largeEntityPatents.add(patNum);
                                        if(smallEntityPatents.contains(patNum)) smallEntityPatents.remove(patNum);
                                        if(microEntityPatents.contains(patNum)) microEntityPatents.remove(patNum);
                                    } else if(maintenanceCode.startsWith("M3")||maintenanceCode.equals("STOM")) {
                                        microEntityPatents.add(patNum);
                                        if(largeEntityPatents.contains(patNum)) largeEntityPatents.remove(patNum);
                                        if(smallEntityPatents.contains(patNum)) smallEntityPatents.remove(patNum);
                                    }
                                }
                            }
                        } catch (NumberFormatException nfe) {
                            // not a utility patent
                            // skip...
                        }
                    }
                    line = reader.readLine();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        saveExpiredPatentsSet(expiredPatentsSet);

        File largeEntityPatentFile = new File("large_entity_patents_set.jobj");
        Database.saveObject(largeEntityPatents,largeEntityPatentFile);

        File smallEntityPatentFile = new File("small_entity_patents_set.jobj");
        Database.saveObject(smallEntityPatents,smallEntityPatentFile);

        File microEntityPatentFile = new File("micro_entity_patents_set.jobj");
        Database.saveObject(microEntityPatents,microEntityPatentFile);

        File patentToFeeReminderMapFile = new File("patent_to_fee_reminder_count_map.jobj");
        Database.saveObject(patentToMaintenanceFeeReminderCount,patentToFeeReminderMapFile);


    }

    public static Set<String> loadAllPatents() throws SQLException {
        System.out.println("Loading all patent numbers from db...");
        // Get all pub_doc_numbers
        PreparedStatement ps = conn.prepareStatement("select distinct pub_doc_number from paragraph_tokens");
        //ps.setFetchSize(100);
        Set<String> allPatents = new HashSet<>();
        ResultSet rs = ps.executeQuery();
        AtomicInteger cnt = new AtomicInteger(0);
        while (rs.next()) {
            allPatents.add(rs.getString(1));
        }
        System.out.println("Finished loading...");
        return allPatents;
    }

    public static void setupLatestAssigneesFromAssignmentRecords() throws Exception {
        // go through assignment xml data and update records using assignment sax handler
        LocalDate date = LocalDate.now();
        String endDateStr = String.valueOf(date.getYear()).substring(2, 4) + String.format("%02d", date.getMonthValue()) + String.format("%02d", date.getDayOfMonth());
        Integer endDateInt = Integer.valueOf(endDateStr);

        // INITIAL OPTIONS TO SET
        final int backYearDataDate = 151231;
        final int numFilesForBackYearData = 14;
        final int backYearDataStartNum = 1;
        final int startDateNum = 160101;

        List<String> backYearDates = new ArrayList<>(numFilesForBackYearData);
        for(int i = backYearDataStartNum; i < backYearDataStartNum + numFilesForBackYearData; i++) {
           backYearDates.add(String.format("%06d", backYearDataDate)+"-"+String.format("%02d", i));
        }

        int lastIngestedDate = startDateNum;
        System.out.println("Starting with date: " + lastIngestedDate);
        System.out.println("Ending with date: " + endDateInt);
        String base_url = "http://patents.reedtech.com/downloads/PatentAssignmentText/---/ad20";
        while (lastIngestedDate <= endDateInt||backYearDates.size()>0) {
            String finalUrlString;
            { //test!!!
                finalUrlString = base_url + String.format("%06d", lastIngestedDate) + ".zip";
                finalUrlString = finalUrlString.replace("---", "20" + String.format("%02d", lastIngestedDate / 10000));
                System.out.println(finalUrlString);
            }// end test!!
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
                finalUrlString=finalUrlString.replace("---","20"+String.format("%02d",lastIngestedDate/10000));
            } else {
                finalUrlString=base_url + backYearDates.remove(0) + ".zip";
                finalUrlString=finalUrlString.replaceFirst("---","1980-2015");
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
                        if(!file.getName().endsWith(".xml")) {
                            file.delete();
                            continue;
                        }
                        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))) {
                            AssignmentSAXHandler handler = new AssignmentSAXHandler();
                            saxParser.parse(bis, handler);
                            //Database.commit();
                        } catch(Exception e) {
                            System.out.println("Error ingesting file: "+file.getName());
                            e.printStackTrace();
                        } finally {
                            file.delete();
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
        try {
            AssignmentSAXHandler.save();
        } catch(Exception e) {
            System.out.println("Unable to save assignee file...");
        }
    }

    public static void setupLatestTransactionData() throws Exception {
        // go through assignment xml data and update records using assignment sax handler
        LocalDate date = LocalDate.now();
        String endDateStr = String.valueOf(date.getYear()).substring(2, 4) + String.format("%02d", date.getMonthValue()) + String.format("%02d", date.getDayOfMonth());
        Integer endDateInt = Integer.valueOf(endDateStr);

        // INITIAL OPTIONS TO SET
        final int backYearDataDate = 151231;
        final int numFilesForBackYearData = 14;
        final int backYearDataStartNum = 1;
        final int startDateNum = 160000;

        List<String> backYearDates = new ArrayList<>(numFilesForBackYearData);
        for(int i = backYearDataStartNum; i < backYearDataStartNum + numFilesForBackYearData; i++) {
            backYearDates.add(String.format("%06d", backYearDataDate)+"-"+String.format("%02d", i));
        }
        int lastIngestedDate = startDateNum;
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
                    FileOutputStream fos = new FileOutputStream(TRANSACTION_ZIP_FILE_NAME);
                    fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
                    fos.close();

                    ZipFile zipFile = new ZipFile(TRANSACTION_ZIP_FILE_NAME);
                    zipFile.extractAll(TRANSACTION_DESTINATION_FILE_NAME);

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

                    for(File file : new File(TRANSACTION_DESTINATION_FILE_NAME).listFiles()) {
                        if(!file.getName().endsWith(".xml")) {
                            file.delete();
                            continue;
                        }
                        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))) {
                            TransactionSAXHandler handler = new TransactionSAXHandler();
                            saxParser.parse(bis, handler);
                            //Database.commit();
                        } catch(Exception e) {
                            System.out.println("Error ingesting file: "+file.getName());
                            e.printStackTrace();
                        } finally {
                            file.delete();
                        }
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }

            } finally {
                // cleanup
                // Delete zip and related folders
                File zipFile = new File(TRANSACTION_ZIP_FILE_NAME);
                if (zipFile.exists()) zipFile.delete();

                File xmlFile = new File(TRANSACTION_DESTINATION_FILE_NAME);
                if (xmlFile.exists()) xmlFile.delete();

            }
        }
        try {
            TransactionSAXHandler.save();
        } catch(Exception e) {
            System.out.println("Unable to save assignee file...");
        }
    }

    public static Map<String,Set<String>> loadPatentToClassificationMap() throws IOException,ClassNotFoundException {
        Map<String,Set<String>> map;
        ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(patentToClassificationMapFile)));
        map = (Map<String,Set<String>>)ois.readObject();
        ois.close();
        return map;
    }

    public static Set<String> loadExpiredPatentsSet() throws IOException, ClassNotFoundException {
        Set<String> set;
        ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(expiredPatentsSetFile)));
        set = (Set<String>)ois.readObject();
        ois.close();
        return set;
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
        Map<String,Set<String>> patentToClassificationHash = new HashMap<>();
        if (!(new File(CPC_DESTINATION_FILE_NAME).exists())) {
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

        Arrays.stream(new File(CPC_DESTINATION_FILE_NAME).listFiles(File::isDirectory)[0].listFiles()).forEach(file -> {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line = reader.readLine();
                while (line != null) {
                    if (line.length() >= 32) {
                        String patNum = line.substring(10, 17).trim();
                        try {
                            if (Integer.valueOf(patNum) >= 6000000) {
                                String cpcSection = line.substring(17, 18);
                                String cpcClass = cpcSection + line.substring(18, 20);
                                String cpcSubclass = cpcClass + line.substring(20, 21);
                                String cpcMainGroup = cpcSubclass + line.substring(21, 25);
                                String cpcSubGroup = cpcMainGroup + line.substring(26, 32);
                                System.out.println("Data for " + patNum + ": " + String.join(", ", cpcSubGroup));
                                if (patentToClassificationHash != null) {
                                    if (patentToClassificationHash.containsKey(patNum)) {
                                        patentToClassificationHash.get(patNum).add(cpcSubGroup);
                                    } else {
                                        Set<String> data = new HashSet<>();
                                        data.add(cpcSubGroup);
                                        patentToClassificationHash.put(patNum, data);
                                    }
                                }
                            }
                        } catch (NumberFormatException nfe) {
                            // not a utility patent
                            // skip...
                        }
                    }
                    line = reader.readLine();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        savePatentToClassificationHash(patentToClassificationHash);

    }

    public static Integer lastIngestedDate() throws IOException {
        FileReader fr = new FileReader(new File("lastDateFile.txt"));
        BufferedReader br = new BufferedReader(fr);
        Integer lastDate = Integer.valueOf(br.readLine());
        fr.close();
        br.close();
        return lastDate;
    }

    public static void ingestRecords(String patentNumber, Collection<String> assigneeData, Collection<String> classData, boolean isExpired, List<List<String>> documents) throws SQLException {
        if(patentNumber==null)return;
        PreparedStatement ps = conn.prepareStatement("INSERT INTO paragraph_tokens (pub_doc_number,assignees,classifications,is_expired,tokens) VALUES (?,?,?,?,?) ON CONFLICT DO NOTHING");
        System.out.println("Ingesting Patent: "+patentNumber+", Assignee(s): "+String.join("; ",assigneeData));
        final Collection<String> cleanAssigneeData = assigneeData==null ? Collections.emptySet() : assigneeData;
        final Collection<String> cleanClassificationData = classData==null ? Collections.emptySet() : classData;
        documents.forEach(doc->{
            try {
                synchronized (ps) {
                    ps.setString(1, patentNumber);
                    ps.setArray(2, conn.createArrayOf("varchar", cleanAssigneeData.toArray()));
                    ps.setArray(3, conn.createArrayOf("varchar", cleanClassificationData.toArray()));
                    ps.setBoolean(4,isExpired);
                    ps.setArray(5, conn.createArrayOf("varchar", doc.toArray()));
                    ps.executeUpdate();
                }
            } catch(Exception e) {
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
