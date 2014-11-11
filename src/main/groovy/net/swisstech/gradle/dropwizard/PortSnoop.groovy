package net.swisstech.gradle.dropwizard

import org.slf4j.*

import org.gradle.api.*
import org.gradle.api.tasks.*
import org.gradle.api.plugins.*

class PortSnoop {

	static final Logger LOG = LoggerFactory.getLogger(DropwizardPlugin.class)

	static void waitForOpenPorts(Process process, Set<Integer> ports, long timeout) {

		LOG.info("[${process}] waiting until all of these ports are open: ${ports}")
		long endTime = System.currentTimeMillis() + timeout

		// loop and sleep until all expected ports are open
		while (true) {
			Set<Integer> found   = [] as Set
			for (String port : ports) {
				def foundPid = "lsof -t -i :${port}".execute().text.trim()
				if (foundPid != null && foundPid.length() > 0) {
					found << Integer.parseInt(port)
				}
				if (foundPid != null && !foundPid.isEmpty() && foundPid as int != process.pid) {
					ProcessUtil.killAndWait(process)
					throw new InvalidUserDataException("Ports are spread across multiple processes, bailing! Go check out that other process with pid: ${foundPid}!")
				}
			}

			LOG.info("[${process}] Open ports right now: ${found}")
			if (found.containsAll(ports)) {
				break
			}

			try {
				int rv = process.exitValue()
				throw new InvalidUserDataException("Dropwizad process exited with exit code ${rv}")
			}
			catch (IllegalThreadStateException e) {
				// ignore, process is still running, all is good
			}

			if (System.currentTimeMillis() > endTime) {
				ProcessUtil.killAndWait(process)
				throw new InvalidUserDataException("Timeout while waiting for dropwizard to start (was waiting for ports: ${ports})")
			}

			sleep(100)
		}
	}
}
