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
package org.spongepowered.downloads.versions;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import io.vavr.control.Try;
import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RegexValidations {

    @Test
    public void ValidateTagVersionRegistration() {
        final var patternRegex = Pattern.compile(
            "^\\d\\.\\d{1,2}(\\.\\d{1,2})?(-((rc)|(pre))\\d)?-(\\d{1,2}\\.\\d{1,2})\\.\\d$"
        );
        final var valids = List.of("1.12.2-7.3.0", "1.16.5-8.0.0", "1.9-4.1.0");
        final var invalids = List.of("1.12.2-7.3.0-RC1723", "1.16.5-8.0.0-RC495", "1.12.2-2838-7.3.1-RC3482");
        final var regex = Try.of(() -> patternRegex);
        final var validSuccess = valids
            .map(valid -> regex.map(pattern -> pattern.matcher(valid))
                .mapTry(Matcher::find).getOrElse(false)
            )
            .filter(b -> !b)
            .isEmpty();
        final var invalidSuccess = invalids
            .map(invalid -> regex.map(pattern -> pattern.matcher(invalid))
                .mapTry(Matcher::find)
                .getOrElse(() -> true)
            )
            .filter(b -> !b)
            .isEmpty();

        assertTrue(validSuccess);
        assertFalse(invalidSuccess);
    }
}
