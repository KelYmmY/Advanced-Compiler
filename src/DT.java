import java.util.*;
import java.util.Map.Entry;

/**
 * DT Construction References:
 * https://tanujkhattar.wordpress.com/2016/01/11/dominator-tree-of-a-directed-graph/
 * http://blog.csdn.net/qq_35649707/article/details/64125918
 */

public class DT {
    private List<Integer> nodes;      // the nodes in the graph
    private Map<Integer, Set<Integer>> edges;   // the edges in the graph
    private int root = 0;      // the first element in the graph
    private ArrayList<Integer> seq;     // the arrival time for each node in the DFS tree
    private Map<Integer, Set<Integer>> inlinks;      // the in-links for each node in the graph
    private Map<Integer, Set<Integer>> ancestors;     // the ancestors for each node in the DFS tree
    private Map<Integer, Integer> sdom;     // the semi-dominator for each node in the graph
    private Map<Integer, Integer> idom;     // the immediate dominator for each node in the graph

    public DT( Map<Integer, Set<Integer>> E){
        nodes = new ArrayList<>();
        //root = initBlockId-1;
        edges = E;
        seq = new ArrayList<>();
        inlinks = new HashMap<>();
        ancestors = new HashMap<>();
        sdom = new HashMap<>();
        idom = new HashMap<>();

        for (Integer key : E.keySet()) {
            nodes.add(key);
        }
        Collections.sort(nodes);
    }

    public TreeMap<Integer, Set<Integer>> drawDT() {

//        System.out.println("nodes: " + nodes);
//        System.out.println("edges: " + edges);

        drawDFSTree();
        calInlinks();
        calSdom();
        calImpIdom();
        calExpIdom();

    /*

        System.out.println("seq: " + seq);
        System.out.println("expIdom: " + idom);
        System.out.println("ancestors: " + ancestors);
        System.out.println("inlinks: " + inlinks);
        System.out.println("sdom: " + sdom);
        System.out.println("impIdom: " + idom);*/


        TreeMap<Integer, Set<Integer>> dtEdges = new TreeMap<>();
        for (Entry<Integer,Integer> pair : idom.entrySet()){
            if(!pair.getKey().equals(pair.getValue())){
                if(!dtEdges.containsKey(pair.getValue())){
                    HashSet<Integer> tmp = new HashSet();
                    tmp.add(pair.getKey());
                    dtEdges.put(pair.getValue(), tmp);
                }
                else{
                    dtEdges.get(pair.getValue()).add(pair.getKey());
                }
            }
        }
        for(int i = 0; i < nodes.size(); i++){
            if(!dtEdges.containsKey(nodes.get(i))){
                dtEdges.put(nodes.get(i), new HashSet<Integer>());
            }
        }
        //System.out.println("dt edges: " + dtEdges);

        return dtEdges;
    }

    private void drawDFSTree(){
        boolean visited[] = new boolean[nodes.size()];
        Set<Integer> path = new HashSet<>();
        DFSHelper(root, visited, path);
    }

    private void DFSHelper(int index, boolean visited[], Set<Integer> path){
        seq.add(nodes.get(index));
        sdom.put(nodes.get(index), nodes.get(index));
        idom.put(nodes.get(index), nodes.get(index));
        HashSet<Integer> tmp = new HashSet(path);
        tmp.add(nodes.get(index));
        ancestors.put(nodes.get(index), tmp);
        path.add(nodes.get(index));
        visited[index] = true;
        if (edges.containsKey(nodes.get(index))){
            Iterator<Integer> iter = edges.get(nodes.get(index)).iterator();
            while(iter.hasNext()){
                int next = nodes.indexOf(iter.next());
                if(!visited[next]){
                    DFSHelper(next, visited, path);
                }
            }
        }
        path.remove(nodes.get(index));
    }

    // calculate the in-links for each node in the graph
    private void calInlinks(){
        for (Entry<Integer,Set<Integer>> pair : edges.entrySet()){
            for(Integer target: pair.getValue()){
                if(! inlinks.containsKey(target)){
                    inlinks.put(target,new HashSet<Integer>());
                }
                inlinks.get(target).add(pair.getKey());
            }
        }

    }

    // calculate semi-dominator for each node in the tree
    // order: from the back to the front in the seq(ArrayList<String>)
    // For any w ≠ r，sdom(w) = min({v|(v,w) ∈ E,v < w} ∪ {sdom(u)|u > w,∃(v,w) ∈ E,u →˙v})
    private void calSdom(){
        for(int i = seq.size() - 1; i >= 1; i--){

            // calculate sdom
            Set<Integer> in_nodes = inlinks.get(seq.get(i));
            int mini = nodes.size();
            Set<Integer> ances_tmp = new HashSet<>();

            // calculate {v|(v,w) ∈ E,v < w} and store ancestors of each in_nodes
            for(Integer ele: in_nodes){
                if(seq.indexOf(ele) < mini){
                    mini = seq.indexOf(ele);
                }
                for(Integer anc: ancestors.get(ele)){
                    ances_tmp.add(anc);
                }
            }

            // calculate { sdom(u)|u > w,∃(v,w) ∈ E,u →˙v }
            for(Integer ele: ances_tmp){
                if(seq.indexOf(ele) > i){
                    if(seq.indexOf(sdom.get(ele)) < mini){
                        mini = seq.indexOf(sdom.get(ele));
                    }
                }
            }
            sdom.put(seq.get(i), seq.get(mini));
        }
    }

    private void calImpIdom(){
        for(int i = seq.size() - 1; i >= 1; i--){
            // calculate implicit idom
            Integer mini_u = seq.get(seq.size() - 1);
            for(Integer ele: edges.get(sdom.get(seq.get(i)))){
                if(ancestors.get(seq.get(i)).contains(ele)){
                    if(seq.indexOf(sdom.get(ele)) < seq.indexOf(mini_u)){
                        mini_u = ele;
                    }
                }
            }

            if(sdom.get(seq.get(i)).equals(sdom.get(mini_u))){
                idom.put(seq.get(i), sdom.get(seq.get(i)));
            }
            else{
                idom.put(seq.get(i), idom.get(seq.get(i)));
            }
        }
    }

    //if sdom(w) != idom(w) then idom(w) = idom(idom(w))
    // else idom(w) = sdom(w)
    private void calExpIdom(){
        for(int i = 0; i < seq.size(); i++){
            if(!sdom.get(seq.get(i)).equals(idom.get(seq.get(i)))){
                idom.put(seq.get(i), idom.get(idom.get(seq.get(i))));
            }
            else{
                idom.put(seq.get(i), sdom.get(seq.get(i)));
            }
        }
    }

}
