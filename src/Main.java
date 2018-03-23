import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class Main {

    public static void main(String[] args) throws IOException, StrongSymbolMissing {
        try {
            String FilePath;
            FilePath = "./TestFiles/test001.txt";
            Parser p = new Parser(FilePath);
            p.IRgeneration();
            p.IRoptimization();
            p.RegisterAllocate();
            p.WriteLog();
            p.DLXExecution();
        }
        catch (StrongSymbolMissing e) {
            e.Exit();
        }
    }
}
