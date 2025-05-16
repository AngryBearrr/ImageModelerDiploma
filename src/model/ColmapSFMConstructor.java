package model;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class ColmapSFMConstructor {
    /**
     * Запускает внешний COLMAP-пайплайн на всех текущих изображениях из ImageProcessor
     * и возвращает список 3D-точек в виде List<Point3D>.
     */
    public static List<Point3D> reconstructAll(ImageProcessor proc) {
        try {
            // 1) Собираем пути к изображениям
            List<String> imagePaths = Collections.list(proc.getImagesModel().elements());
            if (imagePaths.isEmpty()) {
                throw new RuntimeException("No images to reconstruct");
            }

            // 2) Создаём временную рабочую папку
            Path workspace = Files.createTempDirectory("colmap_sfm_");
            Path imagesDir = workspace.resolve("images");
            Files.createDirectory(imagesDir);

            // 3) Копируем туда все картинки
            for (String imgPath : imagePaths) {
                Path src = Paths.get(imgPath);
                Path dst = imagesDir.resolve(src.getFileName());
                Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
            }

            // Пути для базы данных и результатов
            Path databasePath = workspace.resolve("database.db");
            Path sparseDir    = workspace.resolve("sparse");
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
            throw new RuntimeException("COLMAP pipeline error: " + e.getMessage(), e);
        }
    }

    /**
     * Запускает одну команду в subprocess и кидает исключение, если exitCode != 0.
     */
    private static void runCommand(List<String> command, Path workingDir)
            throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workingDir.toFile());
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("[COLMAP] " + line);
            }
        }
        int exit = proc.waitFor();
        if (exit != 0) {
            throw new RuntimeException(
                    "Command failed (exit " + exit + "): " + String.join(" ", command)
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
                // формат: POINT3D_ID X Y Z R G B ERROR TRACK_LENGTH [TRACK...]
                String   id = tok[0];
                double   x  = Double.parseDouble(tok[1]);
                double   y  = Double.parseDouble(tok[2]);
                double   z  = Double.parseDouble(tok[3]);
                points.add(new Point3D(id, x, y, z));
            }
        }
        return points;
    }
}
