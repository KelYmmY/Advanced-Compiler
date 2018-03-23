import java.util.*;

// block可以简化，在block类中index属性暂时没用
// 因为block的index在function的存储中使用了
public class Block {
    private int index;
    private int count;
    private int feature;
    private int times;      // used in the while block
    private TreeSet<String> initialRA;
    private List<Statement> statements;
    private Map<String, String> SSA;
    private Map<String, LinkedList<Statement>> anchor;
    private int jumpTo;     // store the index of the machine code generated for the first valid statement in the block

    public Block(int index){
        this.index = index;
        this.count = 0;
        this.feature = 0;
        this.times = 2;
        this.statements = new LinkedList<>();
        this.SSA = new HashMap<>();
        this.initialRA = new TreeSet<>();
        jumpTo = -1;
        anchor = new HashMap<>();
        anchor.put("add", new LinkedList<>());
        anchor.put("sub", new LinkedList<>());
        anchor.put("div", new LinkedList<>());
        anchor.put("mul", new LinkedList<>());
        anchor.put("adda", new LinkedList<>());
        anchor.put("load", new LinkedList<>());
    }
    public Block(int index, int feature){
        this.index = index;
        this.count = 0;
        this.times = 2;
        this.feature = feature;
        this.statements = new LinkedList<>();
        this.SSA = new HashMap<>();
        this.initialRA = new TreeSet<>();
        jumpTo = -1;
        anchor = new HashMap<>();
        anchor.put("add", new LinkedList<>());
        anchor.put("sub", new LinkedList<>());
        anchor.put("div", new LinkedList<>());
        anchor.put("mul", new LinkedList<>());
        anchor.put("adda", new LinkedList<>());
        anchor.put("load", new LinkedList<>());
    }
    // flag is true -> the true last block
    public boolean initialRA(boolean flag){
        //System.out.println("count: " + count);
        if(flag && count > 1) {
            int tmp = 2;
            for (int i = statements.size()-1; i >= 0; i--){
                if(statements.get(i).isValid()) {
                    tmp--;
                    if(tmp == 0) {
                        initialRA.add("(" + statements.get(i).getIndex() + ")");
                        //System.out.println("initial RA: " + statements.get(i).getIndex());
                        break;
                    }
                }
            }
            return true;
        }
        if(!flag){
            for (int i = statements.size()-1; i >= 0; i--){
                if(statements.get(i).isValid()) {
                    initialRA.add("(" + statements.get(i).getIndex() + ")");
                    break;
                }
            }
        }
        return false;
    }
    public void subCount(){
        this.count--;
    }
    public boolean isValid(){
        //System.out.println("block: " + index + "  count: " + count);
        return this.count != 0;
    }
    public void addStatement(Statement statement){
        // if the block is empty, add statement directly
        if(statements.size() == 0){
            this.statements.add(statement);
            this.count++;
            return;
        }
        // if the last statement is branch statement, add it in front of the last one
        String lastOpcode = statements.get(statements.size()-1).getOpcode();
        if(lastOpcode.equals("bra") || lastOpcode.equals("bne") || lastOpcode.equals("beq") || lastOpcode.equals("ble")
                || lastOpcode.equals("blt") || lastOpcode.equals("bge") || lastOpcode.equals("bgt"))
            statements.add(statements.size()-1, statement);
            // else add it directly
        else this.statements.add(statement);
        this.count++;
    }
    public void addStatement_front(Statement statement){
        this.statements.add(0, statement);
        this.count++;
    }
    public Integer getIndex(){
        return index;
    }
    public List<Statement> getStatements(){
        return statements;
    }
    public Statement getLastStatement(){
        return statements.get(statements.size()-1);
    }
    public List<Statement> getValidStatements(){
        List<Statement> tmp = new ArrayList<>();
        Iterator<Statement> iter = statements.iterator();
        while(iter.hasNext()){
            Statement stat = iter.next();
            if(stat.isValid()){
                tmp.add(stat);
            }
        }
        return tmp;
    }
    public void setSSA(Map<String, String> SSA){
        this.SSA = new HashMap<>(SSA);
    }
    public void addSSAValue(String key, String value){
        SSA.put(key, value);
    }
    public boolean checkSSAValue(String key){
        return SSA.containsKey(key);
    }
    public Integer getFeature(){
        return feature;
    }
    public Map<String, String> getSSA(){
        return SSA;
    }
    public String getSSAValue(String key){
        return SSA.get(key);
    }
    public List<String> getBlock() {
        List<String> res = new ArrayList<>();
        for(int i = 0; i < statements.size(); i++){
            Statement tmp = statements.get(i);
            if(tmp.isValid()) {
                res.add(tmp.getStatement());
            }
        }
        return res;
    }
    public Map<String, LinkedList<Statement>> getAnchor(){
        return anchor;
    }
    public void setAnchor(Map<String, LinkedList<Statement>> anchor){
        this.anchor.put("add", new LinkedList<>(anchor.get("add")));
        this.anchor.put("sub", new LinkedList<>(anchor.get("sub")));
        this.anchor.put("div", new LinkedList<>(anchor.get("div")));
        this.anchor.put("mul", new LinkedList<>(anchor.get("mul")));
        this.anchor.put("adda", new LinkedList<>(anchor.get("adda")));
        this.anchor.put("load", new LinkedList<>(anchor.get("load")));
    }
    // if flag is true, ignore the phi function while renew identifier
    public void renewIdentifier(Map<String, String> replacement, boolean flag){
        for(int i = 0; i < statements.size(); i++){
            Statement sta = statements.get(i);
            if(flag) if(sta.getOpcode().equals("phi")) continue;
            List<String> symbols = sta.getSymbols();
            for(int j = 0; j < symbols.size(); j++){
                String symbol = symbols.get(j);
                if(replacement.containsKey(symbol))
                    symbols.set(j, replacement.get(symbol));
            }
            sta.setSymbols(symbols);
        }
    }
    public void setInitialRA(TreeSet<String> ra){
        Iterator tmp = ra.iterator();
        while(tmp.hasNext()){
            initialRA.add(String.valueOf(tmp.next()));
        }
    }
    public TreeSet<String> getInitialRA(){
        return initialRA;
    }
    public int getTimes(){
        return times;
    }
    public void subTimes(){
        times -= 1;
    }
    public List<Statement> getReverseBlock(){
        List<Statement> res = new ArrayList<>();
        for(int i = statements.size() - 1; i > -1; i--){
            Statement tmp = statements.get(i);
            if(tmp.isValid()) {
                res.add(tmp);
            }
        }
        return res;
    }
    public void setJumpTo(int index){
        jumpTo = index;
    }
    public int getJumpTo(){
        return jumpTo;
    }
}