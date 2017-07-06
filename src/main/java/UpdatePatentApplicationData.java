package main.java;

import main.java.handlers.ApplicationCitationSAXHandler;
import main.java.handlers.ApplicationInventionTitleSAXHandler;
import main.java.handlers.CitationSAXHandler;
import main.java.handlers.InventionTitleSAXHandler;
import main.java.iterators.PatentGrantIterator;
import main.java.tools.Constants;

/**
 * Created by Evan on 1/22/2017.
 */
public class UpdatePatentApplicationData {

    public static void main(String[] args) {
        PatentGrantIterator iterator = Constants.DEFAULT_PATENT_APPLICATION_ITERATOR;
        iterator.applyHandlers(new ApplicationCitationSAXHandler(), new ApplicationInventionTitleSAXHandler());
        System.out.println("FINAL DATE: "+ iterator.startDate.toString());
    }
}
