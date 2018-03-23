import java.util.*;
import java.io.IOException;

public class MachineCodeGeneration {
    private List<MachineStatement> machineCode;
    private boolean flag; // to mark whether it is a main function; true = main function
    private TreeMap<Integer, Block> blocks;
    // pair: key -> mCode inserted index, val -> targeted jump block
    private Map<Integer, Statement> jumps;
    private TreeMap<Integer, Set<Integer>> edges;
    private Map<String, Integer> baseMap;
    private int RegNum;
    private List<String> defParameter;
    private Map<Integer, String> calls;

    public MachineCodeGeneration(Function f, boolean flag){
        machineCode = new ArrayList<>();
        this.flag = flag;
        RegNum = f.getRegNum();
        defParameter = f.getDefParameters();
        blocks = new TreeMap<>();
        blocks = f.getBlocks();
        jumps = new HashMap<>();
        edges = new TreeMap<>();
        baseMap = f.getBaseMap();
        edges = f.getEdges();
        baseMap = f.getBaseMap();
        calls = new HashMap<>();
    }

    public static void Execution(List<MachineStatement> mCode) throws IOException{
        int [] code = new int [mCode.size()];
        for(int i = 0; i < mCode.size(); i++){
            MachineStatement mcode = mCode.get(i);
            int size = mcode.getSymbols().size();
            switch (size){
                case 0: code[i] = DLX.assemble(mcode.getOpcode());break;
                case 1: code[i] = DLX.assemble(mcode.getOpcode(), mcode.getSymbols().get(0));break;
                case 2: code[i] = DLX.assemble(mcode.getOpcode(), mcode.getSymbols().get(0), mcode.getSymbols().get(1));break;
                case 3: code[i] = DLX.assemble(mcode.getOpcode(), mcode.getSymbols().get(0), mcode.getSymbols().get(1), mcode.getSymbols().get(2));break;
                default: System.out.println("Machine Statement: wrong statement");
            }
        }
        DLX.load(code);
        DLX.execute();
    }

    public void GenerateAll() throws IOException{
        // let the stack pointer to be above the array area
        int base;
        if(baseMap.isEmpty()) base = 0;
        else base = baseMap.get("end");
        machineCode.add(new MachineStatement(DLX.ADDI, 29, 28, base));

        for(Map.Entry<Integer, Block> entry: blocks.entrySet()){
            Generate(entry.getValue());
        }
        // handle bra and bge alike statements
        for(Map.Entry<Integer, Statement> entry: jumps.entrySet()){
            int index = entry.getKey();
            String tmp = entry.getValue().getSymbols().get(entry.getValue().getSymbols().size() - 1);
            int jump = Integer.parseInt(tmp.substring(1, tmp.length()-1));
            int jumpSymbol = blocks.get(jump).getJumpTo() - index;

            if(entry.getValue().getOpcode().equals("bra")){
                machineCode.set(index, new MachineStatement(DLX.BEQ, 0, jumpSymbol));
            }
            // deal with bne, beq, ble, blt, bge, bgt
            else{
                String op0 = entry.getValue().getSymbols().get(0);
                int flag0 = typeHelper(op0, true).get(0);
                int symbol0 = typeHelper(op0, true).get(1);
                MachineStatement sta = new MachineStatement(-1, symbol0, jumpSymbol);
                switch(entry.getValue().getOpcode()){
                    case "bne": sta.setOpcode(DLX.BNE);break;
                    case "beq": sta.setOpcode(DLX.BEQ);break;
                    case "ble": sta.setOpcode(DLX.BLE);break;
                    case "blt": sta.setOpcode(DLX.BLT);break;
                    case "bge": sta.setOpcode(DLX.BGE);break;
                    case "bgt": sta.setOpcode(DLX.BGT);break;
                }
                machineCode.set(index, sta);
            }
        }
    }


