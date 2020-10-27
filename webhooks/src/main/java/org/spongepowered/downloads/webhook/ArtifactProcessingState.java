package org.spongepowered.downloads.webhook;

public class ArtifactProcessingState {

    public enum State {
        EMPTY {
            @Override
            public boolean hasStarted() {
                return false;
            }
        },
        MANIFEST_DOWNLOADED {
            @Override
            public boolean hasStarted() {
                return true;
            }
        },
        COMMIT_MISSING {
            @Override
            public boolean hasStarted() {
                return true;
            }
        },
        COMMIT_PRESENT {
            @Override
            public boolean hasStarted() {
                return true;
            }
        },
        PROCESSED {
            @Override
            public boolean hasStarted() {
                return true;
            }
        };

        public abstract boolean hasStarted();
    }

    private final State state;
    private final String coordinates;
    private final String componentId;

    public static ArtifactProcessingState empty() {
        return new ArtifactProcessingState(State.EMPTY, "", "");
    }

    public ArtifactProcessingState(final State state, final String coordinates, final String s) {
        this.state = state;
        this.coordinates = coordinates;
        this.componentId = s;
    }

    public State getState() {
        return this.state;
    }

    public String getCoordinates() {
        return this.coordinates;
    }

    public String getComponentId() {
        return this.componentId;
    }
}
