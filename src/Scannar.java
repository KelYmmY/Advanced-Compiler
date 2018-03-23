import java.util.ArrayList;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class  Scannar {

    private ArrayList<Token> arrL = new ArrayList<Token>();
    private int index = 0;

    public Scannar(String filePath) throws IOException{
        ReadFile(filePath);
    }

    private void ReadFile(String filePath) throws IOException{
        // read the input file line by line so as to record line number
        File file = new File(filePath);
        BufferedReader b = new BufferedReader(new FileReader(file));
        int lineNumber = 1;
        String line = "";
        line = b.readLine();
        while (line != null) {
            // analyze the line only when it is not empty and it is not a comment
            String trimLine = line.toString().trim();
            if(trimLine.length() != 0 && !trimLine.substring(0, 1).equals("/") && !trimLine.substring(0, 1).equals("#")){
                // line.toString().trim(): eliminate heading and tailing whitespaces
                // but add one whitespace to the end of the string to facilitate token manipulation
                // under the circumstances that there is only one token on one line, e.g. main
                AnalyzeByLine(line.toString().trim() + " ", lineNumber);
            }
            lineNumber++;
            line = b.readLine();
        }
        arrL.add(new Token("end of file", 255, lineNumber));
//        for(int i = 0; i < arrL.size(); i++){
//            System.out.println(arrL.get(i).getCharacters() + " " + arrL.get(i).getValue() + " " + arrL.get(i).getLineNum());
//        }
    }

    // translate the line into tokens
    // and store tokens into the ArrayList arrL
    private void AnalyzeByLine(String str, int lineNum){
        int prev = 0;
        int cur = 0;
        char[] specialCh={' ', '*', '/', '+', '-', '=', '!', '<', '>', '.', ',', '[', ']', '(', ')', '{', '}', ';'};
        boolean isSpecial = false;
        String tmp = "";

        while (cur < str.length()){

            // check the current char is a special char or not
            for (char x : specialCh) {
                if (x == str.charAt(cur)) {
                    isSpecial = true;
                    break;
                }
                else{
                    isSpecial = false;
                }
            }

            if(isSpecial) {
                // every time encounter a special char, handle the token before it
                tmp = str.substring(prev, cur);
                switch (tmp){
                    case "":
                        break;
                    case "then":
                        arrL.add(new Token(tmp, 41, lineNum));
                        break;
                    case "do":
                        arrL.add(new Token(tmp, 42, lineNum));
                        break;
                    case "od":
                        arrL.add(new Token(tmp, 81, lineNum));
                        break;
                    case "fi":
                        arrL.add(new Token(tmp, 82, lineNum));
                        break;
                    case "else":
                        arrL.add(new Token(tmp, 90, lineNum));
                        break;
                    case "let":
                        arrL.add(new Token(tmp, 100, lineNum));
                        break;
                    case "call":
                        arrL.add(new Token(tmp, 101, lineNum));
                        break;
                    case "if":
                        arrL.add(new Token(tmp, 102, lineNum));
                        break;
                    case "while":
                        arrL.add(new Token(tmp, 103, lineNum));
                        break;
                    case "return":
                        arrL.add(new Token(tmp, 104, lineNum));
                        break;
                    case "var":
                        arrL.add(new Token(tmp, 110, lineNum));
                        break;
                    case "array":
                        arrL.add(new Token(tmp, 111, lineNum));
                        break;
                    case "function":
                        arrL.add(new Token(tmp, 112, lineNum));
                        break;
                    case "procedure":
                        arrL.add(new Token(tmp, 113, lineNum));
                        break;
                    case "main":
                        arrL.add(new Token(tmp, 200, lineNum));
                        break;
                    case "InputNum":
                    case "OutputNum":
                    case "OutputNewLine":
                        arrL.add(new Token(tmp, 61, lineNum));
                        break;
                    default:
                        AnalyzeIdentNum(tmp, lineNum);
                }

                // consider binary operator first
                if(str.charAt(cur) == '<' || str.charAt(cur) == '>' || str.charAt(cur) == '!'){
                    switch(str.charAt(cur)){
                        case '<':
                            if(str.charAt(cur+1) == '='){
                                arrL.add(new Token("<=", 24, lineNum));
                                cur++;
                            }
                            else if(str.charAt(cur+1) == '-'){
                                arrL.add(new Token("<-", 40, lineNum));
                                cur++;
                            }
                            else{
                                arrL.add(new Token("<", 22, lineNum));
                            }
                            break;
                        case '>':
                            if(str.charAt(cur+1) == '='){
                                arrL.add(new Token(">=", 23, lineNum));
                                cur++;
                            }
                            else{
                                arrL.add(new Token(">", 25, lineNum));
                            }
                            break;
                        case  '!':
                            arrL.add(new Token("!=", 21, lineNum));
                            cur++;
                            break;
                    }
                }
                else{
                    // consider unary operator
                    switch(str.charAt(cur)){
                        case '*':
                            arrL.add(new Token("*", 1, lineNum));
                            break;
                        case '/':
                            arrL.add(new Token("/", 2, lineNum));
                            break;
                        case '+':
                            arrL.add(new Token("+", 11, lineNum));
                            break;
                        case '-':
                            arrL.add(new Token("-", 12, lineNum));
                            break;
                        case '.':
                            arrL.add(new Token(".", 30, lineNum));
                            break;
                        case ',':
                            arrL.add(new Token(",", 31, lineNum));
                            break;
                        case '[':
                            arrL.add(new Token("[", 32, lineNum));
                            break;
                        case ']':
                            arrL.add(new Token("]", 34, lineNum));
                            break;
                        case ')':
                            arrL.add(new Token(")", 35, lineNum));
                            break;
                        case '(':
                            arrL.add(new Token("(", 50, lineNum));
                            break;
                        case ';':
                            arrL.add(new Token(";", 70, lineNum));
                            break;
                        case '}':
                            arrL.add(new Token("}", 80, lineNum));
                            break;
                        case '{':
                            arrL.add(new Token("{", 150, lineNum));
                            break;
                    }
                }
                prev = cur + 1;
            }
            cur++;
        }
    }

    private void AnalyzeIdentNum(String s, int lineNum){
        //check s is identifier or not
        if (s.matches("[a-z](([a-z]|[0-9])*)")){
            arrL.add(new Token(s, 61, lineNum));
        }
        //check s is number or not
        else if (s.matches("[1-9]([0-9]*)")||s.matches("0")){
            arrL.add(new Token(s, 60, lineNum));
        }
        // check s is a
        // none of above, then s is an errorToken
        else{
            arrL.add(new Token(s, 0, lineNum));
            Error("Error: Invalid input \"" + s + "\" on line " + lineNum);
        }
    }

    // Use iterator
    public Token Next(){
        if (index < arrL.size() - 1){
            return arrL.get(index++);
        }
        else{
            return arrL.get(index);
        }
    }

    private void Error(String errorMsg){
        System.out.println(errorMsg);
    }

    public String Id2String(Token token){
        return token.getCharacters();
    }

    public int String2Id(Token token){
        return token.getValue();
    }

}
