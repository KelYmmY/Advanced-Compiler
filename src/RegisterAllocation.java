import javafx.beans.binding.MapExpression;
import javax.swing.plaf.nimbus.State;
import java.util.*;

public class RegisterAllocation {
    private Function f;
    private int depth;
    private int curStaId;
    private TreeMap<Integer, Block> blocks;
    private TreeMap<Integer, Set<Integer>> redges;
    private Set<List<String>> graph;
    private Map<String, Double> costFunc;
    private Map<String, String> coloring;

    public RegisterAllocation(Function f, int id){
        this.f = f;
        depth = 0;
        curStaId = id;
        graph = new HashSet<>();
        redges = new TreeMap<>();
        blocks = new TreeMap<>();
        coloring = new HashMap<>();
        costFunc = new HashMap<>();
        redges = f.getRedges();
        blocks = f.getBlocks();

        boolean flag = blocks.lastEntry().getValue().initialRA(true);
        if(flag) {
            addEdges(blocks.lastEntry().getValue().getInitialRA());
            costFunc.put(blocks.lastEntry().getValue().getInitialRA().first(), 0.0);
        }
        if(!flag && !redges.get(blocks.lastEntry().getKey()).isEmpty()){
            Block block = blocks.get(Collections.min(redges.get(blocks.lastEntry().getKey())));
            block.initialRA(false);
            addEdges(block.getInitialRA());
            costFunc.put(block.getInitialRA().first(), 0.0);
        }

    }

    private void getRA(){
        for (Map.Entry<Integer,Set<Integer>> pair : redges.entrySet()){
            if(blocks.get(pair.getKey()).getFeature() != 2){
                if(blocks.get(pair.getKey()).getFeature() == 3){
                    depth -= 1;
                }
                helperRA(blocks.get(pair.getKey()));
                if(blocks.get(pair.getKey()).getFeature() == 1){
                    depth += 1;
                }
            }
            if(pair.getValue().size() == 1){
                if(blocks.get(Collections.min(pair.getValue())).getFeature() == 2){
                    Block tmp = blocks.get(Collections.min(pair.getValue()));
                    while(tmp.getFeature() == 2){
                        if(tmp.getTimes() == 2){
                            depth += 1;
                            helperRA(tmp);
                        }
                        else if(tmp.getTimes() == 1){
                            helperRA(tmp);
                            depth -= 1;
                        }
                        tmp = blocks.get(Collections.max(redges.get(tmp.getIndex())));
                    }

                }
            }
            // pair.getValue().size() == 2
            else{
                if(blocks.get(pair.getKey()).getFeature() == 2 && blocks.get(Collections.min(pair.getValue())).getFeature() == 2){
                    if(blocks.get(Collections.min(pair.getValue())).getTimes() == 2){
                        depth += 1;
                        helperRA(blocks.get(Collections.min(pair.getValue())));
                    }
                    else if(blocks.get(Collections.min(pair.getValue())).getTimes() == 1){
                        helperRA(blocks.get(Collections.min(pair.getValue())));
                        depth -= 1;
                    }
                }
            }
        }
    }

