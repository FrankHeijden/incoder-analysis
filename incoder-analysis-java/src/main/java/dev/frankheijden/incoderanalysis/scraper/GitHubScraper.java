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
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class GitHubScraper {

    private static final Gson gson = new Gson();

    private static final String BASE_URL = "https://api.github.com";
    private static final int PER_PAGE = 100;
    private static final int MAX_PAGES = 10;
    private static final String SEARCH_REPOSITORIES_URL = BASE_URL + "/search/repositories?page={page}&q=language:{language}&sort={sort}&per_page=" + PER_PAGE;
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
            String sort,
            int page
    ) {
        return fetchGitHubURL(
                SEARCH_REPOSITORIES_URL
                        .replace("{language}", language)
                        .replace("{sort}", sort)
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

    public static void execute(
            Path outputPath,
            Set<String> languages,
            Set<String> sorts,
            String githubToken
    ) throws IOException {
        GitHubScraper scraper = new GitHubScraper();

        Set<GitHubSearchRepositoriesResponse.Repository> repositories = new HashSet<>();
        for (String language : languages) {
            for (String sort : sorts) {
                for (int page = 1; page <= MAX_PAGES; page++) {
                    System.out.println("[" + language + "/" + sort + "] Fetching " + page + "/" + MAX_PAGES + "...");
                    GitHubSearchRepositoriesResponse response = scraper.fetchRepositories(
                            githubToken,
                            language,
                            sort,
                            page
                    ).join();
                    repositories.addAll(Arrays.asList(response.items));

                    try {
                        Thread.sleep(6500); // 10 per minute
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }

        List<GitHubSearchRepositoriesResponse.Repository> repositoryList = new ArrayList<>(repositories);
        System.out.println("# Unique Repositories = " + repositoryList.size());
        int duplicateRepositories = (languages.size() * sorts.size() * PER_PAGE * MAX_PAGES) - repositoryList.size();
        System.out.println("# Duplicate Repositories = " + duplicateRepositories);

        AtomicInteger counter = new AtomicInteger(1);
        int n = Runtime.getRuntime().availableProcessors();
        @SuppressWarnings("unchecked")
        CompletableFuture<List<String>>[] futures = new CompletableFuture[n];
        for (int i = 0; i < n; i++) {
            int offset = i;
            futures[i] = CompletableFuture.supplyAsync(() -> {
                List<String> output = new ArrayList<>();
                for (int j = offset; j < repositoryList.size(); j += n) {
                    GitHubSearchRepositoriesResponse.Repository repo = repositoryList.get(j);
                    System.out.println("[" + counter.getAndIncrement() + "/" + repositoryList.size() + "] Fetching latest commit hash of repo '" + repo.url + "'...");

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

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;
                Repository that = (Repository) o;
                return full_name.equals(that.full_name)
                        && default_branch.equals(that.default_branch)
                        && url.equals(that.url);
            }

            @Override
            public int hashCode() {
                return Objects.hash(full_name, default_branch, url);
            }
        }
    }

    public static class GithubRepoCommitsResponse {
        public String sha;
    }
}
