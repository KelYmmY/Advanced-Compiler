public class Token {
    private String characters;
    private int value;
    private int lineNum;

    public Token(String chara, int val, int lineNum){
        characters = chara;
        value = val;
        this.lineNum = lineNum;
    }
    public String getCharacters(){ return characters;}
    public int getValue(){ return value;}
    public int getLineNum(){
        return lineNum;
    }
}