    private void Generate(Block block){
        // store the index of the machine code generated for the first valid statement in this block
        block.setJumpTo(machineCode.size());
        List<Statement> stats = new ArrayList<>(block.getValidStatements());
        for(int i = 0; i < stats.size(); i++){
            Statement statement = stats.get(i);
            String opcode = statement.getOpcode();
            if(opcode.equals("add") || opcode.equals("adda") || opcode.equals("sub") || opcode.equals("mul")  || opcode.equals("div") || opcode.equals("cmp")){
                String op0 = statement.getSymbols().get(0);
                String op1 = statement.getSymbols().get(1);
                String op2 = statement.getSymbols().get(2);

                int flag0 = typeHelper(op0, false).get(0);
                int flag1 = typeHelper(op1, true).get(0);
                int flag2 = typeHelper(op2, true).get(0);
                int symbol0 = typeHelper(op0, false).get(1);
                int symbol1 = typeHelper(op1, true).get(1);
                int symbol2 = typeHelper(op2, true).get(1);


                // deal with special FP case: add R1 FP abase
                if(opcode.equals("add") && op1.equals("FP")){
                    machineCode.add(new MachineStatement(DLX.ADDI, symbol0, 28, baseMap.get(op2)));
                }
                // both of the operators are register
                else if(flag1 > 0 && flag2 > 0){
                    MachineStatement sta = new MachineStatement(-1, symbol0, symbol1, symbol2);
                    switch(opcode){
                        case "add":
                        case "adda": sta.setOpcode(DLX.ADD);break;
                        case "sub": sta.setOpcode(DLX.SUB);break;
                        case "mul": sta.setOpcode(DLX.MUL);break;
                        case "div": sta.setOpcode(DLX.DIV);break;
                        case "cmp": sta.setOpcode(DLX.CMP);break;
                    }
                    machineCode.add(sta);
                }
                // both of the operators are numbers
                else if(flag1 == 0 && flag2 == 0){
                    machineCode.add(new MachineStatement(DLX.ADDI, symbol0, 0, symbol1));
                    MachineStatement sta = new MachineStatement(-1, symbol0, symbol0, symbol2);
                    switch(opcode){
                        case "add":
                        case "adda": sta.setOpcode(DLX.ADDI);break;
                        case "sub": sta.setOpcode(DLX.SUBI);break;
                        case "mul": sta.setOpcode(DLX.MULI);break;
                        case "div": sta.setOpcode(DLX.DIVI);break;
                        case "cmp": sta.setOpcode(DLX.CMPI);break;
                    }
                    machineCode.add(sta);
                }
                // one register and one number
                else {
                    if (flag1 == 0) {
                        if (opcode.equals("add") || opcode.equals("adda") || opcode.equals("mul")) {
                            MachineStatement sta = new MachineStatement(-1, symbol0, symbol2, symbol1);
                            if (!opcode.equals("mul")) sta.setOpcode(DLX.ADDI);
                            else sta.setOpcode(DLX.MULI);
                            machineCode.add(sta);
                        }
                        else {
                            machineCode.add(new MachineStatement(DLX.ADDI, 9, 0, symbol1));
                            MachineStatement sta = new MachineStatement(-1, symbol0, 9, symbol2);
                            if (opcode.equals("sub")) sta.setOpcode(DLX.SUB);
                            else if(opcode.equals("div")) sta.setOpcode(DLX.DIV);
                            else sta.setOpcode(DLX.CMP);
                            machineCode.add(sta);
                        }
                    }
                    else {
                        MachineStatement sta = new MachineStatement(-1, symbol0, symbol1, symbol2);
                        switch (opcode) {
                            case "add":
                            case "adda": sta.setOpcode(DLX.ADDI);break;
                            case "sub": sta.setOpcode(DLX.SUBI);break;
                            case "mul": sta.setOpcode(DLX.MULI);break;
                            case "div": sta.setOpcode(DLX.DIVI);break;
                            case "cmp": sta.setOpcode(DLX.CMPI);break;
                        }
                        machineCode.add(sta);
                    }
                }
                if(flag0 == 2) VirtualRegHelper(op0, true);
            }
            else if(opcode.equals("load")){
                String op0 = statement.getSymbols().get(0);
                String op1 = statement.getSymbols().get(1);

                int flag0 = typeHelper(op0, false).get(0);
                int flag1 = typeHelper(op1, true).get(0);
                int symbol0 = typeHelper(op0, false).get(1);
                int symbol1 = typeHelper(op1, true).get(1);

                // write the load machine statement
                if(flag1 == 0) machineCode.add(new MachineStatement(DLX.LDW, symbol0, 0, symbol1));
                else machineCode.add(new MachineStatement(DLX.LDW, symbol0, symbol1, 0));
                // do the operation of op0 is virtual register
                if(flag0 == 2) VirtualRegHelper(op0, true);
            }
            else if(opcode.equals("store")){
                String op0 = statement.getSymbols().get(0);
                String op1 = statement.getSymbols().get(1);
                int flag0 = typeHelper(op0, true).get(0);
                int flag1 = typeHelper(op1, true).get(0);
                int symbol0 = typeHelper(op0, true).get(1);
                int symbol1 = typeHelper(op1, true).get(1);
                // write the store machine statement
                // if the two symbols are numbers
                if(flag0 == 0 && flag1 == 0){
                    machineCode.add(new MachineStatement(DLX.ADDI, 9, 0, symbol0));
                    machineCode.add(new MachineStatement(DLX.STW, 9, 0, symbol1));
                }
                else if(flag0 != 0 && flag1 != 0){
                    machineCode.add(new MachineStatement(DLX.STW, symbol0, symbol1, 0));
                }
                else{
                    if(flag0 == 0){
                        machineCode.add(new MachineStatement(DLX.ADDI, 9, 0, symbol0));
                        machineCode.add(new MachineStatement(DLX.STW, 9, symbol1, 0));
                    }
                    else machineCode.add(new MachineStatement(DLX.STW, symbol0, 0, symbol1));
                }
            }
            else if(opcode.equals("move")){
                String op0 = statement.getSymbols().get(0);
                String op1 = statement.getSymbols().get(1);
                int flag0 = typeHelper(op0, true).get(0);
                int flag1 = typeHelper(op1, false).get(0);
                int symbol0 = typeHelper(op0, true).get(1);
                int symbol1 = typeHelper(op1, false).get(1);
                if(flag0 > 0 && flag1 > 0){
                    machineCode.add(new MachineStatement(DLX.ADDI, symbol1, symbol0, 0));
                }
                // one register, one number
                else{
                    machineCode.add(new MachineStatement(DLX.ADDI, symbol1, 0, symbol0));
                }
                if(flag1 == 2) VirtualRegHelper(op1, true);
            }
            else if(opcode.equals("bra") || opcode.equals("bne") || opcode.equals("beq") || opcode.equals("ble") || opcode.equals("blt") || opcode.equals("bge") || opcode.equals("bgt")){
                String tmp = statement.getSymbols().get(statement.getSymbols().size()-1);
                int jump = Integer.parseInt(tmp.substring(1, tmp.length()-1));

                // if jump points to a empty block, then modify jump to point to its non-empty children block
                if(!blocks.get(jump).isValid()){
                    while(!blocks.get(jump).isValid()){
                        Set<Integer> linkedBlock = new HashSet<>(edges.get(blocks.get(jump).getIndex()));
                        Iterator iter = linkedBlock.iterator();
                        jump = Integer.parseInt(String.valueOf(iter.next()));
                    }
                }
                statement.modifySymbol(stats.get(i).getSymbols().size()-1, "(" + jump + ")");
                //System.out.println("jump: " + jump);

                // save the space in the machine code and modify it later
                machineCode.add(new MachineStatement(-1));
                jumps.put(machineCode.size()-1, statement);
            }
            else if(opcode.equals("read")){
                String op0 = statement.getSymbols().get(0);
                int flag0 = typeHelper(op0, false).get(0);
                int symbol0 = typeHelper(op0, false).get(1);
                machineCode.add(new MachineStatement(DLX.RDI, symbol0));
                if(flag0 == 2) VirtualRegHelper(op0, true);
            }
            else if(opcode.equals("write")){
                String op0 = statement.getSymbols().get(0);
                int flag0 = typeHelper(op0, true).get(0);
                int symbol0 = typeHelper(op0, true).get(1);
                if (flag0 == 0){
                    machineCode.add(new MachineStatement(DLX.ADDI, 9, 0, symbol0));
                    machineCode.add(new MachineStatement(DLX.WRD, 9));
                }
                else {
                    machineCode.add(new MachineStatement(DLX.WRD, symbol0));
                }
            }
            else if(opcode.equals("writeNL")){
                machineCode.add(new MachineStatement(DLX.WRL));
            }
            else if(opcode.equals("end")){
                if(flag) machineCode.add(new MachineStatement(DLX.RET, 0));
                else machineCode.add(new MachineStatement(DLX.RET, 31));
            }
            else if(opcode.equals("call")){
                List<String> symbols = stats.get(i).getSymbols();
                // push the frame pointer
                machineCode.add(new MachineStatement(DLX.PSH, 28, 29, 4));
                // push the current parameters to the stack if any
                for(int k = 0; k < defParameter.size(); k++){
                    machineCode.add(new MachineStatement(DLX.PSH, k+11, 29, 4));
                }
                // push the register currently used to the stack
                for(int k = 0; k < RegNum; k++){
                    machineCode.add(new MachineStatement(DLX.PSH, k+1, 29, 4));
                }
                // push the R31 to the stack
                machineCode.add(new MachineStatement(DLX.PSH, 31, 29, 4));
                // save the parameters to the register if any
                for(int k = 2; k < symbols.size(); k++){
                    String op0 = symbols.get(k);
                    int flag0 = typeHelper(op0, true).get(0);
                    int symbol0 = typeHelper(op0, true).get(1);
                    if(flag0 == 0) machineCode.add(new MachineStatement(DLX.ADDI, 9+k,0, symbol0));
                    else machineCode.add(new MachineStatement(DLX.ADDI, 9+k, symbol0, 0));
                }
                // change the frame pointer
                machineCode.add(new MachineStatement(DLX.ADDI, 28, 29, 4));
                // leave the place to the jump statement
                machineCode.add(new MachineStatement(-2));
                calls.put(machineCode.size()-1,symbols.get(1));
                // change the stack pointer
                machineCode.add(new MachineStatement(DLX.ADDI, 29, 28, -4));
                // pop the R31 to the stack
                machineCode.add(new MachineStatement(DLX.POP, 31, 29, -4));
                // pop the register currently used from the stack
                for(int k = RegNum-1; k >=0 ; k--){
                    machineCode.add(new MachineStatement(DLX.POP, k+1, 29, -4));
                }
                // push the current parameters to the stack if any
                for(int k = defParameter.size()-1; k >= 0; k--){
                    machineCode.add(new MachineStatement(DLX.POP, k+11, 29, -4));
                }
                // clear the space of variables and virtual registers
                machineCode.add(new MachineStatement(DLX.POP, 28,29,-4));
                // move the return value to the register if any
                if (symbols.get(0) != null){
                    int R = Integer.parseInt(symbols.get(0).substring(1));
                    machineCode.add(new MachineStatement(DLX.ADDI, R, 10, 0));
                }
            }
            else if(opcode.equals("return")){
                // put the return num to the R10
                String op0 = statement.getSymbols().get(0);
                int flag0 = typeHelper(op0, true).get(0);
                int symbol0 = typeHelper(op0, true).get(1);
                if (flag0 == 0){
                    machineCode.add(new MachineStatement(DLX.ADDI, 10, 0, symbol0));
                }
                else {
                    machineCode.add(new MachineStatement(DLX.ADDI, 10, symbol0, 0));
                }
            }
        }
    }

