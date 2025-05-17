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
import java.util.List;

public class MainFrame extends JFrame {
    private ImageProcessor processor = new ImageProcessor();
    private PointCloud3DPanel cloudPanel;
    private SFMControlPanel   controlPanel;
    private String            fileSavePath = null;

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel     mainPanel  = new JPanel(cardLayout);
    private final ImagePanel imagePanel = new ImagePanel(processor);

    private final JList<String> imagesList = new JList<>(processor.getImagesModel());
    private final JList<String> pointsList = new JList<>(processor.getPointsModel());
    private final MouseClickLogic clickLogic = new MouseClickLogic(processor, imagePanel);

    public MainFrame() {
        super("SfM Reconstruction Tool");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1600, 900);
        setLocationRelativeTo(null);

        imagesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        imagesList.addListSelectionListener(
                UiLogicHandler.changeActiveImage(processor, imagePanel, imagesList)
        );

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
                        e2 -> this.save(),
                        e2 -> this.saveAs(),
                        e2 -> this.open()
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

        // Top toolbar
        MyToolbar toolbar = new MyToolbar();
        MyButton buildBtn = new MyButton("Build Solution");
        MyButton scaleBtn = new MyButton("Scale");
        toolbar.add(buildBtn);
        toolbar.addSeparator(new Dimension(5,0));
        toolbar.add(scaleBtn);
        solvePanel.add(toolbar, BorderLayout.NORTH);

        // 3D view container
        JPanel viewContainer = new JPanel(new BorderLayout());
        viewContainer.setBackground(Palette.DARK_GREY);
        solvePanel.add(viewContainer, BorderLayout.CENTER);

        // Placeholder for side panel
        Component placeholder = Box.createRigidArea(new Dimension(300,0));
        solvePanel.add(placeholder, BorderLayout.EAST);

        // Build action
        buildBtn.addActionListener(e -> {
            try {
                List<Point3D> cloud = OpenCVSFMConstructor.reconstructAll(processor);

                // Create or update 3D view
                if (cloudPanel == null) cloudPanel = new PointCloud3DPanel(cloud);
                else cloudPanel.updatePoints(cloud);
                viewContainer.removeAll();
                viewContainer.add(cloudPanel, BorderLayout.CENTER);

                // Create control panel
                if (controlPanel != null) solvePanel.remove(controlPanel);
                controlPanel = new SFMControlPanel(cloud);
                controlPanel.setOnTransform(() -> {
                    List<Point3D> updated = OpenCVSFMConstructor.reconstructAll(processor);
                    cloudPanel.updatePoints(updated);
                });
                controlPanel.getPointList().addListSelectionListener(evt -> {
                    if (!evt.getValueIsAdjusting()) {
                        int idx = controlPanel.getPointList().getSelectedIndex();
                        cloudPanel.setActiveIndex(idx);
                    }
                });

                solvePanel.remove(placeholder);
                solvePanel.add(controlPanel, BorderLayout.EAST);

                solvePanel.revalidate();
                solvePanel.repaint();
            } catch (RuntimeException ex) {
                JOptionPane.showMessageDialog(
                        MainFrame.this,
                        ex.getMessage(),
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

        return solvePanel;
    }

    private JPanel createSidePanel() {
        JPanel container = new JPanel(new GridLayout(2,1));
        container.setPreferredSize(new Dimension(300,0));
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
        toolbar.setLayout(new BorderLayout(5,0));
        toolbar.add(addBtn, BorderLayout.EAST);
        toolbar.setBorder(BorderFactory.createEmptyBorder(6,6,6,6));
        toolbar.setFloatable(false);
        toolbar.add(new JLabel(new ImageIcon(
                icon.getImage().getScaledInstance(24,24,Image.SCALE_SMOOTH)
        )), BorderLayout.WEST);

        panel.add(toolbar, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(list);
        scroll.getVerticalScrollBar().setUI(new MyScrollBarUI());
        scroll.getHorizontalScrollBar().setUI(new MyScrollBarUI());
        scroll.setBorder(null);
        panel.add(scroll, BorderLayout.CENTER);

        return panel;
    }

    // --- File operations: open, save, saveAs, serialize, loadDefaultImage ---

    private void open() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Open file");
        if (chooser.showDialog(this, "Open") == JFileChooser.APPROVE_OPTION) {
            String path = chooser.getSelectedFile().getAbsolutePath();
            try {
                ImageProcessor loaded = ImageProcessor.load(path);
                this.processor.replaceWith(loaded);
                imagesList.setModel(processor.getImagesModel());
                pointsList.setModel(processor.getPointsModel());
                if (processor.activeImageExists()) {
                    imagesList.setSelectedValue(processor.getActiveImagePath(), true);
                } else if (processor.getImagesModel().getSize() > 0) {
                    imagesList.setSelectedIndex(0);
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this,
                        "Failed to load: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
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
            return new BufferedImage(600,600,BufferedImage.TYPE_INT_ARGB);
        }
    }
}
