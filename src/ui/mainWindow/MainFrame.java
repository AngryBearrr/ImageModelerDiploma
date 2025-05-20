package ui.mainWindow;

import model.ImageProcessor;
import model.OpenCVSFMConstructor;
import model.Point3D;
import model.buttonsLogic.MouseClickLogic;
import model.buttonsLogic.UiLogicHandler;
import ui.uiComponents.*;

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
    private final ImageProcessor processor = new ImageProcessor();
    private PointCloud3DPanel cloudPanel;
    private SFMControlPanel controlPanel;
    private String fileSavePath;

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
        imagesList.setCellRenderer(new MyListCellRenderer());
        imagesList.addListSelectionListener(
                UiLogicHandler.changeActiveImage(processor, imagePanel, imagesList)
        );

        pointsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        pointsList.setCellRenderer(new MyListCellRenderer());

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
        MyToolbar tb = new MyToolbar();

        // File menu
        MyButton fileBtn = new MyButton("File");
        fileBtn.addActionListener(e ->
                new FileDropdownMenu(
                        ev -> open(),
                        ev -> save(),
                        ev -> saveAs()
                ).show(fileBtn, 0, fileBtn.getHeight())
        );
        tb.add(fileBtn);
        tb.addSeparator(new Dimension(5, 0));

        // Navigation buttons
        MyButton imgBtn = new MyButton("Images");
        imgBtn.addActionListener(ev -> cardLayout.show(mainPanel, "images"));
        tb.add(imgBtn);
        tb.addSeparator(new Dimension(5, 0));

        MyButton solBtn = new MyButton("Solve");
        solBtn.addActionListener(ev -> cardLayout.show(mainPanel, "solve"));
        tb.add(solBtn);

        return tb;
    }

    private JPanel createImageTab() {
        JPanel p = new JPanel(new BorderLayout());
        p.add(imagePanel, BorderLayout.CENTER);
        p.add(createSidePanel(), BorderLayout.EAST);
        imagePanel.setImage(loadDefaultImage(Paths.get("src","resources","imageBGFinal.png")));
        imagePanel.addMouseListener(clickLogic);
        return p;
    }

    private JPanel createSolveTab() {
        JPanel solve = new JPanel(new BorderLayout());
        solve.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));

        // Top toolbar
        MyToolbar top = new MyToolbar();
        MyButton build = new MyButton("Build Solution");
        MyButton scale = new MyButton("Scale");
        top.add(build);
        top.addSeparator(new Dimension(5, 0));
        top.add(scale);
        solve.add(top, BorderLayout.NORTH);

        // 3D view container
        JPanel view = new JPanel(new BorderLayout());
        solve.add(view, BorderLayout.CENTER);

        // Placeholder for control panel
        Component placeholder = Box.createRigidArea(new Dimension(300, 0));
        solve.add(placeholder, BorderLayout.EAST);

        build.addActionListener(e -> {
            try {
                List<Point3D> cloud = OpenCVSFMConstructor.reconstructAll(processor);
                if (cloudPanel == null) cloudPanel = new PointCloud3DPanel(cloud);
                else cloudPanel.updatePoints(cloud);

                view.removeAll();
                view.add(cloudPanel, BorderLayout.CENTER);

                if (controlPanel != null) solve.remove(controlPanel);
                controlPanel = new SFMControlPanel(cloud);
                controlPanel.setOnTransform(() -> {
                    cloudPanel.updatePoints(controlPanel.getTransformedPoints());
                });
                controlPanel.getPointList().addListSelectionListener(evt -> {
                    if (!evt.getValueIsAdjusting()) {
                        cloudPanel.setActiveIndex(controlPanel.getPointList().getSelectedIndex());
                    }
                });

                solve.remove(placeholder);
                solve.add(controlPanel, BorderLayout.EAST);
                solve.revalidate();
                solve.repaint();
            } catch (RuntimeException ex) {
                JOptionPane.showMessageDialog(
                        this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE
                );
            }
        });

        scale.addActionListener(evt -> {
            if (cloudPanel == null) return;
            String in = JOptionPane.showInputDialog(
                    this, "Scale factor:", cloudPanel.getScaleFactor()
            );
            if (in != null) {
                try {
                    cloudPanel.setScaleFactor(Double.parseDouble(in));
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(
                            this, "Invalid number", "Error", JOptionPane.ERROR_MESSAGE
                    );
                }
            }
        });

        return solve;
    }

    private JPanel createSidePanel() {
        JPanel side = new JPanel(new GridLayout(2, 1));
        side.setPreferredSize(new Dimension(300, 0));
        side.add(createListPanel(
                imagesList,
                e -> UiLogicHandler.addImage(this, processor, imagePanel),
                "/resources/imageIcon.png"
        ));
        side.add(createListPanel(
                pointsList,
                e -> UiLogicHandler.addPoint(this, processor, imagePanel),
                "/resources/pointIcon.png"
        ));
        return side;
    }

    private JPanel createListPanel(JList<String> list,
                                   ActionListener onAdd,
                                   String iconPath) {
        JPanel panel = new JPanel(new BorderLayout());

        JToolBar tb = new MyToolbar();
        ImageIcon orig = new ImageIcon(getClass().getResource(iconPath));
        ImageIcon icon = new ImageIcon(
                orig.getImage().getScaledInstance(32, 32, Image.SCALE_SMOOTH)
        );
        tb.add(new JLabel(icon));
        tb.add(Box.createHorizontalGlue());
        PlusButton plus = new PlusButton();
        plus.setPreferredSize(new Dimension(32, 32));
        plus.addActionListener(onAdd);
        tb.add(plus);
        panel.add(tb, BorderLayout.NORTH);

        JScrollPane sp = new JScrollPane(list);
        sp.setBorder(null);
        panel.add(sp, BorderLayout.CENTER);

        return panel;
    }

    private void open() {
        JFileChooser c = new JFileChooser();
        c.setFileFilter(new FileNameExtensionFilter("Model files (*.mdlr)", "mdlr"));
        if (c.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            String path = c.getSelectedFile().getAbsolutePath();
            ImageProcessor loaded = ImageProcessor.load(path);
            processor.replaceWith(loaded);
            imagesList.setModel(processor.getImagesModel());
            pointsList.setModel(processor.getPointsModel());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Load failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void save() {
        if (fileSavePath == null) saveAs();
        else serializeProcessor();
    }

    private void saveAs() {
        JFileChooser c = new JFileChooser();
        c.setFileFilter(new FileNameExtensionFilter("Model files (*.mdlr)", "mdlr"));
        if (c.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        String p = c.getSelectedFile().getAbsolutePath();
        fileSavePath = p.toLowerCase().endsWith(".mdlr") ? p : p + ".mdlr";
        serializeProcessor();
    }

    private void serializeProcessor() {
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(fileSavePath))) {
            out.writeObject(processor);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                    "Save failed: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
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
