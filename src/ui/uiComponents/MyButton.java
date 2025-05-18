package ui.uiComponents;

import ui.theme.Palette;

import javax.swing.*;
import java.awt.*;

/**
 * Просто JButton, у которого фон и скругления берутся из UIManager,
 * а текст и отступы из Palette.
 */
public class MyButton extends JButton {
    public MyButton(String text) {
        super(text);
        setMargin(new Insets(4,12,4,12));
        setBackground(Palette.BUTTONFG);
        setContentAreaFilled(true);
        setBorderPainted(false);
    }

    @Override
    public void updateUI() {
        super.updateUI();
        Color bg = UIManager.getColor("Button.background");
        Color fg = UIManager.getColor("Button.foreground");
        if (bg != null) setBackground(bg);
        if (fg != null) setForeground(fg);
    }
}
