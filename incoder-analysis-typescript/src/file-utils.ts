import { Dirent, PathLike } from 'fs';
const { resolve } = require('path');
const { readdir } = require('fs/promises');

export async function* getFiles(dir: PathLike): AsyncIterableIterator<string> {
  const dirEntries: Dirent[] = await readdir(dir, { withFileTypes: true });
  for (const dirEntry of dirEntries) {
    const res: string = resolve(dir, dirEntry.name);
    if (dirEntry.isDirectory()) {
      yield* getFiles(res);
    } else {
      yield res;
    }
  }
}
