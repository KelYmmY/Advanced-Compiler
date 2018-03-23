import java.util.*;

public class Function {
    private int regNum;
    private List<String> defParameters;
    private TreeMap<Integer, Block> blocks;
    private TreeMap<Integer, Set<Integer>> edges;
    private TreeMap<Integer, Set<Integer>> redges; // the redge is generated after 2nd step
    private TreeMap<Integer, Set<Integer>> DT_edges;
    private Map<String, Integer> baseMap;
    private Map<String, List<Integer>> arrayMap;

    // 构建一个函数的时候先定义好一个block
    public Function(int blockId) {
        regNum = 0;
        defParameters = new ArrayList<>();
        blocks = new TreeMap<>();
        edges = new TreeMap<>();
        redges = new TreeMap<>(Comparator.reverseOrder());
        DT_edges = new TreeMap<>();
        baseMap = new HashMap<>();
        arrayMap = new HashMap<>();
        blocks.put(blockId, new Block(blockId));
        edges.put(blockId, new HashSet<>());
        redges.put(blockId, new HashSet<>());
        DT_edges.put(blockId, new HashSet<>());

    }
    public void addDefParameters(String param){
        defParameters.add(param);
    }
    public void setRegNum(int regNum){
        this.regNum = regNum;
    }
    public void setDTTree(){
        DT dt = new DT(edges);
        DT_edges = dt.drawDT();
    }
    public boolean setArrayMap(Map<String, List<Integer>> map){
        System.out.println("input map: " + map);
        this.arrayMap = new HashMap<>();
        this.baseMap = new HashMap<>();
        // to allocate the baseMap according to the size of the array
        int base = 0;
        for(Map.Entry<String, List<Integer>> entry : map.entrySet()){
            this.baseMap.put(entry.getKey() + "base", base*4);
            int tmp = 1;
            List<Integer> result = new ArrayList<>();
            for(int i = entry.getValue().size()-1; i >= 0; i--){
                result.add(0, tmp);
                tmp *= entry.getValue().get(i);
            }
            this.arrayMap.put(entry.getKey(), result);
            base += tmp;
        }
        this.baseMap.put("end", base*4);
/*
        System.out.println("Array Map: ");
        System.out.println(this.arrayMap);
        System.out.println("Base Map: ");
        System.out.println(this.baseMap);*/

        return base <= 2000;
    }
    public void addBlock(int blockId){
        blocks.put(blockId, new Block(blockId));
        edges.put(blockId, new HashSet<>());
    }
    public void addBlock(int blockId, int feature){
        blocks.put(blockId, new Block(blockId, feature));
        edges.put(blockId, new HashSet<>());
    }
    public void addRelation(int blockId1, int blockId2){
        edges.get(blockId1).add(blockId2);
        if(!redges.containsKey(blockId2))
            redges.put(blockId2, new HashSet<>());
        redges.get(blockId2).add(blockId1);
    }
    public int getRegNum(){
        return this.regNum;
    }
    public List<String> getDefParameters(){
        return this.defParameters;
    }
    public Block getBlock(int blockId){
        return blocks.get(blockId);
    }
    public TreeMap<Integer, Block> getBlocks(){
        return blocks;
    }
    public TreeMap<Integer, Set<Integer>> getEdges(){
        return edges;
    }
    public TreeMap<Integer, Set<Integer>> getRedges(){
        return redges;
    }
    public TreeMap<Integer, Set<Integer>> getDTEdges(){
        return DT_edges;
    }
    // clear empty block
    // and build the reversed edges
    public void clearEmptyBlock(){
        Iterator<Map.Entry<Integer, Block>> bit = blocks.entrySet().iterator();
        Iterator<Map.Entry<Integer, Set<Integer>>> eit = edges.entrySet().iterator();
        Iterator<Map.Entry<Integer, Set<Integer>>> dit = DT_edges.entrySet().iterator();
        while (bit.hasNext()){
            Map.Entry<Integer, Block> bitem = bit.next();
            Map.Entry<Integer, Set<Integer>> eitem = eit.next();
            Map.Entry<Integer, Set<Integer>> ditem = dit.next();
            if(bitem.getValue().isValid()){
                if(!redges.containsKey(bitem.getKey())) redges.put(bitem.getKey(), new HashSet<>());
                // check the edge map and build the reversed edge relationship
                boolean flag = true;
                while(flag){
                    flag = false;
                    Set<Integer> tmp = new HashSet<>();
                    for(Iterator<Integer> edgeit = eitem.getValue().iterator(); edgeit.hasNext();){
                        Integer edgeitem = edgeit.next();
                        if(blocks.get(edgeitem).isValid()){
                            if(!redges.containsKey(edgeitem)) redges.put(edgeitem, new HashSet<>());
                            redges.get(edgeitem).add(eitem.getKey());
                        }
                        else{
                            flag = true;
                            tmp.addAll(edges.get(edgeitem));
                            edgeit.remove();
                        }
                    }
                    edges.get(eitem.getKey()).addAll(tmp);
                }
                // check the dtedge map
                flag = true;
                while(flag){
                    flag = false;
                    Set<Integer> tmp = new HashSet<>();
                    for(Iterator<Integer> dtit = ditem.getValue().iterator(); dtit.hasNext();){
                        Integer dtitem = dtit.next();
                        if(!blocks.get(dtitem).isValid()){
                            flag = true;
                            tmp.addAll(DT_edges.get(dtitem));
                            dtit.remove();
                        }
                    }
                    DT_edges.get(ditem.getKey()).addAll(tmp);
                }
            }
            else{
                bit.remove();
                eit.remove();
                dit.remove();
            }
        }
    }

    public Map<String, Integer> getBaseMap(){
        return baseMap;
    }
    public Map<String, List<Integer>> getArrayMap(){
        return arrayMap;
    }
}
