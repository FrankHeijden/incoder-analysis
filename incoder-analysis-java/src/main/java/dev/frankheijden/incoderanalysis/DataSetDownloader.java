package dev.frankheijden.incoderanalysis;

import dev.frankheijden.incoderanalysis.preprocessing.RepositoryUnzipper;
import dev.frankheijden.incoderanalysis.scraper.GitHubScraper;
import dev.frankheijden.incoderanalysis.scraper.GitHubZipDownloader;
import io.github.cdimascio.dotenv.Dotenv;
import io.github.cdimascio.dotenv.DotenvException;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "dataset-downloader",
        mixinStandardHelpOptions = true,
        version = "dataset-downloader %%VERSION%%",
        description = "Downloads the top-1000 github repositories of specified language(s)"
)
public class DataSetDownloader implements Callable<Integer> {

    @CommandLine.Option(names = {"-l", "--languages"}, required = true, description = "javascript, python, ...")
    private String languagesString;

    @CommandLine.Option(names = {"-e", "--extensions"}, required = true, description = "js, py, ...")
    private String extensionsString;

    @CommandLine.Option(names = {"-o", "--output-directory"}, required = true, description = "The output directory")
    private String outputDirectory;

    @CommandLine.Option(names = {"-t", "--github-token"}, required = true, description = "GitHub token")
    private String githubToken;

    private boolean isValid(String arg, String msg) {
        if (arg == null || arg.isBlank()) {
            System.err.println(msg);
            return false;
        }
        return true;
    }

    @Override
    public Integer call() throws Exception {
        Dotenv dotenv = null;
        try {
            dotenv = Dotenv.load();
        } catch (DotenvException ignored) {
            // ignored, it doesnt have to exist.
        }

        if (
                !isValid(languagesString, "You must specify at least one language")
                || !isValid(extensionsString, "You must specify at least one extension")
                || !isValid(outputDirectory, "You must specify a directory to save the downloaded datasets")
        ) {
            return 1;
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

        List<String> languages = Arrays.asList(languagesString.split(","));

        List<String> fileExtensions = new ArrayList<>();
        for (String extension : extensionsString.split(",")) {
            if (extension.isBlank()) continue;
            if (!extension.startsWith(".")) {
                extension = "." + extension;
            }
            fileExtensions.add(extension);
        }

        Path outputPath = Paths.get(this.outputDirectory);
        Files.createDirectories(outputPath);

        System.out.println("Scraping top-1000 repositories...");
        GitHubScraper.execute(outputPath, languages, githubToken);

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
