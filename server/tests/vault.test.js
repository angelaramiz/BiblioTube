import { describe, it } from "node:test";
import assert from "node:assert/strict";
import {
  detectPlatform,
  validateVideoInput,
  validateCategoryInput,
  sortVideos,
  fetchPreview,
} from "../src/vault.js";

// --- detectPlatform ---
describe("detectPlatform", () => {
  it("detecta youtube por dominio y youtu.be", () => {
    assert.equal(detectPlatform("https://www.youtube.com/watch?v=abc123"), "youtube");
    assert.equal(detectPlatform("https://youtube.com/shorts/xyz"), "youtube");
    assert.equal(detectPlatform("https://m.youtube.com/watch?v=abc"), "youtube");
    assert.equal(detectPlatform("https://youtu.be/abc123"), "youtube");
    assert.equal(detectPlatform("http://youtu.be/abc123"), "youtube");
  });

  it("detecta instagram y tiktok por dominio", () => {
    assert.equal(detectPlatform("https://www.instagram.com/reel/abc/"), "instagram");
    assert.equal(detectPlatform("https://instagram.com/p/abc/"), "instagram");
    assert.equal(detectPlatform("https://www.tiktok.com/@user/video/123"), "tiktok");
    assert.equal(detectPlatform("https://vm.tiktok.com/abc/"), "tiktok");
  });

  it("devuelve 'otra' para dominios desconocidos", () => {
    assert.equal(detectPlatform("https://example.com/video.mp4"), "otra");
    assert.equal(detectPlatform("https://vimeo.com/123"), "otra");
  });

  it("nunca lanza: input nulo o no-http devuelve 'otra'", () => {
    assert.equal(detectPlatform(null), "otra");
    assert.equal(detectPlatform(undefined), "otra");
    assert.equal(detectPlatform(""), "otra");
    assert.equal(detectPlatform("no-es-url"), "otra");
    assert.equal(detectPlatform("ftp://archivos.com/x"), "otra");
    assert.equal(detectPlatform(123), "otra");
  });
});

