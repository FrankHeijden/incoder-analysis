import json
import os
import sys
import time

from tqdm import tqdm

import incoder
import util


def _run_file(file: str) -> None:
    data = json.loads(util.read_file(file))

    left_context_only = data["left_context_only"]
    t_bf = time.time()
    left_context_only["prediction"] = incoder.generate(left_context_only["left_context"], "")
    left_context_only["inference_time"] = (time.time() - t_bf) * 1000

    both_contexts = data["both_contexts"]
    t_bf = time.time()
    both_contexts["prediction"] = incoder.generate(both_contexts["left_context"], both_contexts["right_context"])
    both_contexts["inference_time"] = (time.time() - t_bf) * 1000
    with open("output" + file[7:], "w") as f:
        json.dump(data, f)


def run(n: int, i: int) -> None:
    files = util.get_files_in_dir("dataset/python") + util.get_files_in_dir("dataset/javascript")
    os.makedirs("output/python", exist_ok=True)
    os.makedirs("output/javascript", exist_ok=True)

    output_files = set(util.get_files_in_dir("output/python") + util.get_files_in_dir("output/javascript"))
    files = list(filter(lambda f: f not in output_files and abs(hash(f)) % n == i, files))

    for file in tqdm(files, total=len(files)):
        _run_file(file)


if __name__ == '__main__':
    run(int(sys.argv[1]), int(sys.argv[2]))
