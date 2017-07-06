package main.java.iterators;

import main.java.handlers.CustomHandler;
import org.xml.sax.helpers.DefaultHandler;

import java.io.File;

/**
 * Created by Evan on 7/5/2017.
 */
public interface WebIterator {
    void applyHandlers(CustomHandler... handlers);
}
