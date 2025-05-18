package ui.uiComponents;

import java.awt.*;

public class PlusButton extends MyButton {
    public PlusButton() { super("+"); setMargin(new Insets(5, 5, 5, 5)); }

    @Override public Dimension getPreferredSize() { return square(super.getPreferredSize()); }
    @Override public Dimension getMinimumSize()  { return square(super.getMinimumSize()); }
    @Override public Dimension getMaximumSize()  { return square(super.getMaximumSize()); }

    private Dimension square(Dimension d) {
        int s = Math.max(d.width, d.height);
        return new Dimension(s, s);
    }
}