    // the first return is flag, and the second return is actual symbol
    private List<Integer> typeHelper(String symbol, boolean flag){
        List<Integer> result = new ArrayList<>();
        // if the symbol begin with #: number & parameter
        if(symbol.charAt(0) == '#') {
            int index = defParameter.indexOf(symbol.substring(1));
            if(index == -1) {
                result.add(0);
                result.add(Integer.parseInt(symbol.substring(1)));
            }
            else {
                result.add(1);
                result.add(11+index);
            }
        }
        // if the symbol begin with R: register
        else if (symbol.charAt(0) == 'R') {
            result.add(1);
            result.add(Integer.parseInt(symbol.substring(1)));
        }
        else if (symbol.substring(0,2).equals("VR")) {
            result.addAll(List.of(2, 9));
            if(flag) VirtualRegHelper(symbol, false);
        }
        else{
            result.addAll(List.of(-1, -1));
        }
        return result;
    }

    // flag is true when store; flag is false when load
    private void VirtualRegHelper(String Register, boolean flag) {
        int VR = Integer.parseInt(Register.substring(2));
        System.out.println("VR: " + VR);
        if(flag) machineCode.add(new MachineStatement(DLX.STW, 9, 30,(-4)*VR));
        else machineCode.add(new MachineStatement(DLX.LDW, 9, 30, (-4)*VR));
    }
    public List<MachineStatement> getMachineCode(){
        return machineCode;
    }
    public Map<Integer, String> getCalls(){
        return this.calls;
    }

}
