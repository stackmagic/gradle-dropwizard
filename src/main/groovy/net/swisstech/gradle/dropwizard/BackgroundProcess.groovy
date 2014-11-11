package net.swisstech.gradle.dropwizard

/** simple util to launch a process, wait for a number of open ports and kill the process */
class BackgroundProcess {

	private final Process process;

	private BackgroundProcess(Process process) {
		this.process = process;
	}

	static BackgroundProcess launch(List<String> cmd, File workingDir = null) {
		Process process = ProcessUtil.launch(cmd, workingDir)
		return new BackgroundProcess(process)
	}

	BackgroundProcess waitForOpenPorts(Collection<Integer> ports, long timeout) {
		PortSnoop.waitForOpenPorts(process, ports as Set, timeout)
		return this
	}

	int shutdown() {
		return ProcessUtil.killAndWait(process)
	}
}
