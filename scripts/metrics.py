import json
from typing import Dict, List, Any, Optional

import numpy as np
import stringcase
from transformers import AutoTokenizer

import util

tokenizer = AutoTokenizer.from_pretrained("facebook/incoder-1B")


def print_context_metrics(all_data: List[Dict[str, Any]], context: str) -> None:
    print(stringcase.titlecase(context))
    print(
        " * Exact Match =",
        np.average([
            data[context]["prediction"] == data["ground_truth"].strip()
            for data in all_data
        ]) * 100,
    )
    print(
        " * Inference Time =",
        np.average([
            data[context]["inference_time"]
            for data in all_data
        ]),
    )
    print(
        " * Input Length =",
        np.average([
            len(tokenizer(data[context]["left_context"] + data[context].get("right_context", "")).input_ids)
            for data in all_data
        ]),
    )
    print(
        " * Prediction Length =",
        np.average([
            len(tokenizer(data[context]["prediction"]).input_ids)
            for data in all_data
        ]),
    )


def parse(f: str) -> Optional[Dict[str, Any]]:
    try:
        return json.loads(util.read_file(f))
    except:
        print(f"Error: {f}")
        return None


def print_metrics(output_dir: str) -> None:
    files = util.get_files_in_dir(output_dir)
    all_data = [x for file in files if (x := parse(file)) is not None]

    print(f"--- Metrics {output_dir} ---")
    print_context_metrics(all_data, "left_context_only")
    print_context_metrics(all_data, "both_contexts")
    print("----------------")


if __name__ == '__main__':
    print_metrics("output/python")
    print_metrics("output/javascript")
