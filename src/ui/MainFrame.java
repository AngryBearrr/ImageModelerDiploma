package ui;

import model.ImageProcessor;
import model.OpenCVSFMConstructor;
import model.Point3D;
import model.buttonsLogic.MouseClickLogic;
import model.buttonsLogic.UiLogicHandler;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Main application window:
 * - Image tab: display images, add points, select points
 * - Solve tab: build 3D cloud, scale, configure via control panel
 */
public class MainFrame extends JFrame {
    private ImageProcessor processor = new ImageProcessor();
    private PointCloud3DPanel cloudPanel;      // панель для облака точек
    private String fileSavePath = null;
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel mainPanel = new JPanel(cardLayout);
    private final ImagePanel imagePanel = new ImagePanel(processor);

    private final JList<String> pointsList = new JList<>(processor.getPointsModel());
    private final JList<String> imagesList = new JList<>(processor.getImagesModel());

    private final MouseClickLogic clickLogic = new MouseClickLogic(processor, imagePanel);

    public MainFrame() {
        super("SfM Reconstruction Tool");
        imagesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        imagesList.addListSelectionListener(
                UiLogicHandler.changeActiveImage(processor, imagePanel, imagesList)
        );
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1600, 900);
        setLocationRelativeTo(null);
        buildUI();
    }

    private void buildUI() {
        setLayout(new BorderLayout());
        add(createToolbar(), BorderLayout.NORTH);
        add(mainPanel, BorderLayout.CENTER);
        mainPanel.add(createImageTab(), "images");
        mainPanel.add(createSolveTab(), "solve");
        cardLayout.show(mainPanel, "images");
    }

    private JToolBar createToolbar() {
        MyToolbar toolbar = new MyToolbar();
        MyButton fileBtn = toolbar.addNavButton("File");
        fileBtn.addActionListener(e ->
                new FileDropdownMenu(
                        save  -> this.save(),
                        saveAs -> this.saveAs(),
                        open  -> this.open()
                ).show(fileBtn, 0, fileBtn.getHeight())
        );
        toolbar.addNavButton("Images", e -> cardLayout.show(mainPanel, "images"));
        toolbar.addNavButton("Solve",  e -> cardLayout.show(mainPanel, "solve"));
        return toolbar;
    }

    private JPanel createImageTab() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(imagePanel, BorderLayout.CENTER);
        panel.add(createSidePanel(), BorderLayout.EAST);
        imagePanel.setImage(loadDefaultImage(Paths.get("src","resources","imageBGFinal.png")));
        imagePanel.addMouseListener(clickLogic);
        return panel;
    }

    private JPanel createSolveTab() {
        MyPanel solvePanel = new MyPanel(new BorderLayout(), 10);
        solvePanel.setBackground(Palette.DARKEST_GREY);

        // Toolbar with Build, Scale, Configure
        MyToolbar toolbar = new MyToolbar();
        MyButton buildBtn    = new MyButton("Build Solution");
        MyButton scaleBtn    = new MyButton("Scale");
        MyButton configBtn   = new MyButton("Configure Cloud");
        toolbar.add(buildBtn);
        toolbar.addSeparator(new Dimension(5, 0));
        toolbar.add(scaleBtn);
        toolbar.addSeparator(new Dimension(5, 0));
        toolbar.add(configBtn);
        solvePanel.add(toolbar, BorderLayout.NORTH);

        // Container for 3D view
        JPanel viewContainer = new JPanel(new BorderLayout());
        viewContainer.setBackground(Palette.DARK_GREY);
        solvePanel.add(viewContainer, BorderLayout.CENTER);

        // Build action
        buildBtn.addActionListener(e -> {
            try {
                List<Point3D> cloud = OpenCVSFMConstructor.reconstructAll(processor);
                viewContainer.removeAll();
                cloudPanel = new PointCloud3DPanel(cloud);
                viewContainer.add(cloudPanel, BorderLayout.CENTER);
                viewContainer.revalidate();
                viewContainer.repaint();
            } catch (RuntimeException ex) {
                String msg = ex.getMessage().contains("стартовой пары")
                        ? "Не удалось найти пару изображений с достаточным числом общих точек.\n" +
                        "Отметьте хотя бы 5 совпадающих точек на двух изображениях."
                        : ex.getMessage();
                JOptionPane.showMessageDialog(
                        MainFrame.this,
                        msg,
                        "Ошибка реконструкции",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        });

        // Scale action
        scaleBtn.addActionListener(evt -> {
            if (cloudPanel == null) return;
            String input = JOptionPane.showInputDialog(
                    MainFrame.this,
                    "Enter scale factor:",
                    cloudPanel.getScaleFactor()
            );
            if (input != null) {
                try {
                    double f = Double.parseDouble(input);
                    cloudPanel.setScaleFactor(f);
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(
                            MainFrame.this,
                            "Invalid number format",
                            "Error",
                            JOptionPane.ERROR_MESSAGE
                    );
                }
            }
        });

        // Configure Cloud action: opens control panel dialog
        configBtn.addActionListener(evt -> {
            if (cloudPanel == null) {
                JOptionPane.showMessageDialog(
                        MainFrame.this,
                        "Сначала постройте облако точек (Build Solution).",
                        "Нет данных",
                        JOptionPane.WARNING_MESSAGE
                );
                return;
            }
            // Retrieve current 3D points
            List<Point3D> pts = cloudPanel.getPoints();
            // Build control panel
            SFMControlPanel ctrl = new SFMControlPanel(pts);
            // Show in modal dialog
            JDialog dialog = new JDialog(MainFrame.this, "Configure Point Cloud", true);
            dialog.getContentPane().add(ctrl);
            dialog.pack();
            dialog.setLocationRelativeTo(MainFrame.this);
            dialog.setVisible(true);
            // After closing, re-render cloud with updated transform
            cloudPanel.updatePoints(OpenCVSFMConstructor.reconstructAll(processor));
        });

        return solvePanel;
    }

    private JPanel createSidePanel() {
        JPanel container = new JPanel(new GridLayout(2, 1, 0, 0));
        container.setPreferredSize(new Dimension(300, 0));
        container.add(createListPanel(
                imagesList,
                e -> UiLogicHandler.addImage(this, processor, imagePanel),
                new ImageIcon(getClass().getResource("/resources/imageIcon.png"))
        ));
        container.add(createListPanel(
                pointsList,
                e -> UiLogicHandler.addPoint(this, processor, imagePanel),
                new ImageIcon(getClass().getResource("/resources/pointIcon.png"))
        ));
        return container;
    }

    private JPanel createListPanel(JList<String> list,
                                   ActionListener onAdd,
                                   ImageIcon icon) {
        JPanel panel = new JPanel(new BorderLayout());
        PlusButton addBtn = new PlusButton();

        list.setBorder(null);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new MyListCellRenderer());
        list.setForeground(Palette.TEXT);
        list.setBackground(Palette.MEDIUM_GREY);
        list.setSelectionForeground(Palette.TEXT);
        list.setSelectionBackground(Palette.LIGHT_GREY);

        addBtn.addActionListener(onAdd);
        MyToolbar toolbar = new MyToolbar();
        toolbar.setLayout(new BorderLayout(5, 0));
        toolbar.add(addBtn, BorderLayout.EAST);
        toolbar.setBorder(BorderFactory.createEmptyBorder(6,6,6,6));
        toolbar.setFloatable(false);
        ImageIcon scaledIcon = new ImageIcon(
                icon.getImage().getScaledInstance(24, 24, Image.SCALE_SMOOTH)
        );
        toolbar.add(new JLabel(scaledIcon), BorderLayout.WEST);

        panel.add(toolbar, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(list);
        scroll.getVerticalScrollBar().setUI(new MyScrollBarUI());
        scroll.getHorizontalScrollBar().setUI(new MyScrollBarUI());
        scroll.setBorder(null);
        panel.add(scroll, BorderLayout.CENTER);

        return panel;
    }

    // File operations: open, save, saveAs
    private void open() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Open file");
        if (chooser.showDialog(this, "Open") == JFileChooser.APPROVE_OPTION) {
            getSerializedProcessor(chooser.getSelectedFile().getAbsolutePath());
            imagesList.setModel(processor.getImagesModel());
            pointsList.setModel(processor.getPointsModel());
            if (processor.activeImageExists()) {
                imagesList.setSelectedValue(processor.getActiveImagePath(), true);
            } else if (processor.getImagesModel().getSize() > 0) {
                imagesList.setSelectedIndex(0);
            }
        }
    }

    private void save() {
        if (fileSavePath == null) saveAs();
        else serializeProcessor();
    }

    private void saveAs() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save As…");
        chooser.setFileFilter(new FileNameExtensionFilter("Model files (*.mdlr)", "mdlr"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith(".mdlr")) {
                file = new File(file.getParentFile(), file.getName() + ".mdlr");
            }
            fileSavePath = file.getAbsolutePath();
            serializeProcessor();
        }
    }

    private void getSerializedProcessor(String filePath) {
        try {
            ImageProcessor loaded = ImageProcessor.load(filePath);
            this.processor.replaceWith(loaded);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Failed to load model: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void serializeProcessor() {
        if (fileSavePath == null) return;
        try (ObjectOutputStream oos = new ObjectOutputStream(
                new FileOutputStream(fileSavePath))) {
            oos.writeObject(processor);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                    "Can't write to file: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private BufferedImage loadDefaultImage(Path path) {
        try {
            return ImageIO.read(path.toFile());
        } catch (IOException e) {
            return new BufferedImage(600, 600, BufferedImage.TYPE_INT_ARGB);
        }
    }
}
