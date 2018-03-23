import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.*;


public class Parser {
    private String FilePath;
    private String outFilePath;
    private int curStateId; // the current statement id
    private int curBlockId; // the current block id
    private Map<String, Function> functions; // the function library of the file (contain the main function)


    // initialize the current statement and block id
    // new the main function
    public Parser(String FilePath){
        this.FilePath = FilePath;
        outFilePath = FilePath.substring(0, FilePath.length()-4);
        curStateId = 1;
        curBlockId = 0;
        functions = new HashMap<>();
    }

    // use the function to add PHI functions
    // when flag is TRUE means it's used to an IF statement; when flag is FALSE means it's used to a WHILE statement
    // For WHILE LOOP, SSA1 must from the loop, while SSA must from the BB before while loop
    private Map<String, String> addPHI(Block block, boolean flag, Map<String, String> SSA1, Map<String, String> SSA2){
        Map<String, String> replacement = new HashMap<>();
        // handle the keys that contains in two SSA maps
        for(String key : SSA1.keySet()){
            if(SSA2.containsKey(key) && !SSA1.get(key).equals(SSA2.get(key))){
                Statement sta = new Statement(curStateId++, "phi");
                String symbol = key + "_" + sta.getIndex();
                String symbol1 = SSA1.get(key);
                String symbol2 = SSA2.get(key);
                sta.addSymbol(symbol);
                sta.addSymbol(symbol1);
                sta.addSymbol(symbol2);
                replacement.put(symbol1, symbol);
                if(flag) block.addStatement(sta);
                else block.addStatement_front(sta);
                // for WHILE LOOP, put the index
                SSA2.put(key, symbol);
            }
        }
        // when conflicting, the value of SSA2 cover the value of SSA1;
        SSA1.putAll(SSA2);
        block.setSSA(SSA1);
        return replacement;
    }


    // check Designator
    // to make sure that designator is not empty.
    private String checkDesignator(Function function, List<Token> designator) throws StrongSymbolMissing{
        Block block = function.getBlock(curBlockId);
        int size = designator.size(), count = 0;
        // if the designator is a identifier
        if(size == 1 && designator.get(0).getValue() == 61) {
            return designator.get(0).getCharacters();
        }
        // if the designator is an array
        if(size > 3 && designator.get(0).getValue() == 61 && designator.get(1).getValue() == 32){
            // firstly, write the statement "add FP base"
            String arrayName = designator.get(0).getCharacters();
            Statement addSta = new Statement(curStateId++, "add");
            addSta.addSymbol("FP");
            addSta.addSymbol(arrayName + "base");
            block.addStatement(addSta);
            String symbol = "(" + addSta.getIndex() + ")";
            // process the array iteratively
            int index = 1, nested = 1, indexVal = designator.get(index).getValue();
            // search the "]" from the beginning, if the index is "]", do operation
            for(int i = 2; i < size; i++){
                int curVal = designator.get(i).getValue();
                if(curVal == 32) nested++;
                if(curVal == 34) nested--;
                if(nested == 0 && curVal == 34 && indexVal == 32){
                    Statement mul4Sta = new Statement("mul");
                    String tmpSymbol = checkExpression(function, designator.subList(index+1, i));
                    if(tmpSymbol.charAt(0) != '(' && tmpSymbol.charAt(0) != '#') {
                        if(block.checkSSAValue(tmpSymbol)) tmpSymbol = block.getSSAValue(tmpSymbol);
                        else throw new StrongSymbolMissing("Error: Variables should be used after initialization.");
                    }
                    mul4Sta.addSymbol(tmpSymbol);
                    mul4Sta.addSymbol("#4");
                    mul4Sta.setIndex(curStateId++);
                    block.addStatement(mul4Sta);
                    Statement mulLenSta = new Statement(curStateId++, "mul");
                    mulLenSta.addSymbol("(" + mul4Sta.getIndex() + ")");
                    if(function.getArrayMap().containsKey(arrayName))
                        mulLenSta.addSymbol("#" + function.getArrayMap().get(arrayName).get(count++));
                    else throw new StrongSymbolMissing("Error: Array should be used after definition on line " + designator.get(i).getLineNum());
                    block.addStatement(mulLenSta);
                    Statement addaSta = new Statement(curStateId++,"adda");
                    addaSta.addSymbol(symbol);
                    addaSta.addSymbol("(" + mulLenSta.getIndex() + ")");
                    block.addStatement(addaSta);
                    symbol = "(" + addaSta.getIndex() + ")";
                    if(i != size-1) {
                        index = i + 1;
                        indexVal = designator.get(index).getValue();
                    }
                }
            }
            // lastly, write the load statement
            Statement loadSta = new Statement(curStateId++, "load");
            loadSta.addSymbol(symbol);
            block.addStatement(loadSta);
            if(nested != 0) return ""; // Error();
            return "(" + loadSta.getIndex() + ")";
        }
        else{
            throw new StrongSymbolMissing("Error: Invalid designator on line " + designator.get(0).getLineNum());
        }
    }


