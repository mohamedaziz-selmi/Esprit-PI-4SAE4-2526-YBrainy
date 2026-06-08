package esprit.tn.breadandbutteruser.services;

import esprit.tn.breadandbutteruser.config.FaceBiometricProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class FaceBiometricEngineService {
    private final FaceBiometricProperties properties;

    public String computeFaceHash(MultipartFile image) {
        ensureImagePresent(image);
        Path tempImage = null;
        try {
            tempImage = writeMultipartAsPng(image, "face-enroll-", ".png");
            String output = runPythonCommand(List.of(
                    properties.getPythonCommand(),
                    resolveExistingPath(properties.getScriptPath()).toString(),
                    "hash",
                    tempImage.toString(),
                    resolveExistingPath(properties.getCascadePath()).toString()
            ));
            if (!StringUtils.hasText(output)) {
                throw new RuntimeException("Unable to compute face biometric hash.");
            }
            return output.trim();
        } finally {
            deleteQuietly(tempImage);
        }
    }

    public String matchFaceHash(MultipartFile image) {
        ensureImagePresent(image);
        Path tempImage = null;
        try {
            tempImage = writeMultipartAsPng(image, "face-login-", ".png");
            String output = runPythonCommand(List.of(
                    properties.getPythonCommand(),
                    resolveExistingPath(properties.getScriptPath()).toString(),
                    "match",
                    resolveStorageDir().toString(),
                    tempImage.toString(),
                    resolveExistingPath(properties.getCascadePath()).toString()
            ));
            return StringUtils.hasText(output) ? output.trim() : null;
        } finally {
            deleteQuietly(tempImage);
        }
    }

    public void storeEnrollmentImage(String faceHash, MultipartFile image, String previousFaceHash) {
        ensureImagePresent(image);
        if (!StringUtils.hasText(faceHash)) {
            throw new RuntimeException("Face biometric hash is required.");
        }

        Path targetPath = resolveStorageDir().resolve(faceHash.trim() + ".png");
        try {
            deleteEnrollmentImage(previousFaceHash);
            BufferedImage buffered = readImage(image);
            ImageIO.write(buffered, "png", targetPath.toFile());
        } catch (IOException e) {
            throw new RuntimeException("Unable to store face biometric image.", e);
        }
    }

    public void deleteEnrollmentImage(String faceHash) {
        if (!StringUtils.hasText(faceHash)) {
            return;
        }

        try (Stream<Path> files = Files.list(resolveStorageDir())) {
            files.filter(path -> {
                        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
                        String expectedStem = faceHash.trim().toLowerCase(Locale.ROOT) + ".";
                        return fileName.startsWith(expectedStem);
                    })
                    .forEach(this::deleteQuietly);
        } catch (IOException e) {
            log.warn("Unable to delete face biometric image for hash {}: {}", faceHash, e.getMessage());
        }
    }

    private void ensureImagePresent(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new RuntimeException("A face image is required.");
        }
    }

    private BufferedImage readImage(MultipartFile image) throws IOException {
        try (InputStream stream = image.getInputStream()) {
            BufferedImage buffered = ImageIO.read(stream);
            if (buffered == null) {
                throw new RuntimeException("The uploaded file is not a supported image.");
            }
            return buffered;
        }
    }

    private Path writeMultipartAsPng(MultipartFile image, String prefix, String suffix) {
        try {
            BufferedImage buffered = readImage(image);
            Path tempFile = Files.createTempFile(prefix, suffix);
            ImageIO.write(buffered, "png", tempFile.toFile());
            return tempFile;
        } catch (IOException e) {
            throw new RuntimeException("Unable to read the uploaded face image.", e);
        }
    }

    private String runPythonCommand(List<String> command) {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(false);

        try {
            Process process = processBuilder.start();
            boolean finished = process.waitFor(15, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("Face recognition timed out. Please try again with better lighting.");
            }
            int exitCode = process.exitValue();
            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).trim();

            if (exitCode != 0) {
                String message = StringUtils.hasText(stderr) ? stderr : "Face recognition helper failed.";
                log.warn("Face biometric Python error (exit {}): {}", exitCode, message);
                if (message.contains("No face detected")) {
                    throw new RuntimeException("No face was detected. Center your face in the camera and try again.");
                }
                throw new RuntimeException(message);
            }

            if (StringUtils.hasText(stderr)) {
                log.warn("Face biometric match info: {}", stderr);
            }
            return stdout;
        } catch (IOException e) {
            throw new RuntimeException(
                    "Unable to start the Python face recognition helper. Verify Python and OpenCV are installed.",
                    e
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Face recognition was interrupted.", e);
        }
    }

    private Path resolveStorageDir() {
        Path storageDir = resolveConfiguredPath(properties.getStorageDir()).normalize();
        try {
            Files.createDirectories(storageDir);
        } catch (IOException e) {
            throw new RuntimeException("Unable to prepare face biometric storage directory.", e);
        }
        return storageDir;
    }

    private Path resolveExistingPath(String configuredPath) {
        Path path = resolveConfiguredPath(configuredPath).normalize();
        if (!Files.exists(path)) {
            throw new RuntimeException("Configured face biometric path does not exist: " + path);
        }
        return path;
    }

    private Path resolveConfiguredPath(String configuredPath) {
        if (!StringUtils.hasText(configuredPath)) {
            throw new RuntimeException("Face biometric path configuration is missing.");
        }

        Path path = Paths.get(configuredPath.trim());
        if (!path.isAbsolute()) {
            path = path.toAbsolutePath();
        }

        return path;
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.debug("Unable to delete temporary file {}: {}", path, e.getMessage());
        }
    }
}
