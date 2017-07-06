package main.java;

import main.java.iterators.PatentGrantIterator;
import main.java.tools.Constants;
import main.java.handlers.SAXHandler;


/**
 * Created by ehallmark on 1/3/17.
 */
public class IngestGoogleXML {
    public static void main(String[] args) throws Exception {
        PatentGrantIterator iterator = Constants.DEFAULT_PATENT_GRANT_ITERATOR;
        iterator.applyHandlers(new SAXHandler());
    }

}