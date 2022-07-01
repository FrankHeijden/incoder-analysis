package dev.frankheijden.incoderanalysis.scraper;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.IntStream;

public class GitHubZipDownloader {
    private static final String REPOSITORIES_FILE = "repositories.csv";
    private static final String REPOSITORIES_FOLDER = "repositories";
    private static final String REPOSITORY_ZIP_FILE = "{fullName}.zip";

    public GitHubZipDownloader() {

    }

    public void download(Path repositoriesPath, String fullName, String downloadUrl) {
        Path targetPath = repositoriesPath.resolve(
                REPOSITORY_ZIP_FILE.replace("{fullName}", fullName.replace("/", "_"))
        );

        if (!Files.exists(targetPath)) {
            URL url;
            try {
                url = new URL(downloadUrl);
            } catch (MalformedURLException ex) {
                throw new RuntimeException(ex);
            }

            try (
                    ReadableByteChannel inChannel = Channels.newChannel(url.openStream());
                    FileChannel fileChannel = FileChannel.open(
                            targetPath,
                            StandardOpenOption.CREATE_NEW,
                            StandardOpenOption.WRITE
                    )
            ) {
                fileChannel.transferFrom(inChannel, 0, Long.MAX_VALUE);
            } catch (FileAlreadyExistsException ignored) {
                // ignored
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    public static void execute(Path outputPath) throws IOException {
        GitHubZipDownloader downloader = new GitHubZipDownloader();
        Path repositoriesPath = outputPath.resolve(REPOSITORIES_FOLDER);
        Files.createDirectories(repositoriesPath);

        List<String> lines = Files.readAllLines(outputPath.resolve(REPOSITORIES_FILE));
        ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>(lines.subList(1, lines.size()));
        int total = queue.size();
        CompletableFuture.allOf(IntStream.range(0, Runtime.getRuntime().availableProcessors())
                .mapToObj(i -> CompletableFuture.runAsync(() -> {
                    while (!queue.isEmpty()) {
                        String line = queue.poll();
                        String[] columns = line.split(",");
                        String fullName = columns[0];
                        String downloadUrl = columns[1];

                        System.out.println("[" + (total - queue.size()) + "/" + total + "] Downloading '" + fullName + "' from '" + downloadUrl + "'");
                        downloader.download(repositoriesPath, fullName, downloadUrl);
                    }
                })).toArray(CompletableFuture[]::new)).join();
    }
}
