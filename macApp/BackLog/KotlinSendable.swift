import BackLogCore

// Kotlin classes reach Swift as plain ObjC classes, which Swift 6 treats as
// non-Sendable, so passing one across actors (a SKIE `async` method's
// receiver or result) is a "sending risks data race" error. Declare the
// thread-safe and immutable shared types here, one line per type.

// An immutable data class, returned from an async function to the main actor.
extension DatabaseStatus: @retroactive @unchecked Sendable {}
