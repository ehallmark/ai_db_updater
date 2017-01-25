package main.java;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.validation.Schema;
import javax.xml.validation.Validator;
import javax.xml.validation.ValidatorHandler;
import java.io.*;
import java.util.*;

/**
 * Created by ehallmark on 1/25/17.
 */
public class UpdateClassCodeToClassTitleMap {
    public static final File mapFile = new File("class_code_to_class_title_map.jobj");
    private static final File cpcInputDataFile = new File("cpc_xml/");
    // the file is located here: http://www.cooperativepatentclassification.org/Archive.html

    public static void main(String[] args) {
        Map<String,String> classCodeToTitleMap = new HashMap<>();

        // parse html data
        Arrays.stream(cpcInputDataFile.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".xml");
            }
        })).forEach(file->{
            try {
                parse(file, classCodeToTitleMap);
            } catch(Exception e) {
                System.out.println("Error parsing file: "+file.getName());
                System.out.println("Exception: "+e.getMessage());
            }
        });

        // save object
        try {
            ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(mapFile)));
            oos.writeObject(classCodeToTitleMap);
            oos.flush();
            oos.close();
        }catch(IOException e) {
            e.printStackTrace();
        }
        // test
        String testClass = "H04W4/00";
        String fullTitle = getFullClassTitleFromClassCode(testClass,classCodeToTitleMap);
        System.out.println("Title for "+testClass+": "+fullTitle);
    }

    private static void parse(File file, Map<String,String> map) throws Exception {
        DocumentBuilderFactory factory
                = DocumentBuilderFactory.newInstance();
        factory.setValidating(false);
        DocumentBuilder dBuilder = factory.newDocumentBuilder();
        Document doc = dBuilder.parse(file);
        System.out.println("Root element :"
                + doc.getDocumentElement().getNodeName());
        NodeList classifications = doc.getElementsByTagName("classification-item");
        System.out.println("----------------------------");
        for (int temp = 0; temp < classifications.getLength(); temp++) {
            Node classification = classifications.item(temp);
            if (classification.getNodeType() == Node.ELEMENT_NODE) {
                Element classElement = (Element) classification;
                NodeList symbol = classElement.getElementsByTagName("classification-symbol");
                if(symbol.getLength()>0) {
                    String classSymbol = symbol.item(0).getTextContent();
                    if(!classSymbol.trim().endsWith("/00")) {
                        NodeList titles = classElement.getElementsByTagName("title-part");
                        List<String> titleParts = new ArrayList<>(titles.getLength());
                        for (int titleIdx = 0; titleIdx < titles.getLength(); titleIdx++) {
                            Node titlePartNode = titles.item(titleIdx);
                            if (titlePartNode.getNodeType() == Node.ELEMENT_NODE) {
                                titleParts.add(titlePartNode.getTextContent());
                            }
                        }
                        System.out.println("Symbol: " + classSymbol);
                        System.out.println("Title: " + String.join("; ", titleParts));
                        map.put(classSymbol, String.join("; ", titleParts));
                    }
                }
            }
        }
    }

    public static String getFullClassTitleFromClassCode(String formattedCode, Map<String,String> classCodeToClassTitleMap) {
        formattedCode=formattedCode.toUpperCase().replaceAll(" ","");
        if(classCodeToClassTitleMap.containsKey(formattedCode)) return classCodeToClassTitleMap.get(formattedCode);
        return "";
    }

}
