import java.util.ArrayList;
import java.util.List;

public class Statement {
    private int index;
    private String opcode;
    private List<String> symbols;
    private String pointer;

    public Statement(){
        symbols = new ArrayList<>();
    }
    public Statement(int index) {
        this.index = index;
        this.pointer = "(" + index + ")";
        symbols = new ArrayList<>();
    }
    public Statement(String opcode) {
        this.opcode = opcode;
        symbols = new ArrayList<>();
    }
    public Statement(int index, String opcode){
        this.index = index;
        this.opcode = opcode;
        this.pointer = "(" + index + ")";
        symbols = new ArrayList<>();
    }
    public void setIndex(int index){
        this.index = index;
        this.pointer = "(" + index + ")";
    }
    public void setOpcode(String opcode){
        this.opcode = opcode;
    }
    public void setSymbols(List<String> symbols){
        this.symbols = new ArrayList<>(symbols);
    }
    public void modifySymbol(int index, String symbol){
        symbols.set(index, symbol);
    }
    public void addSymbol(String symbol){
        symbols.add(symbol);
    }
    public void addSymbol_front(String symbol){
        symbols.add(0, symbol);
    }
    public void deleteSymbol(){
        symbols.remove(0);
    }
    public String getOpcode(){
        return opcode;
    }
    public List<String> getSymbols(){
        return symbols;
    }
    public String getStatement(){
        StringBuilder res = new StringBuilder(String.valueOf(index) + " : " + opcode);
        for(int i = 0; i < symbols.size(); i++){
            res.append(" " + symbols.get(i));
        }
        return res.toString();
    }
    public int getIndex(){ return index;}
    public boolean isValid(){
        if(pointer.contains("#")){
            return false;
        }
        else{
            return pointer.substring(1, pointer.length()-1).equals(String.valueOf(index));
        }
    }

    public String getSubscript(int index){
        // var_num
        if (symbols.get(index).contains("_")){
            int underscore = symbols.get(index).indexOf("_");
            return symbols.get(index).substring(underscore+1);
        }
        // (num), #num or function name(string)
        else{
            return symbols.get(index);
        }
    }
    public void setPointer(String point){
        pointer = point;
    }
    public String getPointer(){
        return pointer;
    }
}