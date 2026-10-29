import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { mkdtemp, writeFile, rm, utimes } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import { apkMeta, withApkMeta, _resetApkMetaCache } from "../src/apkMeta.js";

async function withTempDir(fn) {
  const dir = await mkdtemp(path.join(tmpdir(), "bt-apkmeta-"));
  try {
    return await fn(dir);
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
}

describe("apkMeta", () => {
  it("devuelve size y sha256 correctos", async () => {
    await withTempDir(async (dir) => {
      const file = path.join(dir, "bibliotube.apk");
      const content = Buffer.from("PK\u0003\u0004contenido-de-prueba");
      await writeFile(file, content);
      const meta = await apkMeta(file);
      assert.equal(meta.size, content.length);
      assert.equal(meta.sha256, createHash("sha256").update(content).digest("hex"));
    });
  });

  it("reutiliza la cache si mtime y size no cambian", async () => {
    await withTempDir(async (dir) => {
      const file = path.join(dir, "a.apk");
      await writeFile(file, "uno");
      const first = await apkMeta(file);
      const second = await apkMeta(file);
      assert.deepEqual(second, first);
      // Mismo contenido y tamano pero mtime distinto -> recalcula.
      const past = new Date(Date.now() - 60_000);
      await utimes(file, past, past);
      const third = await apkMeta(file);
      assert.equal(third.sha256, first.sha256);
      assert.equal(third.size, first.size);
    });
  });

  it("rechaza rutas invalidas", async () => {
    await assert.rejects(() => apkMeta(""), /invalid apk path/i);
    await assert.rejects(() => apkMeta(null), /invalid apk path/i);
  });

  it("propaga ENOENT si el archivo no existe", async () => {
    await withTempDir(async (dir) => {
      await assert.rejects(() => apkMeta(path.join(dir, "nope.apk")), /ENOENT/);
    });
  });
});

describe("withApkMeta", () => {
  it("enriquece un apkUrl local con size y sha256", async () => {
    await withTempDir(async (dir) => {
      _resetApkMetaCache();
      const content = Buffer.from("apk-bytes");
      await writeFile(path.join(dir, "bibliotube.apk"), content);
      const info = { versionCode: 3, versionName: "1.0.2", apkUrl: "/public/bibliotube.apk" };
      const out = await withApkMeta(info, dir);
      assert.equal(out.apkSize, content.length);
      assert.equal(out.apkSha256, createHash("sha256").update(content).digest("hex"));
      assert.equal(out.versionCode, 3);
    });
  });

  it("no toca URLs externas (http/https)", async () => {
    const info = { versionCode: 3, versionName: "1.0.2", apkUrl: "https://cdn.example.com/x.apk" };
    assert.deepEqual(await withApkMeta(info, "/tmp"), info);
  });

  it("devuelve el info intacto si el archivo no existe", async () => {
    await withTempDir(async (dir) => {
      const info = { versionCode: 3, versionName: "1.0.2", apkUrl: "/public/missing.apk" };
      assert.deepEqual(await withApkMeta(info, dir), info);
    });
  });

  it("no permite traversal desde la BD", async () => {
    await withTempDir(async (dir) => {
      const info = { versionCode: 3, versionName: "1.0.2", apkUrl: "/public/../../secret.apk" };
      const out = await withApkMeta(info, dir);
      assert.equal(out.apkSize, undefined);
      assert.equal(out.apkUrl, info.apkUrl);
    });
  });

  it("ignora entradas nulas o sin apkUrl", async () => {
    assert.equal(await withApkMeta(null, "/tmp"), null);
    const noUrl = { versionCode: 1, versionName: "1.0.0" };
    assert.deepEqual(await withApkMeta(noUrl, "/tmp"), noUrl);
  });
});
