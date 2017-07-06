package main.java;

import main.java.handlers.InventionTitleSAXHandler;
import main.java.iterators.PatentGrantIterator;
import main.java.iterators.url_creators.UrlCreator;
import main.java.handlers.CitationSAXHandler;
import main.java.tools.Constants;

import java.time.LocalDate;
import java.time.Month;

/**
 * Created by Evan on 1/22/2017.
 */
public class UpdatePatentGrantData {

    public static void main(String[] args) {
        PatentGrantIterator iterator = Constants.DEFAULT_PATENT_GRANT_ITERATOR;
        iterator.applyHandlers(new InventionTitleSAXHandler(), new CitationSAXHandler());
        System.out.println("FINAL DATE: "+ iterator.startDate.toString());
    }
}
