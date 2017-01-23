package main.java;

import main.java.database.Database;
import main.java.tools.SAXHandler;
import main.java.tools.ZipHelper;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.RecursiveAction;

/**
 * Created by ehallmark on 1/3/17.
 */
public class IngestGoogleXML {
    private static final String ZIP_FILE_NAME = "tmp_uspto_zip_file.zip";
    private static final String DESTINATION_FILE_NAME = "uspto_xml_file.xml";

    public static void main(String[] args) {
        try {
            Set<String> expiredPatents = UpdateExpiredPatentsSet.load();
            if(expiredPatents==null) throw new RuntimeException("No expiredPatents found");
            Map<String,List<String>> patentToAssigneeMap = UpdateLatestAssigneeHash.load();
            if(patentToAssigneeMap==null) throw new RuntimeException("No patentToAssigneeMap found");
            Map<String,Set<String>> patentToClassificationMap = UpdateClassificationHash.load();
            if(patentToClassificationMap==null) throw new RuntimeException("No patentToClassificationMap found");
            final int numTasks = 50;
            List<RecursiveAction> tasks = new ArrayList<>(numTasks);
            // Get last ingested date
            Integer lastIngestedDate = Database.lastIngestedDate();
            LocalDate date = LocalDate.now();
            String endDateStr = String.valueOf(date.getYear()).substring(2,4)+String.format("%02d",date.getMonthValue())+String.format("%02d",date.getDayOfMonth());
            Integer endDateInt = Integer.valueOf(endDateStr);

            System.out.println("Starting with date: "+lastIngestedDate);
            System.out.println("Ending with date: "+endDateInt);
            String base_url = "http://storage.googleapis.com/patents/grant_full_text";
            String secondary_url = "https://bulkdata.uspto.gov/data2/patent/grant/redbook/fulltext";
            while(lastIngestedDate<=endDateInt) {
                // Commit results to DB and update last ingest table
                Database.updateLastIngestedDate(lastIngestedDate);
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
                                FileOutputStream fos = new FileOutputStream(ZIP_FILE_NAME + finalLastIngestedDate);
                                fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
                                fos.close();

                                try {
                                    // Unzip file
                                    BufferedInputStream bis = new BufferedInputStream(new FileInputStream(new File(ZIP_FILE_NAME + finalLastIngestedDate)));
                                    BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(new File(DESTINATION_FILE_NAME + finalLastIngestedDate)));
                                    ZipHelper.unzip(bis, bos);
                                    bis.close();
                                    bos.close();
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
                                    FileOutputStream fos = new FileOutputStream(ZIP_FILE_NAME + finalLastIngestedDate);
                                    fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
                                    fos.close();
                                } catch (Exception e2) {
                                    System.out.println("Not found");
                                    return;
                                }

                                try {
                                    // Unzip file
                                    BufferedInputStream bis = new BufferedInputStream(new FileInputStream(new File(ZIP_FILE_NAME + finalLastIngestedDate)));
                                    BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(new File(DESTINATION_FILE_NAME + finalLastIngestedDate)));
                                    ZipHelper.unzip(bis, bos);
                                    bis.close();
                                    bos.close();
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

                                SAXHandler handler = new SAXHandler();


                                FileReader fr = new FileReader(new File(DESTINATION_FILE_NAME + finalLastIngestedDate));
                                BufferedReader br = new BufferedReader(fr);
                                String line;
                                boolean firstLine = true;
                                List<String> lines = new ArrayList<>();
                                while ((line = br.readLine()) != null) {
                                    if (line.contains("<?xml") && !firstLine) {
                                        // stop
                                        saxParser.parse(new ByteArrayInputStream(String.join("", lines).getBytes()), handler);

                                        if (handler.getPatentNumber() != null && !handler.getFullDocuments().isEmpty()) {
                                            boolean isExpired = expiredPatents.contains(handler.getPatentNumber());
                                            Set<String> classData = patentToClassificationMap.get(handler.getPatentNumber());
                                            Collection<String> assigneeData = patentToAssigneeMap.get(handler.getPatentNumber());
                                            if(assigneeData==null) {
                                                // default to original assignee
                                                assigneeData=handler.getAssignees();
                                            }
                                            Database.ingestRecords(handler.getPatentNumber(), assigneeData, classData, isExpired, handler.getFullDocuments());
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

                                    if (handler.getPatentNumber() != null && !handler.getFullDocuments().isEmpty()) {
                                        boolean isExpired = expiredPatents.contains(handler.getPatentNumber());
                                        Set<String> classData = patentToClassificationMap.get(handler.getPatentNumber());
                                        Collection<String> assigneeData = patentToAssigneeMap.get(handler.getPatentNumber());
                                        if(assigneeData==null) {
                                            // default to original assignee
                                            assigneeData=handler.getAssignees();
                                        }
                                        Database.ingestRecords(handler.getPatentNumber(), assigneeData, classData, isExpired, handler.getFullDocuments());
                                    }

                                    lines.clear();
                                    handler.reset();
                                }

                                // Commit results to DB and update last ingest table
                                //Database.updateLastIngestedDate(finalLastIngestedDate);

                            } catch (Exception e) {
                                e.printStackTrace();
                            }

                        } finally {
                            // cleanup
                            // Delete zip and related folders
                            File zipFile = new File(ZIP_FILE_NAME + finalLastIngestedDate);
                            if (zipFile.exists()) zipFile.delete();

                            File xmlFile = new File(DESTINATION_FILE_NAME + finalLastIngestedDate);
                            if (xmlFile.exists()) xmlFile.delete();
                        }

                    }
                };
                action.fork();
                tasks.add(action);

                while(tasks.size()>numTasks) {
                    tasks.remove(0).join();
                }
                Database.commit();

            }

            while(!tasks.isEmpty()) {
                tasks.remove(0).join();
            }
            Database.commit();


            // Repeat
        } catch(Exception e) {
            e.printStackTrace();
        }


    }



}
