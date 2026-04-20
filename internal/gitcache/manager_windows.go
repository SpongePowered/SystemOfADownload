package gitcache

import "os/exec"

// configureProcessGroup is a no-op on Windows: POSIX process groups don't
// exist, and exec.CommandContext already calls TerminateProcess on the
// direct child when the context is cancelled. WaitDelay still handles the
// grandchild-pipe case. Release binaries for Windows are built for
// completeness but the production worker runs on Linux.
func configureProcessGroup(_ *exec.Cmd) {}
