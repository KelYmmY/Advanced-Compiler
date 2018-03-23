public class StrongSymbolMissing extends Exception{
    private String  errorMsg = "";
    public StrongSymbolMissing(){
        super();
    }
    public StrongSymbolMissing(String errorMsg){
        this.errorMsg = errorMsg;
    }
    public void Exit(){
        System.out.print(errorMsg + "\n");
        this.printStackTrace();
        System.exit(1);
    }
}
