import { describe, it, before, after } from "node:test";
import assert from "node:assert/strict";
import { createApp } from "../src/createApp.js";

function fakeSupabase(rows) {
  return {
    from: () => ({
      select: () => ({
        eq: async () => ({ data: rows.single ?? null, error: rows.singleError ?? null }),
      }),
    }),
  };
}

describe("GET /api/app-version", () => {
  it("200 + envelope con version valida", async () => {
    const fake = {
      from: () => ({
        select: () => ({
          eq: () => ({
            single: async () => ({
              data: { valor: { versionCode: 2, versionName: "1.0.1", apkUrl: "/public/bibliotube.apk" } },
              error: null,
            }),
          }),
        }),
      }),
    };
    const app = createApp(fake);
    const server = app.listen(0);
    await new Promise((r) => server.on("listening", r));
    const port = server.address().port;
    try {
      const res = await fetch(`http://127.0.0.1:${port}/api/app-version?app=bibliotube`);
      assert.equal(res.status, 200);
      const body = await res.json();
      assert.equal(body.success, true);
      assert.equal(body.data.versionCode, 2);
      assert.equal(body.data.versionName, "1.0.1");
    } finally {
      server.close();
    }
  });

  it("400 para app invalida (path traversal)", async () => {
    const app = createApp(fakeSupabase({}));
    const server = app.listen(0);
    await new Promise((r) => server.on("listening", r));
    const port = server.address().port;
    try {
      const res = await fetch(`http://127.0.0.1:${port}/api/app-version?app=..%2Fetc`);
      assert.equal(res.status, 400);
      const body = await res.json();
      assert.equal(body.success, false);
      assert.ok(body.error);
    } finally {
      server.close();
    }
  });

  it("200 con default cuando no hay fila en Supabase", async () => {
    const fake = {
      from: () => ({
        select: () => ({
          eq: () => ({ single: async () => ({ data: null, error: { message: "not found" } }) }),
        }),
      }),
    };
    const app = createApp(fake);
    const server = app.listen(0);
    await new Promise((r) => server.on("listening", r));
    const port = server.address().port;
    try {
      const res = await fetch(`http://127.0.0.1:${port}/api/app-version`);
      assert.equal(res.status, 200);
      const body = await res.json();
      assert.equal(body.success, true);
      assert.equal(body.data.versionCode, 1);
    } finally {
      server.close();
    }
  });

  it("500 cuando Supabase lanza excepcion", async () => {
    const fake = {
      from: () => {
        throw new Error("db down");
      },
    };
    const app = createApp(fake);
    const server = app.listen(0);
    await new Promise((r) => server.on("listening", r));
    const port = server.address().port;
    try {
      const res = await fetch(`http://127.0.0.1:${port}/api/app-version?app=bibliotube`);
      assert.equal(res.status, 500);
      const body = await res.json();
      assert.equal(body.success, false);
      // No debe filtrar detalles internos
      assert.ok(!JSON.stringify(body).includes("db down"));
    } finally {
      server.close();
    }
  });
});

describe("GET /api/app-catalog", () => {
  it("200 + envelope con lista", async () => {
    const fake = {
      from: () => ({
        select: async () => ({
          data: [{ clave: "app_version_bibliotube", valor: { versionCode: 1, versionName: "1.0.0", apkUrl: "/x.apk" } }],
          error: null,
        }),
      }),
    };
    const app = createApp(fake);
    const server = app.listen(0);
    await new Promise((r) => server.on("listening", r));
    const port = server.address().port;
    try {
      const res = await fetch(`http://127.0.0.1:${port}/api/app-catalog`);
      assert.equal(res.status, 200);
      const body = await res.json();
      assert.equal(body.success, true);
      assert.ok(Array.isArray(body.data.apps));
    } finally {
      server.close();
    }
  });

  it("500 cuando Supabase falla", async () => {
    const fake = {
      from: () => ({ select: async () => ({ data: null, error: { message: "boom" } }) }),
    };
    const app = createApp(fake);
    const server = app.listen(0);
    await new Promise((r) => server.on("listening", r));
    const port = server.address().port;
    try {
      const res = await fetch(`http://127.0.0.1:${port}/api/app-catalog`);
      assert.equal(res.status, 500);
      const body = await res.json();
      assert.equal(body.success, false);
    } finally {
      server.close();
    }
  });
});

describe("POST /api/events/emit", () => {
  it("400 cuando body no es objeto o supera limite", async () => {
    const app = createApp(fakeSupabase({}));
    const server = app.listen(0);
    await new Promise((r) => server.on("listening", r));
    const port = server.address().port;
    try {
      const res = await fetch(`http://127.0.0.1:${port}/api/events/emit`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify("solo-un-string"),
      });
      assert.equal(res.status, 400);
    } finally {
      server.close();
    }
  });
});