    // check Factor
    // to make sure that it is not empty
    private String checkFactor(Function function, List<Token> factor) throws StrongSymbolMissing{
        // printList(factor);
        int size = factor.size();
        // check if it's Number
        if(size == 1 && factor.get(0).getValue() == 60)
            return "#" + factor.get(0).getCharacters();
        // check if it's FuncCall
        if(size > 0 && factor.get(0).getValue() == 101)
            return checkFuncCall(function, factor);
        // check if it's designator
        if(size > 0 && factor.get(0).getValue() == 61)
            return checkDesignator(function, factor);
        // check if it's "(" expression ")"
        if(size > 3 && factor.get(0).getValue() == 50 && factor.get(size-1).getValue() == 35)
            return checkExpression(function, factor.subList(1, size-1));
        else{
            throw new StrongSymbolMissing("Error: Invalid factor on line " + factor.get(0).getLineNum());
        }
    }


    // check Term
    // Note to process of the nested parentheses: () and []
    private String checkTerm(Function function, List<Token> term) throws StrongSymbolMissing{
        Block block = function.getBlock(curBlockId);
        // find the last * or / out side of the parentheses
        int size = term.size(), index = -1, parent = 0;
        boolean flag = true; // flag to mark * or /
        for(int i = size - 1; i > -1; i--){
            int curVal = term.get(i).getValue();
            if(curVal == 34 || curVal == 35) parent++;
            if(curVal == 32 || curVal == 50) parent--;
            if(parent == 0 && (curVal == 1 || curVal == 2)){
                index = i;
                flag = curVal == 1;
                break;
            }
        }
        // if no * or / exists
        if(index == -1) return checkFactor(function, term);
        if(index != 0 && index != size-1){
            Statement sta = new Statement();
            if(flag) sta.setOpcode("mul");
            else sta.setOpcode("div");
            // for the front part, check Term again. Ex: a * b * c
            String symbol1 = checkTerm(function, term.subList(0, index));
            String symbol2 = checkFactor(function, term.subList(index+1, size));
            if(symbol1.charAt(0) != '(' && symbol1.charAt(0) != '#') {
                if(block.checkSSAValue(symbol1)) symbol1 = block.getSSAValue(symbol1);
                else throw new StrongSymbolMissing("Error: Variables should be used after initialization.");
            }
            if(symbol2.charAt(0) != '(' && symbol2.charAt(0) != '#') {
                if(block.checkSSAValue(symbol2)) symbol2 = block.getSSAValue(symbol2);
                else throw new StrongSymbolMissing("Error: Variables should be used after initialization.");
            }
            sta.addSymbol(symbol1);
            sta.addSymbol(symbol2);
            sta.setIndex(curStateId++);
            block.addStatement(sta);
            return "(" + sta.getIndex() + ")";
        }
        else{
            throw new StrongSymbolMissing("Error: Invalid term on line " + term.get(0).getLineNum());
        }
    }


    // check Expression
    // Note to process of the nested parentness: () and []
    private String checkExpression(Function function, List<Token> expression) throws StrongSymbolMissing{
        Block block = function.getBlock(curBlockId);
        // find the last + or - out side of the parentness
        int size = expression.size(), index = -1, parent = 0;
        boolean flag = true; // flag to mark + or -
        for(int i = size - 1; i > -1; i--){
            int curVal = expression.get(i).getValue();
            if(curVal == 34 || curVal == 35){ parent++;}
            if(curVal == 32 || curVal == 50){ parent--;}
            if(parent == 0 && (curVal == 11 || curVal == 12)){
                index = i;
                flag = curVal == 11;
                break;
            }
        }
        // if no + or - exists
        if(index == -1) return checkTerm(function, expression);
        if(index != 0 && index != size-1){
            Statement sta = new Statement();
            if(flag) sta.setOpcode("add");
            else sta.setOpcode("sub");
            // for the front part, check Expression again. Ex: a + b + c
            String symbol1 = checkExpression(function, expression.subList(0, index));
            String symbol2 = checkTerm(function, expression.subList(index+1, size));
            if(symbol1.charAt(0) != '(' && symbol1.charAt(0) != '#') {
                if(block.checkSSAValue(symbol1)) symbol1 = block.getSSAValue(symbol1);
                else throw new StrongSymbolMissing("Error: Variables should be used after initialization.");
            }
            if(symbol2.charAt(0) != '(' && symbol2.charAt(0) != '#') {
                if(block.checkSSAValue(symbol2)) symbol2 = block.getSSAValue(symbol2);
                else throw new StrongSymbolMissing("Error: Variables should be used after initialization.");
            }
            sta.addSymbol(symbol1);
            sta.addSymbol(symbol2);
            sta.setIndex(curStateId++);
            block.addStatement(sta);
            return "(" + sta.getIndex() + ")";
        }
        else{
            throw new StrongSymbolMissing("Error: Invalid expression on line " + expression.get(0).getLineNum());
        }
    }


