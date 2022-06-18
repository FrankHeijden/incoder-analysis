import json

import numpy as np
from metrics import _encode, get_latex_results

import util
import Levenshtein
from nltk.translate.bleu_score import sentence_bleu, SmoothingFunction
from nltk.translate.meteor_score import meteor_score
from rouge_score import rouge_scorer

rougeL_scorer = rouge_scorer.RougeScorer(["rougeL"], use_stemmer=True)


def is_not_valid_data(d):
    return 'groundTruth' not in d or ('groundTruth' in d and d['groundTruth'].strip() == '') or d['predictions'] == ['']


def get_prediction(d):
    if d['chosenPrediction'] is not None:
        p = d['chosenPrediction']
    else:
        p = d['predictions'][0]
    return p.strip()


def calculate_metrics(d):
    prediction = get_prediction(d)
    prediction_tokens = _encode(prediction)

    ground_truth = d['groundTruth'].strip()
    ground_truth_tokens = _encode(ground_truth)

    rougel_scores = rougeL_scorer.score(ground_truth, prediction)["rougeL"]
    return [
        float(prediction == ground_truth),
        d["inferenceTime"],
        0,
        len(prediction_tokens),
        sentence_bleu([ground_truth_tokens], prediction_tokens, smoothing_function=SmoothingFunction().method2),
        Levenshtein.ratio(ground_truth, prediction),
        meteor_score(references=[ground_truth_tokens], hypothesis=prediction_tokens),
        rougel_scores.precision,
        rougel_scores.recall,
        rougel_scores.fmeasure,
    ]


if __name__ == '__main__':
    languages = ["python", "javascript"]
    types = ["all", "chosen"]
    metrics = {}

    for language in languages:
        metrics[language] = {}
        for t in types:
            metrics[language][t] = {
                "metrics": np.zeros(10),
                "size": 0,
            }

    for file in util.get_files_in_dir("../data"):
        if not file.endswith(".json"):
            continue

        with open(file) as f:
            try:
                data = json.load(f)
            except:
                continue

            if data['model'] != 'InCoder' and data['model'] != 'CodeFill':
                continue

            language = data['language']
            if language != 'javascript' and language != 'python':
                continue

            if is_not_valid_data(data):
                continue

            language_metrics = metrics[language]
            calculated_metrics = calculate_metrics(data)
            language_metrics["all"]["metrics"] += calculated_metrics
            language_metrics["all"]["size"] += 1

            if data['chosenPrediction'] is not None:
                language_metrics["chosen"]["metrics"] += calculated_metrics
                language_metrics["chosen"]["size"] += 1

    for language in languages:
        language_metrics = metrics[language]
        for t in types:
            n = language_metrics[t]["size"]
            t_metrics = language_metrics[t]["metrics"] / n
            print("language = ", language, ", type = ", t, ", => ", n, " & ", get_latex_results(t_metrics), sep="")



