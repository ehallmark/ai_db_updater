import database.Database;
import tools.SAXHandler;
import tools.ZipHelper;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by ehallmark on 1/3/17.
 */
public class IngestGoogleXML {
    private static final String ZIP_FILE_NAME = "tmp_uspto_zip_file.zip";
    private static final String DESTINATION_FILE_NAME = "uspto_xml_file.xml";

    public static void main(String[] args) {
        try {
            // Get last ingested date
            Integer lastIngestedDate = Database.lastIngestedDate();
            LocalDate date = LocalDate.now();
            String endDateStr = String.valueOf(date.getYear()).substring(2,4)+String.format("%02d",date.getMonthValue())+String.format("%02d",date.getDayOfMonth());
            Integer endDateInt = Integer.valueOf(endDateStr);

            System.out.println("Starting with date: "+lastIngestedDate);
            System.out.println("Ending with date: "+endDateInt);
            while(lastIngestedDate<=endDateInt) {
                // Commit results to DB and update last ingest table
                Database.updateLastIngestedDate(lastIngestedDate);
                lastIngestedDate = lastIngestedDate+1;

                // Load file from Google
                try {
                    String dateStr = String.format("%06d",lastIngestedDate);
                    URL website = new URL("http://storage.googleapis.com/patents/grant_full_text/20"+dateStr.substring(0,2)+"/ipg" + String.format("%06d",lastIngestedDate) + ".zip");
                    System.out.println("Trying: "+website.toString());
                    ReadableByteChannel rbc = Channels.newChannel(website.openStream());
                    FileOutputStream fos = new FileOutputStream(ZIP_FILE_NAME);
                    fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
                    fos.close();
                } catch(Exception e) {
                    System.out.println("Not found");
                    continue;
                }

                // Unzip file
                BufferedInputStream bis = new BufferedInputStream(new FileInputStream(new File(ZIP_FILE_NAME)));
                BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(new File(DESTINATION_FILE_NAME)));
                ZipHelper.unzip(bis, bos);
                bis.close();
                bos.close();

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


                    FileReader fr = new FileReader(new File(DESTINATION_FILE_NAME));
                    BufferedReader br = new BufferedReader(fr);
                    String line;
                    boolean firstLine = true;
                    List<String> lines = new ArrayList<>();
                    while ((line = br.readLine()) != null) {
                        if (line.contains("<?xml") && !firstLine) {
                            // stop
                            saxParser.parse(new ByteArrayInputStream(String.join("",lines).getBytes()), handler);

                            if (handler.getPatentNumber() != null && !handler.getFullDocuments().isEmpty()) {
                                Database.ingestRecords(handler.getPatentNumber(),handler.getFullDocuments());
                                Database.commit();
                            }

                            lines.clear();
                            handler.reset();
                        }
                        if(firstLine) firstLine = false;
                        lines.add(line);
                    }
                    br.close();
                    fr.close();


                    // get the last one
                    if(!lines.isEmpty()) {
                        saxParser.parse(new ByteArrayInputStream(String.join("",lines).getBytes()), handler);

                        if (handler.getPatentNumber() != null && !handler.getFullDocuments().isEmpty()) {
                            Database.ingestRecords(handler.getPatentNumber(),handler.getFullDocuments());
                            Database.commit();

                        }
                        lines.clear();
                        handler.reset();
                    }



                } catch (Exception e) {
                    e.printStackTrace();
                }

                // Commit results to DB and update last ingest table
                Database.updateLastIngestedDate(lastIngestedDate);
                Database.commit();

                // Delete zip and related folders
                File zipFile = new File(ZIP_FILE_NAME);
                if (zipFile.exists()) zipFile.delete();

                File xmlFile = new File(DESTINATION_FILE_NAME);
                if (xmlFile.exists()) xmlFile.delete();

            }

            // Repeat
        } catch(Exception e) {
            e.printStackTrace();
        } finally {
            try {
                Database.close();
            } catch(Exception sql) {
                sql.printStackTrace();
            }
        }

    }



}
