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
package org.spongepowered.synchronizer.gitmanaged.util.jgit;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.SystemReader;

import java.io.File;
import java.net.URL;

public class StubSystemReader extends SystemReader {

    private static final class Holder {
        private static final URL dummyGitConfig = Holder.class.getClassLoader().getResource(
            "soad.gitconfig");
        private static final File gitConfig = new File(dummyGitConfig.getFile());
        static final SystemReader INSTANCE = new StubSystemReader(gitConfig);
        static {
            SystemReader.setInstance(INSTANCE);
        }
    }

    public static SystemReader getInstance() {
        return Holder.INSTANCE;
    }

    public static void init() {
        final var instance = getInstance();
        if (SystemReader.getInstance() != instance) {
            SystemReader.setInstance(instance);
        }
    }

    private static final SystemReader proxy = SystemReader.getInstance();
    private final File userGitConfig;

    public StubSystemReader(File userGitConfig) {
        super();
        this.userGitConfig = userGitConfig;
    }

    @Override
    public String getenv(String variable) {
        return proxy.getenv(variable);
    }

    @Override
    public String getHostname() {
        return proxy.getHostname();
    }

    @Override
    public String getProperty(String key) {
        return proxy.getProperty(key);
    }

    @Override
    public long getCurrentTime() {
        return proxy.getCurrentTime();
    }

    @Override
    public int getTimezone(long when) {
        return proxy.getTimezone(when);
    }

    @Override
    public FileBasedConfig openUserConfig(Config parent, FS fs) {
        return new FileBasedConfig(parent, userGitConfig, fs);
    }

    // Return an empty system configuration, based on example in SystemReader.Default#openSystemConfig
    @Override
    public FileBasedConfig openSystemConfig(Config parent, FS fs) {
        return new FileBasedConfig(parent, this.userGitConfig, fs) {
            @Override
            public void load() {
            }

            @Override
            public boolean isOutdated() {
                return false;
            }
        };
    }

    @Override
    public FileBasedConfig openJGitConfig(final Config parent, final FS fs) {
        return new FileBasedConfig(parent, this.userGitConfig, fs) {
            @Override
            public void load() {
            }

            @Override
            public boolean isOutdated() {
                return false;
            }
        };
    }
}