    // check Relation
    private void checkRelation(Function function, List<Token> relation) throws StrongSymbolMissing{
        Block block = function.getBlock(curBlockId);
        // find the ONLY relOp
        int size = relation.size(), index = -1;
        for(int i = 0; i < size; i++){
            if(index == -2) break;
            if(relation.get(i).getValue() > 19 && relation.get(i).getValue() < 26){
                if(index == -1) index = i;
                else index = -2;
            }
        }
        // if the ONLY relOp exists
        if(index > 0 && index != size-1){
            Statement cmpSta = new Statement("cmp");
            String symbol1 = checkExpression(function, relation.subList(0, index));
            String symbol2 = checkExpression(function, relation.subList(index+1, size));
            if(symbol1.charAt(0) != '(' && symbol1.charAt(0) != '#') {
                if(block.checkSSAValue(symbol1)) symbol1 = block.getSSAValue(symbol1);
                else throw new StrongSymbolMissing("Error: Variables should be used after initialization.");
            }
            if(symbol2.charAt(0) != '(' && symbol2.charAt(0) != '#') {
                if(block.checkSSAValue(symbol2)) symbol2 = block.getSSAValue(symbol2);
                else throw new StrongSymbolMissing("Error: Variables should be used after initialization.");
            }
            cmpSta.addSymbol(symbol1);
            cmpSta.addSymbol(symbol2);
            cmpSta.setIndex(curStateId++);
            block.addStatement(cmpSta);
            Statement bSta = new Statement(curStateId++);
            int relVal = relation.get(index).getValue();
            if(relVal == 20) bSta.setOpcode("bne");
            else if(relVal == 21) bSta.setOpcode("beq");
            else if(relVal == 22) bSta.setOpcode("bge");
            else if(relVal == 23) bSta.setOpcode("blt");
            else if(relVal == 24) bSta.setOpcode("bgt");
            else bSta.setOpcode("ble");
            bSta.addSymbol("(" + cmpSta.getIndex() + ")");
            block.addStatement(bSta);
        }
        else{
            throw new StrongSymbolMissing("Error: Invalid comparision relationship on line " + relation.get(0).getLineNum());
        }
    }


    // check Assignment
    // the Assignment here must started with "let"
    private void checkAssignment(Function function, List<Token> assignment) throws StrongSymbolMissing{
        Block block = function.getBlock(curBlockId);
        // find the ONLY "<-"
        int size = assignment.size(), index = -1;
        for(int i = 0; i < size; i++){
            if(index == -2) break;
            if(assignment.get(i).getValue() == 40){
                if(index == -1) index = i;
                else index = -2;
            }
        }
        // if the ONLY "<-" exists
        // here consider the Array operation
        if(index > 0 && index != size-1){
            String symbol1 = checkExpression(function, assignment.subList(index+1, size));
            String symbol2 = checkDesignator(function, assignment.subList(1, index));
            if(symbol1.charAt(0) != '(' && symbol1.charAt(0) != '#') {
                if(block.checkSSAValue(symbol1)) symbol1 = block.getSSAValue(symbol1);
                else throw new StrongSymbolMissing("Error: Variables should be used after initialization.");
            }
            // if the designator is array, then change the last statement into STORE statement
            if(symbol2.charAt(0) == '('){
                Statement sta = block.getLastStatement();
                sta.setOpcode("store");
                sta.addSymbol_front(symbol1);
            }
            // if the designator is identifier, then add MOVE statement
            else{
                Statement sta = new Statement(curStateId++,"move");
                sta.addSymbol(symbol1);
                sta.addSymbol(symbol2 + "_" + String.valueOf(curStateId-1));
                block.addStatement(sta);
                block.addSSAValue(symbol2, symbol2 + "_" + String.valueOf(curStateId-1));
            }
        }
        //Error(): invalid assignment stat
        else{
            throw new StrongSymbolMissing("Error: Invalid assignment on line " + assignment.get(0).getLineNum());
        }
    }


