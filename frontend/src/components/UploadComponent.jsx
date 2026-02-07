import { useState } from "react";

export default function UploadComponent({ onUploaded }) {
  const [dragOver, setDragOver] = useState(false);
  const [file, setFile] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

 const API = (import.meta.env.VITE_API_BASE || "https://ai-ad-manager-7.onrender.com").replace(/\/$/, "");

  function handleFile(f) {
    setError("");
    if (!f) return;
    if (!["image/png", "image/jpeg"].includes(f.type)) {
      setError("Only PNG/JPG allowed.");
      return;
    }
    setFile(f);
  }

  async function upload() {
    if (!file) return;
    setLoading(true);
    setError("");

    try {
      const form = new FormData();
      form.append("file", file);

      const res = await fetch(`${API}/api/ads/analyze`, {
        method: "POST",
        body: form,
      });

      const text = await res.text();
      if (!res.ok) {
        // backend sends JSON {error: "..."} sometimes
        try {
          const j = JSON.parse(text);
          throw new Error(j.error || "Upload failed");
        } catch {
          throw new Error(text || "Upload failed");
        }
      }

      const data = JSON.parse(text);
      onUploaded?.(data);
      setFile(null);
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="bg-white rounded-xl shadow p-6">
      <div
        className={[
          "border-2 border-dashed rounded-xl p-8 text-center transition",
          dragOver ? "border-black" : "border-gray-300",
        ].join(" ")}
        onDragOver={(e) => { e.preventDefault(); setDragOver(true); }}
        onDragLeave={() => setDragOver(false)}
        onDrop={(e) => {
          e.preventDefault();
          setDragOver(false);
          handleFile(e.dataTransfer.files?.[0]);
        }}
      >
        <p className="text-lg font-semibold">Drop your ad screenshot here</p>
        <p className="text-sm text-gray-500 mt-1">PNG or JPG. We'll critique it with AI.</p>

        <div className="mt-4">
          <label className="inline-block px-4 py-2 rounded-lg bg-black text-white cursor-pointer">
            Choose File
            <input
              type="file"
              className="hidden"
              accept="image/png,image/jpeg"
              onChange={(e) => handleFile(e.target.files?.[0])}
            />
          </label>
        </div>

        {file && (
          <div className="mt-4 text-sm">
            <span className="font-medium">Selected:</span> {file.name}
          </div>
        )}
      </div>

      {error && <div className="mt-4 text-sm text-red-600">{error}</div>}

      <button
        className="mt-4 w-full bg-black text-white rounded-lg py-3 font-semibold disabled:opacity-50"
        disabled={!file || loading}
        onClick={upload}
      >
        {loading ? "Analyzing..." : "Analyze Ad"}
      </button>
    </div>
  );
}
