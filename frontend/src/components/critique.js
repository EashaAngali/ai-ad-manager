function stripCodeFences(s) {
  if (!s) return "";
  return s
    .replace(/^\s*```json\s*/i, "")
    .replace(/^\s*```\s*/i, "")
    .replace(/\s*```\s*$/i, "")
    .trim();
}

function extractFirstJsonObject(text) {
  if (!text) return null;
  const start = text.indexOf("{");
  const end = text.lastIndexOf("}");
  if (start === -1 || end === -1 || end <= start) return null;
  return text.slice(start, end + 1);
}

function normalizeScore(x) {
  const n = Number(x);
  if (!Number.isFinite(n)) return 0;

  // If model gave 75 meaning 7.5
  if (n > 10 && n <= 100) return n / 10;

  // If model gave 0.75 meaning 7.5
  if (n > 0 && n <= 1) return n * 10;

  // Clamp
  return Math.max(0, Math.min(10, n));
}

function normalizeCritique(obj) {
  if (!obj || typeof obj !== "object") return null;

  const normalized = {
    overallScore: normalizeScore(obj.overallScore),
    scores: {
      visualHierarchy: normalizeScore(obj?.scores?.visualHierarchy),
      copyEffectiveness: normalizeScore(obj?.scores?.copyEffectiveness),
      colorTheory: normalizeScore(obj?.scores?.colorTheory),
    },
    strengths: Array.isArray(obj.strengths) ? obj.strengths.filter(Boolean).slice(0, 5) : [],
    issues: Array.isArray(obj.issues) ? obj.issues.filter(Boolean).slice(0, 5) : [],
    actionableFixes: Array.isArray(obj.actionableFixes)
      ? obj.actionableFixes
          .filter(Boolean)
          .map((f) => ({
            title: String(f?.title ?? "").trim(),
            why: String(f?.why ?? "").trim(),
            how: String(f?.how ?? "").trim(),
          }))
          .slice(0, 3)
      : [],
    improvedHeadlineOptions: Array.isArray(obj.improvedHeadlineOptions)
      ? obj.improvedHeadlineOptions.filter(Boolean).slice(0, 5)
      : [],
  };

  return normalized;
}

export function getCritiqueObject(aiJson) {
  try {
    if (!aiJson) return null;

    // Case 1: already object or JSON string
    const base = typeof aiJson === "string" ? JSON.parse(aiJson) : aiJson;
    if (!base) return null;

    // If already clean critique
    if (base.overallScore !== undefined) {
      return normalizeCritique(base);
    }

    // Case 2: Gemini wrapped response
    const parts = base?.candidates?.[0]?.content?.parts;
    if (!Array.isArray(parts) || parts.length === 0) return null;

    const text = parts.map((p) => p?.text || "").join("\n").trim();
    if (!text) return null;

    const cleaned = stripCodeFences(text);
    const jsonText = extractFirstJsonObject(cleaned) || cleaned;

    const parsed = JSON.parse(jsonText);
    return normalizeCritique(parsed);
  } catch (e) {
    return null;
  }
}
