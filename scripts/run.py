import json
import os
import sys
import time

from joblib import Parallel, delayed
from typing import List

import incoder

batch_count = 4


def _run_data(data: str) -> str:
    left_context_only = data["left_context_only"]
    t_bf = time.time()
    left_context_only["prediction"] = incoder.generate(left_context_only["left_context"], "")
    left_context_only["inference_time"] = (time.time() - t_bf) * 1000

    both_contexts = data["both_contexts"]
    t_bf = time.time()
    both_contexts["prediction"] = incoder.generate(both_contexts["left_context"], both_contexts["right_context"])
    both_contexts["inference_time"] = (time.time() - t_bf) * 1000
    return data


def _process_data_batch(data_batch: List[str], data_path: str, i: int, j: int) -> None:
    output_file = f"../output/{data_path}/data-{i}-{j}.jsonl"
    line_count = 0
    if os.path.exists(output_file):
        with open(output_file, "r") as f:
            for line_count, _ in enumerate(f):
                pass

    with open(output_file, "a") as f_out:
        for data_str in data_batch[line_count:]:
            f_out.write(json.dumps(_run_data(json.loads(data_str))) + "\n")


def run(data_path: str, n: int, i: int) -> None:
    os.makedirs(f"../output/{data_path}", exist_ok=True)

    with open(f"../dataset/{data_path}/data.jsonl", "r") as f:
        lines = f.readlines()[i::n]
        Parallel(n_jobs=batch_count, verbose=1)(delayed(_process_data_batch)(
            lines[j::batch_count],
            data_path,
            i,
            j
        ) for j in range(batch_count))


if __name__ == '__main__':
    run(sys.argv[1], int(sys.argv[2]), int(sys.argv[3]))
