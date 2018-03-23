import java.util.*;

public class SSAOptimization {
    private Map<Integer, Block> blocks;
    private Map<Integer, Set<Integer>> DT_edges;
    private Map<Integer, String> stat_mapping;
    private List<Statement> unhandledPhi;


    public SSAOptimization(Function F){
        blocks = new HashMap<>();
        DT_edges = new HashMap<>();
        blocks = F.getBlocks();
        DT_edges = F.getDTEdges();
        stat_mapping = new HashMap<>();
        unhandledPhi = new ArrayList<>();
//        System.out.print("DT_edges: ");
//        System.out.println(DT_edges);

    }

    private List<String> reviseSSA(List<String> subscript){
        // revise the SSA first
        for(int i = 0; i < subscript.size(); i++){
            // try block deals with the case in which the subscript can be parsed into Integer
            try{
                int subs = Integer.parseInt(subscript.get(i));
                if(stat_mapping.containsKey(subs)){
                    subscript.set(i, stat_mapping.get(subs));
                }
            }
            catch(NumberFormatException e){
                if(subscript.get(i).charAt(0) == '(' && stat_mapping.containsKey(Integer.parseInt(subscript.get(i).substring(1, subscript.get(i).length()-1)))){
                    subscript.set(i, stat_mapping.get(Integer.parseInt(subscript.get(i).substring(1, subscript.get(i).length()-1))));
                }
            }
        }
        return subscript;
    }

    public void getAllSSA(){
        ArrayList<Integer> parents = new ArrayList<>();
        Iterator itr = blocks.keySet().iterator();
        while(itr.hasNext()){
            parents.add((Integer)itr.next());
        }

        for(int i = 0; i < parents.size(); i++){
            getSSA(parents.get(i));
            if(DT_edges.keySet().contains(parents.get(i)) && !DT_edges.get(parents.get(i)).isEmpty()){
                for(Integer ele: DT_edges.get(parents.get(i))){
                    blocks.get(ele).setAnchor(blocks.get(parents.get(i)).getAnchor());
                }
            }
        }

        if(unhandledPhi.size() > 0){
            for(Statement stat: unhandledPhi){
                List<String> subscript = new ArrayList<>();    // store the subscripts of all symbols in this stats
                for(int i = 0; i < stat.getSymbols().size(); i++){
                    subscript.add(stat.getSubscript(i));
                }
                for(int i = 0; i < subscript.size(); i++){
                    if(!subscript.get(i).contains("#") && !subscript.get(i).contains("(")){
                        stat.modifySymbol(i, stat_mapping.get(Integer.parseInt(subscript.get(i))));
                    }
                }
            }
        }
    }

