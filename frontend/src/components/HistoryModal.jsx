export default function HistoryModal({ open, row, onClose }) {
  if (!open || !row) return null;

  // row.aiRaw may be string JSON (from your backend)
  let pretty = "";
  try {
    const obj = typeof row.aiRaw === "string" ? JSON.parse(row.aiRaw) : row.aiRaw;
    pretty = JSON.stringify(obj, null, 2);
  } catch {
    pretty = String(row.aiRaw ?? "");
  }

  return (
    <div className="fixed inset-0 z-50">
      {/* Backdrop */}
      <div
        className="absolute inset-0 bg-black/50"
        onClick={onClose}
      />

      {/* Modal */}
      <div className="absolute inset-0 flex items-center justify-center p-4">
        <div className="w-full max-w-3xl bg-white rounded-xl shadow-xl overflow-hidden">
          <div className="flex items-center justify-between px-5 py-4 border-b">
            <div>
              <h3 className="text-lg font-semibold">Analysis</h3>
              <p className="text-sm text-gray-500">
                {row.originalFilename} • {new Date(row.createdAt).toLocaleString()}
              </p>
            </div>

            <button
              className="px-3 py-1 rounded-md border border-gray-300 hover:border-black"
              onClick={onClose}
            >
              Close
            </button>
          </div>

          <div className="p-5 grid gap-4 md:grid-cols-2">
            {/* Image preview (if backend stores base64) */}
            <div className="rounded-lg border p-3">
              <div className="text-sm font-semibold mb-2">Uploaded Image</div>
              {row.base64 ? (
                <img
                  src={`data:${row.contentType};base64,${row.base64}`}
                  alt={row.originalFilename}
                  className="w-full rounded-md"
                />
              ) : (
                <div className="text-sm text-gray-500">
                  No image data available in this row.
                </div>
              )}
            </div>

            {/* JSON output */}
            <div className="rounded-lg border p-3">
              <div className="text-sm font-semibold mb-2">AI Output (JSON)</div>
              <pre className="text-xs whitespace-pre-wrap break-words bg-gray-50 p-3 rounded-md max-h-[55vh] overflow-auto">
{pretty || "No AI output found."}
              </pre>
            </div>
          </div>

          <div className="px-5 py-4 border-t flex justify-end gap-2">
            <button
              className="px-3 py-2 rounded-md border border-gray-300 hover:border-black"
              onClick={() => {
                navigator.clipboard?.writeText(pretty);
              }}
            >
              Copy JSON
            </button>
            <button
              className="px-3 py-2 rounded-md bg-black text-white"
              onClick={onClose}
            >
              Done
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
