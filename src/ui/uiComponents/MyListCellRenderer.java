package ui.uiComponents;

import ui.theme.Palette;

import javax.swing.*;
import java.awt.*;

public class MyListCellRenderer implements ListCellRenderer<Object> {
    @Override
    public Component getListCellRendererComponent(JList<?> list,
                                                  Object value,
                                                  int index,
                                                  boolean isSelected,
                                                  boolean cellHasFocus) {
        JLabel lbl = new JLabel(value == null ? "" : value.toString());
        lbl.setOpaque(true);
        lbl.setBorder(BorderFactory.createEmptyBorder(6,6,6,6));
        lbl.setBackground(isSelected
                ? list.getSelectionBackground()
                : (index%2==0 ? Palette.DARK_GREY : Palette.DARKEST_GREY));
        lbl.setForeground(isSelected
                ? list.getSelectionForeground()
                : list.getForeground());
        return lbl;
    }
}
