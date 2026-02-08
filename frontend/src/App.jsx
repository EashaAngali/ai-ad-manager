import { useEffect, useState } from "react";
import UploadComponent from "./components/UploadComponent.jsx";
import HistoryTable from "./components/HistoryTable.jsx";
import HistoryModal from "./components/HistoryModal.jsx";

function safeJsonParse(s) {
  try { return JSON.parse(s); } catch { return null; }
}

export default function App() {
  const [history, setHistory] = useState([]);
  const [selected, setSelected] = useState(null);
  const [loadError, setLoadError] = useState("");
  const [modalOpen, setModalOpen] = useState(false);


  const API = import.meta.env.VITE_API_BASE || "http://localhost:8080";

  async function loadHistory() {
    setLoadError("");
    try {
      const res = await fetch(`${API}/api/ads/history`);
      const text = await res.text();
      if (!res.ok) throw new Error(text || "Failed to load history");
      setHistory(JSON.parse(text));
    } catch (e) {
      setLoadError(e.message);
    }
  }

  useEffect(() => { loadHistory(); }, []);

  const parsed = selected ? safeJsonParse(selected.aiJson) : null;

  return (
    <div className="min-h-screen bg-gray-100">
      <div className="max-w-6xl mx-auto p-6">
        <div className="flex items-center justify-between">
          <h1 className="text-2xl font-bold">AI Ad-Manager</h1>
          <span className="text-sm text-gray-600">Upload → Critique → Improve</span>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-6 mt-6">
          <UploadComponent
            onUploaded={(item) => {
  setSelected(item);
  setModalOpen(true);
  loadHistory();
}}

          />

          <div className="bg-white rounded-xl shadow p-6">
            <h2 className="text-lg font-semibold">Critique</h2>

            {!selected && (
              <p className="text-sm text-gray-500 mt-2">
                Upload an ad or pick one from history to see the critique.
              </p>
            )}

            {selected && (
              <div className="mt-4 space-y-4">
                <img
                  className="rounded-lg border w-full"
                  src={`data:${selected.contentType};base64,${selected.imageBase64}`}
                  alt="Ad"
                />

                {parsed ? (
                  <>
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
                  </>
                ) : (
                  <div className="text-sm text-red-600">
                    Could not parse AI JSON. Raw response:
                    <pre className="mt-2 text-xs whitespace-pre-wrap bg-gray-50 border rounded-lg p-3">
                      {selected.aiJson}
                    </pre>
                  </div>
                )}
              </div>
            )}
          </div>

          <div className="md:col-span-2">
            {loadError && (
              <div className="mb-3 text-sm text-red-600">
                History load failed: {loadError}
              </div>
            )}
<HistoryTable
  rows={history}
  onSelect={(r) => { setSelected(r); setModalOpen(true); }}
/>
<HistoryModal
  open={modalOpen}
  row={selected}
  onClose={() => setModalOpen(false)}
/>


          </div>
        </div>
      </div>
    </div>
  );
}
