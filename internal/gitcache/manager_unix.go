//go:build !windows

package gitcache

import (
	"os/exec"
	"syscall"
)

// configureProcessGroup puts git in its own process group so Cancel can
// SIGKILL the whole group (including git-remote-https), not just the direct
// child. This is the Unix-only piece of the zombie/pipe-inheritance fix;
// Windows uses a no-op because it lacks POSIX process groups.
func configureProcessGroup(cmd *exec.Cmd) {
	cmd.SysProcAttr = &syscall.SysProcAttr{Setpgid: true}
	cmd.Cancel = func() error {
		if cmd.Process == nil {
			return nil
		}
		// Negative pid targets the whole process group.
		return syscall.Kill(-cmd.Process.Pid, syscall.SIGKILL)
	}
}
