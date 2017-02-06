package main.java.patent_view_api;

import main.java.database.Database;
import main.java.tools.AssigneeTrimmer;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.client.utils.URIUtils;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by Evan on 2/5/2017.
 */
public class PatentAPIHandler {
    public static List<Patent> requestAllPatentsFromAssigneesAndClassCodes(Collection<String> assignees, Collection<String> classCodes) {
        PatentQuery query = new PatentQuery(assignees,classCodes,1);
        int totalResults;
        try {
            totalResults = requestPatents(query).getTotalPatentCount();
        } catch(Exception e) {
            e.printStackTrace();
            totalResults=0;
        }
        if(totalResults==0) return new ArrayList<>();
        List<Patent> results = new ArrayList<>(totalResults);
        int page = 1;
        while(results.size()<totalResults) {
            query = new PatentQuery(assignees,classCodes,page);
            try {
                results.addAll(requestPatents(query).getPatents());
            } catch(Exception e) {
                e.printStackTrace();
                break;
            }
            page++;
        }
        return results;
    }

    public static List<Patent> requestAllPatentsFromKeywords(Collection<String> keywords) {
        PatentKeywordQuery query = new PatentKeywordQuery(keywords,1);
        int totalResults;
        try {
            totalResults = requestPatents(query).getTotalPatentCount();
        } catch(Exception e) {
            e.printStackTrace();
            totalResults=0;
        }
        if(totalResults==0) return new ArrayList<>();
        List<Patent> results = new ArrayList<>(totalResults);
        int page = 1;
        while(results.size()<totalResults) {
            query = new PatentKeywordQuery(keywords,page);
            try {
                results.addAll(requestPatents(query).getPatents());
            } catch(Exception e) {
                e.printStackTrace();
                break;
            }
            page++;
        }
        return results;
    }

    private static PatentResponse requestPatents(Query query) {
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            URI uri = new URIBuilder()
                    .setScheme("http")
                    .setHost("www.patentsview.org")
                    .setPath("/api/patents/query")
                    .setCustomQuery(query.toString())
                    .build();
            System.out.println("URI: "+uri.toString());
            HttpGet request = new HttpGet(uri.toString());
            request.addHeader("Accept","application/json");
            request.addHeader("Content-Type","application/json");

            HttpResponse result = httpClient.execute(request);
            String json = EntityUtils.toString(result.getEntity(), "UTF-8");
            System.out.println("Response: "+json);
            com.google.gson.Gson gson = new com.google.gson.Gson();
            PatentResponse response = gson.fromJson(json, PatentResponse.class);

            System.out.println("Total patent count: "+response.getTotalPatentCount()+" patents");
            System.out.println("Total patent count: "+response.getTotalPatentCount()+" patents");

            return response;
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

    private static void addResultsToAssigneeMap(List<Patent> patents, Map<String,Set<String>> map) {
        patents.forEach(patent->{
            String patNum = patent.getPatentNumber();
            patent.getAssignees().forEach(assignee->{
                if(assignee.getAssignee()==null) return;
                String assigneeName = assignee.getAssignee();
                assigneeName = AssigneeTrimmer.standardizedAssignee(assigneeName);
                if(map.containsKey(assigneeName)) {
                    map.get(assigneeName).add(patNum);
                } else {
                    Set<String> set = new HashSet<>();
                    set.add(patNum);
                    map.put(assigneeName,set);
                }
            });
        });
        // consolidate assignees
        Map<String,Set<String>> newMap = new HashMap<>();
        boolean done = false;
        Set<String> alreadyAdded = new HashSet<>();
        while(!done) {
            done = true;
            for(Map.Entry<String,Set<String>> e : map.entrySet().stream().sorted((e1,e2)->Integer.compare(e1.getKey().length(),e2.getKey().length())).collect(Collectors.toList())) {
                if(!alreadyAdded.contains(e.getKey())) {
                    Set<String> set = new HashSet<>(map.get(e.getKey()));
                    newMap.put(e.getKey(),set);
                }
                else if(newMap.keySet().stream().anyMatch(a->a.startsWith(e.getKey())||e.getKey().startsWith(a))) {
                    for(String a : newMap.keySet()) {
                        if(a.startsWith(e.getKey())||e.getKey().startsWith(a)) {
                            newMap.get(a).addAll(map.get(e.getKey()));
                        } else {
                            Set<String> set = new HashSet<>(map.get(e.getKey()));
                            newMap.put(a,set);
                        }
                    }
                    done=false;
                    alreadyAdded.add(e.getKey());
                    break;
                }
            }
        }
        map.clear();
        map.putAll(newMap);
    }

    public static void writeAssigneeDataCountsToCSV(Map<String,Set<String>> assigneeToPatentsMap, File file) {
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(file));
            for (Map.Entry<String, Set<String>> e : assigneeToPatentsMap.entrySet()) {
                String assignee = e.getKey();
                Set<String> patents = e.getValue();
                String line = assignee + "," + patents.size() + "\n";
                writer.write(line);
                writer.flush();
            }

            writer.flush();
            writer.close();
        } catch(IOException ioe) {
            ioe.printStackTrace();
        }
    }

    public static void main(String[] args) {
        /*
            List<Patent> patents = requestAllPatentsFromAssigneesAndClassCodes(Arrays.asList("microsoft","panasonic"),Arrays.asList("G06F3\\/0383"));
        */
        {
            Map<String, Set<String>> assigneeTo2GMap = new HashMap<>();
            List<Patent> patents2G = requestAllPatentsFromKeywords(Arrays.asList("GSM"));
            System.out.println("Total 2G patents found: " + patents2G.size());
            addResultsToAssigneeMap(patents2G, assigneeTo2GMap);
            Database.saveObject(assigneeTo2GMap,new File("assignee_to_2g_patents_map.jobj"));
            writeAssigneeDataCountsToCSV(assigneeTo2GMap,new File("2g_assignee_data.csv"));
        }
        {
            Map<String,Set<String>> assigneeTo3GMap = new HashMap<>();
            List<Patent> patents3G = requestAllPatentsFromKeywords(Arrays.asList("UMTS"));
            System.out.println("Total 4G patents found: "+patents3G.size());
            addResultsToAssigneeMap(patents3G, assigneeTo3GMap);
            Database.saveObject(assigneeTo3GMap,new File("assignee_to_3g_patents_map.jobj"));
            writeAssigneeDataCountsToCSV(assigneeTo3GMap,new File("3g_assignee_data.csv"));
        }
        {
            List<Patent> patents4G = requestAllPatentsFromKeywords(Arrays.asList("LTE"));
            System.out.println("Total 4G patents found: " + patents4G.size());
            Map<String, Set<String>> assigneeTo4GMap = new HashMap<>();
            addResultsToAssigneeMap(patents4G, assigneeTo4GMap);
            Database.saveObject(assigneeTo4GMap,new File("assignee_to_4g_patents_map.jobj"));
            writeAssigneeDataCountsToCSV(assigneeTo4GMap,new File("4g_assignee_data.csv"));
        }

    }
}
