package org.spongepowered.synchronizer.test.worker;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.SystemReader;

import java.io.File;

public class TestGitSystemReader extends SystemReader {
    private static final SystemReader proxy = SystemReader.getInstance();
    private final File userGitConfig;

    public TestGitSystemReader(File userGitConfig) {
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
