import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { createApp } from "../src/createApp.js";
import { validateThumbUrl, THUMB_HOSTS } from "../src/thumb.js";

async function listen(app) {
  const server = app.listen(0);
  await new Promise((r) => server.on("listening", r));
  return server;
}

const okImage = (bytes = Buffer.from([0x89, 0x50, 0x4e, 0x47])) => ({
  ok: true,
  status: 200,
  headers: { get: (k) => (k.toLowerCase() === "content-type" ? "image/jpeg" : null) },
  arrayBuffer: async () => bytes,
});

describe("validateThumbUrl", () => {
  it("acepta ytimg", () => {
    assert.equal(validateThumbUrl("https://i.ytimg.com/vi/x/hqdefault.jpg").host, "i.ytimg.com");
  });
  it("rechaza sin url", () => {
    assert.throws(() => validateThumbUrl(""), /url/);
  });
  it("rechaza no-http", () => {
    assert.throws(() => validateThumbUrl("ftp://x/y.jpg"), /http/);
  });
  it("rechaza host fuera de lista (anti open-proxy)", () => {
    assert.throws(() => validateThumbUrl("https://evil.com/a.jpg"), /no permitido/);
  });
  it("rechaza subdominio trampa (evilytimg.com)", () => {
    assert.throws(() => validateThumbUrl("https://evilytimg.com/a.jpg"), /no permitido/);
  });
  it("lista incluye tiktok e instagram", () => {
    assert.ok(THUMB_HOSTS.includes("tiktokcdn.com"));
    assert.ok(THUMB_HOSTS.includes("cdninstagram.com"));
  });
});

describe("GET /api/thumb", () => {
  it("400 sin url", async () => {
    const app = createApp(() => null);
    const server = await listen(app);
    try {
      const res = await fetch(`http://127.0.0.1:${server.address().port}/api/thumb`);
      assert.equal(res.status, 400);
    } finally {
      server.close();
    }
  });

  it("403 host no permitido", async () => {
    const app = createApp(() => null);
    const server = await listen(app);
    try {
      const res = await fetch(
        `http://127.0.0.1:${server.address().port}/api/thumb?url=https://evil.com/a.jpg`
      );
      assert.equal(res.status, 403);
    } finally {
      server.close();
    }
  });

  it("200 proxea bytes + content-type + cache", async () => {
    const seen = {};
    const fetchFn = async (url) => {
      seen.url = String(url);
      return okImage();
    };
    const app = createApp(() => null, { fetchFn });
    const server = await listen(app);
    try {
      const res = await fetch(
        `http://127.0.0.1:${server.address().port}/api/thumb?url=https://i.ytimg.com/vi/x/hqdefault.jpg`
      );
      assert.equal(res.status, 200);
      assert.match(res.headers.get("content-type"), /image/);
      assert.ok(res.headers.get("cache-control")?.includes("max-age"));
      assert.ok((await res.arrayBuffer()).byteLength > 0);
      assert.ok(seen.url.includes("i.ytimg.com"));
    } finally {
      server.close();
    }
  });

  it("502 si el origen falla", async () => {
    const fetchFn = async () => ({ ok: false, status: 404 });
    const app = createApp(() => null, { fetchFn });
    const server = await listen(app);
    try {
      const res = await fetch(
        `http://127.0.0.1:${server.address().port}/api/thumb?url=https://i.ytimg.com/vi/x/hqdefault.jpg`
      );
      assert.equal(res.status, 502);
    } finally {
      server.close();
    }
  });
});
