package main.java.iterators;

import main.java.handlers.CustomHandler;
import main.java.iterators.url_creators.UrlCreator;
import main.java.tools.ZipHelper;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RecursiveAction;


/**
 * Created by Evan on 7/5/2017.
 */
public class PatentGrantIterator implements WebIterator {
    private LocalDate startDate;
    private UrlCreator[] urlCreators;
    private String zipFilePrefix;
    private String destinationPrefix;
    public PatentGrantIterator(LocalDate startDate, String zipFilePrefix, String destinationPrefix, UrlCreator... urlCreators) {
        this.startDate=startDate;
        this.zipFilePrefix=zipFilePrefix;
        this.destinationPrefix=destinationPrefix;
        this.urlCreators=urlCreators;
    }

    @Override
    public void applyHandlers(CustomHandler... handlers) {
        List<RecursiveAction> tasks = new ArrayList<>();
        while (startDate.isBefore(LocalDate.now())) {
            final String zipFilename = zipFilePrefix + startDate;
            final String destinationFilename = destinationPrefix + startDate;
            RecursiveAction action = new RecursiveAction() {
                @Override
                protected void compute() {
                    try {
                        for (UrlCreator urlCreator : urlCreators) {
                            try {
                                URL website = new URL(urlCreator.create(startDate));
                                System.out.println("Trying: " + website.toString());
                                ReadableByteChannel rbc = Channels.newChannel(website.openStream());
                                FileOutputStream fos = new FileOutputStream(zipFilename);
                                fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
                                fos.close();

                                try {
                                    // Unzip file
                                    BufferedInputStream bis = new BufferedInputStream(new FileInputStream(new File(zipFilename)));
                                    BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(new File(destinationFilename)));
                                    ZipHelper.unzip(bis, bos);
                                    bis.close();
                                    bos.close();
                                    break;

                                } catch (Exception e) {
                                    System.out.println("Unable to unzip file");
                                }
                            } catch (Exception e) {
                            }
                        }

                        // break if we found one
                        if (!new File(zipFilename).exists()) return;

                        File xmlFile = new File(destinationFilename);
                        if (xmlFile.exists()) {
                            for (CustomHandler handler : handlers) {

                                // Ingest data for each file
                                try {

                                    SAXParserFactory factory = SAXParserFactory.newInstance();
                                    factory.setNamespaceAware(false);
                                    factory.setValidating(false);
                                    // security vulnerable
                                    factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
                                    factory.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
                                    SAXParser saxParser = factory.newSAXParser();

                                    FileReader fr = new FileReader(xmlFile);
                                    BufferedReader br = new BufferedReader(fr);
                                    String line;
                                    boolean firstLine = true;
                                    List<String> lines = new ArrayList<>();
                                    while ((line = br.readLine()) != null) {
                                        if (line.contains("<?xml") && !firstLine) {
                                            // stop
                                            saxParser.parse(new ByteArrayInputStream(String.join("", lines).getBytes()), handler);
                                            try {

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
                                        lines.clear();
                                        handler.reset();
                                    }

                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }

                    } finally {
                        // cleanup
                        // Delete zip and related folders
                        File zipFile = new File(zipFilename);
                        if (zipFile.exists()) zipFile.delete();

                        File xmlFile = new File(destinationFilename);
                        if (xmlFile.exists()) xmlFile.delete();
                    }
                }
            };

            startDate = startDate.plusDays(1);
            action.fork();
            tasks.add(action);

            while(tasks.size()>Runtime.getRuntime().availableProcessors()) {
                tasks.remove(0).join();
            }
        }

        while(!tasks.isEmpty()) {
            tasks.remove(0).join();
        }

        // save
        for(CustomHandler handler : handlers) {
            handler.save();
        }

    }

}
