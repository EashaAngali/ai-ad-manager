function getCritiqueObject(aiJson) {
  try {
    const obj = typeof aiJson === "string" ? JSON.parse(aiJson) : aiJson;
    if (!obj) return null;

    if (obj.overallScore !== undefined) return obj;

    const parts = obj?.candidates?.[0]?.content?.parts;
    if (Array.isArray(parts)) {
      const text = parts.map(p => p.text || "").join("").trim();
      if (!text) return null;

      const cleaned = text
        .replace(/^```json\s*/i, "")
        .replace(/^```\s*/i, "")
        .replace(/```$/i, "")
        .trim();

      return JSON.parse(cleaned);
    }

    return null;
  } catch {
    return null;
  }
}

export default function HistoryModal({ open, row, onClose }) {
  if (!open || !row) return null;

  const parsed = getCritiqueObject(row.aiJson);

  const close = (e) => {
    e?.preventDefault?.();
    e?.stopPropagation?.();
    onClose?.();
  };

  return (
    <div className="fixed inset-0 z-50">
      <div className="absolute inset-0 bg-black/50" onClick={close} />

      <div className="absolute inset-0 flex items-center justify-center p-4">
        <div
          className="w-full max-w-4xl bg-white rounded-xl shadow-xl overflow-hidden"
          onClick={(e) => e.stopPropagation()}
        >
          <div className="flex items-center justify-between px-5 py-4 border-b">
            <div>
              <h3 className="text-lg font-semibold">Analysis</h3>
              <p className="text-sm text-gray-500">
                {row.originalFilename} • {new Date(row.createdAt).toLocaleString()}
              </p>
            </div>

            <button
              type="button"
              className="px-4 py-2 rounded-md bg-gray-100 hover:bg-gray-200 font-medium"
              onClick={close}
            >
              ← Back
            </button>
          </div>

          <div className="p-5 grid gap-4 md:grid-cols-2">
            <div className="rounded-lg border p-3">
              <div className="text-sm font-semibold mb-2">Uploaded Image</div>
              {row.imageBase64 ? (
                <img
                  src={`data:${row.contentType};base64,${row.imageBase64}`}
                  alt={row.originalFilename}
                  className="w-full rounded-md"
                />
              ) : (
                <div className="text-sm text-gray-500">No image data.</div>
              )}
            </div>

            <div className="rounded-lg border p-3">
              <div className="text-sm font-semibold mb-2">Critique</div>

              {!parsed ? (
                <div className="text-sm text-red-600">
                  Critique JSON parse nahi ho raha. (Most common reason: Gemini response truncate / invalid JSON)
                </div>
              ) : (
                <div className="space-y-4">
                  <div className="grid grid-cols-2 gap-3">
                    <div className="p-3 rounded-lg bg-gray-50 border">
                      <div className="text-xs text-gray-500">Overall</div>
                      <div className="text-xl font-bold">{parsed.overallScore}/10</div>
                    </div>
                    <div className="p-3 rounded-lg bg-gray-50 border">
                      <div className="text-xs text-gray-500">Hierarchy</div>
                      <div className="text-xl font-bold">{parsed.scores?.visualHierarchy}/10</div>
                    </div>
                    <div className="p-3 rounded-lg bg-gray-50 border">
                      <div className="text-xs text-gray-500">Copy</div>
                      <div className="text-xl font-bold">{parsed.scores?.copyEffectiveness}/10</div>
                    </div>
                    <div className="p-3 rounded-lg bg-gray-50 border">
                      <div className="text-xs text-gray-500">Color</div>
                      <div className="text-xl font-bold">{parsed.scores?.colorTheory}/10</div>
                    </div>
                  </div>

                  <div>
                    <h3 className="font-semibold">Strengths</h3>
                    <ul className="list-disc pl-5 text-sm text-gray-700 mt-2 space-y-1">
                      {(parsed.strengths || []).map((x, i) => <li key={i}>{x}</li>)}
                    </ul>
                  </div>

                  <div>
                    <h3 className="font-semibold">Issues</h3>
                    <ul className="list-disc pl-5 text-sm text-gray-700 mt-2 space-y-1">
                      {(parsed.issues || []).map((x, i) => <li key={i}>{x}</li>)}
                    </ul>
                  </div>

                  <div>
                    <h3 className="font-semibold">Actionable Fixes</h3>
                    <div className="mt-2 space-y-2">
                      {(parsed.actionableFixes || []).map((x, i) => (
                        <div key={i} className="p-3 rounded-lg border bg-gray-50">
                          <div className="font-semibold">{x.title}</div>
                          <div className="text-sm text-gray-700 mt-1"><b>Why:</b> {x.why}</div>
                          <div className="text-sm text-gray-700 mt-1"><b>How:</b> {x.how}</div>
                        </div>
                      ))}
                    </div>
                  </div>

                  <div>
                    <h3 className="font-semibold">Improved Headline Options</h3>
                    <ul className="list-disc pl-5 text-sm text-gray-700 mt-2 space-y-1">
                      {(parsed.improvedHeadlineOptions || []).map((x, i) => <li key={i}>{x}</li>)}
                    </ul>
                  </div>
                </div>
              )}
            </div>
          </div>

          <div className="px-5 py-4 border-t flex justify-end gap-2">
            <button
              type="button"
              className="px-3 py-2 rounded-md bg-black text-white"
              onClick={close}
            >
              Done
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
App