    // check IF Statement
    // the IF Statement here must started with "if" and end with "fi"
    private void checkIfStatement(Function function, List<Token> ifStatement) throws StrongSymbolMissing{
        // find the ONLY "then", and the POSSIBLE ONLY "else"
        // Note: the if statement may be nested
        int size = ifStatement.size(), thenId = -1, elseId = -1, nested = 0, errorIndex = -1;
        for(int i = 0; i < size; i++){
            if(thenId == -2 || elseId == -2) break;
            int curVal = ifStatement.get(i).getValue();
            if(curVal == 102) nested++;
            if(curVal == 82) nested--;
            if(nested == 1){
                if(curVal == 41){
                    if(thenId == -1) thenId = i;
                    else {
                        thenId = -2;
                        errorIndex = ifStatement.get(i).getLineNum();
                    }
                }
                if(curVal == 90){
                    if(elseId == -1) elseId = i;
                    else {
                        elseId = -2;
                        errorIndex = ifStatement.get(i).getLineNum();
                    }
                }
            }
        }
        // if the ONLY "then" exits, and "else" appears none or once
        if(nested == 0 && thenId > 1 && thenId != size-1 && elseId > -2){
            // add the comp block as the head of if statement
            function.addBlock(++curBlockId,3);
            function.addRelation(curBlockId-1, curBlockId);
            function.getBlock(curBlockId).setSSA(function.getBlock(curBlockId-1).getSSA());
            int index = curBlockId;
            // write the relation statement in the comp block
            checkRelation(function, ifStatement.subList(1, thenId));
            // add the left block and initiate the SSA
            function.addBlock(++curBlockId);
            function.addRelation(curBlockId-1, curBlockId);
            function.getBlock(curBlockId).setSSA(function.getBlock(index).getSSA());
            // if the "else" do not exists
            if(elseId == -1){
                /*
                // write the state sequence in the left block
                checkStatSequence(function, ifStatement.subList(thenId+1, size-1));
                Statement sta = new Statement(curStateId++, "bra");
                function.getBlock(curBlockId).addStatement(sta);
                int leftIndex = curBlockId;
                // add the right block and initiate the SSA;
                function.addBlock(++curBlockId);
                function.addRelation(index, curBlockId);
                function.getBlock(curBlockId).setSSA(function.getBlock(index).getSSA());
                // add the join block
                function.addBlock(++curBlockId,1);
                function.addRelation(curBlockId-1, curBlockId);
                function.addRelation(leftIndex, curBlockId);
                // complete the branch statement in the comp block
                function.getBlock(index).getLastStatement().addSymbol("[" + (leftIndex+1) + "]");
                // complete the branch statement in the left block
                function.getBlock(leftIndex).getLastStatement().addSymbol("[" + (curBlockId) + "]");
                // add phi functions and renew the SSA form;
                Block joinBlock = function.getBlock(curBlockId);
                addPHI(joinBlock, true, function.getBlock(leftIndex).getSSA(), function.getBlock(curBlockId-1).getSSA());*/


                // write the state sequence in the left block
                checkStatSequence(function, ifStatement.subList(thenId+1, size-1));
                Statement sta = new Statement(curStateId++, "bra");
                function.getBlock(curBlockId).addStatement(sta);
                int leftIndex = curBlockId;
                // add an empty right block and initiate the SSA
                function.addBlock(++curBlockId);
                function.addRelation(index, curBlockId);
                function.getBlock(curBlockId).setSSA(function.getBlock(index).getSSA());
                // add the join block and initiate the SSA
                function.addBlock(++curBlockId, 1);
                function.addRelation(curBlockId-1, curBlockId);
                function.addRelation(leftIndex, curBlockId);
                // complete the branch statement in the comp block
                function.getBlock(index).getLastStatement().addSymbol("[" + (leftIndex+1) + "]");
                // complete the branch statement in the left block
                function.getBlock(leftIndex).getLastStatement().addSymbol("[" + (curBlockId) + "]");
                // add phi functions and renew the SSA form;
                Block joinBlock = function.getBlock(curBlockId);
                addPHI(joinBlock, true, function.getBlock(leftIndex).getSSA(), function.getBlock(curBlockId-1).getSSA());
            }
            // if the ONLY "else" exits
            else if(elseId != thenId + 1 && elseId != size-1){
                // write the state sequence in the left block
                checkStatSequence(function, ifStatement.subList(thenId+1, elseId));
                Statement sta = new Statement(curStateId++, "bra");
                function.getBlock(curBlockId).addStatement(sta);
                int leftIndex = curBlockId;
                // add the right block and initiate the SSA;
                function.addBlock(++curBlockId);
                function.addRelation(index, curBlockId);
                function.getBlock(curBlockId).setSSA(function.getBlock(index).getSSA());
                // write the state sequence in the right block
                checkStatSequence(function, ifStatement.subList(elseId+1, size-1));
                // add the join block
                function.addBlock(++curBlockId,1);
                function.addRelation(curBlockId-1, curBlockId);
                function.addRelation(leftIndex, curBlockId);
                // complete the branch statement in the comp block
                function.getBlock(index).getLastStatement().addSymbol("[" + (leftIndex+1) + "]");
                // complete the branch statement in the left block
                function.getBlock(leftIndex).getLastStatement().addSymbol("[" + (curBlockId) + "]");
                // add phi functions and renew the SSA form;
                Block joinBlock = function.getBlock(curBlockId);
                addPHI(joinBlock, true, function.getBlock(leftIndex).getSSA(), function.getBlock(curBlockId-1).getSSA());
            }
            else{
                throw new StrongSymbolMissing("Error: Invalid if-statement on line " + ifStatement.get(elseId).getLineNum());
            }
        }
        // Error(): invalid if stat
        else{
            throw new StrongSymbolMissing("Error: Invalid if-statement on line " + errorIndex);
        }
    }


    // check WHILE Statement
    // the WHILE Statement here must started with "while" and end with "od"
    private void checkWhileStatement(Function function, List<Token> whileStatement) throws StrongSymbolMissing{
        // printList(whileStatement);
        // find the ONLY "do". Note: the if statement may be nested
        int size = whileStatement.size(), doId = -1, nested = 0, errorIndex = -1;
        for(int i = 0; i < size; i++){
            if(doId == -2) break;
            int curVal = whileStatement.get(i).getValue();
            if(curVal == 103) nested++;
            if(curVal == 81) nested--;
            if(nested == 1){
                if(curVal == 42){
                    if(doId == -1) doId = i;
                    else {
                        doId = -2;
                        errorIndex = whileStatement.get(i).getLineNum();
                    }
                }
            }
        }
        // if the ONLY "do" exists
        if(nested == 0 && doId > 1 && doId != size-1){
            // save the original SSA from the loop not begins
            Map<String, String> oriSSA = function.getBlock(curBlockId).getSSA();
            // add the comp and join block of while statement
            function.addBlock(++curBlockId, 2);
            function.addRelation(curBlockId-1, curBlockId);
            function.getBlock(curBlockId).setSSA(oriSSA);
            int index = curBlockId;
            // write the relation statement in the join block
            checkRelation(function, whileStatement.subList(1, doId));
            // add the LOOP block and write the state sequence in it
            function.addBlock(++curBlockId);
            function.addRelation(index, curBlockId);
            function.getBlock(curBlockId).setSSA(function.getBlock(index).getSSA());
            checkStatSequence(function, whileStatement.subList(doId+1, size-1));
            // add the BRA statement in the LOOP block
            Statement sta = new Statement(curStateId++, "bra");
            sta.addSymbol("[" + String.valueOf(index) + "]");
            function.getBlock(curBlockId).addStatement(sta);
            // add PHI function to the joint block and renew the SSA form
            Block joinBlock = function.getBlock(index);
            Map<String, String> replacement = addPHI(joinBlock, false, oriSSA, function.getBlock(curBlockId).getSSA());
            // for the phi added, reset all the identifier appeared
            // for the first block, ignore the phi function
            function.getBlock(index).renewIdentifier(replacement, true);
            for (int i = index + 1; i < curBlockId + 1; i++) {
                function.getBlock(i).renewIdentifier(replacement, false);
            }
            // add the exit block
            function.addBlock(++curBlockId);
            function.addRelation(curBlockId-1, index);
            function.addRelation(index, curBlockId);
            // initiate the SSA of the exit block
            function.getBlock(curBlockId).setSSA(function.getBlock(index).getSSA());
            // complete the comp statement in the comp block
            function.getBlock(index).getLastStatement().addSymbol("[" + (curBlockId) + "]");
        }
        // if the "do" appears more than once, Error();
        else{
            throw new StrongSymbolMissing("Error: Invalid while-statement on line " + errorIndex);
        }
    }


