import BackLogCore

// Kotlin classes reach Swift as plain ObjC classes, which Swift 6 treats as
// non-Sendable, so calling a SKIE `async` method on one from the main actor is
// a "sending risks data race" error. The shared repositories are thread-safe
// (SQLDelight driver + coroutine dispatcher), so declare that here, one line
// per repository type.
extension SpikeRepository: @retroactive @unchecked Sendable {}
