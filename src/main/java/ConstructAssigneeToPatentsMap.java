package main.java;

import java.io.*;
import java.util.*;

/**
 * Created by ehallmark on 1/23/17.
 */
public class ConstructAssigneeToPatentsMap {
    public static File assigneeToPatentsMapFile = new File("assignee_to_patents_map.jobj");

    public static Map<String,Set<String>> load() throws IOException, ClassNotFoundException {
        ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(assigneeToPatentsMapFile)));
        Map<String,Set<String>> assigneeToPatentsMap = (Map<String,Set<String>>)ois.readObject();
        ois.close();
        return assigneeToPatentsMap;
    }

    public static void saveAssigneeToPatentsHash(Map<String,Set<String>> assigneeToPatentsMap) throws IOException {
        if(assigneeToPatentsMap!=null) {
            ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(assigneeToPatentsMapFile)));
            oos.writeObject(assigneeToPatentsMap);
            oos.flush();
            oos.close();
        }
    }


    public static void main(String[] args) throws Exception {
        // first load original assignee map and latest assignee map
        Map<String,List<String>> latestAssigneeMap = UpdateLatestAssigneeHash.load();
        Map<String,List<String>> originalAssigneeMap = UpdateInventionTitleAndOriginalAssigneeHash.loadOriginalAssigneeMap();

        if(latestAssigneeMap==null) throw new RuntimeException("Latest Assignee Map is null");
        if(originalAssigneeMap==null) throw new RuntimeException("Original Assignee Map is null");

        // then merge all into this map
        Map<String,Set<String>> assigneeToPatentsMap = new HashMap<>();
        latestAssigneeMap.forEach((patent,assignees)->{
            assignees.forEach(assignee->{
               if(assigneeToPatentsMap.containsKey(assignee)) {
                   assigneeToPatentsMap.get(assignee).add(patent);
               } else{
                   Set<String> patents = new HashSet<String>();
                   patents.add(patent);
                   assigneeToPatentsMap.put(assignee,patents);
               }
            });
        });
        originalAssigneeMap.forEach((patent,assignees)->{
            // skip if latestAssigneeMap has the patent
            if(!latestAssigneeMap.containsKey(patent)) {
                assignees.forEach(assignee -> {
                    if (assigneeToPatentsMap.containsKey(assignee)) {
                        assigneeToPatentsMap.get(assignee).add(patent);
                    } else {
                        Set<String> patents = new HashSet<>();
                        patents.add(patent);
                        assigneeToPatentsMap.put(assignee, patents);
                    }
                });
            }
        });
        // save
        saveAssigneeToPatentsHash(assigneeToPatentsMap);
    }
}
