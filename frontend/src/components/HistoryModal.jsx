import { useEffect } from "react";
import { getCritiqueObject } from "./critique";

function lockScroll() {
  const html = document.documentElement;
  const body = document.body;

  // Save previous styles only once
  const prev = {
    htmlOverflow: html.style.overflow,
    bodyOverflow: body.style.overflow,
    bodyPaddingRight: body.style.paddingRight,
  };

  // Compensate for scrollbar to prevent layout shift
  const scrollBarWidth = window.innerWidth - html.clientWidth;
  html.style.overflow = "hidden";
  body.style.overflow = "hidden";
  if (scrollBarWidth > 0) {
    body.style.paddingRight = `${scrollBarWidth}px`;
  }

  return () => {
    html.style.overflow = prev.htmlOverflow || "";
    body.style.overflow = prev.bodyOverflow || "";
    body.style.paddingRight = prev.bodyPaddingRight || "";
  };
}

export default function HistoryModal({ open, row, onClose }) {
  useEffect(() => {
    if (!open) return;
    const unlock = lockScroll();
    return () => unlock();
  }, [open]);

  if (!open || !row) return null;

  const parsed = getCritiqueObject(row.aiJson);

  const close = (e) => {
    e?.preventDefault?.();
    e?.stopPropagation?.();
    onClose?.();
  };

  return (
    <div className="fixed inset-0 z-50" role="dialog" aria-modal="true">
      <div className="absolute inset-0 bg-black/50" onClick={close} />

      {/* IMPORTANT: use items-start (not center) so footer never gets pushed off-screen */}
      <div className="absolute inset-0 flex items-start justify-center p-4 sm:p-6 overflow-y-auto">
        <div
          className="w-full max-w-4xl bg-white rounded-xl shadow-xl overflow-hidden flex flex-col"
          style={{ maxHeight: "calc(100vh - 3rem)" }}
          onClick={(e) => e.stopPropagation()}
        >
          {/* Header */}
          <div className="flex items-center justify-between px-5 py-4 border-b shrink-0 bg-white">
            <div className="min-w-0">
              <h3 className="text-lg font-semibold">Analysis</h3>
              <p className="text-sm text-gray-500 truncate">
                {row.originalFilename} • {new Date(row.createdAt).toLocaleString()}
              </p>
            </div>

            <button
              type="button"
              className="ml-3 px-4 py-2 rounded-md bg-gray-100 hover:bg-gray-200 font-medium shrink-0"
              onClick={close}
            >
              ← Back
            </button>
          </div>

          {/* Body (scrolls) */}
          <div className="p-5 grid gap-4 md:grid-cols-2 overflow-y-auto flex-1">
            <div className="rounded-lg border p-3">
              <div className="text-sm font-semibold mb-2">Uploaded Image</div>
              {row.imageBase64 ? (
                <img
                  src={`data:${row.contentType};base64,${row.imageBase64}`}
                  alt={row.originalFilename || "Uploaded"}
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

          {/* Footer (always visible) */}
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
