package dev.frankheijden.incoderanalysis.preprocessing;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.io.inputstream.ZipInputStream;
import net.lingala.zip4j.model.FileHeader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class RepositoryUnzipper {

    private static final String REPOSITORIES_FOLDER = "repositories";
    private static final String REPOSITORY_FILES_FOLDER = "repository-files";
    private static final List<String> FILE_EXTENSIONS = List.of(
            ".py",
            ".js"
    );

    private static boolean shouldUnzipFile(String fileName) {
        for (String ext : FILE_EXTENSIONS) {
            if (fileName.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    private final Set<String> contents;
    private final AtomicInteger files = new AtomicInteger();
    private final AtomicInteger duplicates = new AtomicInteger();

    public RepositoryUnzipper() {
        this.contents = new HashSet<>();
    }

    public void unzipRepository(Path repositoryPath) throws IOException {
        try (ZipFile zipFile = new ZipFile(repositoryPath.toFile())) {
            for (FileHeader header : zipFile.getFileHeaders()) {
                unzipRepositoryFile(zipFile, header);
            }
        }
    }

    private void unzipRepositoryFile(ZipFile zipFile, FileHeader header) {
        String name = header.getFileName();
        if (!shouldUnzipFile(name)) return;

        try (ZipInputStream in = zipFile.getInputStream(header)) {
            files.incrementAndGet();

            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            if (!contents.add(text)) {
                duplicates.incrementAndGet();
                return;
            }

            Files.writeString(Paths.get(REPOSITORY_FILES_FOLDER).resolve(name.replace("/", "_")), text);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public static void main(String[] args) throws IOException {
        RepositoryUnzipper unzipper = new RepositoryUnzipper();
        Files.createDirectories(Paths.get(REPOSITORY_FILES_FOLDER));

        ConcurrentLinkedQueue<Path> queue;
        try (Stream<Path> repositoryPaths = Files.list(Paths.get(REPOSITORIES_FOLDER))) {
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

        System.out.println("--- Finished ---");
        System.out.println("# Total Files = " + unzipper.files.get());
        System.out.println("# Duplicates = " + unzipper.duplicates.get());
        System.out.println("# Files = " + (unzipper.files.get() - unzipper.duplicates.get()));
        System.out.println("--------");
    }
}
