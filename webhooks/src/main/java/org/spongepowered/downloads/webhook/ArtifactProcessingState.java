package org.spongepowered.downloads.webhook;

public class ArtifactProcessingState {

    public enum State {
        EMPTY,
        MANIFEST_DOWNLOADED,
        COMMIT_MISSING,
        COMMIT_PRESENT,
        PROCESSED;
    }

    private final State state;

}
