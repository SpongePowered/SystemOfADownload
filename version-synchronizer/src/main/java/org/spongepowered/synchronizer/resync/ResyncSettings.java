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
package org.spongepowered.synchronizer.resync;

import akka.actor.Extension;
import com.typesafe.config.Config;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class ResyncSettings implements Extension {
    public final String repository;
    public final Duration timeout;
    public final int retryCount;
    public final String agentName;

    public ResyncSettings(Config config) {
        this.repository = config.getString("systemofadownload.synchronizer.resync.repository");
        this.retryCount = config.getInt("systemofadownload.synchronizer.resync.retry");
        final var seconds = config.getDuration(
            "systemofadownload.synchronizer.resync.timeout", TimeUnit.SECONDS);
        this.agentName = config.getString("systemofadownload.synchronizer.resync.agent-name");
        this.timeout = Duration.ofSeconds(seconds);
    }
}
