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
package org.spongepowered.downloads.versions.util.jgit;

import org.eclipse.jgit.lib.BatchingProgressMonitor;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.slf4j.Logger;

import java.util.Objects;

public final class ActorLoggerPrinterWriter extends BatchingProgressMonitor implements ProgressMonitor {
    private final Logger logger;

    public ActorLoggerPrinterWriter(Logger logger) {
        this.logger = logger;
    }

    @Override
    protected void onUpdate(final String taskName, final int workCurr) {
        StringBuilder s = new StringBuilder();
        format(s, taskName, workCurr);
        if (this.logger.isDebugEnabled()) {
            this.logger.debug(s.toString());
        }
    }

    private void format(StringBuilder s, String taskName, int workCurr) {
        s.append("\r"); //$NON-NLS-1$
        s.append(taskName);
        s.append(": "); //$NON-NLS-1$
        while (s.length() < 25)
            s.append(' ');
        s.append(workCurr);
    }
    @Override
    protected void onEndTask(final String taskName, final int workCurr) {
        StringBuilder s = new StringBuilder();
        format(s, taskName, workCurr);
        if (this.logger.isDebugEnabled()) {
            this.logger.debug(s.toString());
        }
    }

    @Override
    protected void onUpdate(final String taskName, final int workCurr, final int workTotal, final int percentDone) {
        StringBuilder s = new StringBuilder();
        format(s, taskName, workCurr, workTotal, percentDone);
    }

    @Override
    protected void onEndTask(final String taskName, final int workCurr, final int workTotal, final int percentDone) {
        StringBuilder s = new StringBuilder();
        format(s, taskName, workCurr, workTotal, percentDone);
    }

    private void format(StringBuilder s, String taskName, int cmp,
        int totalWork, int pcnt) {
        s.append("\r"); //$NON-NLS-1$
        s.append(taskName);
        s.append(": "); //$NON-NLS-1$
        while (s.length() < 25)
            s.append(' ');

        String endStr = String.valueOf(totalWork);
        StringBuilder curStr = new StringBuilder(String.valueOf(cmp));
        while (curStr.length() < endStr.length())
            curStr.insert(0, " "); //$NON-NLS-1$
        if (pcnt < 100)
            s.append(' ');
        if (pcnt < 10)
            s.append(' ');
        s.append(pcnt);
        s.append("% ("); //$NON-NLS-1$
        s.append(curStr);
        s.append("/"); //$NON-NLS-1$
        s.append(endStr);
        s.append(")"); //$NON-NLS-1$
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (ActorLoggerPrinterWriter) obj;
        return Objects.equals(this.logger, that.logger);
    }

    @Override
    public int hashCode() {
        return Objects.hash(logger);
    }

    @Override
    public String toString() {
        return "ActorLoggerPrinterWriter[" +
            "logger=" + logger + ']';
    }

}
