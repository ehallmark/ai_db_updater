package main.java.patent_view_api;

import java.util.Collection;
import java.util.StringJoiner;

/**
 * Created by Evan on 2/5/2017.
 */
public class PatentKeywordQuery implements Query {
    private String query;
    public PatentKeywordQuery(Collection<String> keywords, int page) {
        StringJoiner keywordOr = new StringJoiner(" ","\"","\"");
        keywords.forEach(keyword->keywordOr.add(keyword));
        query="q={\"_or\":[{\"_text_any\":{\"patent_title\":"+keywordOr.toString()+"}},{\"_text_any\":{\"patent_abstract\":"+keywordOr.toString()+"}}]}&f=[\"patent_number\",\"assignee_organization\",\"patent_title\"]&o={\"page\":"+page+",\"per_page\":25}";
    }
    public String toString() {
        return query;
    }
}