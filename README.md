# InCoder Analysis
This repo contains files to perform an empirical analysis on the LM [facebook/incoder-1B](https://huggingface.co/facebook/incoder-1B).
The focus for this analysis is on statement completion, which will be done on two large code corpora, JavaScript and Python code, scraped from the top-1000 most starred GitHub repositories.

## 1. Scraping
In the `src/main/java/dev/frankheijden/incoderanalysis` is the `scraper` folder, which will be the working directory for this part.
I suggest opening this project in an IDE such as Intellij IDEA, to execute the main functions in these files easily.

### 1.1 Fetching the top-1000
To fetch the top-1000 github repositories for JavaScript and Python, run the [GitHubScraper](tree/master/src/main/java/dev/frankheijden/incoderanalysis/GitHubScraper.java) file.
This will create a `repositories.json` file, containing ~1000 JavaScript and ~1000 Python repositories.

### 1.2 Fetching the zip files
Now run the [GitHubZipDownloader](tree/master/src/main/java/dev/frankheijden/incoderanalysis/GitHubZipDownloader.java) file, which will download approximately ~25GB of zip files containing the code of the default branch for each repository.

## 2. Preprocessing

```bash
--- Finished ---
# Total Files = 327972
# Duplicates = 41267
# Files = 286705
--------
```
