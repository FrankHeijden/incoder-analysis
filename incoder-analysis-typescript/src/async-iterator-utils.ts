export async function* asyncSlice<T>(
  it: AsyncIterableIterator<T>,
  start: number = 0,
  end: number = Infinity,
  step: number = 1
): AsyncIterableIterator<T> {
  let i = 0;
  let next = start;
  for await (const e of it) {
    if (i >= end) break;
    if (i >= next) {
      next += step;
      yield e;
    }
    i++;
  }
}
