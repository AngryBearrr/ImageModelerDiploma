package ui;

import model.ImageProcessor;
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

public class MainFrame extends JFrame {
    private ImageProcessor processor = new ImageProcessor();

    private String fileSavePath = null;
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel mainPanel = new JPanel(cardLayout);
    private final ImagePanel imagePanel = new ImagePanel(processor);

    final JList<String> pointsList = new JList<>(processor.getPointsModel());
    final JList<String> imagesList = new JList<>(processor.getImagesModel());

    MouseClickLogic clickLogic = new MouseClickLogic(processor, imagePanel);

    public MainFrame() {
        super("Image Selection Window");
        imagesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        imagesList.addListSelectionListener(UiLogicHandler.changeActiveImage(processor,imagePanel,imagesList));
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
        MyButton btn = toolbar.addNavButton("file");
        btn.addActionListener(e -> new FileDropdownMenu(
                save ->this.save(),
                saveAs -> this.saveAs(),
                open ->this.open()).show(btn, 0, btn.getHeight()));
        toolbar.addNavButton("images", e -> cardLayout.show(mainPanel, "images"));
        toolbar.addNavButton("solve", e -> cardLayout.show(mainPanel, "solve"));
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

    private void open() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Open file");
        chooser.setDialogType(JFileChooser.OPEN_DIALOG);
        if (chooser.showDialog(this, "Open") == JFileChooser.APPROVE_OPTION) {
            String filePath = chooser.getSelectedFile().getAbsolutePath();
            try {
                // Заменили присвоение на вызов метода, который сам инициализирует поле processor
                getSerializedProcessor(filePath);

                // Обновляем модели списков
                imagesList.setModel(processor.getImagesModel());
                pointsList.setModel(processor.getPointsModel());

                // Восстанавливаем выделение активного изображения
                if (processor.activeImageExists()) {
                    String activeName = processor.getActiveImagePath();
                    imagesList.setSelectedValue(activeName, true);
                } else if (processor.getImagesModel().getSize() > 0) {
                    imagesList.setSelectedIndex(0);
                }
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
    }


    private void save(){
        if(fileSavePath == null)
            saveAs();
        else
            serializeProcessor();
    }

    private void saveAs() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save As…");
        chooser.setFileFilter(new FileNameExtensionFilter("Model files (*.mdlr)", "mdlr"));

        int res = chooser.showSaveDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith(".mdlr")) {
                file = new File(file.getParentFile(),
                        file.getName() + ".mdlr");
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
                e -> UiLogicHandler.addImage(this,
                        processor,
                        imagePanel),
                new ImageIcon (getClass().getResource("/resources/imageIcon.png"))));
        container.add(createListPanel(
                pointsList,
                e -> UiLogicHandler.addPoint(this,processor, imagePanel),
                new ImageIcon(getClass().getResource("/resources/pointIcon.png"))));
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
        Image preScaled = icon.getImage();
        ImageIcon scaledIcon = new ImageIcon(preScaled.getScaledInstance(24,24, Image.SCALE_SMOOTH));
        JLabel iconLabel = new JLabel(scaledIcon);
        toolbar.add(iconLabel, BorderLayout.WEST);

        panel.add(toolbar, BorderLayout.NORTH);
        JScrollPane scrollPane = new JScrollPane(list);
        JScrollBar vsb = scrollPane.getVerticalScrollBar();
        vsb.setUI(new MyScrollBarUI());
        JScrollBar hsb = scrollPane.getVerticalScrollBar();
        hsb.setUI(new MyScrollBarUI());
        scrollPane.setBorder(null);
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createSolveTab() {
        JPanel panel = new JPanel();
        panel.add(new JLabel("Это панель 2"));
        return panel;
    }

    private void getSerializedProcessor(String filePath) throws ClassNotFoundException{
        File file = new File(filePath);
        try (ObjectInputStream is = new ObjectInputStream(new FileInputStream(filePath))){
            this.processor = (ImageProcessor) is.readObject();
        } catch (FileNotFoundException e) {
            JOptionPane.showMessageDialog(
                    this,
                    "file does not exist : " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE
            );
        } catch (IOException e) {
            JOptionPane.showMessageDialog(
                    this,
                    "can't access file : " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void serializeProcessor(){
        if (fileSavePath == null)
            throw new RuntimeException("ПИДАРАС ЕБАНЫЙ");
        File file = new File(fileSavePath);
        if (!file.exists()){
            try {
                file.createNewFile();
            } catch (IOException e) {
                JOptionPane.showMessageDialog(
                        this,
                        "file does not exist and can't be created: " + e.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        }
        try (ObjectOutputStream ostream = new ObjectOutputStream(new FileOutputStream(file))){
            ostream.writeObject(processor);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(
                    this,
                    "can't write to file : " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }
}