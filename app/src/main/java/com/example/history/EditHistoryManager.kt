package com.example.history

data class HistoryEntryUi(
    val index: Int, // 1-based position in linear timeline (0 = initial state)
    val label: String,
    val category: CommandCategory,
    val isApplied: Boolean,
    val isCurrent: Boolean
)

data class HistorySnapshotUi(
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val undoLabel: String? = null,
    val redoLabel: String? = null,
    val appliedCount: Int = 0,
    val totalCount: Int = 0,
    val entries: List<HistoryEntryUi> = emptyList()
)

data class HistoryStepResult(
    val newState: EditorDocumentState,
    val commandLabel: String,
    val summary: HistorySnapshotUi
)

/**
 * Command-pattern Undo/Redo history manager supporting:
 * - Reversible command execution, undo, and redo
 * - Time-windowed command coalescing for smooth continuous drags/sliders
 * - Explicit coalescing barriers (e.g., pointer-up / gesture-end)
 * - Random-access jump to any historical checkpoint
 * - Bounded memory capacity
 */
class EditHistoryManager(
    private val maxCapacity: Int = 60,
    private val coalesceWindowMs: Long = 900L
) {
    private val undoStack = ArrayDeque<EditCommand>()
    private val redoStack = ArrayDeque<EditCommand>()
    private var coalescingBroken: Boolean = false

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun execute(
        command: EditCommand,
        currentState: EditorDocumentState
    ): HistoryStepResult? {
        val nextState = command.execute(currentState)
        if (nextState == currentState) return null

        val top = undoStack.lastOrNull()
        val canMerge = !coalescingBroken &&
            top != null &&
            command.mergeKey != null &&
            top.mergeKey == command.mergeKey &&
            (command.timestampMs - top.timestampMs) in 0L..coalesceWindowMs

        val merged = if (canMerge) top?.mergeWith(command) else null
        if (merged != null) {
            undoStack.removeLast()
            undoStack.addLast(merged)
        } else {
            undoStack.addLast(command)
            if (undoStack.size > maxCapacity) {
                undoStack.removeFirst()
            }
        }

        coalescingBroken = false
        redoStack.clear()

        return HistoryStepResult(
            newState = nextState,
            commandLabel = (merged ?: command).label,
            summary = snapshotUi()
        )
    }

    /**
     * Records an already-applied live command (such as an overlay drag gesture whose
     * final state is already reflected on screen) without re-executing mutations.
     */
    fun recordExecuted(command: EditCommand): HistorySnapshotUi {
        undoStack.addLast(command)
        if (undoStack.size > maxCapacity) {
            undoStack.removeFirst()
        }
        coalescingBroken = true
        redoStack.clear()
        return snapshotUi()
    }

    fun undo(currentState: EditorDocumentState): HistoryStepResult? {
        val command = undoStack.removeLastOrNull() ?: return null
        val restored = command.undo(currentState)
        redoStack.addLast(command)
        coalescingBroken = true
        return HistoryStepResult(
            newState = restored,
            commandLabel = command.label,
            summary = snapshotUi()
        )
    }

    fun redo(currentState: EditorDocumentState): HistoryStepResult? {
        val command = redoStack.removeLastOrNull() ?: return null
        val reapplied = command.execute(currentState)
        undoStack.addLast(command)
        coalescingBroken = true
        return HistoryStepResult(
            newState = reapplied,
            commandLabel = command.label,
            summary = snapshotUi()
        )
    }

    /**
     * Jumps directly to [targetAppliedCount] (0 = initial loaded state before all commands,
     * up to `undoStack.size + redoStack.size`), executing or undoing commands sequentially.
     */
    fun jumpTo(
        targetAppliedCount: Int,
        currentState: EditorDocumentState
    ): HistoryStepResult? {
        val total = undoStack.size + redoStack.size
        val clamped = targetAppliedCount.coerceIn(0, total)
        if (clamped == undoStack.size) return null

        var state = currentState
        var lastLabel = "Initial State"

        while (undoStack.size > clamped) {
            val cmd = undoStack.removeLast()
            state = cmd.undo(state)
            redoStack.addLast(cmd)
            lastLabel = cmd.label
        }
        while (undoStack.size < clamped) {
            val cmd = redoStack.removeLast()
            state = cmd.execute(state)
            undoStack.addLast(cmd)
            lastLabel = cmd.label
        }

        coalescingBroken = true
        return HistoryStepResult(
            newState = state,
            commandLabel = if (clamped == 0) "Initial State" else lastLabel,
            summary = snapshotUi()
        )
    }

    /**
     * Breaks coalescing so the next property/drag edit starts a distinct undo step.
     */
    fun breakCoalescing() {
        coalescingBroken = true
    }

    fun clear(): HistorySnapshotUi {
        undoStack.clear()
        redoStack.clear()
        coalescingBroken = false
        return snapshotUi()
    }

    fun snapshotUi(): HistorySnapshotUi {
        val applied = undoStack.toList()
        val future = redoStack.toList().reversed()
        val allEntries = buildList {
            applied.forEachIndexed { idx, cmd ->
                add(
                    HistoryEntryUi(
                        index = idx + 1,
                        label = cmd.label,
                        category = cmd.category,
                        isApplied = true,
                        isCurrent = idx == applied.lastIndex
                    )
                )
            }
            future.forEachIndexed { idx, cmd ->
                add(
                    HistoryEntryUi(
                        index = applied.size + idx + 1,
                        label = cmd.label,
                        category = cmd.category,
                        isApplied = false,
                        isCurrent = false
                    )
                )
            }
        }
        return HistorySnapshotUi(
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty(),
            undoLabel = undoStack.lastOrNull()?.label,
            redoLabel = redoStack.lastOrNull()?.label,
            appliedCount = applied.size,
            totalCount = allEntries.size,
            entries = allEntries
        )
    }
}
