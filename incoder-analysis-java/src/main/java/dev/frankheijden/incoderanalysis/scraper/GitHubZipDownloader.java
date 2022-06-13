package dev.frankheijden.incoderanalysis.scraper;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.IntStream;

public class GitHubZipDownloader {

    private static final Gson gson = new Gson();
    private static final String REPOSITORIES_FILE = "repositories.json";
    private static final String REPOSITORIES_FOLDER = "repositories";
    private static final String REPOSITORY_ZIP_FILE = "{owner}_{repo}.zip";

    public GitHubZipDownloader() {

    }

    public void download(String downloadUrl) {
        String[] split = downloadUrl.split("/");
        String owner = split[4];
        String repo = split[5];

        Path targetPath = Paths.get(
                REPOSITORIES_FOLDER,
                REPOSITORY_ZIP_FILE
                        .replace("{owner}", owner)
                        .replace("{repo}", repo)
        );
        if (Files.exists(targetPath)) {
            System.out.println("Skipping '" + owner + "_" + repo + "'");
        } else {
            System.out.println("Downloading '" + owner + "_" + repo + "'");

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
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    public static void main(String[] args) throws IOException {
        GitHubZipDownloader downloader = new GitHubZipDownloader();
        Files.createDirectories(Paths.get(REPOSITORIES_FOLDER));

        ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>(gson.<List<String>>fromJson(
                Files.readString(Paths.get(REPOSITORIES_FILE)),
                new TypeToken<List<String>>(){}.getType()
        ));
        CompletableFuture.allOf(IntStream.range(0, Runtime.getRuntime().availableProcessors())
                .mapToObj(i -> CompletableFuture.runAsync(() -> {
                    while (!queue.isEmpty()) {
                        downloader.download(queue.poll());
                    }
                })).toArray(CompletableFuture[]::new)).join();
    }
}
