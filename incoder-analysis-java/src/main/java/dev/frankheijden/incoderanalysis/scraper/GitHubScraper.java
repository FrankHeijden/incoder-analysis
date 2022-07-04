package dev.frankheijden.incoderanalysis.scraper;

import com.google.common.io.CharStreams;
import com.google.gson.Gson;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class GitHubScraper {

    private static final Gson gson = new Gson();

    private static final String BASE_URL = "https://api.github.com";
    private static final String SEARCH_REPOSITORIES_URL = BASE_URL + "/search/repositories?page={page}&q=language:{language}&stars:%3E0&sort=stars&per_page=100";
    private static final int MAX_PAGES = 10;
    private static final String COMMITS_URL = "/commits/{ref}";
    private static final String ZIP_REPOSITORY_URL = "/zipball/{ref}";
    private static final String REPOSITORIES_FILE = "repositories.csv";

    public GitHubScraper() {

    }

    public static <T> CompletableFuture<T> fetchGitHubURL(String url, String token, Class<T> responseClass) {
        CompletableFuture<T> future = new CompletableFuture<>();

        ForkJoinPool.commonPool().execute(() -> {
            try (CloseableHttpClient client = HttpClients.createDefault()) {
                HttpGet request = new HttpGet(url);
                request.addHeader("Authorization", "token " + token);
                future.complete(client.execute(request, res -> {
                    try (
                            InputStream in = res.getEntity().getContent();
                            InputStreamReader reader = new InputStreamReader(in)
                    ) {
                        if (res.getStatusLine().getStatusCode() != 200) {
                            throw new IOException(CharStreams.toString(reader));
                        }
                        return gson.fromJson(reader, responseClass);
                    }
                }));
            } catch (IOException e) {
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    public CompletableFuture<GitHubSearchRepositoriesResponse> fetchRepositories(
            String token,
            String language,
            int page
    ) {
        return fetchGitHubURL(
                SEARCH_REPOSITORIES_URL
                        .replace("{language}", language)
                        .replace("{page}", String.valueOf(page)),
                token,
                GitHubSearchRepositoriesResponse.class
        );
    }

    public CompletableFuture<GithubRepoCommitsResponse> fetchRepoCommits(
            String token,
            String repoUrl,
            String branch
    ) {
        return fetchGitHubURL(
                repoUrl + COMMITS_URL.replace("{ref}", branch),
                token,
                GithubRepoCommitsResponse.class
        );
    }

    public static void execute(Path outputPath, List<String> languages, String githubToken) throws IOException {
        GitHubScraper scraper = new GitHubScraper();

        List<GitHubSearchRepositoriesResponse.Repository> repositories = new ArrayList<>();
        for (String language : languages) {
            for (int page = 1; page <= MAX_PAGES; page++) {
                System.out.println("[" + language + "] Fetching " + page + "/" + MAX_PAGES + "...");
                GitHubSearchRepositoriesResponse response = scraper.fetchRepositories(
                        githubToken,
                        language,
                        page
                ).join();
                repositories.addAll(List.of(response.items));

                try {
                    Thread.sleep(1000 * page);
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }
        }

        AtomicInteger counter = new AtomicInteger(1);
        int n = Runtime.getRuntime().availableProcessors();
        @SuppressWarnings("unchecked")
        CompletableFuture<List<String>>[] futures = new CompletableFuture[n];
        for (int i = 0; i < n; i++) {
            int offset = i;
            futures[i] = CompletableFuture.supplyAsync(() -> {
                List<String> output = new ArrayList<>();
                for (int j = offset; j < repositories.size(); j += n) {
                    GitHubSearchRepositoriesResponse.Repository repo = repositories.get(j);
                    System.out.println("[" + counter.getAndIncrement() + "/" + repositories.size() + "] Fetching latest commit hash of repo '" + repo.url + "'...");

                    String sha = scraper.fetchRepoCommits(githubToken, repo.url, repo.default_branch).join().sha;
                    output.add(
                            repo.full_name
                                    + "," + repo.url + ZIP_REPOSITORY_URL.replace("{ref}", sha)
                                    + "," + sha
                    );
                }
                return output;
            });
        }
        CompletableFuture.allOf(futures).join();
        List<String> output = Stream.concat(
                Stream.of("Full Name,Repository Download URL,Commit SHA1"),
                Arrays.stream(futures)
                        .flatMap(future -> future.join().stream())
        ).toList();
        Files.write(outputPath.resolve(REPOSITORIES_FILE), output);
    }

    public static class GitHubSearchRepositoriesResponse {
        public Repository[] items;

        public static class Repository {
            public String full_name;
            public String default_branch;
            public String url;
        }
    }

    public static class GithubRepoCommitsResponse {
        public String sha;
    }
}
