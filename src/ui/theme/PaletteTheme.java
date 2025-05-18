package ui.theme;

import com.formdev.flatlaf.FlatLightLaf;  // or FlatDarkLaf, or your own

import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import java.awt.*;

public class PaletteTheme {
    public static void install() throws UnsupportedLookAndFeelException {
        UIManager.setLookAndFeel(new FlatLightLaf());

        UIManager.put("Panel.background", Palette.DARKEST_GREY);
        UIManager.put("Viewport.background", Palette.DARK_GREY);
        UIManager.put("ScrollBar.thumbBackground", Palette.LIGHT_GREY);
        UIManager.put("ScrollBar.trackBackground", Palette.DARK_GREY);

        UIManager.put("Button.background", Palette.BUTTON);
        UIManager.put("Button.foreground", Palette.TEXT);
        UIManager.put("Button.hoverBackground", Palette.BUTTON.brighter());
        UIManager.put("Button.pressedBackground", Palette.BUTTON.darker());
        UIManager.put("Button.font", new FontUIResource("Segoe UI", Font.PLAIN, 12));
        UIManager.put("Button.arc", 8);

        UIManager.put("ToolBar.background", Palette.DARK_GREY);
        UIManager.put("ToolBar.border", BorderFactory.createEmptyBorder(5,5,5,5));

        UIManager.put("List.background", Palette.MEDIUM_GREY);
        UIManager.put("List.foreground", Palette.TEXT);
        UIManager.put("List.selectionBackground", Palette.LIGHT_GREY);
        UIManager.put("List.selectionForeground", Palette.TEXT);

        UIManager.put("Label.foreground", Palette.TEXT);
        UIManager.put("Label.font", new FontUIResource("Segoe UI", Font.PLAIN, 12));

        UIManager.put("FileChooser.background", Palette.DARK_GREY);
        UIManager.put("FileChooser.foreground", Palette.TEXT);

        UIManager.put("ScrollBar.thumbBackground", Palette.LIGHT_GREY);
        UIManager.put("ScrollBar.trackBackground", Palette.DARK_GREY);

        UIManager.put("Menu.background", Palette.MEDIUM_GREY);
        UIManager.put("Menu.foreground", Palette.TEXT);
        UIManager.put("Menu.selectionBackground", Palette.LIGHT_GREY);
        UIManager.put("Menu.selectionForeground", Palette.TEXT);

        UIManager.put("MenuItem.background", Palette.MEDIUM_GREY);
        UIManager.put("MenuItem.foreground", Palette.TEXT);
        UIManager.put("MenuItem.selectionBackground", Palette.LIGHT_GREY);
        UIManager.put("MenuItem.selectionForeground", Palette.TEXT);

        UIManager.put("PopupMenu.background", Palette.MEDIUM_GREY);
        UIManager.put("PopupMenu.border", BorderFactory.createLineBorder(Palette.DARK_GREY));

        // фон всего диалога и панелей внутри него
        UIManager.put("OptionPane.background",         Palette.MEDIUM_GREY);
        UIManager.put("Panel.background",              Palette.MEDIUM_GREY);

        // сообщения
        UIManager.put("OptionPane.messageForeground",  Palette.TEXT);
        UIManager.put("OptionPane.messageAreaBackground", Palette.MEDIUM_GREY);
        UIManager.put("OptionPane.buttonAreaBackground",  Palette.DARK_GREY);

        UIManager.put("Button.background",              Palette.BUTTON);
        UIManager.put("Button.foreground",              Palette.TEXT);
        UIManager.put("OptionPane.buttonAreaBackground", Palette.BUTTON);

        UIManager.put("OptionPane.messageFont",         new FontUIResource("Segoe UI", Font.PLAIN, 12));
        UIManager.put("OptionPane.buttonFont",          new FontUIResource("Segoe UI", Font.PLAIN, 12));

    }
}
