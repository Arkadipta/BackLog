import BackLogCore
import SwiftUI

/// Spike window for task 0.2: a text field and a live list. Task 2.1 replaces it.
struct SpikeView: View {
    @State private var model = SpikeNotesModel()
    @State private var draft = ""

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                TextField("New note", text: $draft)
                    .textFieldStyle(.roundedBorder)
                    .onSubmit(add)
                Button("Add", action: add)
                    .keyboardShortcut(.defaultAction)
                    .disabled(draft.trimmingCharacters(in: .whitespaces).isEmpty)
            }

            List(model.notes, id: \.id) { note in
                HStack {
                    Text(note.text)
                    Spacer()
                    Text(Date(timeIntervalSince1970: Double(note.created_at) / 1000), style: .time)
                        .foregroundStyle(.secondary)
                        .monospacedDigit()
                }
            }

            if let error = model.lastError {
                Text(error).foregroundStyle(.red)
            }
            Text("\(model.notes.count) notes · \(greeting())")
                .font(.caption)
                .foregroundStyle(.secondary)
            Text(model.databaseDirectory)
                .font(.caption.monospaced())
                .foregroundStyle(.secondary)
                .textSelection(.enabled)
        }
        .padding()
        .frame(minWidth: 460, minHeight: 380)
        .task { await model.observe() }
    }

    private func add() {
        let text = draft
        draft = ""
        Task { await model.add(text) }
    }
}
