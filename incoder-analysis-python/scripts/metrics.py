import itertools
import json
import numpy as np
import os
from joblib import Parallel, delayed
from transformers import AutoTokenizer
from typing import Dict, List, Any, Optional
# from run import batch_count
batch_count = 4

import util

tokenizer = AutoTokenizer.from_pretrained("facebook/incoder-1B")


def print_context_metrics(metrics: tuple[float, float, float, float]) -> None:
    print(f" * Exact Match = {metrics[0]}%")
    print(f" * Inference Time = {metrics[1] / batch_count}ms")
    print(f" * Input Length = {metrics[2]}")
    print(f" * Prediction Length = {metrics[3]}")


def _parse(json_str: str) -> Optional[Dict[str, Any]]:
    try:
        return json.loads(json_str)
    except:
        print(f"Can't parse JSON object: '{json_str}'")
        return None


def _calculate_context_metrics(all_data: List[Dict[str, Any]], context: str) -> tuple[float, float, float, float]:
    exact_match = np.sum([
        data[context]["prediction"] == data["ground_truth"].strip()
        for data in all_data
    ]) * 100
    inference_time = np.sum([
        data[context]["inference_time"]
        for data in all_data
    ])
    input_token_length = np.sum([
        len(tokenizer(data[context]["left_context"] + data[context].get("right_context", "")).input_ids)
        for data in all_data
    ])
    prediction_token_length = np.sum([
        len(tokenizer(data[context]["prediction"]).input_ids)
        for data in all_data
    ])
    return np.array([
        exact_match,
        inference_time,
        input_token_length,
        prediction_token_length,
    ])


def _calculate_metrics(output_dir: str, i: int, n: int) -> tuple[tuple[float, float], int]:
    left_context_only_metrics = np.zeros(4)
    both_context_metrics = np.zeros(4)
    data_size = 0
    for file in util.get_files_in_dir(output_dir):
        with open(file, "r") as f:
            lines = f.readlines()[i::n]
            all_data = [x for json_str in lines if (x := _parse(json_str)) is not None]
            left_context_only_metrics += _calculate_context_metrics(all_data, "left_context_only")
            both_context_metrics += _calculate_context_metrics(all_data, "both_contexts")
            data_size += len(all_data)
    return np.array([
        np.array([
            left_context_only_metrics,
            both_context_metrics,
        ]),
        data_size
    ], dtype=object)


def print_metrics(output_dir: str) -> None:
    print(f"Calculating '{output_dir}' metrics...")
    batch_count = os.cpu_count()
    metrics = sum(Parallel(n_jobs=batch_count, verbose=1)(delayed(_calculate_metrics)(
        output_dir,
        i,
        batch_count,
    ) for i in range(batch_count)))
    metrics = metrics[0] / metrics[1]

    print(f"--- Metrics {output_dir} ---")
    print("Left Context Only")
    print_context_metrics(metrics[0])
    print("Both Contexts")
    print_context_metrics(metrics[1])
    print("----------------")


if __name__ == '__main__':
    outputs = [item for item in itertools.product(["raw", "without-comments"], ["python", "javascript"])]
    for (dataset, language) in outputs:
        print_metrics(f"../output/{dataset}/{language}")
