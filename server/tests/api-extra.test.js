import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { createApp } from "../src/createApp.js";

async function listen(app) {
  const server = app.listen(0);
  await new Promise((r) => server.on("listening", r));
  return server;
}

describe("cobertura extra createApp", () => {
  it("SSE stream responde hello y cierra limpio", async () => {
    const fake = { from: () => { throw new Error("no usado"); } };
    const app = createApp(fake);
    const server = await listen(app);
    const port = server.address().port;
    try {
      const res = await fetch(`http://127.0.0.1:${port}/api/events/stream`);
      assert.equal(res.status, 200);
      assert.match(res.headers.get("content-type"), /text\/event-stream/);
      const reader = res.body.getReader();
      const { value } = await reader.read();
      const text = new TextDecoder().decode(value);
      assert.match(text, /hello/);
      await reader.cancel();
    } finally {
      server.close();
    }
  });

  it("emit valido entrega a 0 clientes con envelope", async () => {
    const fake = { from: () => ({ select: async () => ({ data: [], error: null }) }) };
    const app = createApp(fake);
    const server = await listen(app);
    const port = server.address().port;
    try {
      const res = await fetch(`http://127.0.0.1:${port}/api/events/emit`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ type: "ping" }),
      });
      assert.equal(res.status, 200);
      const body = await res.json();
      assert.equal(body.success, true);
      assert.equal(body.data.delivered, 0);
    } finally {
      server.close();
    }
  });

  it("emit demasiado grande -> 400", async () => {
    const fake = { from: () => ({ select: async () => ({ data: [], error: null }) }) };
    const app = createApp(fake);
    const server = await listen(app);
    const port = server.address().port;
    try {
      const res = await fetch(`http://127.0.0.1:${port}/api/events/emit`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ big: "x".repeat(9000) }),
      });
      assert.equal(res.status, 400);
    } finally {
      server.close();
    }
  });

  it("JSON malformado -> 400 envelope", async () => {
    const fake = { from: () => ({ select: async () => ({ data: [], error: null }) }) };
    const app = createApp(fake);
    const server = await listen(app);
    const port = server.address().port;
    try {
      const res = await fetch(`http://127.0.0.1:${port}/api/events/emit`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: "{no-json",
      });
      assert.equal(res.status, 400);
      const body = await res.json();
      assert.equal(body.success, false);
    } finally {
      server.close();
    }
  });

  it("valor corrupto en Supabase -> fallback default 200", async () => {
    const fake = {
      from: () => ({
        select: () => ({
          eq: () => ({
            single: async () => ({ data: { valor: { versionCode: "roto" } }, error: null }),
          }),
        }),
      }),
    };
    const app = createApp(fake);
    const server = await listen(app);
    const port = server.address().port;
    try {
      const res = await fetch(`http://127.0.0.1:${port}/api/app-version?app=bibliotube`);
      assert.equal(res.status, 200);
      const body = await res.json();
      assert.equal(body.success, true);
      assert.equal(body.data.versionCode, 1);
    } finally {
      server.close();
    }
  });

  it("catalog con data null -> lista vacia", async () => {
    const fake = { from: () => ({ select: async () => ({ data: null, error: null }) }) };
    const app = createApp(fake);
    const server = await listen(app);
    const port = server.address().port;
    try {
      const res = await fetch(`http://127.0.0.1:${port}/api/app-catalog`);
      assert.equal(res.status, 200);
      const body = await res.json();
      assert.deepEqual(body.data.apps, []);
    } finally {
      server.close();
    }
  });
});
