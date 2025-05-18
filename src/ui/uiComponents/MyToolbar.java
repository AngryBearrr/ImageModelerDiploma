package ui.uiComponents;

import javax.swing.*;

/**
 * Лаконичный JToolBar без собственных цветов —
 * берёт их из UIManager (установленного L&F).
 */
public class MyToolbar extends JToolBar {
    public MyToolbar() {
        setFloatable(false);
    }
}
