package org.spongepowered.downloads.webhook;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize
    record SonatypeComponent(String id, String componentId, String format, String name, String group,
                             String version) {
}
