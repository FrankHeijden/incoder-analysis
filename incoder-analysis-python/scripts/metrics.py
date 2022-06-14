import itertools
import json
import numpy as np
import os
import util
from joblib import Parallel, delayed
from transformers import AutoTokenizer
from typing import Dict, List, Any, Optional
from create_dataset import encode, decode

import Levenshtein
from nltk.translate.bleu_score import sentence_bleu, SmoothingFunction
from nltk.translate.meteor_score import meteor_score
from rouge_score import rouge_scorer

# from run import batch_count
batch_count = 4

tokenizer = AutoTokenizer.from_pretrained("facebook/incoder-1B")

rougeL_scorer = rouge_scorer.RougeScorer(["rougeL"], use_stemmer=True)


def print_context_metrics(metrics: np.ndarray) -> None:
    print(f" * Exact Match = {metrics[0] * 100}%")
    print(f" * Inference Time = {metrics[1] / batch_count}ms")
    print(f" * Input Length = {metrics[2]}")
    print(f" * Prediction Length = {metrics[3]}")
    print(f" * BLEU-4 = {metrics[4]}")
    print(f" * Levenshtein Distance = {metrics[5]}")
    print(f" * METEOR = {metrics[6]}")
    print(f" * RougeL Precision = {metrics[7]}")
    print(f" * RougeL Recall = {metrics[8]}")
    print(f" * RougeL F-measure = {metrics[9]}")


def _parse(json_str: str) -> Optional[Dict[str, Any]]:
    try:
        return json.loads(json_str)
    except:
        print(f"Can't parse JSON object: '{json_str}'")
        return None


def _encode(text: str) -> List[str]:
    return [decode(token) for token in encode(text)[1:]]


def _calculate_data_metrics(data: Dict[str, Any], context: str) -> list:
    context_data = data[context]

    prediction = context_data["prediction"]
    prediction_tokens = _encode(prediction)

    ground_truth = data["ground_truth"]
    ground_truth_tokens = _encode(ground_truth)

    rougel_scores = rougeL_scorer.score(ground_truth, prediction)["rougeL"]
    return [
        float(context_data["prediction"] == data["ground_truth"].strip()),
        context_data["inference_time"],
        len(encode(context_data["left_context"] + context_data.get("right_context", ""))) - 1,
        len(prediction_tokens),
        sentence_bleu([ground_truth_tokens], prediction_tokens, smoothing_function=SmoothingFunction().method2),
        Levenshtein.ratio(ground_truth, prediction),
        meteor_score(references=[ground_truth_tokens], hypothesis=prediction_tokens),
        rougel_scores.precision,
        rougel_scores.recall,
        rougel_scores.fmeasure,
    ]


def _calculate_context_metrics(all_data: List[Dict[str, Any]], context: str) -> np.ndarray:
    return np.sum([_calculate_data_metrics(data, context) for data in all_data], axis=0)


def _calculate_metrics(output_dir: str, i: int, n: int) -> np.ndarray:
    left_context_only_metrics = np.zeros(10)
    both_context_metrics = np.zeros(10)
    data_size = 0
    for file in util.get_files_in_dir(output_dir):
        if not file.endswith(".jsonl"):
            continue
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
    n_jobs = os.cpu_count()
    metrics = sum(Parallel(n_jobs=n_jobs)(delayed(_calculate_metrics)(
        output_dir,
        i,
        n_jobs,
    ) for i in range(n_jobs)))
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
