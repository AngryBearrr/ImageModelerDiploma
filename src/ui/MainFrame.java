package ui;

import model.ImageProcessor;
import model.SFMConstructor;
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
import java.util.Collections;
import java.util.List;

public class MainFrame extends JFrame {
    private ImageProcessor processor = new ImageProcessor();
    private String fileSavePath = null;
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel mainPanel = new JPanel(cardLayout);
    private final ImagePanel imagePanel = new ImagePanel(processor);

    private final JList<String> pointsList = new JList<>(processor.getPointsModel());
    private final JList<String> imagesList = new JList<>(processor.getImagesModel());

    private final MouseClickLogic clickLogic = new MouseClickLogic(processor, imagePanel);

    public MainFrame() {
        super("Image Selection Window");
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
        MyButton fileBtn = toolbar.addNavButton("file");
        fileBtn.addActionListener(e ->
                new FileDropdownMenu(
                        save  -> this.save(),
                        saveAs -> this.saveAs(),
                        open  -> this.open()
                ).show(fileBtn, 0, fileBtn.getHeight())
        );
        toolbar.addNavButton("images", e -> cardLayout.show(mainPanel, "images"));
        toolbar.addNavButton("solve",  e -> cardLayout.show(mainPanel, "solve"));
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
        JPanel solvePanel = new JPanel(new BorderLayout());

        JButton buildBtn = new JButton("Build Solution");
        solvePanel.add(buildBtn, BorderLayout.NORTH);

        JPanel viewContainer = new JPanel(new BorderLayout());
        solvePanel.add(viewContainer, BorderLayout.CENTER);

        buildBtn.addActionListener(e -> {
            try {
                List<Point3D> cloud = SFMConstructor.reconstructAll(processor);
                viewContainer.removeAll();
                PointCloud3DPanel cloudPanel = new PointCloud3DPanel(cloud);
                viewContainer.add(cloudPanel, BorderLayout.CENTER);
                viewContainer.revalidate();
                viewContainer.repaint();
            } catch (RuntimeException ex) {
                String msg = ex.getMessage().contains("No valid initial image pair")
                        ? "Не удалось найти пару изображений с достаточным числом общих точек.\n" +
                        "Убедитесь, что вы пометили хотя бы 8 совпадающих точек на двух разных изображениях."
                        : ex.getMessage();
                JOptionPane.showMessageDialog(
                        MainFrame.this,
                        msg,
                        "Ошибка реконструкции",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        });

        return solvePanel;
    }

    private void open() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Open file");
        chooser.setDialogType(JFileChooser.OPEN_DIALOG);
        if (chooser.showDialog(this, "Open") == JFileChooser.APPROVE_OPTION) {
            String filePath = chooser.getSelectedFile().getAbsolutePath();
            getSerializedProcessor(filePath);
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

    private BufferedImage loadDefaultImage(Path path) {
        try {
            BufferedImage img = ImageIO.read(path.toFile());
            System.out.println("Image loaded: " + path);
            return img;
        } catch (IOException e) {
            System.err.println("Failed to load image: " + e.getMessage());
            return new BufferedImage(600, 600, BufferedImage.TYPE_INT_ARGB);
        }
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

    private JPanel createListPanel(JList<String> list, ActionListener event, ImageIcon icon) {
        JPanel panel = new JPanel(new BorderLayout());
        PlusButton addBtn = new PlusButton();

        list.setBorder(null);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new MyListCellRenderer());
        list.setForeground(Palette.TEXT);
        list.setBackground(Palette.MEDIUM_GREY);
        list.setSelectionForeground(Palette.TEXT);
        list.setSelectionBackground(Palette.LIGHT_GREY);

        addBtn.addActionListener(event);
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
        JScrollPane scrollPane = new JScrollPane(list);
        scrollPane.getVerticalScrollBar().setUI(new MyScrollBarUI());
        scrollPane.getHorizontalScrollBar().setUI(new MyScrollBarUI());
        scrollPane.setBorder(null);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private void getSerializedProcessor(String filePath) {
        try {
            ImageProcessor loaded = ImageProcessor.load(filePath);
            this.processor.replaceWith(loaded);
        } catch (FileNotFoundException e) {
            JOptionPane.showMessageDialog(this,
                    "File does not exist: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        } catch (IOException | ClassNotFoundException e) {
            JOptionPane.showMessageDialog(this,
                    "Failed to load model: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void serializeProcessor() {
        if (fileSavePath == null)
            throw new RuntimeException("File path is null");
        File file = new File(fileSavePath);
        if (!file.exists()) {
            try { file.createNewFile(); }
            catch (IOException e) {
                JOptionPane.showMessageDialog(this,
                        "Can't create file: " + e.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
            oos.writeObject(processor);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                    "Can't write to file: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
