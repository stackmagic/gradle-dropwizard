package net.swisstech.gradle.dropwizard

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/** util class to start and stop system processes */
class ProcessUtil {

	static final Logger LOG = LoggerFactory.getLogger(ProcessUtil.class)

	static Process launch(String command, File workingDir = null) {
		return launch(command.split(' '), workingDir);
	}

	static int launchAndWait(String command, File workingDir = null) {
		return launch(command, workingDir).waitFor();
	}

	static Process launch(String[] command, File workingDir = null) {
		return launch(command as List, workingDir);
	}

	static int launchAndWait(String[] command, File workingDir = null) {
		return launch(command, workingDir).waitFor();
	}

	static Process launch(List<String> command, File workingDir = null) {
		ProcessBuilder pb = new ProcessBuilder(command)
		pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
		pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
		pb.redirectError(ProcessBuilder.Redirect.INHERIT);
		if (workingDir != null) {
			pb.directory(workingDir);
		}
		return pb.start();
	}

	static int launchAndWait(List<String> command, File workingDir = null) {
		return launch(command, workingDir).waitFor();
	}

	static int killAndWait(Process process) {
		LOG.info("killAndWait for process with pid ${process?.pid}")
		process.destroy();
		process.waitFor();
		return process.exitValue();
	}

	static int killAndWaitMustSucceed(Process process) throws IOException {
		LOG.info("killAndWaitMustSucceed for process with pid ${process?.pid}")
		int rv = killAndWait(process)
		if (rv != 0) {
			throw new IOException("Kill command returned with non-zero exit value from trying to kill process with pid ${process.pid}")
		}
	}

	static int killByPid(int pid, Signal signal) {
		LOG.info("killByPid for with signal ${signal?.name()} for pid ${pid}")
		String cmd = "/bin/kill -${signal.name()} ${pid}"
		return launchAndWait(cmd)
	}
}
