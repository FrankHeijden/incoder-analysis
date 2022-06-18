import itertools
import os
import re
from collections import defaultdict
from typing import List, Dict, Any

from joblib import Parallel, delayed
from transformers import AutoTokenizer

import util
from metrics import _parse, keywords
from create_dataset import encode

tokenizer = AutoTokenizer.from_pretrained("facebook/incoder-1B")


def count_lines(text: str) -> int:
    return len(re.findall(r'\n+', text.strip())) + 1


def _calculate_context_metrics(all_data: List[Dict[str, Any]], context: str) -> tuple[int, Dict[int, int], int]:
    input_length_sum = 0
    token_frequences = defaultdict(lambda: 0, {})
    line_count = 0
    for data in all_data:
        context_data = data[context]
        left_context = context_data["left_context"]
        left_context_tokens = encode(left_context)[1:]

        right_context = context_data.get("right_context", "")
        right_context_tokens = encode(right_context)[1:]
        input_length_sum += len(left_context_tokens) + len(right_context_tokens)
        for token in itertools.chain(left_context_tokens, right_context_tokens):
            token_frequences[token] += 1

        line_count += count_lines(left_context) + count_lines(right_context) - 1
    return input_length_sum, token_frequences, line_count


def _is_trigger_point(data: Dict[str, Any], language: str) -> bool:
    try:
        return data["left_context_only"]["left_context"].split()[-1] in keywords[language]
    except IndexError:
        return False


def _calculate_metrics(output_dir: str, i: int, n: int):
    metrics = {
        "all": {
            "lc": [],
            "bc": [],
            "n": 0,
        },
        "tp": {
            "lc": [],
            "bc": [],
            "n": 0,
        },
    }

    for file in util.get_files_in_dir(output_dir):
        if not file.endswith(".jsonl"):
            continue
        language = file.split("/")[-2]
        with open(file, "r") as f:
            lines = f.readlines()[i::n]
            all_data = [x for json_str in lines if (x := _parse(json_str)) is not None]
            metrics["all"]["lc"].append(_calculate_context_metrics(all_data, "left_context_only"))
            metrics["all"]["bc"].append(_calculate_context_metrics(all_data, "both_contexts"))
            metrics["all"]["n"] += len(all_data)

            all_tp_data = [data for data in all_data if _is_trigger_point(data, language)]
            metrics["tp"]["lc"].append(_calculate_context_metrics(all_tp_data, "left_context_only"))
            metrics["tp"]["bc"].append(_calculate_context_metrics(all_tp_data, "both_contexts"))
            metrics["tp"]["n"] += len(all_tp_data)
    return metrics


def merge_all_metrics(all_metrics):
    metrics = {
        "all": {
            "lc": [],
            "bc": [],
            "n": 0,
        },
        "tp": {
            "lc": [],
            "bc": [],
            "n": 0,
        },
    }
    for i_metrics in all_metrics:
        for t in ["all", "tp"]:
            for m in ["lc", "bc", "n"]:
                metrics[t][m] += i_metrics[t][m]
    return metrics


def print_metrics(output_dir: str) -> None:
    print(f"Computing {output_dir}")
    n_jobs = os.cpu_count()
    all_metrics = merge_all_metrics(Parallel(n_jobs=n_jobs)(
        delayed(_calculate_metrics)(output_dir, i, n_jobs) for i in range(n_jobs)
    ))

    for t in ["all", "tp"]:
        n = all_metrics[t]["n"]

        for context in ["lc", "bc"]:
            n_tokens = 0
            total_tokens = 0
            total_chars = 0
            n_lines = 0
            for (input_length_sum, token_frequencies, line_count) in all_metrics[t][context]:
                n_tokens += input_length_sum
                for token, frequency in token_frequencies.items():
                    token_length = len(tokenizer.decode([int(token)]))
                    total_tokens += frequency
                    total_chars += token_length * frequency
                n_lines += line_count

            print("{:s} => {:d} & {:s} & {:d} & {:.2f} & {:.2f}".format(
                t,
                n,
                context.upper(),
                int(round(n_tokens / n)),
                n_tokens / n_lines,
                total_chars / total_tokens,
            ))


if __name__ == '__main__':
    outputs = [item for item in itertools.product(["raw", "without-comments"], ["python", "javascript"])]
    for (dataset, language) in outputs:
        print_metrics(f"../output/{dataset}/{language}")

