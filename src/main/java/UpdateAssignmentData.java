package main.java;

import main.java.iterators.AssignmentIterator;
import main.java.iterators.PatentGrantIterator;
import main.java.handlers.AssignmentSAXHandler;
import main.java.iterators.WebIterator;
import main.java.tools.Constants;
import main.java.handlers.TransactionSAXHandler;

/**
 * Created by Evan on 1/22/2017.
 */
public class UpdateAssignmentData {

    public static void main(String[] args) {
        AssignmentIterator iterator = Constants.DEFAULT_ASSIGNMENT_ITERATOR;
        iterator.applyHandlers(new TransactionSAXHandler(), new AssignmentSAXHandler());
        System.out.println("Finished");
    }
}
