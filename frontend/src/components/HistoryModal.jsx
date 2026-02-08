import { useEffect } from "react";
import { getCritiqueObject } from "./critique";

export default function HistoryModal({ open, row, onClose }) {

  // ✅ Lock/unlock background scroll based on "open"
  useEffect(() => {
    const prev = document.body.style.overflow;

    if (open) {
      document.body.style.overflow = "hidden";
    } else {
      document.body.style.overflow = prev || "";
    }

    return () => {
      document.body.style.overflow = prev || "";
    };
  }, [open]);

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
          className="w-full max-w-4xl bg-white rounded-xl shadow-xl overflow-hidden flex flex-col max-h-[90vh]"
          onClick={(e) => e.stopPropagation()}
        >
          <div className="flex items-center justify-between px-5 py-4 border-b shrink-0">
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

          <div className="p-5 grid gap-4 md:grid-cols-2 overflow-y-auto flex-1">
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
                  Critique JSON parse nahi ho raha.
                </div>
              ) : (
                <div className="space-y-4">
                  {/* your critique UI same as before */}
                </div>
              )}
            </div>
          </div>

          <div className="px-5 py-4 border-t shrink-0 bg-white flex justify-end gap-2">
            <button
              type="button"
              className="px-3 py-2 rounded-md border border-gray-300 hover:border-black"
              onClick={close}
            >
              Close
            </button>
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
