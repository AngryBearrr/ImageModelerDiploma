import ui.MainFrame;

import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) {
        System.load("E:\\OpenCV\\opencv\\build\\java\\x64\\opencv_java4110.dll");
        SwingUtilities.invokeLater(() -> new MainFrame().setVisible(true));
    }
}