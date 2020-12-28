package org.spongepowered.downloads.webhook;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize
    record SonatypeData(String timestamp, String nodeId, String initiator, String repositoryName, String action,
                        SonatypeComponent component) {
}
