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
import java.util.List;
import java.util.concurrent.Callable;

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
                    "-e",
                    "--extensions",
            },
            required = true,
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

        List<String> allLanguages = Arrays.stream(languages)
                .flatMap(l -> Arrays.stream(l.split(",")))
                .map(String::trim)
                .toList();

        List<String> fileExtensions = Arrays.stream(extensions)
                .filter(String::isBlank)
                .map(e -> {
                    if (e.startsWith(".")) {
                        return e;
                    }
                    return "." + e;
                })
                .toList();

        Path outputPath = outputDirectory.toPath();
        Files.createDirectories(outputPath);

        System.out.println("Scraping top-1000 repositories...");
        GitHubScraper.execute(outputPath, allLanguages, githubToken);

        System.out.println("Downloading all repositories...");
        GitHubZipDownloader.execute(outputPath);

        System.out.println("Unzipping files...");
        RepositoryUnzipper.execute(outputPath, fileExtensions);

        System.out.println("Done!");
        return 0;
    }

    public static void main(String[] args) {
        System.exit(new CommandLine(new DataSetDownloader()).execute(args));
    }
}
