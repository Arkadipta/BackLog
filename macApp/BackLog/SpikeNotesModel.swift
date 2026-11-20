import BackLogCore
import Observation

/// Spike model for task 0.2: reads the shared repository's Flow as an AsyncSequence.
/// Task 2.1 replaces it with the real list models.
@MainActor
@Observable
final class SpikeNotesModel {
    private(set) var notes: [Spike_note] = []
    private(set) var lastError: String?
    let databaseDirectory: String

    @ObservationIgnored private let repository: SpikeRepository

    init() {
        databaseDirectory = applicationSupportDirectory()
        repository = createSpikeRepository()
    }

    /// Runs for as long as the calling task (the view's `.task`) lives.
    func observe() async {
        for await list in repository.notes() {
            notes = list
        }
    }

    func add(_ text: String) async {
        do {
            try await repository.add(text: text)
        } catch {
            lastError = String(describing: error)
        }
    }
}
