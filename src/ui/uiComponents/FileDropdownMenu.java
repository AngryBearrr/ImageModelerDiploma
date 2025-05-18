package ui.uiComponents;

import ui.theme.Palette;

import javax.swing.*;
import java.awt.event.ActionListener;

/**
 * Простое JPopupMenu, пункты которого тоже стилизуются L&F
 * (цвета берутся из UIManager, но можно задать явные).
 */
public class FileDropdownMenu extends JPopupMenu {
    public FileDropdownMenu(ActionListener onOpen,
                            ActionListener onSave,
                            ActionListener onSaveAs) {
        JMenuItem open = new JMenuItem("Open");
        JMenuItem save = new JMenuItem("Save");
        JMenuItem saveAs = new JMenuItem("Save As…");

        open.addActionListener(onOpen);
        save.addActionListener(onSave);
        saveAs.addActionListener(onSaveAs);

        add(open);
        add(save);
        add(saveAs);
    }
}
