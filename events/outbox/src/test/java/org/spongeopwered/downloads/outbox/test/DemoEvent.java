package org.spongeopwered.downloads.outbox.test;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.spongepowered.downloads.events.EventMarker;

@JsonSerialize
public record DemoEvent(String foo, String bar) implements EventMarker {

    @Override
    public String topic() {
        return "foo";
    }

    @Override
    public String partitionKey() {
        return foo;
    }
}
