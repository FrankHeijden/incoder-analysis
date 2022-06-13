package dev.frankheijden.incoderanalysis.scraper;

import com.google.common.io.CharStreams;
import com.google.gson.Gson;
import io.github.cdimascio.dotenv.Dotenv;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.IntStream;

public class GitHubScraper {

    private static final Gson gson = new Gson();

    private static final String BASE_URL = "https://api.github.com";
    private static final String SEARCH_REPOSITORIES_URL = BASE_URL + "/search/repositories?page={page}&q=language:{language}&stars:%3E0&sort=stars&per_page=100";
    private static final String ZIP_REPOSITORY_URL = "/zipball/{ref}";
    private static final List<String> LANGUAGES = List.of("python", "javascript");
    private static final String REPOSITORIES_FILE = "repositories.json";

    public GitHubScraper() {

    }

    public CompletableFuture<GitHubSearchRepositoriesResponse> fetchRepositories(
            String token,
            String language,
            int page
    ) {
        CompletableFuture<GitHubSearchRepositoriesResponse> future = new CompletableFuture<>();

        ForkJoinPool.commonPool().execute(() -> {
            try {
                Thread.sleep((page - 1) * 2000L);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }

            try (CloseableHttpClient client = HttpClients.createDefault()) {
                HttpGet request = new HttpGet(SEARCH_REPOSITORIES_URL
                        .replace("{language}", language)
                        .replace("{page}", String.valueOf(page)));
                System.out.println("GET " + request.getURI());
                request.addHeader("Authorization", "token " + token);

                future.complete(client.execute(request, res -> {
                    try (
                            InputStream in = res.getEntity().getContent();
                            InputStreamReader reader = new InputStreamReader(in)
                    ) {
                        if (res.getStatusLine().getStatusCode() != 200) {
                            throw new IOException(CharStreams.toString(reader));
                        }
                        return gson.fromJson(reader, GitHubSearchRepositoriesResponse.class);
                    }
                }));
            } catch (IOException e) {
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    public static void main(String[] args) throws IOException {
        GitHubScraper scraper = new GitHubScraper();
        Dotenv dotenv = Dotenv.load();
        String token = dotenv.get("GITHUB_TOKEN");

        List<String> responses = LANGUAGES.stream()
                .flatMap(language -> IntStream.range(1, 10).mapToObj(page -> scraper.fetchRepositories(
                        token,
                        language,
                        page
                )))
                .map(CompletableFuture::join)
                .flatMap(res -> Arrays.stream(res.getItems())
                        .map(repo -> repo.getUrl() + ZIP_REPOSITORY_URL.replace("{ref}", repo.getDefaultBranch())))
                .toList();
        Files.writeString(Paths.get(REPOSITORIES_FILE), gson.toJson(responses));
    }

    public static class GitHubSearchRepositoriesResponse {

        private Repository[] items;

        public Repository[] getItems() {
            return items;
        }

        public static class Repository {

            private String default_branch;
            private String url;

            public String getDefaultBranch() {
                if (default_branch == null) {
                    System.out.println("NULL: " + url);
                }
                return default_branch;
            }

            public String getUrl() {
                return url;
            }
        }
    }
}
