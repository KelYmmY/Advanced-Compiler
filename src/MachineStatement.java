import java.util.ArrayList;
import java.util.List;

public class MachineStatement {
    private int opcode;
    private List<Integer> symbols;
    public MachineStatement(int opcode){
        this.opcode = opcode;
        this.symbols = new ArrayList<>();
    }
    public MachineStatement(int opcode, int a){
        this.opcode = opcode;
        this.symbols = new ArrayList<>(List.of(a));
    }
    public MachineStatement(int opcode, int a, int b){
        this.opcode = opcode;
        this.symbols = new ArrayList<>(List.of(a, b));
    }
    public MachineStatement(int opcode, int a, int b, int c){
        this.opcode = opcode;
        this.symbols = new ArrayList<>(List.of(a, b, c));
    }
    public void setOpcode(int opcode) {
        this.opcode = opcode;
    }
    public void setSymbol(List<Integer> symbols){
        this.symbols = symbols;
    }
    public int getOpcode(){
        return this.opcode;
    }
    public List<Integer> getSymbols(){
        return this.symbols;
    }
    public String getMachineStatement(){
        int code = -1;
        int size = symbols.size();
        switch (size){
            case 0: code = DLX.assemble(opcode);break;
            case 1: code = DLX.assemble(opcode, symbols.get(0));break;
            case 2: code = DLX.assemble(opcode, symbols.get(0), symbols.get(1));break;
            case 3: code = DLX.assemble(opcode, symbols.get(0), symbols.get(1), symbols.get(2));break;
        }
        return DLX.disassemble(code);
    }
}