    // the funcCall must be started with "call"
    // 暂时 对于函数的调用，使用call语句。（暂时的处理方法！）
    private String checkFuncCall(Function function, List<Token> funcCall) throws StrongSymbolMissing{
        Block block = function.getBlock(curBlockId);
        int size = funcCall.size();
        // if the funcCall is valid;
        if(size > 1 && funcCall.get(1).getValue() == 61){
            String funcName = funcCall.get(1).getCharacters();
            List<String> paras = new ArrayList<>();
            // get the value of parameters
            List<Token> parameters = funcCall.subList(2, size);
            //printList(parameters);
            if(size > 4 && funcCall.get(2).getValue() == 50 && funcCall.get(size-1).getValue() == 35){
                int index = 3;
                for(int i = 3; i < size; i++){
                    int curVal = funcCall.get(i).getValue();
                    if(curVal == 31 || curVal == 35){
                        if(i == index)
                            throw new StrongSymbolMissing("Error: Invalid function call on line " + funcCall.get(i).getLineNum());
                        String symbol = checkExpression(function, funcCall.subList(index, i));
                        //System.out.println("expression: ");
                        //printList(funcCall.subList(index, i));
                        //System.out.println("symbol: " + symbol);
                        if(symbol.charAt(0) != '(' && symbol.charAt(0) != '#') {
                            if(block.checkSSAValue(symbol)) symbol = block.getSSAValue(symbol);
                            else throw new StrongSymbolMissing("Error: Variables should be used after initialization.");
                        }
                        paras.add(symbol);
                        index = i+1;
                    }
                }
            }
            //System.out.print(funcName + " : paras: ");
            //for (String para : paras)
            //    System.out.print(para + ", ");
            //System.out.println();
            // do the operation
            Statement sta;
            if(funcName.equals("InputNum")){
                sta = new Statement("read");
            }
            else if(funcName.equals("OutputNum")){
                sta = new Statement("write");
                if(paras.size() > 0)
                    sta.addSymbol(paras.get(0));
                else throw new StrongSymbolMissing("Error: Invalid function call on line " + funcCall.get(0).getLineNum());
            }
            else if(funcName.equals("OutputNewLine")){
                sta = new Statement("writeNL");
            }
            else{
                sta = new Statement("call");
                sta.addSymbol(funcName);
                if(functions.get(funcName).getDefParameters().size() != paras.size())
                    throw new StrongSymbolMissing("Function Call Error: the parameters of function " + funcName + " do not fit the definition.");
                for(int i = 0; i < paras.size(); i++){
                    sta.addSymbol(paras.get(i));
                }
            }
            sta.setIndex(curStateId++);
            block.addStatement(sta);
            return "(" + sta.getIndex() + ")";
        }
        else{
            throw new StrongSymbolMissing("Error: Invalid function call on line " + funcCall.get(0).getLineNum());
        }
    }



    // check return statement
    // the returnStatement must be started with "return"
    // 怎么处理直接从函数中结束跳出
    private String checkReturnStatement(Function function, List<Token> returnStatement) throws StrongSymbolMissing{
        Block block = function.getBlock(curBlockId);
        int size = returnStatement.size();
        Statement sta = new Statement("return");
        if(size > 1){
            String symbol = checkExpression(function, returnStatement.subList(1, size));
            if(symbol.charAt(0) != '(' && symbol.charAt(0) != '#') {
                if(block.checkSSAValue(symbol)) symbol = block.getSSAValue(symbol);
                else{
                    block.addSSAValue(symbol, symbol + "_" + String.valueOf(curStateId));
                    symbol = symbol + "_" + String.valueOf(curStateId);
                }
            }
            sta.addSymbol(symbol);
        }
        sta.setIndex(curStateId++);
        block.addStatement(sta);
        return "(" + sta.getIndex() + ")";
    }


