import os
from typing import List


def read_file(file: str) -> str:
    with open(file, "r") as content:
        return content.read()


def get_files_in_dir(dir_path: str, add_dir_path=True) -> List[str]:
    files = next(os.walk(dir_path), (None, None, []))[2]
    if add_dir_path:
        return [dir_path + "/" + file for file in files]
    return files


def merge_files_in_dir(dir_path: str, output_file: str, delete_files=False) -> None:
    with open(output_file, "a") as f_out:
        for file in get_files_in_dir(dir_path):
            if file == output_file:
                continue
            f_out.write(read_file(file) + "\n")
            if delete_files:
                os.remove(file)


def truncate_left_context(context: List[int], max_length: int) -> List[int]:
    return context[max(0, len(context) - max_length):]


def truncate_right_context(context: List[int], max_length: int) -> List[int]:
    return context[:min(max_length, len(context))]
