package net.swisstech.gradle.dropwizard

import org.slf4j.*

import org.gradle.api.*
import org.gradle.api.tasks.*
import org.gradle.api.plugins.*

/** util to check for open ports with a timeout, to wait until a process is ready so you can run tests against it */
class PortSnoop {

	static final Logger LOG = LoggerFactory.getLogger(DropwizardPlugin.class)

	/** wait for the process to be the owner of all the ports or time is running out */
	static void waitForOpenPorts(Process process, Set<Integer> ports, long timeout) {

		LOG.info("[${process}] waiting until all of these ports are open: ${ports}")
		long start = System.currentTimeMillis()
		long end   = start + timeout

		// loop and sleep until all expected ports are open
		while (true) {

			// get what processes have opened the ports we're interested in
			Map<Integer, Integer> portToPid = getPortToPid(ports)
			LOG.debug("[${process}] Ports open right now with their pids: ${portToPid}")

			// check for multiple processes owning our ports
			// TODO some programs may start subprocesses with new pids and we may allow for that
			Set<Integer> pids = [] as Set
			pids.addAll(portToPid.values())

			if (!pids.isEmpty() && pids.size() != 1) {
				ProcessUtil.killAndWait(process)
				throw new InvalidUserDataException("Ports are spread across multiple processes, bailing! Go check out the involved processes: ${pids}!")
			}

			if (!pids.isEmpty() && !pids.contains(process.pid)) {
				ProcessUtil.killAndWait(process)
				throw new InvalidUserDataException("Port is owned by a different process, bailing! Go check out the other process: ${pids}!")
			}

			// see if we've got all ports we want
			Set<Integer> found = portToPid.keySet()
			if (found.containsAll(ports) && ports.containsAll(found)) {
				long duration = System.currentTimeMillis() - start
				LOG.debug("[${process}] All ports we want are open! Process is ready after ${duration} millis")
				break
			}

			// process must still be running, if it exited something's bad
			try {
				int rv = process.exitValue()
				throw new InvalidUserDataException("Process has exited with exit code ${rv}")
			}
			catch (IllegalThreadStateException e) {
				// ignore, process is still running, all is good
			}

			// timeout!
			if (System.currentTimeMillis() > end) {
				ProcessUtil.killAndWait(process)
				throw new InvalidUserDataException("Timeout while waiting for process to start (was waiting for ports: ${ports}, had ${portToPid}, waited for ${timeout} millis)")
			}

			// wait a bit before checking again
			sleep(100)
		}
	}

	/**
	 * returns a map of the supplied ports mapping to the process pid owning the open port. if there's no entry for a
	 * supplied port in the map, there's no process owning it (yet)
	 */
	static Map<Integer, Integer> getPortToPid(Set<Integer> ports) {
		Map<Integer, Integer> map = [:]
		for (Integer port : ports) {
			def pid = "lsof -t -i :${port}".execute().text.trim()
			if (pid != null && pid.length() > 0) {
				map[port] = Integer.parseInt(pid)
			}
		}
		return map
	}
}
