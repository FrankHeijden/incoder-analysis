import json
import os
import random
import uuid
import pyparsing
import itertools
from typing import List, Union

import numpy as np
from joblib import Parallel, delayed
from transformers import AutoTokenizer

import util

tokenizer = AutoTokenizer.from_pretrained("facebook/incoder-1B")
max_tokens = 2048
stop_tokens = [205, 284, 353, 536, 994, 3276, 4746, 15471, 16027, 28602, 40289, 43275, 50517]
samples_per_file = 10
languages = ["python", "javascript"]


def decode(context: Union[List[int], int]) -> str:
    return tokenizer.decode(context, clean_up_tokenization_spaces=False, skip_special_tokens=True)


def encode(text: str) -> List[int]:
    return tokenizer(text, max_length=1000000, truncation=True).input_ids


def _process_file_content(file_content: str, file: str, dir_path: str) -> None:
    tokens = encode(file_content)
    token_count = len(tokens)

    for _ in range(samples_per_file):
        random_i = random.randint(0, token_count)
        left_context = tokens[:random_i]

        right_context = tokens[random_i:]
        right_context_length = len(right_context)
        i = 0
        while i < right_context_length and right_context[i] not in stop_tokens:
            i += 1
        ground_truth = decode(right_context[:i]).strip()
        if ground_truth == "":
            continue
        right_context = right_context[i:]

        if len(file) > 240:
            file = file[:240] + str(uuid.uuid4())[:4]

        with open(f"../dataset/{dir_path}/{file}L{random_i}.json", "w") as f:
            json.dump({
                "left_context_only": {
                    "left_context": decode(util.truncate_left_context(left_context, 2000)),
                },
                "both_contexts": {
                    "left_context": decode(util.truncate_left_context(left_context, 1000)),
                    "right_context": decode(util.truncate_right_context(right_context, 1000)),
                },
                "ground_truth": ground_truth,
            }, f)


def _process_file(file: str) -> None:
    with open(f"../repository-files/{file}", "r") as f:
        ext = file[-2:]
        if ext == "py":
            language = "python"
            comment_filter = pyparsing.python_style_comment.suppress()
        elif ext == "js":
            language = "javascript"
            comment_filter = pyparsing.java_style_comment.suppress()
        else:
            return

        file_content = f.read()
        _process_file_content(file_content, file, f"raw/{language}")
        _process_file_content(comment_filter.transform_string(file_content), file, f"without-comments/{language}")


def _process_file_batch(file_batch: List[str]) -> None:
    for file in file_batch:
        _process_file(file)


def create_dataset() -> None:
    files = util.get_files_in_dir("../repository-files", add_dir_path=False)
    datasets = [item for item in itertools.product(["raw", "without-comments"], languages)]
    for (dataset, language) in datasets:
        os.makedirs(f"../dataset/{dataset}/{language}", exist_ok=True)

    print("Creating dataset")
    file_batches = np.array_split(files, os.cpu_count())
    Parallel(n_jobs=os.cpu_count(), verbose=1)(delayed(_process_file_batch)(file_batch) for file_batch in file_batches)

    print("Merging dataset")
    Parallel(n_jobs=os.cpu_count(), verbose=1)(delayed(util.merge_files_in_dir)(
        f"../dataset/{dataset}/{language}",
        f"../dataset/{dataset}/{language}/data.jsonl",
        delete_files=True
    ) for (dataset, language) in datasets)


if __name__ == '__main__':
    create_dataset()