    // check State Sequence
    // Note: ";" is not the weak symbol, because the funcCall can be nested;
    // Consider the example of "let a <- call foo"
    private void checkStatSequence(Function function, List<Token> statSequence) throws StrongSymbolMissing{
        int staSize = statSequence.size(), index = 0, nested = 0;
        for(int i = 0; i < staSize; i++){
            int curVal = statSequence.get(i).getValue();
            if(curVal == 102 || curVal == 103) nested++;
            if(curVal == 81 || curVal == 82) nested--;
            if(i == staSize-1 || (nested == 0 && statSequence.get(i).getValue() == 70)){
                List<Token> statement = new ArrayList<>();
                if(i == staSize-1) statement = statSequence.subList(index, i+1);
                else statement = statSequence.subList(index, i);
                //printList(statement);

                int size = statement.size(), startVal = statement.get(0).getValue();
                if(startVal == 100) checkAssignment(function, statement);
                else if(startVal == 101) checkFuncCall(function, statement);
                else if(startVal == 102 && statement.get(size-1).getValue() == 82) checkIfStatement(function, statement);
                else if(startVal == 103 && statement.get(size-1).getValue() == 81) checkWhileStatement(function, statement);
                else if(startVal == 104) checkReturnStatement(function, statement);
                else throw new StrongSymbolMissing("Error: Invalid statement sequence in line " + statement.get(0).getLineNum());
                index = i+1;
            }
        }
    }

    // check varDecl
    // the varDecl must started with "var" or "array", and statement separated by ";"
    private void checkVarDecl(Function function, List<Token> varDecl) throws  StrongSymbolMissing{
        Map<String, List<Integer>> arrayMap = new HashMap<>();
        int index = 0;
        for(int k = 0; k < varDecl.size(); k++) {
            if (varDecl.get(k).getValue() == 70) {
                List<Token> varDec = varDecl.subList(index, k);
                printList(varDec);
                int size = varDec.size(), i;
                // to check the part before ident
                List<Integer> arraySize = new ArrayList<>();
                if (varDec.get(0).getValue() == 110 && varDec.get(1).getValue() == 61) {
                    i = 1;
                } else if (size > 3 && varDec.get(0).getValue() == 111 && varDec.get(1).getValue() == 32 && varDec.get(2).getValue() == 60 && varDec.get(3).getValue() == 34) {
                    arraySize.add(Integer.parseInt(varDec.get(2).getCharacters()));
                    for (i = 4; i < size; i += 3) {
                        if (varDec.get(i).getValue() == 61) {
                            arrayMap.put(varDec.get(i).getCharacters(), arraySize);
                            break;
                        }
                        if (varDec.get(i).getValue() == 32 && varDec.get(i + 1).getValue() == 60 && varDec.get(i + 2).getValue() == 34) {
                            arraySize.add(Integer.parseInt(varDec.get(i + 1).getCharacters()));
                        } else
                            throw new StrongSymbolMissing("Error: Invalid varDecl on line " + varDec.get(i).getLineNum());
                    }
                } else throw new StrongSymbolMissing("Error: Invalid varDecl on line " + varDec.get(1).getLineNum());
                // to check the part after ident
                for (i = i + 1; i < size; i += 2) {
                    if (varDec.get(i).getValue() == 31 && varDec.get(i + 1).getValue() == 61) {
                        if (arraySize.size() != 0) arrayMap.put(varDec.get(i+1).getCharacters(), arraySize);
                    } else
                        throw new StrongSymbolMissing("Error: Invalid varDecl on line " + varDec.get(1).getLineNum());
                }
                index = k + 1;
            }
        }
        //System.out.println("BaseMap: ");
        //printMap(baseMap);
        if(!function.setArrayMap(arrayMap))
            throw new StrongSymbolMissing("Memory Limited Error: the Array size should be smaller on line " + varDecl.get(0).getLineNum());
    }

    // check funcDecl
    // the funcDecl must started with "function" or "procedure"
    private void checkFuncDecl(List<Token> funcDecl) throws StrongSymbolMissing{
        int size = funcDecl.size(), i = 1;
        if(size > 1 && funcDecl.get(1).getValue() == 61 && funcDecl.get(size-1).getValue() == 70){
            String funcName = funcDecl.get(1).getCharacters();
            Function function = new Function(++curBlockId);
            // get the value of parameters
            if(size > 4 && funcDecl.get(2).getValue() == 50){
                for(i = 3; i < size; i++){
                    int curVal = funcDecl.get(i).getValue();
                    if(curVal == 70) break;
                    if(i % 2 == 1 && curVal == 61) {
                        String parameter = funcDecl.get(i).getCharacters();
                        function.getBlocks().firstEntry().getValue().addSSAValue(parameter, "#" + parameter);
                        function.addDefParameters(parameter);
                    }
                    else if(i % 2 == 0 && (curVal == 31 || curVal == 35));
                }
            }
            // get the funcDel and state sequence
            int index = i+1;
            for(; i < size; i++){
                int curVal = funcDecl.get(i).getValue();
                if(curVal == 150) {
                    if(index < i-1) {
                        printList(funcDecl.subList(index, i));
                        checkVarDecl(function, funcDecl.subList(index, i));
                    }
                    index = i;
                }
                if(curVal == 80) {
                    checkStatSequence(function, funcDecl.subList(index+1, i));
                    break;
                }
            }
            // write the end statement
            Statement end = new Statement(curStateId++, "end");
            function.getBlock(curBlockId).addStatement(end);
            function.setDTTree();
            functions.put(funcName, function);
        }
        else{
            if(funcDecl.get(size-1).getValue() == 70)
                throw new StrongSymbolMissing("Error: Invalid function name on line " + funcDecl.get(0).getLineNum());
            else throw new StrongSymbolMissing("Error: Missing semiToken after funcDecl on line " + funcDecl.get(size-1).getLineNum());
        }
    }


