package model;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.logging.*;

public class ColmapSFMConstructor {
    private static final Logger LOGGER = Logger.getLogger(ColmapSFMConstructor.class.getName());

    /**
     * Исключение, возникающее при ошибках COLMAP-пайплайна.
     */
    public static class ColmapException extends Exception {
        public ColmapException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Запускает внешний COLMAP-пайплайн на всех текущих изображениях из ImageProcessor
     * и возвращает список 3D-точек в виде List<Point3D>.
     */
    public static List<Point3D> reconstructAll(ImageProcessor proc) throws ColmapException {
        Path workspace = null;
        try {
            // Проверяем доступность COLMAP
            checkColmapAvailability();

            // 1) Собираем пути к изображениям
            Enumeration<?> elements = proc.getImagesModel().elements();
            List<String> imagePaths = new ArrayList<>();
            while (elements.hasMoreElements()) {
                Object elem = elements.nextElement();
                if (elem instanceof String) {
                    imagePaths.add((String) elem);
                } else {
                    throw new ColmapException("Unexpected element type in images model: " + elem.getClass(), null);
                }
            }
            if (imagePaths.isEmpty()) {
                throw new ColmapException("No images to reconstruct", null);
            }

            // 2) Создаём временную рабочую папку
            workspace = Files.createTempDirectory("colmap_sfm_");
            Path imagesDir = workspace.resolve("images");
            Files.createDirectory(imagesDir);

            // 3) Копируем туда все картинки, проверяя существование
            for (String imgPath : imagePaths) {
                Path src = Paths.get(imgPath);
                if (!Files.exists(src) || !Files.isRegularFile(src)) {
                    throw new ColmapException("Image file does not exist or is not a regular file: " + src, null);
                }
                Path dst = imagesDir.resolve(src.getFileName());
                Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
            }

            // Пути для базы данных и результатов
            Path databasePath = workspace.resolve("database.db");
            Path sparseDir = workspace.resolve("sparse");
            Files.createDirectory(sparseDir);

            // 4) Запускаем COLMAP feature_extractor
            runCommand(Arrays.asList(
                    "colmap", "feature_extractor",
                    "--database_path", databasePath.toString(),
                    "--image_path",    imagesDir.toString()
            ), workspace);

            // 5) Запускаем COLMAP exhaustive_matcher
            runCommand(Arrays.asList(
                    "colmap", "exhaustive_matcher",
                    "--database_path", databasePath.toString()
            ), workspace);

            // 6) Запускаем COLMAP mapper (incremental SfM)
            runCommand(Arrays.asList(
                    "colmap", "mapper",
                    "--database_path", databasePath.toString(),
                    "--image_path",    imagesDir.toString(),
                    "--output_path",   sparseDir.toString()
            ), workspace);

            // 7) Конвертируем полученную модель в текстовый формат
            Path model0   = sparseDir.resolve("0");
            Path outputTxt = workspace.resolve("points3D.txt");
            runCommand(Arrays.asList(
                    "colmap", "model_converter",
                    "--input_path",  model0.toString(),
                    "--output_path", outputTxt.toString(),
                    "--output_type", "TXT"
            ), workspace);

            // 8) Парсим точки из TXT и возвращаем
            return parseColmapPoints(outputTxt);

        } catch (IOException | InterruptedException e) {
            throw new ColmapException("COLMAP pipeline error: " + e.getMessage(), e);
        } finally {
            if (workspace != null) {
                try {
                    deleteDirectoryRecursively(workspace);
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Failed to delete workspace " + workspace, e);
                }
            }
        }
    }

    /**
     * Проверяем доступность команды COLMAP, выполняя `colmap --version`.
     */
    private static void checkColmapAvailability() throws IOException, InterruptedException, ColmapException {
        ProcessBuilder pb = new ProcessBuilder("colmap", "--version");
        Process proc = pb.start();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                LOGGER.fine("[COLMAP Version] " + line);
            }
        }
        int exit = proc.waitFor();
        if (exit != 0) {
            throw new ColmapException("COLMAP not found or returned exit code " + exit, null);
        }
    }

    /**
     * Запускает одну команду в subprocess и кидает исключение, если exitCode != 0.
     */
    private static void runCommand(List<String> command, Path workingDir)
            throws IOException, InterruptedException, ColmapException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workingDir.toFile());
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                LOGGER.info("[COLMAP] " + line);
            }
        }
        int exit = proc.waitFor();
        if (exit != 0) {
            throw new ColmapException(
                    "Command failed (exit " + exit + "): " + String.join(" ", command), null
            );
        }
    }

    /**
     * Читает текстовый файл points3D.txt, сгенерированный COLMAP,
     * и возвращает список Point3D (id, x, y, z).
     */
    private static List<Point3D> parseColmapPoints(Path txtFile) throws IOException {
        List<Point3D> points = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(txtFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] tok = line.split("\\s+");
                if (tok.length < 4) {
                    throw new IOException("Invalid COLMAP points file format at line: " + line);
                }
                String   id = tok[0];
                double   x  = Double.parseDouble(tok[1]);
                double   y  = Double.parseDouble(tok[2]);
                double   z  = Double.parseDouble(tok[3]);
                points.add(new Point3D(id, x, y, z));
            }
        }
        return points;
    }

    /**
     * Рекурсивно удаляет директорию и все её содержимое.
     */
    private static void deleteDirectoryRecursively(Path path) throws IOException {
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                    throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
