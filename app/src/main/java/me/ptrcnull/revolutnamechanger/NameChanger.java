package me.ptrcnull.revolutnamechanger;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Objects;

public class NameChanger implements IXposedHookLoadPackage {
    public static XC_MethodHook skipAndPrint(String message) {
        return new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                XposedBridge.log(message);
                param.setResult(null);
            }
        };
    }

    private static class RootCheckerBypass extends XC_MethodHook {
        private final Field field;

        private RootCheckerBypass(Field field) {
            this.field = field;
        }

        @Override
        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
            Class<?> actualClass = Objects.requireNonNull(field.get(param.thisObject)).getClass();
            XposedBridge.hookAllMethods(actualClass, "onCreate", skipAndPrint("rootChecker oncreate"));
            XposedBridge.hookAllMethods(actualClass, "onStart", skipAndPrint("rootChecker onstart"));
            XposedBridge.hookAllMethods(actualClass, "onStop", skipAndPrint("rootChecker onstop"));
            XposedBridge.hookAllMethods(actualClass, "onDestroy", skipAndPrint("rootChecker ondestroy"));
        }
    }

    private static Field findActivityField(Field[] fields) throws Exception {
        for (Field field : fields) {
            for (Method method : field.getType().getMethods()) {
                if (method.getName().equals("onStart")) {
                    return field;
                }
            }
        }
        throw new Exception("Could not find root checker activity");
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadedPackage) throws Throwable {
        if (!Objects.equals(loadedPackage.packageName, "com.revolut.revolut")) {
            return;
        }

        XposedBridge.log("awoo");

        // massive heuristics
        final Class<?> loginActivity = XposedHelpers.findClass("com.revolut.ui.login.pin.LoginActivity", loadedPackage.classLoader);
        Field rootCheckerActivityField = findActivityField(loginActivity.getDeclaredFields());
        XposedBridge.hookAllMethods(loginActivity, "onCreate", new RootCheckerBypass(rootCheckerActivityField));

        final Class<?> userClass = XposedHelpers.findClass("sm0.b", loadedPackage.classLoader);
        XposedBridge.hookAllConstructors(userClass, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                param.args[1] = "Name";
            }
        });

        XposedBridge.log("initialized");
    }
}