    private void helperRA(Block block){
        System.out.println("block: " + block.getIndex());

        // if the current normal block is empty, and it is not the start block
        // push the RA to its parent block
        if(block.getStatements().size() == 0){
            if(redges.get(block.getIndex()).size() != 0){
                blocks.get(Collections.min(redges.get(block.getIndex()))).setInitialRA(block.getInitialRA());

            }
        }

        TreeSet<String> lfirstRA = new TreeSet<>(block.getInitialRA());
        TreeSet<String> rfirstRA = new TreeSet<>();  // only useful when the block is a if block
        List<Statement> stats = new ArrayList<>(block.getReverseBlock());
        for(int i = 0; i < stats.size(); i++){
            List<String> symbols = stats.get(i).getSymbols();
            // modify the firstRA
            if(lfirstRA.contains("(" + stats.get(i).getIndex() + ")")){
                // remove the (stat.index) from the lfirstRA
                lfirstRA = new TreeSet<>(removeIndex(lfirstRA, "(" + stats.get(i).getIndex() + ")"));
                if(!stats.get(i).getOpcode().equals("phi")){
                    for(int j = 0; j < symbols.size(); j++){
                        if(symbols.get(j).charAt(0) == '('){
                            lfirstRA.add(symbols.get(j));
                            if(!(block.getFeature() == 2 && block.getTimes() == 1)){
                                modifyCostFunc(symbols.get(j));
                            }
                        }
                    }
                    addEdges(lfirstRA);
                    // if the next processing statement is phi in a if block, initialize rfirstRA
                    if((i == stats.size()-1) || (i != stats.size()-1 && stats.get(i+1).getOpcode().equals("phi") && block.getFeature() == 1)){
                        rfirstRA = new TreeSet<>(lfirstRA);
                    }
                }
                else{
                    // phi statement in a if block
                    if(block.getFeature() == 1){
                        if(rfirstRA.size() > 0){
                            rfirstRA = new TreeSet<>(removeIndex(rfirstRA, "(" + stats.get(i).getIndex() + ")"));
                        }
                        if(!symbols.get(0).contains("#")){
                            lfirstRA.add(symbols.get(0));
                            modifyCostFunc(symbols.get(0));
                        }
                        if(!symbols.get(1).contains("#")){
                            rfirstRA.add(symbols.get(1));
                            modifyCostFunc(symbols.get(1));
                        }

                        TreeSet<String> tmp = new TreeSet<>(lfirstRA);
                        Iterator iter = rfirstRA.iterator();
                        while(iter.hasNext()){
                            String s = String.valueOf(iter.next());
                            if(!tmp.contains(s)){
                                tmp.add(s);
                            }
                        }
                        addEdges(tmp);
                    }
                    // phi statement in a while block
                    else if(block.getFeature() == 2){
                        if(block.getTimes() == 2){
                            if(!symbols.get(1).contains("#")){
                                lfirstRA.add(symbols.get(1));
                                modifyCostFunc(symbols.get(1));
                            }
                        }
                        else if(block.getTimes() == 1){
                            if(!symbols.get(0).contains("#")){
                                lfirstRA.add(symbols.get(0));
                                modifyCostFunc(symbols.get(0));
                            }
                        }
                        addEdges(lfirstRA);
                    }
                }
            }
            else{
                if(stats.get(i).getOpcode().equals("bra") || stats.get(i).getOpcode().equals("bne") || stats.get(i).getOpcode().equals("beq") || stats.get(i).getOpcode().equals("ble") || stats.get(i).getOpcode().equals("blt") || stats.get(i).getOpcode().equals("bge") || stats.get(i).getOpcode().equals("bgt")){
                    for(int j = 0; j < symbols.size(); j++){
                        if(symbols.get(j).charAt(0) == '('){
                            lfirstRA.add(symbols.get(j));

                            if(!(block.getFeature() == 2 && block.getTimes() == 1)){
                                modifyCostFunc(symbols.get(j));
                            }
                        }
                    }
                    addEdges(lfirstRA);
                }
                else if(stats.get(i).getOpcode().equals("write") || stats.get(i).getOpcode().equals("writeNL") || stats.get(i).getOpcode().equals("return")){
                    if(symbols.size() > 0 && !symbols.get(0).contains("#")){
                        lfirstRA.add(symbols.get(0));
                        if(!(block.getFeature() == 2 && block.getTimes() == 1)){
                            modifyCostFunc(symbols.get(0));
                        }
                        addEdges(lfirstRA);
                    }
                }
                else if(stats.get(i).getOpcode().equals("store")){
                    for(int j = 0; j < symbols.size(); j++){
                        if(symbols.get(j).charAt(0) == '('){
                            lfirstRA.add(symbols.get(j));
                            if(!(block.getFeature() == 2 && block.getTimes() == 1)){
                                modifyCostFunc(symbols.get(j));
                            }
                        }
                    }
                    addEdges(lfirstRA);
                }
                else if(stats.get(i).getOpcode().equals("call") && symbols.size() > 1){
                    for(int j = 1; j < symbols.size(); j++){
                        if(symbols.get(j).charAt(0) == '('){
                            lfirstRA.add(symbols.get(j));
                            if(!(block.getFeature() == 2 && block.getTimes() == 1)){
                                modifyCostFunc(symbols.get(j));
                            }
                        }
                    }
                    addEdges(lfirstRA);
                }
                else if(stats.get(i).getOpcode().equals("end")){ }
                else if(stats.get(i).getOpcode().equals("phi")){
                    if(block.getFeature() == 2){
                        if(block.getTimes() == 2){
                            stats.get(i).setPointer("deadcode");
                            block.subCount();
                        }
                        else if(block.getTimes() == 1){
                            if(!symbols.get(0).contains("#")){
                                lfirstRA.add(symbols.get(0));
                                modifyCostFunc(symbols.get(0));
                            }
                        }
                        addEdges(lfirstRA);
                    }
                    else {
                        stats.get(i).setPointer("deadcode");
                        block.subCount();
                    }
                }
                else{
                    stats.get(i).setPointer("deadcode");
                    block.subCount();
                }
            }

            // normal block: push firstRA to its direct parent in redges
            if(i == stats.size()-1 && block.getFeature() == 0){
                if(redges.get(block.getIndex()).size() != 0){
                    blocks.get(Collections.min(redges.get(block.getIndex()))).setInitialRA(lfirstRA);

                }
            }
            // if block: push lfirstRA to the left branch; push rfirstRA to the right branch;
            else if(i == stats.size()-1 && block.getFeature() == 1){
                blocks.get(Collections.min(redges.get(block.getIndex()))).setInitialRA(lfirstRA);
                blocks.get(Collections.max(redges.get(block.getIndex()))).setInitialRA(rfirstRA);
            }
            // while block: times == 2 => push lfirstRA to the larger block; else => push lfirstRA to the smaller block
            else if(i == stats.size()-1 && block.getFeature() == 2){
                if(block.getTimes() == 2){
                    blocks.get(Collections.max(redges.get(block.getIndex()))).setInitialRA(lfirstRA);
                }
                // times == 1
                else{
                    blocks.get(Collections.min(redges.get(block.getIndex()))).setInitialRA(lfirstRA);
                }
                block.subTimes();
            }
            else if(i == stats.size()-1 && block.getFeature() == 3){
                if(redges.get(block.getIndex()).size() != 0){
                    blocks.get(Collections.min(redges.get(block.getIndex()))).setInitialRA(lfirstRA);
                }
            }
        }

//        System.out.println("block id :" + block.getIndex() + "   depth: " + depth);
//        System.out.println("block id :" + block.getIndex() + "   feature: " + block.getFeature());
    }

