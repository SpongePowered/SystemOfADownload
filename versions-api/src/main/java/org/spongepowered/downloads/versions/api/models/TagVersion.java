/*
 * This file is part of SystemOfADownload, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://spongepowered.org/>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.downloads.versions.api.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.vavr.collection.List;

public interface TagVersion {

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = Request.SetRecommendationRegex.class, name = "recommendation")
    })
    interface Request {

        @JsonDeserialize
        final record SetRecommendationRegex(
            String regex,
            List<String> valid,
            List<String> invalid,
            boolean enableManualMarking
        ) implements Request {

            @JsonCreator
            public SetRecommendationRegex(
                @JsonProperty(required = true) final String regex,
                @JsonProperty(required = true) final List<String> valid,
                @JsonProperty(required = true) final List<String> invalid,
                @JsonProperty(required = true) final boolean enableManualMarking
            ) {
                this.regex = regex;
                this.enableManualMarking = enableManualMarking;
                this.valid = valid;
                this.invalid = invalid;
            }
        }
    }

    @JsonSubTypes({
        @JsonSubTypes.Type(value = TagVersion.Response.TagSuccessfullyRegistered.class, name = "Success")
    })
    interface Response {

        @JsonSerialize
        final record TagSuccessfullyRegistered() implements TagVersion.Response {}

    }
}
