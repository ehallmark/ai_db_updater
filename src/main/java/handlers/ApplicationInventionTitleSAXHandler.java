package main.java.handlers;

/**
 * Created by ehallmark on 1/3/17.
 */

import main.java.database.Database;
import main.java.tools.AssigneeTrimmer;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import java.util.*;

/**

 */
public class ApplicationInventionTitleSAXHandler extends InventionTitleSAXHandler{
    @Override
    public CustomHandler newInstance() {
        return new ApplicationInventionTitleSAXHandler();
    }

    @Override
    public void save() {
        Database.saveObject(patentToInventionTitleMap,Database.appToInventionTitleMapFile);
        Database.saveObject(patentToOriginalAssigneeMap,Database.appToOriginalAssigneeMapFile);
    }
}