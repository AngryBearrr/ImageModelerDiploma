package ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionListener;
import javax.swing.plaf.basic.BasicScrollBarUI;

final class Palette {
    public static final Color LIGHT_GREY = new Color(112, 112, 112);
    public static final Color GREY = new Color(77, 77, 77);
    public static final Color DARK_GREY = new Color(40, 40, 40);
    public static final Color TEXT = Color.WHITE;
    public static final Color DARKEST_GREY = new Color(20, 20, 20);
    public static final Color MEDIUM_GREY = new Color(30, 30, 30);

}

class MyButton extends JButton {
    public MyButton(String text) {
        super(text);
        setMargin(new Insets(0,6,0,6));
        setBackground(Palette.GREY);
        setForeground(Palette.TEXT);
        setOpaque(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setContentAreaFilled(false);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(getBackground());
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
        if (getModel().isPressed()) {
            g2.setColor(new Color(0, 0, 0, 50));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
        } else if (getModel().isRollover()) {
            g2.setColor(new Color(255, 255, 255, 30));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
        }
        g2.dispose();
        super.paintComponent(g);
    }
}

class PlusButton extends MyButton {
    public PlusButton() { super("+"); setMargin(new Insets(5, 5, 5, 5)); }

    @Override public Dimension getPreferredSize() { return square(super.getPreferredSize()); }
    @Override public Dimension getMinimumSize()  { return square(super.getMinimumSize()); }
    @Override public Dimension getMaximumSize()  { return square(super.getMaximumSize()); }

    private Dimension square(Dimension d) {
        int s = Math.max(d.width, d.height);
        return new Dimension(s, s);
    }
}

class MyToolbar extends JToolBar {
    public MyToolbar() {
        setBackground(Palette.DARK_GREY);
        setOpaque(true);
        setFloatable(false);
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
    }

    public void addNavButton(String text, ActionListener action) {
        MyButton btn = new MyButton(text);
        btn.addActionListener(action);
        add(btn);
        addSeparator(new Dimension(5, 30));
    }

    public MyButton addNavButton(String text) {
        MyButton btn = new MyButton(text);
        add(btn);
        addSeparator(new Dimension(5, 30));
        return btn;
    }

    @Override public MyToolbar add(Component comp) {
        super.add(comp);
        return this;
    }
}
class MyPanel extends JPanel{
    public MyPanel(BorderLayout layout){
        super (layout);
        setBorder(new EmptyBorder(5, 5, 5, 5));
    }
    public MyPanel(BorderLayout layout, int gap){
        super (layout);
        setBorder(new EmptyBorder(gap, gap, gap, gap));
    }
}

class MyListCellRenderer implements ListCellRenderer<String> {
    private final Color evenColor = Palette.DARK_GREY;
    private final Color oddColor  = Palette.DARKEST_GREY;

    @Override
    public Component getListCellRendererComponent(JList<? extends String> list,
                                                  String value,
                                                  int index,
                                                  boolean isSelected,
                                                  boolean cellHasFocus) {
        JPanel panel = new JPanel(new BorderLayout(0, 0));
        panel.setOpaque(true);

        if (isSelected) {
            panel.setBackground(list.getSelectionBackground());
        } else {
            panel.setBackground((index % 2 == 0) ? evenColor : oddColor);
        }

        JLabel textLabel = new JLabel(value);
        textLabel.setOpaque(false);
        textLabel.setBorder(BorderFactory.createEmptyBorder(6,6,6,6));
        textLabel.setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
        panel.add(textLabel, BorderLayout.WEST);

        Icon circleIcon = new CircleIcon(getColorForIndex(index), 16);
        JLabel iconLabel = new JLabel(circleIcon);
        iconLabel.setBorder(BorderFactory.createEmptyBorder(6,6,6,6));
        iconLabel.setOpaque(false);
        panel.add(iconLabel, BorderLayout.EAST);

        return panel;
    }

    private Color getColorForIndex(int index) {
        // ваша логика: по индексу выбираем цвет состояния
        return Color.RED;
    }
}



class CircleIcon implements Icon {
    private final int diameter;
    private final Color color;

    public CircleIcon(Color color, int diameter) {
        this.color = color;
        this.diameter = diameter;
    }

    @Override
    public int getIconWidth() {
        return diameter;
    }

    @Override
    public int getIconHeight() {
        return diameter;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setColor(color);
        g2d.fillOval(x, y, diameter, diameter);
        g2d.dispose();
    }
}

class MyScrollBarUI extends BasicScrollBarUI {
    @Override
    protected void configureScrollBarColors() {
        // устанавливаем простые цвета
        thumbColor = Palette.LIGHT_GREY;
        trackColor = Palette.DARK_GREY;
        // при наведении/экшенах (необязательно)
        thumbHighlightColor = Palette.LIGHT_GREY.brighter();
        thumbDarkShadowColor = Palette.LIGHT_GREY.darker();
    }

    // Скруглённый «ползунок»
    @Override
    protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(thumbColor);
        int arc = thumbBounds.width; // скругляем по ширине
        g2.fillRoundRect(thumbBounds.x,
                thumbBounds.y,
                thumbBounds.width,
                thumbBounds.height,
                arc, arc);
        g2.dispose();
    }

    // Простой трек без «решётки»
    @Override
    protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
        g.setColor(trackColor);
        g.fillRect(trackBounds.x,
                trackBounds.y,
                trackBounds.width,
                trackBounds.height);
    }

    // Если хотите убрать стрелки
    @Override
    protected JButton createDecreaseButton(int orientation) {
        return createZeroButton();
    }
    @Override
    protected JButton createIncreaseButton(int orientation) {
        return createZeroButton();
    }
    private JButton createZeroButton() {
        JButton btn = new JButton();
        Dimension zero = new Dimension(0, 0);
        btn.setPreferredSize(zero);
        btn.setMinimumSize(zero);
        btn.setMaximumSize(zero);
        return btn;
    }
}
class FileDropdownMenu extends JPopupMenu{
    JMenuItem openButton = new JMenuItem("open");
    JMenuItem saveButton = new JMenuItem("save");
    JMenuItem saveAsButton = new JMenuItem("save as");
    public FileDropdownMenu(ActionListener save, ActionListener saveAs, ActionListener open){
        openButton.setBackground(Palette.GREY);
        openButton.setForeground(Palette.TEXT);
        saveButton.setBackground(Palette.GREY);
        saveButton.setForeground(Palette.TEXT);
        saveAsButton.setBackground(Palette.GREY);
        saveAsButton.setForeground(Palette.TEXT);
        setBackground(Palette.GREY);
        saveButton.addActionListener(save);
        saveAsButton.addActionListener(saveAs);
        openButton.addActionListener(open);
        add(openButton);
        add(saveButton);
        add(saveAsButton);
        setBorder(BorderFactory.createEmptyBorder(1,1,1,1));
    }
}