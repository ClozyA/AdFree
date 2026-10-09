package xyz.fearr.adfree;

import org.junit.Test;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

public class ScopeControllerTest {
    private static final String PACKAGE = "run.xbud.android";
    private static class FakeGateway implements ScopeController.Gateway {
        final Set<String> scope = new HashSet<>();
        ScopeController.Callback callback;
        boolean refuseRemoval;
        @Override public Set<String> readScope() { return new HashSet<>(scope); }
        @Override public void requestScope(String name, ScopeController.Callback callback) { this.callback = callback; }
        @Override public void removeScope(String name) { if (!refuseRemoval) scope.remove(name); }
    }
    private ScopeController controller(FakeGateway fake) {
        return new ScopeController(() -> fake, Runnable::run, () -> {});
    }
    @Test public void approvalDoesNotOptimisticallyEnable() {
        FakeGateway fake = new FakeGateway();
        ScopeController controller = controller(fake);
        controller.setEnabled(PACKAGE, true);
        assertFalse(controller.snapshot().packages().contains(PACKAGE));
        assertTrue(controller.snapshot().pending().contains(PACKAGE));
        fake.scope.add(PACKAGE);
        fake.callback.approved();
        assertTrue(controller.snapshot().packages().contains(PACKAGE));
        assertTrue(controller.snapshot().pending().isEmpty());
        assertTrue(controller.snapshot().errors().isEmpty());
    }
    @Test public void approvalWithoutFrameworkChangeReportsFailure() {
        FakeGateway fake = new FakeGateway();
        ScopeController controller = controller(fake);
        controller.setEnabled(PACKAGE, true);
        fake.callback.approved();
        assertFalse(controller.snapshot().packages().contains(PACKAGE));
        assertTrue(controller.snapshot().errors().containsKey(PACKAGE));
    }
    @Test public void rejectedRequestRetainsActualScope() {
        FakeGateway fake = new FakeGateway();
        ScopeController controller = controller(fake);
        controller.setEnabled(PACKAGE, true);
        fake.callback.failed("denied");
        assertEquals("denied", controller.snapshot().errors().get(PACKAGE));
        assertFalse(controller.snapshot().packages().contains(PACKAGE));
        assertTrue(controller.snapshot().pending().isEmpty());
    }
    @Test public void failedRemovalCannotLookDisabled() {
        FakeGateway fake = new FakeGateway();
        fake.scope.add(PACKAGE);
        fake.refuseRemoval = true;
        ScopeController controller = controller(fake);
        controller.setEnabled(PACKAGE, false);
        assertTrue(controller.snapshot().packages().contains(PACKAGE));
        assertTrue(controller.snapshot().errors().containsKey(PACKAGE));
    }
    @Test public void removalIsReadBack() {
        FakeGateway fake = new FakeGateway();
        fake.scope.add(PACKAGE);
        ScopeController controller = controller(fake);
        controller.setEnabled(PACKAGE, false);
        assertFalse(controller.snapshot().packages().contains(PACKAGE));
        assertTrue(controller.snapshot().errors().isEmpty());
    }
    @Test public void staleCallbackCannotFinishNewRequest() {
        FakeGateway old = new FakeGateway();
        FakeGateway next = new FakeGateway();
        AtomicReference<ScopeController.Gateway> gateway = new AtomicReference<>(old);
        ScopeController controller = new ScopeController(gateway::get, Runnable::run, () -> {});
        controller.setEnabled(PACKAGE, true);
        gateway.set(next);
        controller.connectionChanged();
        controller.setEnabled(PACKAGE, true);
        old.callback.approved();
        assertTrue(controller.snapshot().pending().contains(PACKAGE));
        next.scope.add(PACKAGE);
        next.callback.approved();
        assertTrue(controller.snapshot().pending().isEmpty());
        assertTrue(controller.snapshot().packages().contains(PACKAGE));
    }
    @Test public void disconnectedScopeIsUnavailable() {
        ScopeController controller = new ScopeController(() -> null, Runnable::run, () -> {});
        assertFalse(controller.snapshot().available());
        controller.setEnabled(PACKAGE, true);
        assertTrue(controller.snapshot().errors().containsKey(PACKAGE));
        assertTrue(controller.snapshot().pending().isEmpty());
    }
    @Test public void installedVersionAndBuildBothAffectWarning() {
        TargetCatalog.Target target = TargetCatalog.find(PACKAGE);
        assertNotNull(target);
        assertTrue(target.matches("2.6.8", 150));
        assertFalse(target.matches("2.6.9", 150));
        assertFalse(target.matches("2.6.8", 151));
    }
}