    // add edges into the graph map
    private void addEdges(TreeSet<String> L){
        ArrayList<String> set = new ArrayList<>(L);
        System.out.println(set);
        int size = set.size();
        if(size == 1){
            List<String> sublist = new ArrayList<>();
            sublist.add(set.get(0));
            sublist.add(set.get(0));
            graph.add(sublist);
            return;
        }
        for(int i = 0; i < size; i++){
            for(int j = i+1; j < size; j++) {
                List<String> sublist = new ArrayList<>();
                sublist.add(set.get(i));
                sublist.add(set.get(j));
                graph.add(sublist);
            }
        }
    }

    // remove the targeted statement index from firstRA
    private TreeSet<String> removeIndex(TreeSet<String> firstRA, String target){
        Iterator<String> iter = firstRA.iterator();
        while(iter.hasNext()){
            if(iter.next().equals(target)){
                iter.remove();
                return firstRA;
            }
        }
        return firstRA;
    }

    private void modifyCostFunc(String symbol) {
        if (costFunc.get(symbol) != null) {
            costFunc.put(symbol, costFunc.get(symbol) + Math.pow(10, depth));
        }
        else {
            costFunc.put(symbol, Math.pow(10, depth));
        }
    }

    // define the comparision functions: ordered by cost function from high to low;
    // if the cost function equals, ordered by key from high to low;
    private static class ValueComparator implements Comparator<Map.Entry<String,Double>>
    {
        public int compare(Map.Entry<String,Double> m, Map.Entry<String,Double> n)
        {
            if(m.getValue().equals(n.getValue()))
                return n.getKey().compareTo(m.getKey());
            return n.getValue().compareTo(m.getValue());
        }
    }

