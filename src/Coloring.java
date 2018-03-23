import java.util.*;

public class Coloring {
    TreeMap<String, Set<String>> graph;

    public void initGraph(){
        graph = new TreeMap<>(Comparator.reverseOrder());
        graph.put("1", new HashSet<>(Arrays.asList("3", "4", "6", "7")));
        graph.put("2", new HashSet<>(Arrays.asList("3", "5", "6", "7")));
        graph.put("3", new HashSet<>(Arrays.asList("4", "5")));
        graph.put("4", new HashSet<>(Arrays.asList("5")));
        graph.put("5", new HashSet<>());
        graph.put("6", new HashSet<>());
        graph.put("7", new HashSet<>());
        //graph.put("7", new HashSet<>());
    }

    public TreeMap<String, Set<String>> getGraph(){
        return graph;
    }

    // 优化， first color the node with lowest degree
    public Map<String, String> coloring(TreeMap<String, Set<String>> graph) throws StrongSymbolMissing{
        // print the graph for test
        System.out.println("print graph in order: ");
        for(Map.Entry<String, Set<String>> mtmp : graph.entrySet()) {
            System.out.print(mtmp.getKey() + " : ");
            for (String stmp : mtmp.getValue()) {
                System.out.print(stmp + ", ");
            }
            System.out.println();
        }

        // start coloring
        Set<String> pool = new TreeSet<>(Arrays.asList("R1", "R2", "R3", "R4", "R5", "R6", "R7", "R8"));
        Map<String, String> result = new HashMap<>();
        for(Map.Entry<String, Set<String>> entry : graph.entrySet()){
            Set<String> used = new HashSet<>();
            for(String neighbor : entry.getValue()){
                used.add(result.get(neighbor));
            }
            if(used.equals(pool))
                throw new StrongSymbolMissing("Coloring Error: Limited Color");
            for(String R : pool){
                if(!used.contains(R)){
                    result.put(entry.getKey(), R);
                    break;
                }
            }
        }
        return result;
    }

    public static void main(String[] args) {
        try {
            Coloring color = new Coloring();
            color.initGraph();
            Map<String, String> result = color.coloring(color.getGraph());
            color.printMap(result);
        } catch (StrongSymbolMissing e) {
            e.Exit();
        }
    }

    // for test
    private void printMap(Map<String, String> map){
        for(Map.Entry<String, String> entry : map.entrySet()){
            System.out.println("key = " + entry.getKey() + ", value = " + entry.getValue());
        }
    }
}
