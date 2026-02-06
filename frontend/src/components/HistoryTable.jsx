export default function HistoryTable({ rows, onSelect }) {
  return (
    <div className="bg-white rounded-xl shadow p-6">
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-semibold">History</h2>
        <span className="text-sm text-gray-500">{rows.length} items</span>
      </div>

      <div className="mt-4 overflow-auto">
        <table className="min-w-full text-sm">
          <thead className="text-left text-gray-500 border-b">
            <tr>
              <th className="py-2 pr-4">File</th>
              <th className="py-2 pr-4">Created</th>
              <th className="py-2 pr-4">Action</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.id} className="border-b last:border-b-0">
                <td className="py-3 pr-4 font-medium">{r.originalFilename}</td>
                <td className="py-3 pr-4">{new Date(r.createdAt).toLocaleString()}</td>
                <td className="py-3 pr-4">
                  <button
                    className="px-3 py-1 rounded-md border border-gray-300 hover:border-black"
                    onClick={() => onSelect?.(r)}
                  >
                    View
                  </button>
                </td>
              </tr>
            ))}
            {rows.length === 0 && (
              <tr>
                <td className="py-6 text-gray-500" colSpan={3}>
                  No uploads yet.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
