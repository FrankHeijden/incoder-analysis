# InCoder Analysis
This repo contains files to perform an empirical analysis on the LM [facebook/incoder-1B](https://huggingface.co/facebook/incoder-1B).
The focus for this analysis is on statement completion, which will be done on two large code corpora, JavaScript and Python code, called P1K-22 and JS1K-22 respectively, scraped from the top-1000 most starred GitHub repositories.

## 1. Scraping
In the `incoder-analysis-java/src/main/java/dev/frankheijden/incoderanalysis` are the `scraper` and `preprocessing` folder, which will be the working directories for this part.
I suggest opening this project in an IDE such as Intellij IDEA, to execute the main functions in these files easily.

### 1.1 Fetching the top-1000
To fetch the top-1000 github repositories for JavaScript and Python, run the [GitHubScraper](tree/master/incoder-analysis-java/src/main/java/dev/frankheijden/incoderanalysis/scraper/GitHubScraper.java) file.
This will create a `repositories.json` file, containing ~1000 JavaScript and ~1000 Python repositories.

### 1.2 Fetching the zip files
Now run the [GitHubZipDownloader](tree/master/incoder-analysis-java/src/main/java/dev/frankheijden/incoderanalysis/scraper/GitHubZipDownloader.java) file, which will download approximately ~25GB of zip files containing the code of the default branch for each repository.

### 1.3 Unzipping the files
The final step is to extract the source files from these zips.
This can be done by using the [RepositoryUnzipper](tree/master/incoder-analysis-java/src/main/java/dev/frankheijden/incoderanalysis/preprocessing/RepositoryUnzipper.java) file, which will extract Python and JavaScript source files from the archives.
At the same time, it filters files based on an exact match, such that each source file is unique.

## 2. Creating the final dataset
After the `repository-files` directory has been filled, the dataset can be created from these raw source files.
I suggest opening the `incoder-analysis-python` folder in an IDE such as PyCharm to execute these files easily.
In the `scripts` folder, there's a script called `create_dataset.py`, which will create the P1K-22 and JS1K-22 dataset.

## 3. Running InCoder
The evaluation was ran on the Delft High Performance Cluster [[1]](#dhpc).
To help with job management, a `start.sh` script was made, which bootstraps the sbatch `run.sh` file.
For each dataset subset (P1K-22, P1K-22 without comments, JS1K-22, JS1K-22 without comments) a bash command was ran:
```bash
sh start.sh raw/python 4
sh start.sh raw/javascript 4
sh start.sh without-comments/python 4
sh start.sh without-comments/javascript 4
```

The above commands effectively spawn 16 slurm jobs, each requesting 4 NVIDIA v100 GPUs.

## 4. Metrics computation
The metrics can be evaluated using the following SLURM bash command:
```bash
sbatch metrics.sh
```

## References
<a name="dhpc"></a> [1] Delft High Performance Computing Centre (DHPC), DelftBlue Supercomputer (Phase 1), 2022, https://www.tudelft.nl/dhpc/ark:/44463/DelftBluePhase1