// --- validateVideoInput ---
describe("validateVideoInput (POST)", () => {
  it("acepta payload minimo con defaults y plataforma auto-detectada", () => {
    // Arrange
    const body = { titulo: "Mi video", url: "https://www.youtube.com/watch?v=abc" };
    // Act
    const out = validateVideoInput(body);
    // Assert
    assert.deepEqual(out, {
      category_id: null,
      titulo: "Mi video",
      descripcion: "",
      plataforma: "youtube",
      url: "https://www.youtube.com/watch?v=abc",
      thumbnail_url: "",
      prioridad: 0,
      recordatorio_at: null,
      recordatorio_repeat: "none",
      drive_file_id: "",
      drive_status: "none",
      local_path: "",
    });
  });

  it("acepta payload completo y respeta plataforma explicita", () => {
    // Arrange
    const body = {
      category_id: "cat-1",
      titulo: " Titulo ",
      descripcion: "desc",
      plataforma: "tiktok",
      url: "https://www.youtube.com/watch?v=abc",
      thumbnail_url: "https://img.com/a.jpg",
      prioridad: 5,
      recordatorio_at: "2026-11-01T10:00:00.000Z",
      recordatorio_repeat: "daily",
    };
    // Act
    const out = validateVideoInput(body);
    // Assert
    assert.equal(out.plataforma, "tiktok");
    assert.equal(out.titulo, "Titulo");
    assert.equal(out.prioridad, 5);
    assert.equal(out.recordatorio_repeat, "daily");
  });

  it("rechaza body nulo, array o sin campos obligatorios", () => {
    assert.throws(() => validateVideoInput(null), /invalid/i);
    assert.throws(() => validateVideoInput(undefined), /invalid/i);
    assert.throws(() => validateVideoInput([]), /invalid/i);
    assert.throws(() => validateVideoInput({}), /titulo/i);
    assert.throws(() => validateVideoInput({ titulo: "x" }), /url/i);
    assert.throws(() => validateVideoInput({ titulo: "", url: "https://a.com" }), /titulo/i);
    assert.throws(() => validateVideoInput({ titulo: "x".repeat(201), url: "https://a.com" }), /titulo/i);
  });

  it("rechaza URL no http(s)", () => {
    assert.throws(() => validateVideoInput({ titulo: "t", url: "ftp://a.com/x" }), /url/i);
    assert.throws(() => validateVideoInput({ titulo: "t", url: "no-url" }), /url/i);
    assert.throws(() => validateVideoInput({ titulo: "t", url: "" }), /url/i);
  });

  it("rechaza enums y tipos invalidos", () => {
    const base = { titulo: "t", url: "https://a.com/x" };
    assert.throws(() => validateVideoInput({ ...base, plataforma: "vimeo" }), /plataforma/i);
    assert.throws(() => validateVideoInput({ ...base, prioridad: 1.5 }), /prioridad/i);
    assert.throws(() => validateVideoInput({ ...base, prioridad: "alta" }), /prioridad/i);
    assert.throws(() => validateVideoInput({ ...base, recordatorio_repeat: "monthly" }), /recordatorio/i);
    assert.throws(() => validateVideoInput({ ...base, recordatorio_at: "ayer" }), /recordatorio/i);
  });

  it("ignora campos extra sin mutar el input", () => {
    // Arrange
    const body = Object.freeze({ titulo: "t", url: "https://a.com/x", hack: true, user_id: "otro" });
    // Act
    const out = validateVideoInput(body);
    // Assert
    assert.ok(!("hack" in out));
    assert.ok(!("user_id" in out));
  });

  it("acepta campos drive con defaults y valida drive_status", () => {
    // Arrange + Act
    const out = validateVideoInput({ titulo: "t", url: "https://a.com/x" });
    // Assert
    assert.equal(out.drive_file_id, "");
    assert.equal(out.drive_status, "none");
    assert.equal(out.local_path, "");
    const full = validateVideoInput({
      titulo: "t",
      url: "https://a.com/x",
      drive_file_id: "abc123",
      drive_status: "done",
      local_path: "/sdcard/x.mp4",
    });
    assert.equal(full.drive_file_id, "abc123");
    assert.equal(full.drive_status, "done");
    assert.equal(full.local_path, "/sdcard/x.mp4");
    assert.throws(
      () => validateVideoInput({ titulo: "t", url: "https://a.com/x", drive_status: "nube" }),
      /drive_status/i
    );
    assert.throws(
      () => validateVideoInput({ titulo: "t", url: "https://a.com/x", drive_file_id: 42 }),
      /drive_file_id/i
    );
  });

  it("modo partial (PATCH) acepta update parcial y rechaza vacio", () => {
    // Arrange + Act + Assert
    assert.deepEqual(validateVideoInput({ titulo: "nuevo" }, { partial: true }), { titulo: "nuevo" });
    assert.throws(() => validateVideoInput({}, { partial: true }), /invalid|empty/i);
    assert.throws(() => validateVideoInput({ hack: true }, { partial: true }), /invalid|empty/i);
    assert.throws(() => validateVideoInput({ titulo: "" }, { partial: true }), /titulo/i);
  });
});

// --- validateCategoryInput ---
describe("validateCategoryInput", () => {
  it("acepta nombre con defaults", () => {
    // Arrange
    const body = { nombre: "Musica" };
    // Act
    const out = validateCategoryInput(body);
    // Assert
    assert.deepEqual(out, { nombre: "Musica", color: "#7C3AED", orden: 0 });
  });

  it("acepta color y orden explicitos", () => {
    const out = validateCategoryInput({ nombre: " x ", color: "#ff0000", orden: 3 });
    assert.deepEqual(out, { nombre: "x", color: "#ff0000", orden: 3 });
  });

  it("rechaza body invalido y nombre fuera de rango", () => {
    assert.throws(() => validateCategoryInput(null), /invalid/i);
    assert.throws(() => validateCategoryInput([]), /invalid/i);
    assert.throws(() => validateCategoryInput({}), /nombre/i);
    assert.throws(() => validateCategoryInput({ nombre: "  " }), /nombre/i);
    assert.throws(() => validateCategoryInput({ nombre: "x".repeat(81) }), /nombre/i);
    assert.throws(() => validateCategoryInput({ nombre: "ok", color: "rojo" }), /color/i);
    assert.throws(() => validateCategoryInput({ nombre: "ok", orden: 1.2 }), /orden/i);
  });

  it("ignora campos extra sin mutar el input", () => {
    const body = Object.freeze({ nombre: "n", user_id: "otro" });
    const out = validateCategoryInput(body);
    assert.ok(!("user_id" in out));
  });

  it("modo partial (PATCH) acepta parcial y rechaza vacio", () => {
    assert.deepEqual(validateCategoryInput({ color: "#00ff00" }, { partial: true }), { color: "#00ff00" });
    assert.throws(() => validateCategoryInput({}, { partial: true }), /invalid|empty/i);
    assert.throws(() => validateCategoryInput({ color: "mal" }, { partial: true }), /color/i);
  });
});

