package main.java;

import main.java.handlers.InventionTitleSAXHandler;
import main.java.iterators.PatentGrantIterator;
import main.java.iterators.url_creators.UrlCreator;
import main.java.handlers.CitationSAXHandler;

import java.time.LocalDate;
import java.time.Month;

/**
 * Created by Evan on 1/22/2017.
 */
public class UpdatePatentGrantData {

    public static void main(String[] args) {
        LocalDate startDate = LocalDate.of(2005, Month.JANUARY, 1);
        String zipPrefix = "patent-grant-zips";
        String destinationPrefix = "patent-grant-destinations";
        String googleURL = "http://storage.googleapis.com/patents/grant_full_text";
        String secondURL = "https://bulkdata.uspto.gov/data2/patent/grant/redbook/fulltext";

        UrlCreator googleCreator = date -> googleURL + "/" + date.getYear() + "/ipg" + date.toString().replace("-","").substring(2) + ".zip";
        UrlCreator usptoCreator = date ->  secondURL + "/" + date.getYear() + "/ipg" + date.toString().replace("-","").substring(2) + ".zip";

        PatentGrantIterator iterator = new PatentGrantIterator(startDate, zipPrefix, destinationPrefix, googleCreator, usptoCreator);

        iterator.applyHandlers(new InventionTitleSAXHandler(), new CitationSAXHandler());

        System.out.println("TOTAL PATENTS INGESTED: "+ CitationSAXHandler.allPatents.size());
    }
}
