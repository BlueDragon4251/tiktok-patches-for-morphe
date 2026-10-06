package app.morphe.extension.tiktok.featurecontrols;

import org.junit.Test;
import static org.junit.Assert.*;

public class AccountServiceResolutionTest {
    public interface UserApi { boolean isLogin(); }
    public interface AccountApi { UserImpl renamedUserAccessor(); String region(); }
    public interface AmbiguousApi { UserImpl first(); UserImpl second(); }
    public static class UserImpl implements UserApi { public boolean isLogin() { return true; } }
    public static class AccountImpl implements AccountApi {
        public final UserImpl user = new UserImpl();
        public UserImpl renamedUserAccessor() { return user; }
        public String region() { return "AT"; }
    }
    public static class Manager {
        Object direct, account;
        public Object getService(Class<?> api) { return api == UserApi.class ? direct : account; }
    }
    @Test public void userServiceCanBeObtainedThroughTheRegisteredAccountService() throws Exception {
        Manager manager = new Manager();
        AccountImpl account = new AccountImpl();
        manager.account = account;
        assertSame(account.user, FeatureControls.resolveUserService(manager, Manager.class, UserApi.class, AccountApi.class));
    }
    @Test public void directRegistrationRemainsAvailableWhenTheAccountServiceIsAbsent() throws Exception {
        Manager manager = new Manager(); manager.direct = new UserImpl();
        assertSame(manager.direct, FeatureControls.resolveUserService(manager, Manager.class, UserApi.class, AccountApi.class));
    }
    @Test public void nativeAccountAccessorTakesPrecedenceOverAnUnregisteredServiceProxy() throws Exception {
        Manager manager = new Manager(); AccountImpl account = new AccountImpl(); manager.account = account;
        manager.direct = java.lang.reflect.Proxy.newProxyInstance(UserApi.class.getClassLoader(),
                new Class<?>[]{UserApi.class}, (proxy, method, args) -> false);
        assertSame(account.user, FeatureControls.resolveUserService(manager, Manager.class, UserApi.class, AccountApi.class));
        manager.account = null;
        assertNull(FeatureControls.resolveUserService(manager, Manager.class, UserApi.class, AccountApi.class));
    }
    @Test public void unresolvedAndAmbiguousAccessorsFailOpen() throws Exception {
        Manager manager = new Manager();
        assertNull(FeatureControls.resolveUserService(manager, Manager.class, UserApi.class, AccountApi.class));
        manager.account = new Object();
        assertNull(FeatureControls.resolveUserService(manager, Manager.class, UserApi.class, AmbiguousApi.class));
    }
}
