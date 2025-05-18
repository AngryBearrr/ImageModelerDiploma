import ui.mainWindow.MainFrame;
import ui.theme.PaletteTheme;

import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) {
        try {
            PaletteTheme.install();
        } catch (Exception e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(() -> {
            new MainFrame().setVisible(true);
        });
    }
}