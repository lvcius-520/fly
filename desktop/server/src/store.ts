import { mkdir, readFile, writeFile } from "node:fs/promises";
import path from "node:path";
import type { ApiState } from "@fly/shared";
import { createSeedState } from "./seed.js";

export class DataStore {
  private state: ApiState = createSeedState();
  private readonly runtimeDir = path.resolve(process.cwd(), "runtime");
  private readonly runtimeFile = path.resolve(this.runtimeDir, "mock-db.json");

  async init(): Promise<void> {
    await mkdir(this.runtimeDir, { recursive: true });
    try {
      const raw = await readFile(this.runtimeFile, "utf-8");
      this.state = JSON.parse(raw) as ApiState;
    } catch {
      this.state = createSeedState();
      await this.persist();
    }
  }

  snapshot(): ApiState {
    return structuredClone(this.state);
  }

  async mutate<T>(mutator: (draft: ApiState) => T): Promise<T> {
    const draft = this.snapshot();
    const result = mutator(draft);
    this.state = draft;
    await this.persist();
    return result;
  }

  private async persist(): Promise<void> {
    await writeFile(this.runtimeFile, JSON.stringify(this.state, null, 2), "utf-8");
  }
}
