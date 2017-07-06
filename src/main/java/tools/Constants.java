package main.java.tools;

import main.java.iterators.PatentGrantIterator;
import main.java.iterators.url_creators.UrlCreator;

import java.time.LocalDate;
import java.time.Month;

/**
 * Created by Evan on 7/5/2017.
 */
public class Constants {
    public static final String DATA_FOLDER = "data/";
    public static final String GOOGLE_URL = "http://storage.googleapis.com/patents/grant_full_text";
    public static final String USPTO_URL = "https://bulkdata.uspto.gov/data2/patent/grant/redbook/fulltext";
    public static final UrlCreator GOOGLE_CREATOR = defaultPatentUrlCreator(GOOGLE_URL);
    public static final UrlCreator USPTO_CREATOR = defaultPatentUrlCreator(USPTO_URL);

    public static final LocalDate DEFAULT_START_DATE = LocalDate.of(2005, Month.JANUARY, 1);
    public static final String ZIP_PREFIX = "patent-grant-zips";
    public static final String DESTINATION_PREFIX = "patent-grant-destinations";
    public static final PatentGrantIterator DEFAULT_PATENT_GRANT_ITERATOR = new PatentGrantIterator(DEFAULT_START_DATE, ZIP_PREFIX, DESTINATION_PREFIX, GOOGLE_CREATOR, USPTO_CREATOR);

    private static UrlCreator defaultPatentUrlCreator(String baseUrl) {
        return date -> baseUrl + "/" + date.getYear() + "/ipg" + date.toString().replace("-", "").substring(2) + ".zip";
    }
}
