package main.java.patent_view_api;

import java.util.Collection;
import java.util.StringJoiner;

/**
 * Created by Evan on 2/5/2017.
 */
public class PatentQuery implements Query {
    private String query;
    public PatentQuery(Collection<String> classCodes, int page) {
        StringJoiner classCodeOr = new StringJoiner("\",\"","[\"","\"]");
        classCodes.forEach(classCode->classCodeOr.add(classCode));
        query="q={\"cpc_subgroup_id\":"+classCodeOr.toString()+"}&f=[\"patent_number\",\"assignee_organization\",\"cpc_subgroup_id\"]&o={\"page\":"+page+",\"per_page\":25}";
    }
    public String toString() {
        return query;
    }
}