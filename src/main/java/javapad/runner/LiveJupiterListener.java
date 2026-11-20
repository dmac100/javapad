package javapad.runner;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

// Registered through META-INF/services; does nothing unless JavaPad passed -Djavapad.live.
public class LiveJupiterListener implements TestExecutionListener {
	private final Map<String, Long> starts = new HashMap<String, Long>();

	static String className(TestIdentifier id) {
		Optional<TestSource> source = id.getSource();
		if(source.isPresent() && source.get() instanceof MethodSource) {
			return ((MethodSource)source.get()).getClassName();
		}
		return "";
	}

	public synchronized void testPlanExecutionStarted(TestPlan plan) {
		if(!LiveLog.enabled()) {
			return;
		}
		for(TestIdentifier root : plan.getRoots()) {
			for(TestIdentifier id : plan.getDescendants(root)) {
				if(id.isTest()) {
					LiveLog.event("PLAN", className(id), id.getDisplayName());
				}
			}
		}
	}

	public synchronized void executionStarted(TestIdentifier id) {
		if(!LiveLog.enabled() || !id.isTest()) {
			return;
		}
		starts.put(id.getUniqueId(), System.nanoTime());
		LiveLog.event("RUN", className(id), id.getDisplayName());
	}

	public synchronized void executionSkipped(TestIdentifier id, String reason) {
		if(LiveLog.enabled() && id.isTest()) {
			LiveLog.event("END", className(id), id.getDisplayName(), "K", "0", reason);
		}
	}

	public synchronized void executionFinished(TestIdentifier id, TestExecutionResult result) {
		if(!LiveLog.enabled()) {
			return;
		}
		Long start = starts.remove(id.getUniqueId());
		String seconds = start == null ? "0" : String.valueOf((System.nanoTime() - start) / 1e9);
		Throwable t = result.getThrowable().orElse(null);
		String message = t == null ? "" : String.valueOf(t.getMessage());
		if(id.isTest()) {
			String status = result.getStatus() == TestExecutionResult.Status.SUCCESSFUL ? "P"
				: result.getStatus() == TestExecutionResult.Status.ABORTED ? "K"
				: t instanceof AssertionError ? "F" : "E";
			LiveLog.event("END", className(id), id.getDisplayName(), status, seconds, message);
		} else if(result.getStatus() == TestExecutionResult.Status.FAILED) {
			// a class or engine that failed before its tests could run
			LiveLog.event("END", id.getLegacyReportingName(), "<setup>", "E", seconds, message);
		}
	}
}
