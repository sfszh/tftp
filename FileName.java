import java.io.File;

public class FileName {

    public static void main(String[] args) {

        File fileName = new File("foo.txt");
        System.out.println("Exists? " + fileName.exists());
    }
}
