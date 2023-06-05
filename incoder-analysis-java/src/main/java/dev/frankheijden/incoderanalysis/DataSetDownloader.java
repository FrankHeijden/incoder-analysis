package dev.frankheijden.incoderanalysis;

import dev.frankheijden.incoderanalysis.preprocessing.RepositoryUnzipper;
import dev.frankheijden.incoderanalysis.scraper.GitHubScraper;
import dev.frankheijden.incoderanalysis.scraper.GitHubZipDownloader;
import io.github.cdimascio.dotenv.Dotenv;
import io.github.cdimascio.dotenv.DotenvException;
import picocli.CommandLine;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

@CommandLine.Command(
        name = "dataset-downloader",
        mixinStandardHelpOptions = true,
        version = "dataset-downloader {version}",
        description = """
                Downloads the top-1000 github repositories of specified language(s),
                extracting their source files and creating a dataset for code language models.
                """
)
public class DataSetDownloader implements Callable<Integer> {

    @CommandLine.Option(
            names = {
                    "-l",
                    "--languages",
            },
            required = true,
            description = "The languages to search for on github (python, javascript, ...)",
            arity = "1..*"
    )
    private String[] languages;

    @CommandLine.Option(
            names = {
                    "-s",
                    "--sorts",
            },
            description = "The sorts to use when searching the GitHub API (stars, forks, help-wanted-issues, updated)",
            defaultValue = "stars,forks"
    )
    private String[] sorts;

    @CommandLine.Option(
            names = {
                    "-e",
                    "--extensions",
            },
            description = "The extensions to unzip from the downloaded repositories (js, py, ...)",
            arity = "1..*"
    )
    private String[] extensions;

    @CommandLine.Option(
            names = {
                    "-o",
                    "--output-directory",
            },
            required = true,
            description = "The output directory"
    )
    private File outputDirectory;

    @CommandLine.Option(
            names = {
                    "-t",
                    "--github-token"
            },
            description = "The GitHub token used to allow for a larger api limit."
    )
    private String githubToken;

    @Override
    public Integer call() throws Exception {
        Dotenv dotenv = null;
        try {
            dotenv = Dotenv.load();
        } catch (DotenvException ignored) {
            // ignored, it doesnt have to exist.
        }

        if (githubToken == null || githubToken.isBlank()) {
            if (dotenv != null) {
                githubToken = dotenv.get("GITHUB_TOKEN");
            }

            if (githubToken == null || githubToken.isBlank()) {
                System.err.println("You must specify a GitHub token, either via an .env file or via CLI arguments.");
                return 1;
            }
        }

        if (extensions == null) {
            extensions = new String[0];
        }

        Set<String> allLanguages = createOptionValues(languages);
        Set<String> fileExtensions = createOptionValues(extensions);
        Set<String> allSorts = createOptionValues(sorts);

        Path outputPath = outputDirectory.toPath();
        Files.createDirectories(outputPath);

        System.out.println("Scraping top-1000 repositories...");
        GitHubScraper.execute(outputPath, allLanguages, allSorts, githubToken);

        System.out.println("Downloading all repositories...");
        GitHubZipDownloader.execute(outputPath);

        System.out.println("Unzipping files...");
        RepositoryUnzipper.execute(outputPath, fileExtensions);

        System.out.println("Done!");
        return 0;
    }

    private Set<String> createOptionValues(String[] rawValues) {
        return Arrays.stream(rawValues).flatMap(l -> Arrays.stream(l.split("[, ]")))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
    }

    public static void main(String[] args) {
        System.exit(new CommandLine(new DataSetDownloader()).execute(args));
    }
}