    // 需要在这里处理没有"."的情况，暂时不支持
    // 实际上这两个都属于weak symbol
    public void IRgeneration() throws IOException, StrongSymbolMissing{
        List<Token> computation = new ArrayList<>();
        // to get the whole pages and mark the separators
        int nested = 0, varDecBegin = -1, varDecEnd = -1, staSeqBegin = -1, staSeqEnd = -1, funcDeclBegin = -1;
        List<List<Integer>> funcDecl = new ArrayList<>();
        Scannar scannar = new Scannar(FilePath);
        Token token = scannar.Next();
        for(int i = 0; token.getValue() != 255; i++){
            computation.add(token);
            if(varDecBegin == -1 && (token.getValue() == 110 || token.getValue() == 111)) varDecBegin = i;
            if(token.getValue() == 112 || token.getValue() == 113) {
                nested++;
                // if before is the varDecl not end
                if(varDecBegin != -1 && varDecEnd == -1) varDecEnd = i;
                // if before is the funcDel not end
                if(funcDeclBegin != -1){
                    funcDecl.add(List.of(funcDeclBegin, i));
                }
                funcDeclBegin = i;
            }
            if(token.getValue() == 150) {
                // if before is the varDecl not end
                if(varDecBegin != -1 && varDecEnd == -1) varDecEnd = i;
                // if before is the funcDel not end
                if(nested == 0 && funcDeclBegin != -1) funcDecl.add(List.of(funcDeclBegin, i));
                staSeqBegin = i+1;
            }
            if(token.getValue() == 80) {
                nested--;
                staSeqEnd = i;
            }
            token = scannar.Next();
        }
        System.out.println("varDecBegin: " + varDecBegin + ", varDecEnd: " + varDecEnd);
        System.out.println("staDecBegin: " + staSeqBegin + ", staDecEnd: " + staSeqEnd);

        // to process the computation in order
        // firstly process the functions inorder
        for (List<Integer> func : funcDecl){
            System.out.println("funcDecBegin: " + func.get(0) + ", funcDecEnd: " + func.get(0));
            printList(computation.subList(func.get(0), func.get(1)));
            checkFuncDecl(computation.subList(func.get(0), func.get(1)));
        }
        // to process the main function
        Function main = new Function(++curBlockId);
        if(varDecBegin != -1 && varDecEnd != -1) {
            printList(computation.subList(varDecBegin, varDecEnd));
            checkVarDecl(main, computation.subList(varDecBegin, varDecEnd));
        }
        printList(computation.subList(staSeqBegin, staSeqEnd));
        checkStatSequence(main, computation.subList(staSeqBegin, staSeqEnd));

        // write the end statement
        Statement end = new Statement(curStateId++, "end");
        main.getBlock(curBlockId).addStatement(end);
        main.setDTTree();
        functions.put("main", main);

        getVCG(outFilePath + "_CFG.vcg", true);
        getVCG(outFilePath + "_DT.vcg", false);
    }

    public void IRoptimization() throws IOException{
        for(Function function : functions.values()) {
            // eliminate the common expression and copy expression
            SSAOptimization SSAOpt = new SSAOptimization(function);
            SSAOpt.getAllSSA();
            // clear the empty blocks
            //function.clearEmptyBlock();
        }
        getVCG(outFilePath + "_CFG_opt.vcg", true);
        //getVCG(outFilePath + "_DT_opt.vcg", false);
    }

    // 暂时处理方法，为了测试
    public void RegisterAllocate() throws StrongSymbolMissing, IOException{
        for(Function function : functions.values()){
            RegisterAllocation reg = new RegisterAllocation(function, curStateId);
            curStateId = reg.allocate();
        }
        getVCG(outFilePath + "_CFG_phi.vcg", true);
    }

    public void DLXExecution() throws IOException{
        List<MachineStatement> machineCode = new ArrayList<>();
        Map<String, Integer> funcMId = new HashMap<>();
        Map<Integer, String> calls = new HashMap<>();
        MachineCodeGeneration mainMcode = new MachineCodeGeneration(functions.get("main"), true);
        mainMcode.GenerateAll();
        machineCode.addAll(mainMcode.getMachineCode());
        calls.putAll(mainMcode.getCalls());
        for(Map.Entry<String, Function> entry: functions.entrySet()){
            if(entry.getKey().equals("main")) continue;
            funcMId.put(entry.getKey(), machineCode.size());
            MachineCodeGeneration funcMcode = new MachineCodeGeneration(entry.getValue(), false);
            funcMcode.GenerateAll();
            machineCode.addAll(funcMcode.getMachineCode());
            calls.putAll(funcMcode.getCalls());
        }
        // change the mcode of -2 to the actual word
        for(Map.Entry<Integer, String> entry : calls.entrySet()){
            int tmp = funcMId.get(entry.getValue()) - entry.getKey();
            machineCode.set(entry.getKey(), new MachineStatement(DLX.BSR, tmp));
        }


        // write it to the log file
        for(int k = 0; k < machineCode.size(); k++){
            System.out.print(k + " : " + machineCode.get(k).getOpcode() + ", ");
            for(int sym : machineCode.get(k).getSymbols())
                System.out.print(sym + ", ");
            System.out.println();
        }


        // write it to the log file
        File f = new File("AdvancedCompiler.log");
        FileOutputStream out = new FileOutputStream(f, true);
        OutputStreamWriter writerMcode = new OutputStreamWriter(out, "UTF-8");
        writerMcode.append("\n\nMachine Code: \n");
        for(int k = 0; k < machineCode.size(); k++){
            System.out.print(k + " : " + machineCode.get(k).getMachineStatement());
            writerMcode.append("\t" + k + " : " + machineCode.get(k).getMachineStatement());
        }
        writerMcode.flush();
        out.close();

        MachineCodeGeneration.Execution(machineCode);
    }

