package dev.frankheijden.incoderanalysis.preprocessing;

import com.google.common.hash.Hashing;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.io.inputstream.ZipInputStream;
import net.lingala.zip4j.model.FileHeader;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class RepositoryUnzipper {

    private static final String REPOSITORIES_FOLDER = "repositories";
    private static final String REPOSITORY_FILES_FOLDER = "repository-files";

    private final Path outputPath;
    private final Set<String> extensions;
    private final boolean extractAll;

    public RepositoryUnzipper(Path outputPath, Set<String> extensions) {
        this.outputPath = outputPath;
        this.extensions = extensions;
        this.extractAll = extensions.size() == 0;
    }

    public void unzipRepository(Path repositoryPath) throws IOException {
        try (ZipFile zipFile = new ZipFile(repositoryPath.toFile())) {
            if (extractAll) {
                zipFile.extractAll(outputPath.resolve(REPOSITORY_FILES_FOLDER).toString());
                return;
            }

            for (FileHeader header : zipFile.getFileHeaders()) {
                unzipRepositoryFile(zipFile, header);
            }
        }
    }

    private void unzipRepositoryFile(ZipFile zipFile, FileHeader header) {
        String name = header.getFileName();
        String extension = com.google.common.io.Files.getFileExtension(name);
        if (!extensions.contains(extension)) return;

        Path repositoryFilesPath = outputPath.resolve(REPOSITORY_FILES_FOLDER);
        String repositoryName = zipFile.getFile().getName();
        repositoryName = repositoryName.substring(0, repositoryName.length() - ".zip".length());
        String nameHash = Hashing.murmur3_128().hashBytes(name.getBytes(StandardCharsets.UTF_8)).toString();
        Path outputPath = repositoryFilesPath.resolve(repositoryName + "-" + nameHash + "." + extension);
        try (
                ZipInputStream in = zipFile.getInputStream(header);
                OutputStream out = Files.newOutputStream(outputPath)
        ) {
            in.transferTo(out);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public static void execute(Path outputPath, Set<String> extensions) throws IOException {
        RepositoryUnzipper unzipper = new RepositoryUnzipper(outputPath, extensions);
        Files.createDirectories(outputPath.resolve(REPOSITORY_FILES_FOLDER));

        ConcurrentLinkedQueue<Path> queue;
        try (Stream<Path> repositoryPaths = Files.list(outputPath.resolve(REPOSITORIES_FOLDER))) {
            queue = repositoryPaths.collect(Collectors.toCollection(ConcurrentLinkedQueue::new));
        }

        int total = queue.size();
        CompletableFuture.allOf(IntStream.range(0, Runtime.getRuntime().availableProcessors())
                .mapToObj(i -> CompletableFuture.runAsync(() -> {
                    while (!queue.isEmpty()) {
                        Path path = queue.poll();
                        System.out.println("[" + (total - queue.size()) + "/" + total + "] Unzipping '" + path + "'");

                        try {
                            unzipper.unzipRepository(path);
                        } catch (IOException ex) {
                            ex.printStackTrace();
                        }
                    }
                })).toArray(CompletableFuture[]::new)).join();
    }
}
