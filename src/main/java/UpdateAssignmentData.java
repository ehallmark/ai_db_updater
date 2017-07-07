package main.java;

import main.java.handlers.CitationSAXHandler;
import main.java.handlers.InventionTitleSAXHandler;
import main.java.iterators.PatentGrantIterator;
import main.java.tools.Constants;

/**
 * Created by Evan on 1/22/2017.
 */
public class UpdateAssignmentData {

    public static void main(String[] args) {
        PatentGrantIterator iterator = Constants.DEFAULT_PATENT_GRANT_ITERATOR;
        iterator.applyHandlers(new InventionTitleSAXHandler(), new CitationSAXHandler());
        System.out.println("FINAL DATE: "+ iterator.startDate.toString());
    }
}
