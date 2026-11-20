import BackLogCore
import SwiftUI

/// Placeholder until task 2.1: opens the real database (creating, migrating and
/// seeding it) and shows what it found, including D9's full-text check.
struct StatusView: View {
    @State private var status: DatabaseStatus?
    @State private var failure: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(greeting()).font(.headline)
            if let status {
                Text("SQLite \(status.sqliteVersion) · FTS4 \(yesNo(status.fts4)) · FTS5 \(yesNo(status.fts5))")
                Text("Schema version \(status.schemaVersion)")
                Text("Lists: \(status.lists.map { $0.name + ($0.isInbox ? " (inbox)" : "") }.joined(separator: ", "))")
                Text("Device \(status.deviceId)").font(.caption.monospaced()).foregroundStyle(.secondary)
                Text(status.location)
                    .font(.caption.monospaced())
                    .foregroundStyle(.secondary)
                    .textSelection(.enabled)
                if let error = status.error {
                    Text("Error: \(error)").foregroundStyle(.red)
                }
            } else if let failure {
                Text("Error: \(failure)").foregroundStyle(.red)
            } else {
                Text("Opening database…")
            }
        }
        .padding()
        .frame(minWidth: 460, minHeight: 220, alignment: .topLeading)
        .task {
            do {
                status = try await loadDatabaseStatus()
            } catch {
                failure = String(describing: error)
            }
        }
    }

    private func yesNo(_ value: Bool) -> String { value ? "yes" : "no" }
}