    public void WriteLog() throws IOException {
        File f = new File("AdvancedCompiler.log");
        FileOutputStream out = new FileOutputStream(f);
        OutputStreamWriter writerOpt = new OutputStreamWriter(out, "UTF-8");
        OutputStreamWriter writerDead = new OutputStreamWriter(out, "UTF-8");
        OutputStreamWriter writerPhi = new OutputStreamWriter(out, "UTF-8");
        // start writing the log file
        writerOpt.append("Common Subexpression Elimination and Copy Propagation: \n");
        writerDead.append("\n\nDead Code Elimination: \n");
        writerPhi.append("\n\nPHI Function Elimination: \n");
        for(Function function : functions.values()){
            for(Map.Entry<Integer, Block> entry : function.getBlocks().entrySet()){
                for(Statement statement : entry.getValue().getStatements()){
                    if(statement.isValid()) continue;
                    int index = statement.getIndex();
                    String pointer = statement.getPointer();
                    if(pointer.equals("deadcode"))
                        writerDead.append("\tinstruction " + index + " eliminated as Dead Code.\n");
                    else if(pointer.equals("phiEliminated"))
                        writerPhi.append("\tinstruction " + index + " eliminated as PHI function.\n");
                    else
                        writerOpt.append("\tinstruction " + index + " replaced by " + pointer + "\n");
                }
            }
        }
        writerOpt.flush();
        writerDead.flush();
        writerPhi.flush();
        out.close();
    }

    // 将该function中的block关系和内容以VCG文件的格式来输出到FilePath中去
    // it shows two images
    // the FilePath is original file path
    // when the flag is TRUE means getVCG of CFG, when the flag is FALSE mean getVCG of DT
    private void getVCG(String FilePath, boolean flag) throws IOException{
        File f = new File(FilePath);
        FileOutputStream out = new FileOutputStream(f);
        OutputStreamWriter writer = new OutputStreamWriter(out, "UTF-8");
        // 构建OutputStreamWriter对象,参数可以指定编码,默认为操作系统默认编码,windows上是gbk
        writer.append("graph: { title: \"Control Flow Graph\"\nmanhattan_edge: yes\norientation: top_to_bottom\n" +
                "create_reference_nodes: no\ncreate_loop_tree: no\nbackloop_routing: no\nlayout_against_orientation: no\n");
        for(Function function : functions.values()){
            // eliminate the common expression and copy expression
            for(Map.Entry<Integer, Block> entry : function.getBlocks().entrySet()){
                writer.append("node: {\ntitle: \"" + entry.getKey() + "\"\nlabel: \"" + entry.getKey() + "[\n");
                List<String> statements = entry.getValue().getBlock();
                int size = statements.size();
                for(int i = 0; i < size-1; i++){
                    writer.append(statements.get(i) + " ,\n");
                }
                if(size > 0) writer.append(statements.get(size-1) + " ]\"\n}\n");
                else writer.append("]\"\n}\n");
            }
            Map<Integer, Set<Integer>> edges = new HashMap<>();
            if(flag) edges = function.getEdges();
            else edges = function.getDTEdges();
            for(Map.Entry<Integer, Set<Integer>> entry : edges.entrySet()){
                for(Integer index : entry.getValue()){
                    writer.append("edge: { sourcename: \"" + entry.getKey() + "\"\ntargetname: \"" + index + "\"\n}\n");
                }
            }
        }
        writer.append("}\n");
        writer.close();
        // 关闭写入流,同时会把缓冲区内容写入文件
        out.close();
        // 生成该文件图像
        String [] cmd = {"./ycomp", FilePath, "--dolayout"};
//        Runtime.getRuntime().exec(cmd);
        ProcessBuilder build = new ProcessBuilder(cmd);
        build.directory(new File("/Users/yuanmeng/IdeaProjects/AdvancedCompiler"));
        build.start();
    }

    // for test
    private void printList(List<Token> list){
        for(int i = 0; i < list.size(); i++){
            System.out.print(list.get(i).getCharacters() + "  ");
        }
        System.out.println("");
    }

    private void printMap(Map<String, Integer> map){
        for(Map.Entry<String, Integer> entry : map.entrySet()){
            System.out.println("key = " + entry.getKey() + ", value = " + entry.getValue());
        }
    }
}