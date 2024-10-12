package org.spongepowered.downloads.events;

/**
 * Marker interface for Event serialization via Jackson
 */
public interface EventMarker {

    String topic();

    String partitionKey();

}
