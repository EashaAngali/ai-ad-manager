export function getCritiqueObject(aiJson) {
  try {
    const obj = typeof aiJson === "string" ? JSON.parse(aiJson) : aiJson;
    if (!obj) return null;

    // Already clean JSON
    if (obj.overallScore !== undefined) return obj;

    // Gemini wrapped response
    const parts = obj?.candidates?.[0]?.content?.parts;
    if (!Array.isArray(parts)) return null;

    const text = parts.map(p => p.text || "").join("").trim();
    if (!text) return null;

    const cleaned = text
      .replace(/^```json\s*/i, "")
      .replace(/^```\s*/i, "")
      .replace(/```$/i, "")
      .trim();

    return JSON.parse(cleaned);
  } catch {
    return null;
  }
}
