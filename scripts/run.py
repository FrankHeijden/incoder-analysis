import json
import os
import sys
import time

from tqdm import tqdm

import incoder


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


def run(data_path: str, n: int, i: int) -> None:
    os.makedirs(f"../output/{data_path}", exist_ok=True)

    with open(f"../dataset/{data_path}/data.jsonl", "r") as f:
        data_batch = f.readlines()[i::n]

        with open(f"../output/{data_path}/data.jsonl", "a") as f_out:
            for data_str in tqdm(data_batch, total=len(data_batch)):
                f_out.write(json.dumps(_run_data(json.loads(data_str))) + "\n")


if __name__ == '__main__':
    run(sys.argv[1], int(sys.argv[2]), int(sys.argv[3]))