    public void getSSA(int curId){
        for(Statement stat: blocks.get(curId).getStatements()){
            String opcode = stat.getOpcode();
            List<String> subscript = new ArrayList<>();    // store the subscripts of all symbols in this stats
            for(int i = 0; i < stat.getSymbols().size(); i++){
                subscript.add(stat.getSubscript(i));
            }
            if(opcode.equals("move")){
                blocks.get(curId).subCount();
                if(subscript.get(0).contains("#")){
                    stat.setPointer(subscript.get(0));
                }
                else if(subscript.get(0).contains("(")){
                    if(stat_mapping.containsKey(Integer.parseInt(subscript.get(0).substring(1, subscript.get(0).length()-1)))){
                        stat.setPointer(stat_mapping.get(Integer.parseInt(subscript.get(0).substring(1, subscript.get(0).length()-1))));
                    }
                    else{
                        stat.setPointer(subscript.get(0));
                    }
                }
                else{
                    if(stat_mapping.containsKey(Integer.parseInt(subscript.get(0)))){
                        stat.setPointer(stat_mapping.get(Integer.parseInt(subscript.get(0))));
                    }
                    else{
                        stat.setPointer("(" + subscript.get(0) + ")");
                    }
                }
                stat_mapping.put(stat.getIndex(), stat.getPointer());

            }
            else if(opcode.equals("add") || opcode.equals("sub") || opcode.equals("mul") || opcode.equals("div") || opcode.equals("adda")){
                // revise the SSA first
                List<String> reviseSubscript = reviseSSA(new ArrayList<>(subscript));
                for(int i = 0; i < subscript.size(); i++){
                    if(!reviseSubscript.get(i).equals(subscript.get(i)))
                        stat.modifySymbol(i, reviseSubscript.get(i));
                }


                // if Omapping is empty, add the first statement into the Omapping without checking
                if(blocks.get(curId).getAnchor().get(opcode).size() == 0){
                    blocks.get(curId).getAnchor().get(opcode).addFirst(stat);
                }
                // check the duplication in the anchor (Omapping)
                else{
                    boolean flag = true;       // true: duplication
                    for(int i = 0; i < blocks.get(curId).getAnchor().get(opcode).size(); i++){
                        flag = true;
                        List<String> compared = blocks.get(curId).getAnchor().get(opcode).get(i).getSymbols();
                        List<String> cur_symbol = stat.getSymbols();
                        if(opcode.equals("add") || opcode.equals("mul")){
                            Collections.sort(compared);
                            Collections.sort(cur_symbol);
                        }
                        for(int k = 0; k < compared.size(); k++){
                            if(!compared.get(k).equals(cur_symbol.get(k))){
                                flag = false;
                                break;
                            }
                        }
                        if(flag){
                            stat.setPointer("(" + blocks.get(curId).getAnchor().get(opcode).get(i).getIndex() + ")");
                            stat_mapping.put(stat.getIndex(), stat.getPointer());
                            blocks.get(curId).subCount();
                            break;
                        }
                    }

                    // the current stat is not a duplication
                    if(!flag){
                        blocks.get(curId).getAnchor().get(opcode).addFirst(stat);
                    }
                }
            }
            else if(opcode.equals("bne") || opcode.equals("beq") || opcode.equals("ble") || opcode.equals("blt") || opcode.equals("bge") || opcode.equals("bgt")){
                if(subscript.get(0).contains("(")){
                    if(stat_mapping.containsKey(Integer.parseInt(subscript.get(0).substring(1, subscript.get(0).length()-1)))){
                        stat.modifySymbol(0, stat_mapping.get(Integer.parseInt(subscript.get(0).substring(1, subscript.get(0).length()-1))));
                    }
                }
                else if(!subscript.get(0).contains("#") && stat_mapping.containsKey(Integer.parseInt(subscript.get(0)))){
                    stat.modifySymbol(0, stat_mapping.get(Integer.parseInt(subscript.get(0))));
                }
            }
            else if(opcode.equals("cmp") || opcode.equals("read") || opcode.equals("write") || opcode.equals("writeNL") || opcode.equals("return")){
                for(int i = 0; i < subscript.size(); i++){
                    if(subscript.get(i).contains("(")){
                        if(stat_mapping.containsKey(Integer.parseInt(subscript.get(i).substring(1, subscript.get(i).length()-1)))){
                            stat.modifySymbol(i, stat_mapping.get(Integer.parseInt(subscript.get(i).substring(1, subscript.get(i).length()-1))));
                        }
                    }
                    else if(!subscript.get(i).contains("#") && stat_mapping.containsKey(Integer.parseInt(subscript.get(i)))){
                        stat.modifySymbol(i, stat_mapping.get(Integer.parseInt(subscript.get(i))));
                    }
                }
            }
            else if(opcode.equals("call")){
                for(int i = 1; i < subscript.size(); i++){
                    if(subscript.get(i).contains("(")){
                        if(stat_mapping.containsKey(Integer.parseInt(subscript.get(i).substring(1, subscript.get(i).length()-1)))){
                            stat.modifySymbol(i, stat_mapping.get(Integer.parseInt(subscript.get(i).substring(1, subscript.get(i).length()-1))));
                        }
                    }
                    else if(!subscript.get(i).contains("#") && stat_mapping.containsKey(Integer.parseInt(subscript.get(i)))){
                        stat.modifySymbol(i, stat_mapping.get(Integer.parseInt(subscript.get(i))));
                    }
                }
            }
            else if(opcode.equals("phi")){
                if(subscript.size() == 3){
                    stat_mapping.put(Integer.parseInt(subscript.get(0)), "(" + stat.getIndex() + ")");
                    stat.deleteSymbol();
                    subscript.remove(0);
                }

//                System.out.print("symbols before: ");
//                System.out.println(stat.getSymbols());
//                System.out.print("subscript before: ");
//                System.out.println(subscript);
//                System.out.print("stat_mapping keys: ");
//                System.out.println(stat_mapping.keySet());

                for(int i = 0; i < subscript.size(); i++){
                    if(subscript.get(i).contains("(")){
                        if(stat_mapping.containsKey(Integer.parseInt(subscript.get(i).substring(1, subscript.get(i).length()-1)))){
                            stat.modifySymbol(i, stat_mapping.get(Integer.parseInt(subscript.get(i).substring(1, subscript.get(i).length()-1))));
                        }
                    }
                    else if(!subscript.get(i).contains("#") && stat_mapping.containsKey(Integer.parseInt(subscript.get(i)))){
                        stat.modifySymbol(i, stat_mapping.get(Integer.parseInt(subscript.get(i))));
                    }
                    else if(!subscript.get(i).contains("#") && !stat_mapping.containsKey(Integer.parseInt(subscript.get(i)))){
                        unhandledPhi.add(stat);
                    }
                }
            }
//            else if(opcode.equals("bra")){
//                //System.out.println(stat.getStatement());
//                getSSA(Integer.parseInt(subscript.get(0).substring(1, subscript.get(0).length()-1)));
//            }
            else if(opcode.equals("store")){
                // revise the SSA first
                List<String> reviseSubscript = reviseSSA(new ArrayList<>(subscript));
                for(int i = 0; i < subscript.size(); i++){
                    if(!reviseSubscript.get(i).equals(subscript.get(i)))
                        stat.modifySymbol(i, reviseSubscript.get(i));
                }
                blocks.get(curId).getAnchor().get("load").addFirst(stat);
            }
            else if(opcode.equals("load")){
                // revise the SSA first
                List<String> reviseSubscript = reviseSSA(new ArrayList<>(subscript));

                for(int i = 0; i < subscript.size(); i++){
                    if(!reviseSubscript.get(i).equals(subscript.get(i)))
                        stat.modifySymbol(i, reviseSubscript.get(i));
                }

                LinkedList<Statement> stats = blocks.get(curId).getAnchor().get(opcode);

                boolean flag = true;        // if true, add this stat to the anchor
                for(int i = 0; i < stats.size(); i++){
                    List<String> compared = stats.get(i).getSymbols();
                    if(stats.get(i).getOpcode().equals("store")){
                        if(compared.get(1).equals(subscript.get(0))){
                            stat.setPointer(compared.get(0));
                            stat_mapping.put(stat.getIndex(), stat.getPointer());
                            flag = false;
                            blocks.get(curId).subCount();
                            break;
                        }
                    }
                    else{
                        //System.out.println(stats.get(i).getStatement());
                        if(compared.get(0).equals(subscript.get(0))){
                            if(stat_mapping.containsKey(Integer.parseInt(subscript.get(0).substring(1, subscript.get(0).length()-1)))){
                                stat.setPointer(stat_mapping.get(Integer.parseInt(subscript.get(0).substring(1, subscript.get(0).length()-1))));
                                stat_mapping.put(stat.getIndex(), stat.getPointer());
                                flag = false;
                                blocks.get(curId).subCount();
                                break;
                            }
                        }
                    }
                }
                if(flag){
                    blocks.get(curId).getAnchor().get("load").addFirst(stat);
                }
            }
        }

    }
}
