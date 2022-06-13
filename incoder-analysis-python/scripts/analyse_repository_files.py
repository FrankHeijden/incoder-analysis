import json
import os
from collections import defaultdict
from typing import List, Dict, Tuple

import numpy as np
from joblib import Parallel, delayed
from transformers import AutoTokenizer

import util

tokenizer = AutoTokenizer.from_pretrained("facebook/incoder-1B")


def _calculate_token_frequencies(files: List[str]) -> Dict[str, int]:
    token_frequences = defaultdict(lambda: 0, {})
    for file in files:
        with open(f"../repository-files/{file}", "r") as content:
            try:
                tokens = tokenizer(content.read(), max_length=1000000, truncation=True).input_ids
                for token in tokens:
                    token_frequences[token] += 1
            except:
                continue
    return token_frequences


def calculate_token_frequencies() -> None:
    files = next(os.walk("../repository-files"), (None, None, []))[2]
    file_batches = np.array_split(files, os.cpu_count())
    all_token_frequencies = Parallel(n_jobs=os.cpu_count())(
        delayed(_calculate_token_frequencies)(file_batch) for file_batch in file_batches
    )

    token_frequencies = defaultdict(lambda: 0, {})
    for i_token_frequencies in all_token_frequencies:
        for k, v in i_token_frequencies.items():
            token_frequencies[k] += v

    with open("../token-frequencies.json", "w") as f:
        json.dump(token_frequencies, f)


def calculate_average_token_length() -> float:
    data = json.loads(util.read_file("../token-frequencies.json"))

    total_tokens = 0
    total_chars = 0
    for key, value in data.items():
        token_length = len(tokenizer.decode([int(key)]))
        total_tokens += value
        total_chars += token_length * value
    return total_chars / total_tokens


def _count_tokens_and_lines(files: List[str]) -> Tuple[int, int]:
    token_count = 0
    line_count = 0
    for file in files:
        with open(f"../repository-files/{file}", "r") as content:
            try:
                text = content.read().replace("\n\n", "\n")
                token_count += len(tokenizer(text, max_length=1000000, truncation=True).input_ids)
                line_count += text.count("\n") + 1
            except:
                continue
    return token_count, line_count


def calculate_average_tokens_per_line() -> float:
    files = next(os.walk("../repository-files"), (None, None, []))[2]
    file_batches = np.array_split(files, os.cpu_count())
    all_token_and_lines_count = Parallel(n_jobs=os.cpu_count())(
        delayed(_count_tokens_and_lines)(file_batch) for file_batch in file_batches
    )

    n_tokens = 0
    n_lines = 0
    for (token_count, line_count) in all_token_and_lines_count:
        n_tokens += token_count
        n_lines += line_count
    return n_tokens / n_lines


if __name__ == '__main__':
    # calculate_token_frequencies()
    # print(calculate_average_token_length())
    print(calculate_average_tokens_per_line())

