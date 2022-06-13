import tokenize from 'js-tokens';
import { getFiles } from './file-utils';
import { createReadStream, createWriteStream, WriteStream } from 'fs';
import { createInterface } from 'readline';
import { asyncSlice } from './async-iterator-utils';
import { dirname } from 'path';
import { merge } from 'lodash/fp/object';

const OUTPUT_FOLDER = '../output/';

type ProcessedData = Data & {
  left_context_only: ProcessedDataResult;
  both_contexts: ProcessedDataResult;
  ground_truth_tokenized: string[];
}

type ProcessedDataResult = {
  prediction_tokenized: string[];
}

type Data = {
  left_context_only: LeftContext & DataResult;
  both_contexts: LeftContext & RightContext & DataResult;
  ground_truth: string;
};

type LeftContext = {
  left_context: string;
}

type RightContext = {
  right_context: string;
}

type DataResult = {
  inference_time: number;
  prediction: string;
}

function getTokens(text: string): string[] {
  return [...tokenize(text)].map((token) => token.value);
}

async function processFile(file: string, i: number, n: number): Promise<void> {
  const lines: AsyncIterableIterator<string> = createInterface({
    input: createReadStream(file),
    crlfDelay: Infinity,
  })[Symbol.asyncIterator]();

  const outFile: string = `${dirname(file)}/data-processed-${i}-${n}.jsonl`;
  const out: WriteStream = createWriteStream(outFile);
  for await (const line of asyncSlice(lines, i, Infinity, n)) {
    const data: Data = JSON.parse(line);
    const processedData: ProcessedData = merge(data, {
      left_context_only: {
        prediction_tokenized: getTokens(data.left_context_only.prediction)
      },
      both_contexts: {
        prediction_tokenized: getTokens(data.both_contexts.prediction)
      },
      ground_truth_tokenized: getTokens(data.ground_truth)
    });
    out.write(JSON.stringify(processedData));
    out.write('\n');
  }
}

async function main() {
  const n = Number(process.argv[2]);
  const i = Number(process.argv[3]);

  for await (const file of getFiles(OUTPUT_FOLDER)) {
    if (!file.endsWith('.jsonl')) continue;
    if (file.split('/').at(-2) !== 'javascript') continue;
    await processFile(file, i, n);
  }
}

main();