    // 优化， first color the node with lowest degree
    // coloring according to cost function
    private void coloring() throws StrongSymbolMissing{
        // generate the graph map according to the edges and the cost function
        Map<String, Set<String>> coloringGraph = new HashMap<>();
        for(List<String> entry : graph){
            String x = entry.get(0), y = entry.get(1);
            if(!coloringGraph.containsKey(x)) coloringGraph.put(x, new HashSet<>());
            if(!coloringGraph.containsKey(y)) coloringGraph.put(y, new HashSet<>());
            if(x.equals(y)) continue;
            Double xvalue = costFunc.get(x), yvalue = costFunc.get(y);
            //System.out.println("edges: " + x + " , " + y);
            if(xvalue < yvalue || (xvalue.equals(yvalue) && x.compareTo(y) < 0))
                coloringGraph.get(x).add(y);
            else
                coloringGraph.get(y).add(x);
        }
        // to release the space of graph object
        graph = null;

        //print the graph for test
        System.out.println("graph edges: ");
        for(Map.Entry<String, Set<String>> mtmp : coloringGraph.entrySet()) {
           System.out.print(mtmp.getKey() + " : ");
           for (String stmp : mtmp.getValue()) {
               System.out.print(stmp + ", ");
           }
           System.out.println();
        }
        // start coloring
        int regNum = 0;
        Set<String> pool = new TreeSet<>(Arrays.asList("R1", "R2", "R3", "R4", "R5", "R6", "R7", "R8"));
        // map转换成list进行排序
        List<Map.Entry<String, Double>> list = new ArrayList<>(costFunc.entrySet());
        RegisterAllocation.ValueComparator vc=new ValueComparator();
        Collections.sort(list, vc);
        //start coloring
        for(Map.Entry<String, Double> entry : list){
            System.out.println("costfunc order: " + entry.getKey() );
            Set<String> used = new HashSet<>();
            for(String neighbor :coloringGraph.get(entry.getKey())){
                used.add(coloring.get(neighbor));
            }
            // if the condition can not be coloring in 8 colors
            if(used.containsAll(pool)){
                System.out.println("used: " + used);
                // find the smallest VR
                for(int virReg = 1; virReg < 100; virReg++){
                    if(!used.contains("VR" + virReg)) {
                        coloring.put(entry.getKey(), "VR" + virReg);
                        break;
                    }
                }
            }
            // if it can be colored in 8 colors
            else {
                for (String R : pool) {
                    if (!used.contains(R)) {
                        coloring.put(entry.getKey(), R);
                        int tmp = Integer.parseInt(R.substring(1));
                        if (tmp > regNum) regNum = tmp;
                        break;
                    }
                }
            }
        }
        f.setRegNum(regNum);
    }

    // map the register to the IR and eliminate phi function
    private int phiElimination() {
        for(Map.Entry<Integer, Block> entry : blocks.entrySet()){
            for(Statement statement : entry.getValue().getStatements()){
                if(!statement.isValid()) continue;
                boolean flag = false;
                String opcode = statement.getOpcode();
                // firstly, renew the symbols no matter the opcode
                List<String> symbols = statement.getSymbols();
                System.out.println("id: " + statement.getIndex() + " opcode: " + opcode + " symbols: " + symbols);
                for(int i = 0; i < symbols.size(); i++) {
                    String symbol = symbols.get(i);
                    if (symbol != null && symbol.charAt(0) == '(' && coloring.containsKey(symbol)) {
                        symbols.set(i, coloring.get(symbol));
                        flag = true;
                    }
                }
                if(flag) statement.setSymbols(symbols);
                // change do operations according to different opcode
                if(opcode.equals("phi")){
                    entry.getValue().subCount();
                    statement.setPointer("phiEliminated");
                    String RX = coloring.get("(" + statement.getIndex() + ")");
                    // compare the left branch
                    if(!symbols.get(0).equals(RX)) {
                        Statement sta = new Statement(curStaId++, "move");
                        sta.addSymbol(symbols.get(0));
                        sta.addSymbol(RX);
                        blocks.get(Collections.min(redges.get(entry.getKey()))).addStatement(sta);
                    }
                    // compare the right branch
                    if(!symbols.get(1).equals(RX)) {
                        Statement sta = new Statement(curStaId++, "move");
                        sta.addSymbol(symbols.get(1));
                        sta.addSymbol(RX);
                        blocks.get(Collections.max(redges.get(entry.getKey()))).addStatement(sta);
                    }
                }
                // for some special opcode, add the symbol in the front
                if(opcode.equals("add") || opcode.equals("sub") || opcode.equals("mul") || opcode.equals("div")
                        || opcode.equals("cmp") || opcode.equals("adda") || opcode.equals("load") || opcode.equals("read") || opcode.equals("call")){
                    statement.addSymbol_front(coloring.get("(" + statement.getIndex() + ")"));
                }
            }
        }
        return curStaId;
    }



    public int allocate() throws StrongSymbolMissing {
        getRA();
        System.out.println("cost function: ");
        for(Map.Entry<String, Double> entry : costFunc.entrySet()) {
            System.out.println("key = " + entry.getKey() + ", value = " + entry.getValue());
        }
        coloring();
        System.out.println("coloring map: ");
        for(Map.Entry<String, String> entry : coloring.entrySet()) {
            System.out.println("key = " + entry.getKey() + ", value = " + entry.getValue());
        }
        return phiElimination();
        //return 0;
    }

//    public void showBlockCount(){
//        for(Map.Entry<Integer, Block> entry : blocks.entrySet()){
//            entry.getValue().showcount();
//        }
//    }

}