// --- sortVideos ---
describe("sortVideos", () => {
  it("ordena por prioridad DESC y fecha_guardado DESC", () => {
    // Arrange
    const rows = [
      { id: "a", prioridad: 0, fecha_guardado: "2026-01-01T00:00:00.000Z" },
      { id: "b", prioridad: 2, fecha_guardado: "2026-01-01T00:00:00.000Z" },
      { id: "c", prioridad: 2, fecha_guardado: "2026-05-01T00:00:00.000Z" },
    ];
    // Act
    const out = sortVideos(rows);
    // Assert
    assert.deepEqual(out.map((r) => r.id), ["c", "b", "a"]);
  });

  it("no muta el array original y acepta lista vacia", () => {
    // Arrange
    const rows = Object.freeze([{ id: "a", prioridad: 1, fecha_guardado: "2026-01-01T00:00:00.000Z" }]);
    // Act
    const out = sortVideos(rows);
    // Assert
    assert.notEqual(out, rows);
    assert.deepEqual(sortVideos([]), []);
  });

  it("rechaza input no array", () => {
    assert.throws(() => sortVideos(null), /invalid/i);
    assert.throws(() => sortVideos({}), /invalid/i);
  });
});

// --- fetchPreview ---
describe("fetchPreview", () => {
  it("youtube: usa oEmbed oficial y mapea campos", async () => {
    // Arrange
    let seenUrl = "";
    const fakeFetch = async (url) => {
      seenUrl = String(url);
      return {
        ok: true,
        json: async () => ({
          title: "Titulo YT",
          author_name: "Canal",
          thumbnail_url: "https://i.ytimg.com/vi/abc/hqdefault.jpg",
        }),
      };
    };
    // Act
    const out = await fetchPreview("https://www.youtube.com/watch?v=abc", fakeFetch);
    // Assert
    assert.match(seenUrl, /^https:\/\/www\.youtube\.com\/oembed\?/);
    assert.match(seenUrl, /format=json/);
    assert.deepEqual(out, {
      titulo: "Titulo YT",
      descripcion: "Canal",
      thumbnail_url: "https://i.ytimg.com/vi/abc/hqdefault.jpg",
      plataforma: "youtube",
    });
  });

  it("youtube con oEmbed caido -> fallback con dominio", async () => {
    // Arrange: fetch que falla
    const downFetch = async () => ({ ok: false, status: 404, json: async () => ({}) });
    // Act
    const out = await fetchPreview("https://youtu.be/abc", downFetch);
    // Assert
    assert.deepEqual(out, {
      titulo: "youtu.be",
      descripcion: "",
      thumbnail_url: "",
      plataforma: "youtube",
    });
  });

  it("youtube con red rota -> fallback sin lanzar", async () => {
    const brokenFetch = async () => {
      throw new Error("network down");
    };
    const out = await fetchPreview("https://www.youtube.com/watch?v=abc", brokenFetch);
    assert.equal(out.plataforma, "youtube");
    assert.equal(out.titulo, "www.youtube.com");
  });

  it("no-youtube no llama a fetch y devuelve fallback con plataforma", async () => {
    // Arrange
    let called = 0;
    const spy = async () => {
      called += 1;
      throw new Error("no deberia llamarse");
    };
    // Act
    const ig = await fetchPreview("https://www.instagram.com/reel/abc/", spy);
    const tk = await fetchPreview("https://www.tiktok.com/@u/video/1", spy);
    // Assert
    assert.equal(called, 0);
    assert.equal(ig.plataforma, "instagram");
    assert.equal(ig.titulo, "www.instagram.com");
    assert.equal(tk.plataforma, "tiktok");
    assert.deepEqual([ig.descripcion, ig.thumbnail_url], ["", ""]);
  });

  it("rechaza URL invalida sin llamar a fetch", async () => {
    let called = 0;
    const spy = async () => {
      called += 1;
      return { ok: true, json: async () => ({}) };
    };
    await assert.rejects(() => fetchPreview("no-url", spy), /url/i);
    await assert.rejects(() => fetchPreview("", spy), /url/i);
    assert.equal(called, 0);
  });
});
