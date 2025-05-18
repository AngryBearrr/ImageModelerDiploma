package ui.uiComponents;

import javax.swing.*;
import java.awt.*;

public class MyPanel extends JPanel {
    public MyPanel(LayoutManager layout, int padding) {
        super(layout);
        setBorder(BorderFactory.createEmptyBorder(padding,padding,padding,padding));
        // background comes from UIManager defaults
    }
}
